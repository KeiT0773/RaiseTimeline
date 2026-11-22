package com.raisetimeline.backend;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * テストで CSRF のトークンを、実際の画面と同じ手順で扱う（API 設計書 2.3）。
 * GET で XSRF-TOKEN の Cookie を受け取り、POST にその Cookie と X-XSRF-TOKEN ヘッダーを付ける。
 *
 * Spring Security Test の csrf() は使わない。csrf() はアプリの CsrfFilter のトークン保存先を
 * テスト用のものに差し替え、同じアプリを共有する他のテストで XSRF-TOKEN の Cookie が書き出されなくなるため。
 */
public final class CsrfSupport {

	public static final String COOKIE = "XSRF-TOKEN";
	public static final String HEADER = "X-XSRF-TOKEN";

	private CsrfSupport() {
	}

	/** GET /api/health の応答から XSRF-TOKEN の Cookie を受け取る。 */
	public static Cookie fetchToken(MockMvcTester mvc) {
		Cookie cookie = mvc.get().uri("/api/health").exchange().getResponse().getCookie(COOKIE);
		assertThat(cookie).as("XSRF-TOKEN の Cookie").isNotNull();
		return cookie;
	}

	/** 要求に XSRF-TOKEN の Cookie と X-XSRF-TOKEN ヘッダーを付ける。 */
	public static RequestPostProcessor withToken(Cookie token) {
		return request -> {
			Cookie[] current = request.getCookies() == null ? new Cookie[0] : request.getCookies();
			Cookie[] cookies = java.util.Arrays.copyOf(current, current.length + 1);
			cookies[current.length] = new Cookie(COOKIE, token.getValue());
			request.setCookies(cookies);
			request.addHeader(HEADER, token.getValue());
			return request;
		};
	}

}
