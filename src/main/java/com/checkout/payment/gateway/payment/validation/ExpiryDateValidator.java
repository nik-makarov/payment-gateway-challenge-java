package com.checkout.payment.gateway.payment.validation;

import com.checkout.payment.gateway.payment.PaymentRequest;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.LocalDate;

@RequiredArgsConstructor
public class ExpiryDateValidator implements ConstraintValidator<ValidExpiryDate, PaymentRequest> {

  private final Clock clock;

  @Override
  public boolean isValid(PaymentRequest request, ConstraintValidatorContext context) {
    if (request == null
        || request.expiryMonth() == null
        || request.expiryYear() == null
        || request.expiryMonth() < 1
        || request.expiryMonth() > 12
        || request.expiryYear() < 1
        || request.expiryYear() > 9999) {
      // Field constraints report incomplete or out-of-range dates.
      return true;
    }
    var today = LocalDate.now(clock);
    if (request.expiryYear() > today.getYear()) {
      return true;
    }
    if (request.expiryYear() == today.getYear() && request.expiryMonth() > today.getMonthValue()) {
      return true;
    }
    // @ValidExpiryDate validates the whole request because it needs both year and month.
    // Replace the default object error with an expiryMonth field error, keeping the message.
    // PaymentExceptionHandler collects only field errors and exposes this one as expiry_month.
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
        .addPropertyNode("expiryMonth")
        .addConstraintViolation();
    return false;
  }
}
