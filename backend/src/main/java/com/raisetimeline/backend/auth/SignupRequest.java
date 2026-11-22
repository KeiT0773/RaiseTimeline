package com.raisetimeline.backend.auth;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.raisetimeline.backend.auth.validation.MaxUtf8Bytes;
import com.raisetimeline.backend.auth.validation.PasswordsMatch;
import com.raisetimeline.backend.common.validation.CodePointLength;

/**
 * 新規登録の要求（API 設計書 3.2）。制約と文言は auth.md 5.。
 * 文字数は絵文字も 1 文字として数える（CodePointLength）。メールアドレスは ASCII なので @Size でよい。
 * 検証の前に、コンストラクターで前後の空白を除き、メールアドレスを小文字にそろえる。
 * Spring が JSON からこの record を作る時点で正規化が済み、そのあと Bean Validation が走る。
 * パスワードは空白も含めて入力どおりに扱う（前後の空白を除かない）。
 */
@PasswordsMatch
public record SignupRequest(
		@NotNull(message = "ユーザー名は半角英数字と _ で 3〜15 文字にしてください")
		@Pattern(regexp = "^[A-Za-z0-9_]{3,15}$", message = "ユーザー名は半角英数字と _ で 3〜15 文字にしてください")
		String username,

		@NotBlank(message = "表示名を入力してください")
		@CodePointLength(max = 30, message = "表示名は 30 文字以内にしてください")
		String displayName,

		@NotBlank(message = "メールアドレスを入力してください")
		@Email(message = "メールアドレスの形式が正しくありません")
		@Size(max = 255, message = "メールアドレスの形式が正しくありません")
		String email,

		@NotNull(message = "パスワードを入力してください")
		@CodePointLength(min = 8, message = "パスワードは 8 文字以上にしてください")
		@CodePointLength(max = 72, message = "パスワードは 72 文字以内にしてください")
		@MaxUtf8Bytes(value = 72, message = "パスワードが長すぎます。全角文字を減らしてください")
		String password,

		String passwordConfirmation) implements PasswordsMatch.Confirmable {

	public SignupRequest {
		username = strip(username);
		displayName = strip(displayName);
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	private static String strip(String value) {
		return value == null ? null : value.strip();
	}

}
