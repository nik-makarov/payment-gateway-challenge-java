package com.checkout.payment.gateway.payment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import com.checkout.payment.gateway.payment.PaymentRequest;
import com.checkout.payment.gateway.support.PaymentFixtures;

import jakarta.validation.ConstraintValidatorContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class ExpiryDateValidatorTest {

  @ParameterizedTest
  @CsvSource({
      "7, 2030, true",
      "12, 9999, true",
      "6, 2030, false",
      "12, 2029, false",
      ", 2030, true",
      "7, , true",
      "0, 2030, true",
      "13, 2030, true",
      "7, 0, true",
      "7, 10000, true"
  })
  void validatesExpiryOnlyForCompleteDatesWithinFieldRanges(Integer month, Integer year,
      boolean expected) {
    var clock = Clock.fixed(Instant.parse("2030-06-15T12:00:00Z"), ZoneOffset.UTC);
    var validator = new ExpiryDateValidator(clock);
    var fixture = PaymentFixtures.request();
    var request = new PaymentRequest(
        fixture.cardNumber(), month, year, fixture.currency(), fixture.amount(), fixture.cvv());
    var context = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);

    assertThat(validator.isValid(request, context)).isEqualTo(expected);
  }
}
