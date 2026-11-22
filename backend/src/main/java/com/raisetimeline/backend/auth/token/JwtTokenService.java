package com.raisetimeline.backend.auth.token;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * アクセストークン（JWT）を発行する。検証は Spring Security（JwtDecoder）が行う。
 * JWT の中身（ペイロード）は署名されているだけで暗号化はされず、誰でも読めるため、
 * ユーザー ID 以外の情報（メールアドレスなど）は入れない。
 */
@Service
public class JwtTokenService {

	private final JwtEncoder jwtEncoder;
	private final AuthProperties properties;

	public JwtTokenService(JwtEncoder jwtEncoder, AuthProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	public String issueAccessToken(long userId) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(String.valueOf(userId))
				.issuedAt(now)
				.expiresAt(now.plus(properties.accessTokenTtl()))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
