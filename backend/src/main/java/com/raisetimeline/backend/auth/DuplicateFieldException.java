package com.raisetimeline.backend.auth;

import java.util.List;

import com.raisetimeline.backend.common.FieldErrorResponse;

/**
 * ユーザー名・メールアドレスが既に使われている（409。API 設計書 3.2）。
 * 重複している項目をすべて持つ。
 */
public class DuplicateFieldException extends RuntimeException {

	private final transient List<FieldErrorResponse> errors;

	public DuplicateFieldException(List<FieldErrorResponse> errors) {
		super("duplicate fields: " + errors.stream().map(FieldErrorResponse::field).toList());
		this.errors = List.copyOf(errors);
	}

	public List<FieldErrorResponse> getErrors() {
		return errors;
	}

}
