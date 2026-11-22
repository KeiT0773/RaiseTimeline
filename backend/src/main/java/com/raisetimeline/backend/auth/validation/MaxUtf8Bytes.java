package com.raisetimeline.backend.auth.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * 文字列を UTF-8 にしたときのバイト数の上限。
 * パスワードに使う。BCrypt が扱えるのは 72 バイトまでで、全角文字は 1 文字が 3 バイトになるため、
 * 文字数（@Size）だけでは上限を守れない（auth.md 5.）。null は対象外（必須かどうかは @NotNull で確かめる）。
 */
@Documented
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

	int value();

	String message();

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {

		private int max;

		@Override
		public void initialize(MaxUtf8Bytes annotation) {
			this.max = annotation.value();
		}

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
		}

	}

}
