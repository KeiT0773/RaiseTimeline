package com.raisetimeline.backend.auth.token;

import java.time.OffsetDateTime;

/**
 * DB に保存されていたリフレッシュトークンの持ち主と期限。
 */
public record StoredRefreshToken(long userId, OffsetDateTime expiresAt) {
}
