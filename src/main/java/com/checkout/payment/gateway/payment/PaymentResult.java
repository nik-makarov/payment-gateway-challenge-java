package com.checkout.payment.gateway.payment;

import java.util.Optional;

public record PaymentResult<T>(boolean success, T result) {

  public static <T> PaymentResult<T> success(T value) {
    return new PaymentResult<>(true, value);
  }

  public static <T> PaymentResult<T> failure() {
    return new PaymentResult<>(false, null);
  }

  public boolean isSuccess() {
    return success;
  }

  public Optional<T> value() {
    return Optional.ofNullable(result);
  }
}
