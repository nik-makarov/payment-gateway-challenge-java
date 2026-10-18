package com.checkout.payment.gateway.payment;

import java.util.Map;

public record ErrorResponse(String message, String status, Map<String, String> errors) {

  public ErrorResponse(String message) {
    this(message, null, null);
  }

  public static ErrorResponse rejected(String message, Map<String, String> errors) {
    return new ErrorResponse(message, "Rejected", Map.copyOf(errors));
  }
}
