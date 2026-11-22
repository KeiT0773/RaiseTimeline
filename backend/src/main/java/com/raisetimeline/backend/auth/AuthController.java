package com.raisetimeline.backend.auth;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raisetimeline.backend.auth.token.AuthCookies;

/**
 * 認証の API（API 設計書 3.）。トークンは Set-Cookie でだけ渡し、応答の本文には出さない。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final String REFRESH_FAILED = "ログインの有効期限が切れました。もう一度ログインしてください";

	private final AuthService authService;
	private final AuthCookies authCookies;

	public AuthController(AuthService authService, AuthCookies authCookies) {
		this.authService = authService;
		this.authCookies = authCookies;
	}

	@PostMapping("/signup")
	public ResponseEntity<MeResponse> signup(@Valid @RequestBody SignupRequest request) {
		AuthService.AuthResult result = authService.signup(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.headers(authCookies.issue(result.tokens().accessToken(), result.tokens().refreshToken()))
				.body(MeResponse.from(result.user()));
	}

	@PostMapping("/login")
	public ResponseEntity<MeResponse> login(@Valid @RequestBody LoginRequest request) {
		AuthService.AuthResult result = authService.login(request);
		return ResponseEntity.ok()
				.headers(authCookies.issue(result.tokens().accessToken(), result.tokens().refreshToken()))
				.body(MeResponse.from(result.user()));
	}

	/**
	 * トークンの更新。失敗したら 401 を返し、Cookie も削除する（画面はログイン画面へ移す）。
	 * 例外ではなく戻り値で 401 を返すのは、Set-Cookie（削除）を確実に応答へ載せるため。
	 */
	@PostMapping("/refresh")
	public ResponseEntity<?> refresh(@CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken) {
		if (refreshToken == null || refreshToken.isEmpty()) {
			return unauthorized(REFRESH_FAILED, "/api/auth/refresh");
		}
		return authService.refresh(refreshToken)
				.<ResponseEntity<?>>map(tokens -> ResponseEntity.noContent()
						.headers(authCookies.issue(tokens.accessToken(), tokens.refreshToken()))
						.build())
				.orElseGet(() -> unauthorized(REFRESH_FAILED, "/api/auth/refresh"));
	}

	/** ログアウト。アクセストークンが期限切れでも呼べる。リフレッシュトークンが無くても成功する。 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken) {
		if (refreshToken != null && !refreshToken.isEmpty()) {
			authService.logout(refreshToken);
		}
		return ResponseEntity.noContent().headers(authCookies.clear()).build();
	}

	/**
	 * ログイン中の利用者の情報。未ログインのときは、ここに来る前に Spring Security が 401 を返す。
	 * JWT の sub（ユーザー ID）から利用者を引く。
	 */
	@GetMapping("/me")
	public ResponseEntity<?> me(@AuthenticationPrincipal Jwt jwt) {
		return authService.findUser(Long.parseLong(jwt.getSubject()))
				.<ResponseEntity<?>>map(user -> ResponseEntity.ok(MeResponse.from(user)))
				// トークンは有効でも、ユーザーが存在しない（削除された）場合
				.orElseGet(() -> unauthorized("ログインしてください", "/api/auth/me"));
	}

	private ResponseEntity<ProblemDetail> unauthorized(String detail, String instance) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
		problem.setInstance(URI.create(instance));
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).headers(authCookies.clear()).body(problem);
	}

}
