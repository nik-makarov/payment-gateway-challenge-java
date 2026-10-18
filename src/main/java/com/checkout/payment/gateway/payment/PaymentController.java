package com.checkout.payment.gateway.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @Operation(summary = "Process a card payment")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Payment authorized or declined",
          content = @Content(schema = @Schema(implementation = Payment.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Request rejected before contacting bank",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "503",
          description = "Bank payment processing failed; outcome may be unknown",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<?> processPayment(@Valid @RequestBody PaymentRequest request) {
    var result = paymentService.processPayment(request);
    if (!result.isSuccess()) {
      return error(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Unable to process payment with acquiring bank");
    }

    var payment = result.value().get();
    return ResponseEntity.created(URI.create("/payment/" + payment.id())).body(payment);
  }

  @Operation(summary = "Retrieve a previously authorized or declined payment")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Payment details",
          content = @Content(schema = @Schema(implementation = Payment.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid payment identifier",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Payment not found",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/{id}")
  public ResponseEntity<?> getPaymentById(@PathVariable UUID id) {
    var payment = paymentService.findById(id);
    if (payment.isEmpty()) {
      return error(HttpStatus.NOT_FOUND, "Payment not found");
    }
    return ResponseEntity.ok(payment.get());
  }

  private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(new ErrorResponse(message));
  }
}
