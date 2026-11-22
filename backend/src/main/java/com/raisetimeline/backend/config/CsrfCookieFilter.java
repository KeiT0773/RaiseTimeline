package com.raisetimeline.backend.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * すべての応答で CSRF のトークン（XSRF-TOKEN の Cookie）を発行する。
 *
 * Spring Security は、トークンが実際に読み出されたときにだけ Cookie を書き出す（遅延読み込み）。
 * 画面（SPA）は、最初の GET（/api/auth/me）の応答でこの Cookie を受け取り、ログインなどの POST に付けて送る。
 * 現在の版（7.1）では csrf.spa() だけでも GET の応答に Cookie が付くことを確認しているが、
 * それは内部の実装に依存しているため、ここで明示的に読み出して確実に書き出させる
 * （Spring Security のリファレンスにある SPA 向けの方法）。
 */
class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			// 値を読み出すと、CookieCsrfTokenRepository が Set-Cookie を書き出す
			csrfToken.getToken();
		}
		filterChain.doFilter(request, response);
	}

}
