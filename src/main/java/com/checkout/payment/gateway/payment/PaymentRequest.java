package com.checkout.payment.gateway.payment;

import com.checkout.payment.gateway.payment.validation.ValidExpiryDate;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@ValidExpiryDate
@JsonNaming(SnakeCaseStrategy.class)
public record PaymentRequest(
    @NotNull(message = "is required")
    @Pattern(regexp = "[0-9]{14,19}", message = "must contain 14 to 19 digits")
    @Schema(example = "2222405343248877")
    String cardNumber,
    @NotNull(message = "is required")
    @Min(value = 1, message = "must be between 1 and 12")
    @Max(value = 12, message = "must be between 1 and 12")
    @Schema(example = "12")
    Integer expiryMonth,
    @NotNull(message = "is required")
    @Min(value = 1, message = "must be between 1 and 9999")
    @Max(value = 9999, message = "must be between 1 and 9999")
    @Schema(example = "2099")
    Integer expiryYear,
    @NotNull(message = "is required")
    @Pattern(regexp = "USD|EUR|GBP", message = "must be USD, EUR or GBP")
    @Schema(
        allowableValues = {"USD", "EUR", "GBP"},
        example = "GBP")
    String currency,
    @NotNull(message = "is required")
    @Min(value = 1, message = "must be a positive integer in minor currency units")
    @Schema(example = "1050", description = "Positive integer in minor currency units")
    Long amount,
    @NotNull(message = "is required")
    @Pattern(regexp = "[0-9]{3,4}", message = "must contain 3 or 4 digits")
    @Schema(example = "123")
    String cvv) {

  @Override
  public String toString() {
    return "PaymentRequest[redacted]";
  }
}
