package com.raisetimeline.backend.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * 文字数の範囲。絵文字も 1 文字として数える（ER図・テーブル定義 posts の注記、PostgreSQL の VARCHAR(n) と同じ数え方）。
 * Bean Validation 標準の @Size は Java の内部表現（UTF-16）の長さで数えるため、絵文字が 2 文字になってしまう。
 * null は対象外（必須かどうかは @NotNull / @NotBlank で確かめる）。
 */
@Documented
@Constraint(validatedBy = CodePointLength.Validator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(CodePointLength.List.class)
public @interface CodePointLength {

	int min() default 0;

	int max() default Integer.MAX_VALUE;

	String message();

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** 最小と最大で文言を分けるため、同じ項目に複数付けられるようにする。 */
	@Documented
	@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
	@Retention(RetentionPolicy.RUNTIME)
	@interface List {

		CodePointLength[] value();

	}

	class Validator implements ConstraintValidator<CodePointLength, String> {

		private int min;
		private int max;

		@Override
		public void initialize(CodePointLength annotation) {
			this.min = annotation.min();
			this.max = annotation.max();
		}

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			if (value == null) {
				return true;
			}
			int length = value.codePointCount(0, value.length());
			return length >= min && length <= max;
		}

	}

}
