package com.raisetimeline.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

import com.raisetimeline.backend.auth.token.CookieBearerTokenResolver;

/**
 * Spring Security の設定（機能別要件書 auth.md 3.・F-04、API 設計書 2.2・2.3）。
 *
 * <ul>
 *   <li>ログイン状態は JWT（アクセストークン）で持ち、サーバーはセッションを作らない（STATELESS）。
 *       JWT は access_token の Cookie から読み（CookieBearerTokenResolver）、署名・期限・発行者を確かめる（JwtConfig）。</li>
 *   <li>画面は React（SPA）なので、Spring Security のログイン画面（formLogin）・Basic 認証・ログアウト画面は使わない。
 *       ログイン・ログアウトは AuthController の API で行う。</li>
 *   <li>未ログイン・トークンの期限切れ・改ざんのときは、ログイン画面へのリダイレクトではなく 401 を返す。</li>
 *   <li>トークンを Cookie で送るため CSRF の対策が要る。Cookie（XSRF-TOKEN）のトークンを、画面が X-XSRF-TOKEN ヘッダーで送り返す方式。</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailAuthenticationEntryPoint entryPoint,
			CookieBearerTokenResolver bearerTokenResolver) {
		http
				.authorizeHttpRequests(auth -> auth
						// 死活確認は未ログインでも呼べる
						.requestMatchers(HttpMethod.GET, "/api/health").permitAll()
						// ログインしていない人が呼ぶ API。更新・ログアウトはアクセストークンが期限切れでも呼べるようにする
						.requestMatchers(HttpMethod.POST,
								"/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
						// Spring のエラー応答（/error）を 401 で隠さない
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				// セッション（JSESSIONID）を作らない。ログイン状態は要求ごとに JWT で確かめる
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// SPA 向けの CSRF 設定。CookieCsrfTokenRepository（JavaScript から読める XSRF-TOKEN Cookie）と、
				// X-XSRF-TOKEN ヘッダーの値を照合するハンドラーをまとめて設定する。
				// 状態を変える要求（POST / PUT / PATCH / DELETE）でトークンが合わなければ 403 になる。
				// トークンは Cookie に置くので、セッションを作らない設定と両立する。
				.csrf(csrf -> csrf.spa())
				// 最初の GET（/api/auth/me など）でも XSRF-TOKEN の Cookie が届くようにする
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				// JWT の検証。トークンが無い・期限切れ・改ざんのときは entryPoint が 401 を返す
				.oauth2ResourceServer(oauth2 -> oauth2
						.bearerTokenResolver(bearerTokenResolver)
						.authenticationEntryPoint(entryPoint)
						.jwt(Customizer.withDefaults()))
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

	/**
	 * ログイン時のメールアドレスとパスワードの照合（AuthService.login）。
	 * DaoAuthenticationProvider は、メールアドレスが見つからないときもダミーのハッシュで照合するため、
	 * 応答時間の差からメールアドレスが登録済みかどうかを推測されにくい。
	 */
	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

}
