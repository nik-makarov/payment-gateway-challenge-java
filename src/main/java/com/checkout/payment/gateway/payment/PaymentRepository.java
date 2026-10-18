package com.checkout.payment.gateway.payment;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class PaymentRepository {

  private final Map<UUID, Payment> payments = new ConcurrentHashMap<>();

  public void save(Payment payment) {
    payments.put(payment.id(), payment);
  }

  public Optional<Payment> findById(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

  public int count() {
    return payments.size();
  }
}
