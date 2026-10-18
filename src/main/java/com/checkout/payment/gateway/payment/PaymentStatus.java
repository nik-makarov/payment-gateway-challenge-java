package com.checkout.payment.gateway.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PaymentStatus {
  @JsonProperty("Authorized")
  AUTHORIZED,
  @JsonProperty("Declined")
  DECLINED
}
