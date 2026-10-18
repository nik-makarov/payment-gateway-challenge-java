package com.checkout.payment.gateway.payment;

import com.checkout.payment.gateway.bank.BankClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentRepository payments;
  private final BankClient bank;

  public Optional<Payment> findById(UUID id) {
    return payments.findById(id);
  }

  public PaymentResult<Payment> processPayment(PaymentRequest request) {
    var paymentId = UUID.randomUUID();
    var result = bank.authorize(paymentId, request);
    if (!result.isSuccess()) {
      log.atWarn().addKeyValue("paymentId", paymentId).log("Payment processing failed");
      return PaymentResult.failure();
    }

    var status = result.value().get();
    var payment = Payment.completed(paymentId, request, status);
    payments.save(payment);
    log.atInfo()
        .addKeyValue("paymentId", payment.id())
        .addKeyValue("status", payment.status())
        .log("Payment processed");
    return PaymentResult.success(payment);
  }
}
