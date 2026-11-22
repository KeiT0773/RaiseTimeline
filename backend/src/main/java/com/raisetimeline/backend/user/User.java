package com.raisetimeline.backend.user;

import java.time.OffsetDateTime;

/**
 * users テーブルの 1 行（ER図・テーブル定義 2.）。
 * MyBatis が列の値を詰める（列名 display_name → displayName）。
 * 登録時に採番された id を MyBatis が書き戻すため、record ではなく値を変更できるクラスにしている。
 * 画面へは返さない（password_digest を含むため）。応答には auth.MeResponse などの record を使う。
 */
public class User {

	private Long id;
	private String username;
	private String displayName;
	private String email;
	private String passwordDigest;
	private String bio;
	private String avatarPath;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	public User() {
	}

	/** 新規登録用。id と日時は DB が決める。 */
	public User(String username, String displayName, String email, String passwordDigest) {
		this.username = username;
		this.displayName = displayName;
		this.email = email;
		this.passwordDigest = passwordDigest;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswordDigest() {
		return passwordDigest;
	}

	public void setPasswordDigest(String passwordDigest) {
		this.passwordDigest = passwordDigest;
	}

	public String getBio() {
		return bio;
	}

	public void setBio(String bio) {
		this.bio = bio;
	}

	public String getAvatarPath() {
		return avatarPath;
	}

	public void setAvatarPath(String avatarPath) {
		this.avatarPath = avatarPath;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(OffsetDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

}
