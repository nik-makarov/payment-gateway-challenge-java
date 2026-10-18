package com.checkout.payment.gateway.bank;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.payment.PaymentRequest;
import com.checkout.payment.gateway.payment.PaymentResult;
import com.checkout.payment.gateway.payment.PaymentStatus;
import com.checkout.payment.gateway.support.PaymentFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

@RestClientTest(BankClient.class)
@AutoConfigureMockRestServiceServer(enabled = false)
@Import(BankConfiguration.class)
class BankClientIntegrationTest {

  private static final UUID PAYMENT_ID = UUID.fromString("05e744be-fb40-438a-beb6-ae79030e9c49");
  private static final WireMockServer BANK = new WireMockServer(options().dynamicPort());

  @Autowired
  private BankClient client;
  @Autowired
  private ObjectMapper json;

  @DynamicPropertySource
  static void bankAddress(DynamicPropertyRegistry properties) {
    BANK.start();
    properties.add("bank.base-url", BANK::baseUrl);
  }

  @BeforeEach
  void resetBank() {
    BANK.resetAll();
  }

  @AfterAll
  static void stopBank() {
    BANK.stop();
  }

  @Test
  void sendsTheBankContractThroughTheProductionHttpConfiguration() throws Exception {
    respond(200, "{\"authorized\":true}");
    var request = new PaymentRequest(
        "0000000000000000001", 1, 2031, "GBP", Long.MAX_VALUE, "012");

    assertThat(client.authorize(PAYMENT_ID, request))
        .isEqualTo(PaymentResult.success(PaymentStatus.AUTHORIZED));

    assertThat(BANK.getAllServeEvents()).hasSize(1);
    var sent = BANK.getAllServeEvents().get(0).getRequest();
    assertThat(sent.getMethod().getName()).isEqualTo("POST");
    assertThat(sent.getUrl()).isEqualTo("/payments");
    assertThat(MediaType.parseMediaTypes(sent.getHeader("Accept"))).contains(
        MediaType.APPLICATION_JSON);
    assertThat(sent.getHeader("Content-Type")).isEqualTo("application/json");
    assertThat(json.readTree(sent.getBodyAsString())).isEqualTo(json.readTree("""
        {"card_number":"0000000000000000001","expiry_date":"01/2031",
         "currency":"GBP","amount":9223372036854775807,"cvv":"012"}
        """));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void acceptsBankDecisionsAndIgnoresUnrelatedMetadata(boolean authorized) {
    respond(200, """
        {"authorized":%s,"metadata":{"processor":"test"},"extra":[null,2]}
        """.formatted(authorized));

    assertThat(client.authorize(PAYMENT_ID, PaymentFixtures.request()))
        .isEqualTo(PaymentResult.success(
            authorized ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{}", "{\"authorized\":null}", "{\"authorized\":\"true\"}",
      "{\"authorized\":1}", "{\"authorized\":{}}",
      "{\"authorized\":false,\"authorized\":true}",
      "{\"authorized\":false} {\"authorized\":true}",
      "not json", ""
  })
  void rejectsMalformedOrMistypedSuccessfulResponsesWithoutRetrying(String body) {
    respond(200, body);

    assertThat(client.authorize(PAYMENT_ID, PaymentFixtures.request()))
        .isEqualTo(PaymentResult.failure());
    assertThat(BANK.getAllServeEvents()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {302, 400, 503})
  void doesNotRedirectOrRetryHttpErrors(int status) {
    BANK.stubFor(post(urlEqualTo("/payments")).willReturn(aResponse()
        .withStatus(status).withHeader("Content-Type", "application/json")
        .withHeader("Location", "/payments").withHeader("Retry-After", "0")
        .withBody("{\"authorized\":true}")));

    assertThat(client.authorize(PAYMENT_ID, PaymentFixtures.request()))
        .isEqualTo(PaymentResult.failure());
    assertThat(BANK.getAllServeEvents()).hasSize(1);
  }

  @Test
  void doesNotRetryNetworkFailures() {
    BANK.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

    assertThat(client.authorize(PAYMENT_ID, PaymentFixtures.request()))
        .isEqualTo(PaymentResult.failure());
    assertThat(BANK.getAllServeEvents()).hasSize(1);
  }

  private void respond(int status, String body) {
    BANK.stubFor(post(urlEqualTo("/payments")).willReturn(aResponse()
        .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
  }
}
