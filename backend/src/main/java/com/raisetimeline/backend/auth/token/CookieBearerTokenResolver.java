package com.raisetimeline.backend.auth.token;

import java.util.Set;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * アクセストークン（JWT）を access_token の Cookie から読み出す。
 * Spring Security の既定は Authorization ヘッダーから読むが、本アプリは HttpOnly Cookie で受け渡すため置き換える
 * （Spring Security 7.1 には Cookie 用の実装が無い）。
 */
@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

	/**
	 * ここに挙げた API ではアクセストークンを読まない。
	 * access_token の Cookie は /api 全体に送られるため、期限切れのトークンがこれらにも届く。
	 * 読んでしまうと検証に失敗して 401 になり、期限切れのときに呼ぶはずの更新ができなくなる。
	 */
	private static final Set<String> IGNORED_PATHS = Set.of(
			"/api/auth/signup",
			"/api/auth/login",
			"/api/auth/refresh",
			"/api/auth/logout");

	@Override
	public String resolve(HttpServletRequest request) {
		if (IGNORED_PATHS.contains(request.getRequestURI())) {
			return null;
		}
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (AuthCookies.ACCESS_TOKEN.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
				return cookie.getValue();
			}
		}
		return null;
	}

}
