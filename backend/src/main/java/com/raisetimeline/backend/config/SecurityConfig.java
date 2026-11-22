package com.raisetimeline.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security の設定（機能別要件書 auth.md F-04、技術スタック 3.「認証・認可」）。
 *
 * <ul>
 *   <li>ログイン状態はセッション（Cookie）で保持する。画面は React（SPA）なので、
 *       Spring Security が用意するログイン画面（formLogin）や Basic 認証は使わない。
 *       ログイン・新規登録の API は auth パッケージで作る（次の Issue）。</li>
 *   <li>未ログインで API を呼んだら、ログイン画面へのリダイレクトではなく 401 を返す。</li>
 *   <li>CSRF 対策は Cookie（XSRF-TOKEN）のトークンを、画面が X-XSRF-TOKEN ヘッダーで送り返す方式。</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailAuthenticationEntryPoint entryPoint) {
		http
				.authorizeHttpRequests(auth -> auth
						// 死活確認は未ログインでも呼べる
						.requestMatchers(HttpMethod.GET, "/api/health").permitAll()
						// Spring のエラー応答（/error）を 401 で隠さない
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				// SPA 向けの CSRF 設定。CookieCsrfTokenRepository（JavaScript から読める XSRF-TOKEN Cookie）と、
				// X-XSRF-TOKEN ヘッダーの値を照合するハンドラーをまとめて設定する。
				// 状態を変える要求（POST / PUT / PATCH / DELETE）でトークンが合わなければ 403 になる。
				.csrf(csrf -> csrf.spa())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));
		return http.build();
	}

	/**
	 * パスワードのハッシュ化に BCrypt を使う（要件定義書 非機能要件「セキュリティ」）。
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
