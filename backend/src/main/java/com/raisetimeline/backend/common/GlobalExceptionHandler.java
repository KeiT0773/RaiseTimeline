package com.raisetimeline.backend.common;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.raisetimeline.backend.auth.DuplicateFieldException;

/**
 * エラー応答を ProblemDetail（RFC 9457）にそろえる（API 設計書 2.4）。
 * Spring MVC の標準の例外（JSON の形式の誤りなど）は親クラスの ResponseEntityExceptionHandler が ProblemDetail にする。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	/** 入力制約の違反（400）。項目ごとのエラーを errors に入れる。 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
				.toList();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "入力内容に誤りがあります");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	/** JSON の形式の誤り（400）。既定の文言（英語）を、画面にそのまま表示できる日本語にする。 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "要求の形式が正しくありません");
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	/** ユーザー名・メールアドレスの重複（409）。 */
	@ExceptionHandler(DuplicateFieldException.class)
	public ProblemDetail handleDuplicateField(DuplicateFieldException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "既に使われている値があります");
		problem.setProperty("errors", ex.getErrors());
		return problem;
	}

	/**
	 * ログインの失敗（401）。メールアドレスが無い場合もパスワードが違う場合も同じ文言にし、
	 * どちらが誤りかを伝えない（auth.md F-02）。
	 */
	@ExceptionHandler(BadCredentialsException.class)
	public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません");
	}

}
