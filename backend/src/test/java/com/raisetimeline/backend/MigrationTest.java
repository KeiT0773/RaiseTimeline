package com.raisetimeline.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway のマイグレーションが起動時に適用され、テーブル定義が ER図・テーブル定義 2. と一致することを確かめる。
 * 実行前に docker compose up -d で PostgreSQL を起動しておく。
 * クラスに @Transactional を付け、各テストで入れた行はテストの終了時にロールバックする。
 */
@SpringBootTest
@Transactional
class MigrationTest {

	private static final String INSERT_USER =
			"INSERT INTO users (username, display_name, email, password_digest) VALUES (?, ?, ?, ?)";

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void 起動時にV1が適用されている() {
		Boolean success = jdbc.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);
		assertThat(success).isTrue();
	}

	@Test
	void ユーザー名は大文字と小文字を区別せずに一意() {
		jdbc.update(INSERT_USER, "Hanako", "花子", "hanako@example.com", "x");

		assertThatThrownBy(() -> jdbc.update(INSERT_USER, "hanako", "花子2", "hanako2@example.com", "x"))
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("uq_users_username_lower");
	}

	@Test
	void メールアドレスは一意() {
		jdbc.update(INSERT_USER, "taro", "太郎", "taro@example.com", "x");

		assertThatThrownBy(() -> jdbc.update(INSERT_USER, "taro2", "太郎2", "taro@example.com", "x"))
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("uq_users_email");
	}

	// PostgreSQL は 1 つのトランザクションで SQL が失敗すると以降の SQL を受け付けないため、
	// 失敗させる SQL はテスト 1 つにつき 1 回にする。

	@Test
	void ユーザー名が2文字だと保存できない() {
		assertThatThrownBy(() -> jdbc.update(INSERT_USER, "ab", "短い", "ab@example.com", "x"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_users_username_format");
	}

	@Test
	void ユーザー名に英数字とアンダースコア以外を含むと保存できない() {
		assertThatThrownBy(() -> jdbc.update(INSERT_USER, "a-b-c", "記号", "abc@example.com", "x"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_users_username_format");
	}

	@Test
	void 作成日時と更新日時は省略すると現在時刻が入る() {
		jdbc.update(INSERT_USER, "jiro", "次郎", "jiro@example.com", "x");

		Boolean filled = jdbc.queryForObject(
				"SELECT created_at IS NOT NULL AND updated_at IS NOT NULL FROM users WHERE username = 'jiro'",
				Boolean.class);
		assertThat(filled).isTrue();
	}

}
