package com.raisetimeline.backend.config;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * 未ログインで保護された API を呼んだときに、401 を ProblemDetail（RFC 9457）の JSON で返す。
 * 画面は 401 を受け取ったらログイン画面へ移す（auth.md F-04）。
 * 他の API のエラー応答と形式をそろえるため、Spring Security の既定（本文なしの 401）を置き換える。
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final JsonMapper jsonMapper;

	public ProblemDetailAuthenticationEntryPoint(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "ログインしてください");
		problem.setInstance(URI.create(request.getRequestURI()));
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), problem);
	}

}
