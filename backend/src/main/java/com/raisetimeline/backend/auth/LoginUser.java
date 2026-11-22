package com.raisetimeline.backend.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * ログイン時のパスワード照合で Spring Security に渡す利用者の情報。
 * ログインの識別子はメールアドレス（auth.md F-02）。権限（ロール）は使わない。
 */
public record LoginUser(long id, String email, String passwordDigest) implements UserDetails {

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public String getPassword() {
		return passwordDigest;
	}

	@Override
	public String getUsername() {
		return email;
	}

}
