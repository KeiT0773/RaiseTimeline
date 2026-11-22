package com.raisetimeline.backend.auth.token;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 認証のトークンを入れる Cookie の組み立て（API 設計書 2.2）。
 * どちらも HttpOnly にし、画面の JavaScript から読めないようにする（XSS で盗まれにくくする）。
 */
@Component
public class AuthCookies {

	public static final String ACCESS_TOKEN = "access_token";
	public static final String REFRESH_TOKEN = "refresh_token";

	/** アクセストークンはすべての API に送る。 */
	private static final String ACCESS_TOKEN_PATH = "/api";
	/** リフレッシュトークンは更新・ログアウトの API にだけ送り、送られる機会を減らす。 */
	private static final String REFRESH_TOKEN_PATH = "/api/auth";

	private final AuthProperties properties;

	public AuthCookies(AuthProperties properties) {
		this.properties = properties;
	}

	/** 2 つのトークンを発行する Set-Cookie ヘッダー。 */
	public HttpHeaders issue(String accessToken, String refreshToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_TOKEN, accessToken, ACCESS_TOKEN_PATH, properties.accessTokenTtl()));
		headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_TOKEN, refreshToken, REFRESH_TOKEN_PATH, properties.refreshTokenTtl()));
		return headers;
	}

	/** 2 つのトークンを削除する Set-Cookie ヘッダー（Max-Age=0）。 */
	public HttpHeaders clear() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_TOKEN, "", ACCESS_TOKEN_PATH, Duration.ZERO));
		headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_TOKEN, "", REFRESH_TOKEN_PATH, Duration.ZERO));
		return headers;
	}

	private String cookie(String name, String value, String path, Duration maxAge) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(properties.cookieSecure())
				// 他のサイトから送られた POST には Cookie を付けない。CSRF のトークンと二重の対策
				.sameSite("Lax")
				.path(path)
				.maxAge(maxAge)
				.build()
				.toString();
	}

}
