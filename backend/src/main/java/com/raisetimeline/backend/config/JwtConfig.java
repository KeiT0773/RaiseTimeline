package com.raisetimeline.backend.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.raisetimeline.backend.auth.token.AuthProperties;

/**
 * アクセストークン（JWT）の署名と検証の設定。
 * 1 台のサーバーが発行も検証も行うため、共通鍵の HS256（HMAC-SHA256）で署名する。
 */
@Configuration
public class JwtConfig {

	/** HS256 の鍵は 256 ビット（32 バイト）以上が必要。 */
	private static final int MIN_SECRET_BYTES = 32;

	@Bean
	public SecretKey jwtSecretKey(AuthProperties properties) {
		if (properties.jwtSecret() == null || properties.jwtSecret().isBlank()) {
			throw new IllegalStateException("app.auth.jwt-secret が設定されていません");
		}
		byte[] bytes = Base64.getDecoder().decode(properties.jwtSecret());
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.auth.jwt-secret は 32 バイト以上にしてください");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build();
	}

	/**
	 * 署名・有効期限（exp。既定で 60 秒の時計のずれを許す）・発行者（iss）を確かめる。
	 * どれかが合わなければ、Spring Security が 401 にする。
	 */
	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, AuthProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
		return decoder;
	}

}
