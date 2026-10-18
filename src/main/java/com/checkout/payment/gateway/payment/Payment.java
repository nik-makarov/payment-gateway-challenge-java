package com.checkout.payment.gateway.payment;

import java.util.UUID;

public record Payment(
    UUID id,
    PaymentStatus status,
    String cardNumberLastFour,
    int expiryMonth,
    int expiryYear,
    String currency,
    long amount) {

  public static Payment completed(UUID id, PaymentRequest request, PaymentStatus status) {
    var cardNumber = request.cardNumber();
    return new Payment(
        id,
        status,
        cardNumber.substring(cardNumber.length() - 4),
        request.expiryMonth(),
        request.expiryYear(),
        request.currency(),
        request.amount());
  }
}
