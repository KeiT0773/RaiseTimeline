package com.raisetimeline.backend.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * リフレッシュトークンの発行・使用・削除（auth.md 3.、ER図・テーブル定義 refresh_tokens）。
 * トークンは推測できないランダムな値で、DB には SHA-256 のハッシュだけを保存する。
 * DB の中身が漏れても、ハッシュからは元のトークンを作れないため、そのままでは使えない。
 */
@Service
@Transactional(readOnly = true)
public class RefreshTokenService {

	/** 256 ビット。推測できない長さにする。 */
	private static final int TOKEN_BYTES = 32;

	private final RefreshTokenMapper mapper;
	private final AuthProperties properties;
	private final SecureRandom random = new SecureRandom();

	public RefreshTokenService(RefreshTokenMapper mapper, AuthProperties properties) {
		this.mapper = mapper;
		this.properties = properties;
	}

	/**
	 * 新しいトークンを発行して DB に保存し、トークンの値（Cookie に入れるもの）を返す。
	 */
	@Transactional
	public String issue(long userId) {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(properties.refreshTokenTtl());
		mapper.insert(userId, hash(token), expiresAt);
		return token;
	}

	/**
	 * トークンを使う（DB から削除する）。期限内なら持ち主のユーザー ID を返す。
	 * 見つからない（使用済み・ログアウト済み・偽物）か期限切れなら空を返す。
	 * どちらの場合も行は消えるので、同じトークンは 2 回使えない。
	 */
	@Transactional
	public Optional<Long> consume(String token) {
		StoredRefreshToken stored = mapper.deleteByTokenHashReturning(hash(token));
		if (stored == null || !stored.expiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
			return Optional.empty();
		}
		return Optional.of(stored.userId());
	}

	/** ログアウト。トークンが DB に無くても何もしない。 */
	@Transactional
	public void revoke(String token) {
		mapper.deleteByTokenHash(hash(token));
	}

	/** そのユーザーの期限切れの行を削除する（テーブルが増え続けないようにする）。 */
	@Transactional
	public void deleteExpired(long userId) {
		mapper.deleteExpiredByUserId(userId);
	}

	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 は Java の標準で必ず使えるため、ここには来ない
			throw new IllegalStateException(e);
		}
	}

}
