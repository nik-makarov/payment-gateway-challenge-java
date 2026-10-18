package com.checkout.payment.gateway.payment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

class PaymentRequestValidationTest {

  private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

  @AfterAll
  static void closeValidators() {
    VALIDATOR_FACTORY.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"cardNumber", "expiryMonth", "expiryYear", "currency", "amount", "cvv"})
  void rejectsMissingRequiredFields(String field) {
    var violations = VALIDATOR.validateValue(PaymentRequest.class, field, null);

    assertThat(violations).singleElement().satisfies(violation -> {
      assertThat(violation.getPropertyPath().toString()).isEqualTo(field);
      assertThat(violation.getMessage()).isEqualTo("is required");
    });
  }

  @ParameterizedTest
  @MethodSource("invalidFieldValues")
  void rejectsValuesOutsideTheInputContract(String field, Object value) {
    var violations = VALIDATOR.validateValue(PaymentRequest.class, field, value);

    assertThat(violations).singleElement().satisfies(violation ->
        assertThat(violation.getPropertyPath().toString()).isEqualTo(field));
  }

  static Stream<Arguments> invalidFieldValues() {
    return Stream.of(
        Arguments.of("cardNumber", "4111111111111"),
        Arguments.of("cardNumber", "41111111111111111111"),
        Arguments.of("cardNumber", "4111 1111 1111 0001"),
        Arguments.of("cardNumber", "4111111111110001x"),
        Arguments.of("cardNumber", "４１１１１１１１１１１１０００１"),
        Arguments.of("expiryMonth", 0),
        Arguments.of("expiryMonth", 13),
        Arguments.of("expiryYear", 0),
        Arguments.of("expiryYear", 10000),
        Arguments.of("currency", "usd"),
        Arguments.of("currency", "JPY"),
        Arguments.of("currency", "UNTRUSTED"),
        Arguments.of("currency", " GBP"),
        Arguments.of("currency", "GBP "),
        Arguments.of("amount", 0L),
        Arguments.of("cvv", "12"),
        Arguments.of("cvv", "12345"),
        Arguments.of("cvv", "012xy"),
        Arguments.of("cvv", "１２３"));
  }
}
