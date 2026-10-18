package com.checkout.payment.gateway.bank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BankResponse(
    @JsonProperty(value = "authorized", required = true) @JsonSetter(nulls = Nulls.FAIL)
    boolean authorized,
    @JsonProperty("authorization_code") JsonNode authorizationCode) {

  Optional<String> authorizationCodeForLogging() {
    return Optional.ofNullable(authorizationCode).map(JsonNode::textValue);
  }
}
