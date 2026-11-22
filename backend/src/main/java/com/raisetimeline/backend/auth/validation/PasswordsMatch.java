package com.raisetimeline.backend.auth.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * パスワードと確認用パスワードが一致するか（auth.md 5.）。
 * 2 つの項目を比べるためクラスに付けるが、エラーは passwordConfirmation 項目のものとして返す
 * （画面が「パスワード（確認）」欄の下に表示できるようにするため）。
 */
@Documented
@Constraint(validatedBy = PasswordsMatch.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordsMatch {

	String message() default "パスワードが一致しません";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** この制約を付けるクラスが実装する。 */
	interface Confirmable {

		String password();

		String passwordConfirmation();

	}

	class Validator implements ConstraintValidator<PasswordsMatch, Confirmable> {

		@Override
		public boolean isValid(Confirmable value, ConstraintValidatorContext context) {
			if (value == null || Objects.equals(value.password(), value.passwordConfirmation())) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
					.addPropertyNode("passwordConfirmation")
					.addConstraintViolation();
			return false;
		}

	}

}
