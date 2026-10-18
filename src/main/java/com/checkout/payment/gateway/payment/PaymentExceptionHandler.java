package com.checkout.payment.gateway.payment;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class PaymentExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException exception) {
    var errors = new LinkedHashMap<String, String>();
    for (var error : exception.getBindingResult().getFieldErrors()) {
      var field = SnakeCaseStrategy.INSTANCE.translate(error.getField());
      errors.putIfAbsent(field, error.getDefaultMessage());
    }
    return ResponseEntity.badRequest()
        .body(ErrorResponse.rejected("Invalid payment request", errors));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableRequest() {
    // Parser messages and rejected values can contain the full card number or CVV.
    return ResponseEntity.badRequest()
        .body(ErrorResponse.rejected("Invalid JSON or field types", Map.of()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleInvalidIdentifier() {
    return ResponseEntity.badRequest().body(new ErrorResponse("Invalid payment identifier"));
  }
}
