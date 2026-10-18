package com.checkout.payment.gateway.bank;

import static com.checkout.payment.gateway.payment.PaymentResult.failure;
import static com.checkout.payment.gateway.payment.PaymentResult.success;
import static com.checkout.payment.gateway.payment.PaymentStatus.AUTHORIZED;
import static com.checkout.payment.gateway.payment.PaymentStatus.DECLINED;

import com.checkout.payment.gateway.payment.PaymentRequest;
import com.checkout.payment.gateway.payment.PaymentResult;
import com.checkout.payment.gateway.payment.PaymentStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankClient {

  private final RestTemplate bankRestTemplate;

  public PaymentResult<PaymentStatus> authorize(UUID paymentId, PaymentRequest request) {
    log.atInfo().addKeyValue("paymentId", paymentId).log("Requesting bank authorization");
    try {
      var response = bankRestTemplate.postForEntity("/payments", BankRequest.from(request),
          BankResponse.class);
      var body = response.getBody();
      var successful = response.getStatusCode().is2xxSuccessful();
      if (!successful || body == null) {
        log.atWarn()
            .addKeyValue("paymentId", paymentId)
            .addKeyValue("status", response.getStatusCode().value())
            .addKeyValue("reason", successful ? "EMPTY_RESPONSE" : "HTTP_ERROR")
            .log("Bank response is invalid");
        return failure();
      }
      log.atInfo()
          .addKeyValue("paymentId", paymentId)
          .addKeyValue("status", response.getStatusCode().value())
          .addKeyValue("authorized", body.authorized())
          .addKeyValue("authorizationCode", body.authorizationCodeForLogging().orElse(null))
          .log("Bank response");
      return success(body.authorized() ? AUTHORIZED : DECLINED);
    } catch (RestClientException exception) {
      var status = exception instanceof RestClientResponseException response
          ? response.getStatusCode().value() : -1;
      var cause = exception.getMostSpecificCause();
      log.atWarn()
          .addKeyValue("paymentId", paymentId)
          .addKeyValue("status", status)
          .addKeyValue("exceptionType", exception.getClass().getSimpleName())
          .addKeyValue("causeType", cause.getClass().getSimpleName())
          .log("Bank request failed");
      return failure();
    }
  }
}
