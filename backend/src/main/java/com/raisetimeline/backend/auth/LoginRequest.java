package com.raisetimeline.backend.auth;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * ログインの要求（API 設計書 3.3）。
 * メールアドレスは登録時と同じく、前後の空白を除いて小文字にそろえてから照合する
 * （そろえないと、大文字で入力したときにログインできない）。
 */
public record LoginRequest(
		@NotBlank(message = "メールアドレスを入力してください")
		String email,

		@NotEmpty(message = "パスワードを入力してください")
		String password) {

	public LoginRequest {
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

}
