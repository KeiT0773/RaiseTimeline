package com.raisetimeline.backend.auth.token;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * refresh_tokens の読み書き。SQL は resources/mapper/auth/RefreshTokenMapper.xml。
 */
@Mapper
public interface RefreshTokenMapper {

	void insert(@Param("userId") long userId, @Param("tokenHash") String tokenHash, @Param("expiresAt") OffsetDateTime expiresAt);

	/**
	 * トークンの行を削除し、削除した行の持ち主と期限を返す（無ければ null）。
	 * 「探す」と「消す」を 1 つの SQL で行うので、同じトークンで同時に更新されても成功するのは 1 回だけになる。
	 */
	StoredRefreshToken deleteByTokenHashReturning(@Param("tokenHash") String tokenHash);

	void deleteByTokenHash(@Param("tokenHash") String tokenHash);

	void deleteExpiredByUserId(@Param("userId") long userId);

}
