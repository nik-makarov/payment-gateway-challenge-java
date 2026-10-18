package com.checkout.payment.gateway.support;

import com.checkout.payment.gateway.payment.PaymentRequest;

public final class PaymentFixtures {

  public static final String CARD_NUMBER = "4111111111110001";
  public static final String CVV = "012";
  public static final String JSON =
      """
          {"card_number":"4111111111110001","expiry_month":1,"expiry_year":2031,
           "currency":"GBP","amount":1050,"cvv":"012"}
          """;

  private PaymentFixtures() {
  }

  public static PaymentRequest request() {
    return new PaymentRequest(CARD_NUMBER, 1, 2031, "GBP", 1050L, CVV);
  }
}
