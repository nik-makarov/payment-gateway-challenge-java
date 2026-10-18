package com.checkout.payment.gateway.bank;

import com.checkout.payment.gateway.payment.PaymentRequest;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Locale;

@JsonNaming(SnakeCaseStrategy.class)
public record BankRequest(
    String cardNumber, String expiryDate, String currency, long amount, String cvv) {

  static BankRequest from(PaymentRequest request) {
    var expiryDate =
        String.format(
            Locale.ROOT, "%02d/%04d", request.expiryMonth(), request.expiryYear());
    return new BankRequest(
        request.cardNumber(),
        expiryDate,
        request.currency(),
        request.amount(),
        request.cvv());
  }

  @Override
  public String toString() {
    return "BankRequest[redacted]";
  }
}
