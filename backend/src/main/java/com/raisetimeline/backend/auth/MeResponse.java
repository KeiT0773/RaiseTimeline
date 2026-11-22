package com.raisetimeline.backend.auth;

import com.raisetimeline.backend.user.User;

/**
 * 利用者の情報（API 設計書 3.1）。新規登録・ログイン・/me が返す。
 * パスワードのハッシュやトークンは含めない。
 */
public record MeResponse(long id, String username, String displayName) {

	public static MeResponse from(User user) {
		return new MeResponse(user.getId(), user.getUsername(), user.getDisplayName());
	}

}
