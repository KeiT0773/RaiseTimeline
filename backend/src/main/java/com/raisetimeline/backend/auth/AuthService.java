package com.raisetimeline.backend.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.raisetimeline.backend.auth.token.JwtTokenService;
import com.raisetimeline.backend.auth.token.RefreshTokenService;
import com.raisetimeline.backend.common.FieldErrorResponse;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserMapper;

/**
 * 新規登録・ログイン・トークンの更新・ログアウト（auth.md F-01〜F-04）。
 * HTTP（Cookie）の扱いは AuthController が行い、ここはトークンの値を返すだけにする。
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

	static final String USERNAME_TAKEN = "このユーザー名は既に使われています";
	static final String EMAIL_TAKEN = "このメールアドレスは既に登録されています";

	/** V1__create_users.sql の制約名。同時登録で DB の一意制約に当たったとき、どの項目かを判定する。 */
	private static final String USERNAME_CONSTRAINT = "uq_users_username_lower";
	private static final String EMAIL_CONSTRAINT = "uq_users_email";

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtTokenService jwtTokenService;
	private final RefreshTokenService refreshTokenService;

	public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
			JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtTokenService = jwtTokenService;
		this.refreshTokenService = refreshTokenService;
	}

	/** 発行したトークンの組。値は Cookie にだけ入れ、応答の本文には出さない。 */
	public record Tokens(String accessToken, String refreshToken) {
	}

	/** 利用者とトークン。新規登録・ログインの結果。 */
	public record AuthResult(User user, Tokens tokens) {
	}

	/**
	 * 新規登録し、そのままログイン状態にする（F-01）。
	 *
	 * @throws DuplicateFieldException ユーザー名・メールアドレスが既に使われているとき
	 */
	@Transactional
	public AuthResult signup(SignupRequest request) {
		List<FieldErrorResponse> errors = new ArrayList<>();
		if (userMapper.existsByUsernameIgnoreCase(request.username())) {
			errors.add(new FieldErrorResponse("username", USERNAME_TAKEN));
		}
		if (userMapper.existsByEmail(request.email())) {
			errors.add(new FieldErrorResponse("email", EMAIL_TAKEN));
		}
		if (!errors.isEmpty()) {
			throw new DuplicateFieldException(errors);
		}

		User user = new User(request.username(), request.displayName(), request.email(),
				passwordEncoder.encode(request.password()));
		try {
			userMapper.insert(user);
		} catch (DuplicateKeyException e) {
			// 上の確認と登録の間に、同じ値で別の人が登録した場合
			throw new DuplicateFieldException(List.of(toFieldError(e)));
		}
		return new AuthResult(user, issueTokens(user.getId()));
	}

	/**
	 * メールアドレスとパスワードで認証する（F-02）。
	 * 失敗したときは AuthenticationManager が BadCredentialsException を投げる（GlobalExceptionHandler が 401 にする）。
	 */
	@Transactional
	public AuthResult login(LoginRequest request) {
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
		LoginUser loginUser = (LoginUser) authentication.getPrincipal();
		refreshTokenService.deleteExpired(loginUser.id());
		User user = userMapper.findById(loginUser.id());
		return new AuthResult(user, issueTokens(user.getId()));
	}

	/**
	 * リフレッシュトークンを使って、新しいトークンの組を発行する（F-04）。
	 * 使ったリフレッシュトークンは無効になる。使えないトークンなら空を返す。
	 */
	@Transactional
	public Optional<Tokens> refresh(String refreshToken) {
		return refreshTokenService.consume(refreshToken).map(this::issueTokens);
	}

	/** ログアウト（F-03）。リフレッシュトークンを無効にする。 */
	@Transactional
	public void logout(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
	}

	/** アクセストークンの持ち主。トークンが有効でも、ユーザーが存在しなければ空。 */
	public Optional<User> findUser(long userId) {
		return Optional.ofNullable(userMapper.findById(userId));
	}

	private Tokens issueTokens(long userId) {
		return new Tokens(jwtTokenService.issueAccessToken(userId), refreshTokenService.issue(userId));
	}

	/**
	 * どの一意制約に当たったかを、PostgreSQL のエラーメッセージに含まれる制約名で判定する
	 * （例：duplicate key value violates unique constraint "uq_users_email"）。
	 * JDBC ドライバのクラス（PSQLException）は実行時だけの依存（runtimeOnly）なので、コードからは参照しない。
	 */
	private static FieldErrorResponse toFieldError(DuplicateKeyException e) {
		String message = String.valueOf(e.getMostSpecificCause().getMessage());
		if (message.contains(USERNAME_CONSTRAINT)) {
			return new FieldErrorResponse("username", USERNAME_TAKEN);
		}
		if (message.contains(EMAIL_CONSTRAINT)) {
			return new FieldErrorResponse("email", EMAIL_TAKEN);
		}
		// users の一意制約はこの 2 つだけなので、ここには来ない
		throw e;
	}

}
