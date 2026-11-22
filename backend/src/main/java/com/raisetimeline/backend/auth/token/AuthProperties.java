package com.raisetimeline.backend.auth.token;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 認証（JWT）の設定。application.properties の app.auth.* と対応する。
 *
 * @param jwtSecret       アクセストークンの署名に使う秘密鍵（Base64。32 バイト以上）
 * @param issuer          JWT の発行者（iss）。検証時にも一致を確かめる
 * @param accessTokenTtl  アクセストークンの有効期限
 * @param refreshTokenTtl リフレッシュトークンの有効期限
 * @param cookieSecure    Cookie に Secure 属性を付けるか（本番の HTTPS では true）
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(
		String jwtSecret,
		@DefaultValue("raisetimeline") String issuer,
		@DefaultValue("15m") Duration accessTokenTtl,
		@DefaultValue("14d") Duration refreshTokenTtl,
		@DefaultValue("true") boolean cookieSecure) {
}
