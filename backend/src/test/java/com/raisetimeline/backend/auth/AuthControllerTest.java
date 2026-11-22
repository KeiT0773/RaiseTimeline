package com.raisetimeline.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static com.raisetimeline.backend.CsrfSupport.withToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.raisetimeline.backend.CsrfSupport;
import com.raisetimeline.backend.auth.token.AuthCookies;
import com.raisetimeline.backend.auth.token.AuthProperties;

/**
 * 認証 API（API 設計書 3.）のテスト。受け入れ基準は auth.md 8.。
 * 実行前に docker compose up -d で PostgreSQL を起動しておく。
 * クラスに @Transactional を付け、登録したユーザー・トークンはテストの終了時にロールバックする。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private AuthProperties authProperties;

	/** 画面と同じく、最初の GET で受け取った CSRF のトークン。POST に付けて送る。 */
	private Cookie xsrf;

	@BeforeEach
	void fetchCsrfToken() {
		xsrf = CsrfSupport.fetchToken(mvc);
	}

	// --- F-01 新規登録 -------------------------------------------------------

	@Test
	void 新規登録するとそのままログイン状態になる() {
		MvcTestResult result = signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);

		assertThat(result).hasStatus(HttpStatus.CREATED)
				.bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.username").isEqualTo("Hanako");
					assertThat(json).extractingPath("$.displayName").isEqualTo("山田花子");
					assertThat(json).extractingPath("$.id").isNotNull();
				});
		assertThat(me(accessToken(result))).hasStatusOk()
				.bodyJson().extractingPath("$.username").isEqualTo("Hanako");
	}

	@Test
	void 新規登録で2つのトークンがHttpOnlyのCookieで発行される() {
		MvcTestResult result = signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);

		MockCookie access = (MockCookie) result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN);
		MockCookie refresh = (MockCookie) result.getResponse().getCookie(AuthCookies.REFRESH_TOKEN);
		assertThat(access).isNotNull();
		assertThat(access.isHttpOnly()).isTrue();
		assertThat(access.getPath()).isEqualTo("/api");
		assertThat(access.getMaxAge()).isEqualTo(15 * 60);
		assertThat(access.getSameSite()).isEqualTo("Lax");
		assertThat(refresh).isNotNull();
		assertThat(refresh.isHttpOnly()).isTrue();
		assertThat(refresh.getPath()).isEqualTo("/api/auth");
		assertThat(refresh.getMaxAge()).isEqualTo(14 * 24 * 60 * 60);
	}

	@Test
	void 応答の本文にパスワードのハッシュやトークンを含めない() throws Exception {
		MvcTestResult result = signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);

		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain("password", "Digest", "$2", accessToken(result), refreshToken(result));
	}

	@Test
	void パスワードはBCryptのハッシュで保存しメールアドレスは小文字にそろえる() {
		signup("Hanako", "山田花子", "  Hanako@Example.COM ", PASSWORD, PASSWORD);

		String digest = jdbc.queryForObject(
				"SELECT password_digest FROM users WHERE email = 'hanako@example.com'", String.class);
		assertThat(digest).startsWith("$2").isNotEqualTo(PASSWORD);
	}

	@Test
	void 大文字と小文字だけ違うユーザー名は登録できない() {
		signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);

		assertThat(signup("hanako", "別の人", "other@example.com", PASSWORD, PASSWORD))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.errors[0].field").isEqualTo("username");
					assertThat(json).extractingPath("$.errors[0].message").isEqualTo("このユーザー名は既に使われています");
					assertThat(json).extractingPath("$.errors.length()").isEqualTo(1);
				});
	}

	@Test
	void 登録済みのメールアドレスは大文字で入力しても登録できない() {
		signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);

		assertThat(signup("taro", "太郎", "HANAKO@example.com", PASSWORD, PASSWORD))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.errors[0].field").isEqualTo("email");
					assertThat(json).extractingPath("$.errors[0].message").isEqualTo("このメールアドレスは既に登録されています");
				});
	}

	@Test
	void ユーザー名とメールアドレスが両方重複していれば両方のエラーを返す() {
		signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);

		assertThat(signup("HANAKO", "別の人", "hanako@example.com", PASSWORD, PASSWORD))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.errors[*].field").asArray().containsExactly("username", "email");
	}

	@Test
	void 入力制約に合わない値は項目ごとのエラーを返す() {
		assertThat(signup("ab", "   ", "not-an-email", "1234567", "different"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.detail").isEqualTo("入力内容に誤りがあります");
					assertThat(json).extractingPath("$.errors[*].field").asArray()
							.contains("username", "displayName", "email", "password", "passwordConfirmation");
					assertThat(json).extractingPath("$.errors[?(@.field == 'username')].message").asArray()
							.containsExactly("ユーザー名は半角英数字と _ で 3〜15 文字にしてください");
					assertThat(json).extractingPath("$.errors[?(@.field == 'displayName')].message").asArray()
							.containsExactly("表示名を入力してください");
					assertThat(json).extractingPath("$.errors[?(@.field == 'password')].message").asArray()
							.containsExactly("パスワードは 8 文字以上にしてください");
					assertThat(json).extractingPath("$.errors[?(@.field == 'passwordConfirmation')].message").asArray()
							.containsExactly("パスワードが一致しません");
				});
	}

	@Test
	void JSONの形式が誤っていれば日本語の文言で400を返す() {
		assertThat(mvc.post().uri("/api/auth/signup").with(withToken(xsrf))
				.contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.detail").isEqualTo("要求の形式が正しくありません");
	}

	@Test
	void 文字数が72以内でも72バイトを超えるパスワードは登録できない() {
		// 全角文字は 1 文字が UTF-8 で 3 バイト。25 文字で 75 バイトになる
		String password = "あ".repeat(25);

		assertThat(signup("Hanako", "山田花子", "hanako@example.com", password, password))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.errors[?(@.field == 'password')].message").asArray()
				.containsExactly("パスワードが長すぎます。全角文字を減らしてください");
	}

	@Test
	void 表示名は絵文字も1文字として数える() {
		// 絵文字は Java の内部表現（UTF-16）では 2 文字分になるが、文書では 1 文字と数える
		String thirtyEmoji = "😀".repeat(30);

		assertThat(signup("Hanako", thirtyEmoji, "hanako@example.com", PASSWORD, PASSWORD))
				.hasStatus(HttpStatus.CREATED);
		assertThat(signup("Taro", thirtyEmoji + "😀", "taro@example.com", PASSWORD, PASSWORD))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.errors[?(@.field == 'displayName')].message").asArray()
				.containsExactly("表示名は 30 文字以内にしてください");
	}

	@Test
	void ユーザー名と表示名は前後の空白を除いてから判定する() {
		assertThat(signup("  Hanako  ", "  山田花子  ", "hanako@example.com", PASSWORD, PASSWORD))
				.hasStatus(HttpStatus.CREATED)
				.bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.username").isEqualTo("Hanako");
					assertThat(json).extractingPath("$.displayName").isEqualTo("山田花子");
				});
	}

	// --- F-02 ログイン -------------------------------------------------------

	@Test
	void 正しいメールアドレスとパスワードでログインできる() {
		signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);

		MvcTestResult result = login("hanako@example.com", PASSWORD);

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.username").isEqualTo("Hanako");
		assertThat(me(accessToken(result))).hasStatusOk();
	}

	@Test
	void メールアドレスは大文字で入力してもログインできる() {
		signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);

		assertThat(login(" HANAKO@Example.com ", PASSWORD)).hasStatusOk();
	}

	@Test
	void 誤ったパスワードと存在しないメールアドレスは同じ文言の401になる() {
		signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);

		for (MvcTestResult result : new MvcTestResult[] {
				login("hanako@example.com", "wrong-password"),
				login("nobody@example.com", PASSWORD)}) {
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
					.bodyJson().extractingPath("$.detail").isEqualTo("メールアドレスまたはパスワードが正しくありません");
			assertThat(result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN)).isNull();
		}
	}

	// --- F-04 アクセス制御 ---------------------------------------------------

	@Test
	void 未ログインでmeを呼ぶと401になりCSRFのトークンは発行される() {
		MvcTestResult result = mvc.get().uri("/api/auth/me").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		// 画面は起動時の /me で XSRF-TOKEN を受け取ってからログインする
		assertThat(result).cookies().containsCookie("XSRF-TOKEN");
	}

	@Test
	void 期限切れのアクセストークンは401になる() {
		long userId = userId(signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD));
		Instant past = Instant.now().minusSeconds(3600);
		String expired = encode(JwtClaimsSet.builder()
				.issuer(authProperties.issuer())
				.subject(String.valueOf(userId))
				.issuedAt(past.minusSeconds(900))
				.expiresAt(past)
				.build());

		assertThat(me(expired)).hasStatus(HttpStatus.UNAUTHORIZED)
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void 署名を改ざんしたアクセストークンは401になる() {
		String token = accessToken(signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD));
		// 署名（最後の . より後ろ）の先頭の 1 文字を書き換える
		int signatureStart = token.lastIndexOf('.') + 1;
		char replaced = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
		String tampered = token.substring(0, signatureStart) + replaced + token.substring(signatureStart + 1);

		assertThat(me(tampered)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void 別の発行者のアクセストークンは401になる() {
		long userId = userId(signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD));
		Instant now = Instant.now();
		String token = encode(JwtClaimsSet.builder()
				.issuer("someone-else")
				.subject(String.valueOf(userId))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(900))
				.build());

		assertThat(me(token)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void CSRFのトークンが無い状態を変える要求は403になる() {
		for (String path : new String[] {"/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"}) {
			assertThat(mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
					.as(path)
					.hasStatus(HttpStatus.FORBIDDEN);
		}
	}

	@Test
	void セッションを作らない() {
		MvcTestResult result = signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);

		assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
		assertThat(result.getRequest().getSession(false)).isNull();
	}

	// --- トークンの更新 ------------------------------------------------------

	@Test
	void 更新すると新しい2つのトークンが発行され古いリフレッシュトークンは使えなくなる() {
		MvcTestResult signup = signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);
		String oldRefresh = refreshToken(signup);

		MvcTestResult refreshed = refresh(oldRefresh);
		assertThat(refreshed).hasStatus(HttpStatus.NO_CONTENT);
		String newRefresh = refreshToken(refreshed);
		assertThat(newRefresh).isNotEqualTo(oldRefresh);
		assertThat(me(accessToken(refreshed))).hasStatusOk();

		// 一度使ったリフレッシュトークンは 2 回使えない
		MvcTestResult reused = refresh(oldRefresh);
		assertThat(reused).hasStatus(HttpStatus.UNAUTHORIZED);
		assertCookiesCleared(reused);
		// 新しいリフレッシュトークンは使える
		assertThat(refresh(newRefresh)).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void アクセストークンが期限切れでも更新できる() {
		MvcTestResult signup = signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);
		Instant past = Instant.now().minusSeconds(3600);
		String expired = encode(JwtClaimsSet.builder()
				.issuer(authProperties.issuer())
				.subject(String.valueOf(userId(signup)))
				.issuedAt(past.minusSeconds(900))
				.expiresAt(past)
				.build());

		// 実際のブラウザは /api/auth/refresh にも期限切れの access_token を送ってくる
		assertThat(mvc.post().uri("/api/auth/refresh").with(withToken(xsrf))
				.cookie(new Cookie(AuthCookies.ACCESS_TOKEN, expired), new Cookie(AuthCookies.REFRESH_TOKEN, refreshToken(signup))))
				.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void 期限切れのリフレッシュトークンでは更新できない() {
		MvcTestResult signup = signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);
		String refreshToken = refreshToken(signup);
		jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 second' WHERE token_hash = ?",
				sha256(refreshToken));

		MvcTestResult result = refresh(refreshToken);
		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertCookiesCleared(result);
	}

	@Test
	void リフレッシュトークンが無いと更新できない() {
		assertThat(mvc.post().uri("/api/auth/refresh").with(withToken(xsrf))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void リフレッシュトークンは値ではなくハッシュで保存する() {
		String refreshToken = refreshToken(signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD));

		Integer byHash = jdbc.queryForObject(
				"SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, sha256(refreshToken));
		Integer byValue = jdbc.queryForObject(
				"SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, refreshToken);
		assertThat(byHash).isEqualTo(1);
		assertThat(byValue).isZero();
	}

	// --- F-03 ログアウト -----------------------------------------------------

	@Test
	void ログアウトするとCookieが消えリフレッシュトークンは使えなくなる() {
		MvcTestResult signup = signup("Hanako", "山田花子", "hanako@example.com", PASSWORD, PASSWORD);
		String refreshToken = refreshToken(signup);

		MvcTestResult logout = mvc.post().uri("/api/auth/logout").with(withToken(xsrf))
				.cookie(new Cookie(AuthCookies.REFRESH_TOKEN, refreshToken)).exchange();

		assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
		assertCookiesCleared(logout);
		Integer rows = jdbc.queryForObject(
				"SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, sha256(refreshToken));
		assertThat(rows).isZero();
		assertThat(refresh(refreshToken)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void ログインしていなくてもログアウトは成功する() {
		MvcTestResult logout = mvc.post().uri("/api/auth/logout").with(withToken(xsrf)).exchange();

		assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
		assertCookiesCleared(logout);
	}

	// --- 補助 ----------------------------------------------------------------

	private MvcTestResult signup(String username, String displayName, String email, String password, String confirmation) {
		String json = """
				{"username": %s, "displayName": %s, "email": %s, "password": %s, "passwordConfirmation": %s}
				""".formatted(quote(username), quote(displayName), quote(email), quote(password), quote(confirmation));
		return mvc.post().uri("/api/auth/signup").with(withToken(xsrf))
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult login(String email, String password) {
		String json = """
				{"email": %s, "password": %s}
				""".formatted(quote(email), quote(password));
		return mvc.post().uri("/api/auth/login").with(withToken(xsrf))
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult refresh(String refreshToken) {
		return mvc.post().uri("/api/auth/refresh").with(withToken(xsrf))
				.cookie(new Cookie(AuthCookies.REFRESH_TOKEN, refreshToken)).exchange();
	}

	private MvcTestResult me(String accessToken) {
		return mvc.get().uri("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, accessToken)).exchange();
	}

	private static String accessToken(MvcTestResult result) {
		Cookie cookie = result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN);
		assertThat(cookie).as("access_token の Cookie").isNotNull();
		return cookie.getValue();
	}

	private static String refreshToken(MvcTestResult result) {
		Cookie cookie = result.getResponse().getCookie(AuthCookies.REFRESH_TOKEN);
		assertThat(cookie).as("refresh_token の Cookie").isNotNull();
		return cookie.getValue();
	}

	private static long userId(MvcTestResult result) {
		try {
			return com.jayway.jsonpath.JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
		} catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void assertCookiesCleared(MvcTestResult result) {
		for (String name : new String[] {AuthCookies.ACCESS_TOKEN, AuthCookies.REFRESH_TOKEN}) {
			Cookie cookie = result.getResponse().getCookie(name);
			assertThat(cookie).as(name).isNotNull();
			assertThat(cookie.getMaxAge()).as(name).isZero();
		}
	}

	private String encode(JwtClaimsSet claims) {
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** JSON の文字列にする（テストの値には " や \ を含めない）。 */
	private static String quote(String value) {
		return "\"" + value + "\"";
	}

}
