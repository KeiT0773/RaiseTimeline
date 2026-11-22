package com.raisetimeline.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Spring Security の設定（auth.md F-04）のテスト。
 * アプリ全体を起動するため、実行前に docker compose up -d で PostgreSQL を起動しておく。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void 死活確認は未ログインでも呼べる() {
		assertThat(mvc.get().uri("/api/health"))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.status")
				.isEqualTo("ok");
	}

	@Test
	void 未ログインで保護されたAPIを呼ぶと401をProblemDetailで返す() {
		assertThat(mvc.get().uri("/api/posts"))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.status").isEqualTo(401);
					assertThat(json).extractingPath("$.instance").isEqualTo("/api/posts");
				});
	}

	@Test
	void 未ログインの要求はログイン画面へリダイレクトしない() {
		assertThat(mvc.get().uri("/api/posts").accept(MediaType.TEXT_HTML))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.doesNotContainHeader("Location");
	}

	@Test
	@WithMockUser
	void CSRFのトークンが無い状態を変える要求は403になる() {
		assertThat(mvc.post().uri("/api/posts"))
				.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	@WithMockUser
	void CSRFのトークンがあれば403にはならない() {
		// /api/posts はまだ無いため 404 になる。403 ではないので、CSRF の検査は通過している
		assertThat(mvc.post().uri("/api/posts").with(csrf()))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void 最初のGETでCSRFのトークンがCookieで発行される() {
		// 画面は起動時の GET で XSRF-TOKEN Cookie を受け取り、以降の POST で X-XSRF-TOKEN ヘッダーに入れて送る
		assertThat(mvc.get().uri("/api/health"))
				.cookies()
				.containsCookie("XSRF-TOKEN");
	}

}
