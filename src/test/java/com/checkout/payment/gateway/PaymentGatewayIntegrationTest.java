package com.checkout.payment.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.payment.PaymentRepository;
import com.checkout.payment.gateway.support.FixedClockConfiguration;
import com.checkout.payment.gateway.support.PaymentFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.stream.Stream;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentGatewayIntegrationTest {

  private static final WireMockServer BANK = new WireMockServer(options().dynamicPort());

  @Autowired
  private MockMvc mvc;
  @Autowired
  private TestRestTemplate http;
  @Autowired
  private ObjectMapper json;
  @Autowired
  private PaymentRepository payments;

  @DynamicPropertySource
  static void bankAddress(DynamicPropertyRegistry properties) {
    BANK.start();
    properties.add("bank.base-url", BANK::baseUrl);
  }

  @BeforeEach
  void resetExternalBank() {
    http.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory());
    BANK.resetAll();
    respond(200, "{\"authorized\":true,\"authorization_code\":\"bank-reference\"}");
  }

  @AfterAll
  static void stopExternalBank() {
    BANK.stop();
  }

  @ParameterizedTest
  @CsvSource({
      "true, 00000000000001, 000, USD, 1",
      "false, 0000000000000000001, 0000, EUR, 9223372036854775807"
  })
  void completesAndRetrievesPaymentOverRealIncomingAndOutgoingHttp(
      boolean authorized, String cardNumber, String cvv, String currency, long amount)
      throws Exception {
    respond(200, "{\"authorized\":" + authorized + "}");
    var request = validJson().put("card_number", cardNumber).put("cvv", cvv)
        .put("currency", currency).put("amount", amount).toString();
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    var created = http.postForEntity("/payment", new HttpEntity<>(request, headers), String.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var payment = json.readTree(created.getBody());
    var id = UUID.fromString(payment.path("id").asText());
    assertThat(payment).isEqualTo(json.readTree("""
        {"id":"%s","status":"%s","cardNumberLastFour":"0001",
         "expiryMonth":1,"expiryYear":2031,"currency":"%s","amount":%d}
        """.formatted(id, authorized ? "Authorized" : "Declined", currency, amount)));
    assertThat(created.getHeaders().getLocation()).hasPath("/payment/" + id);
    var retrieved = http.getForEntity(created.getHeaders().getLocation().getPath(), String.class);
    assertThat(retrieved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(json.readTree(retrieved.getBody())).isEqualTo(payment);
    assertThat(BANK.getAllServeEvents()).hasSize(1);
    var sent = json.readTree(BANK.getAllServeEvents().get(0).getRequest().getBodyAsString());
    assertThat(sent.path("card_number").asText()).isEqualTo(cardNumber);
    assertThat(sent.path("cvv").asText()).isEqualTo(cvv);
    assertThat(sent.path("amount").longValue()).isEqualTo(amount);
  }

  @Test
  void rejectsMissingRequiredFieldBeforeCallingTheBank() throws Exception {
    var request = validJson();
    request.remove("card_number");
    assertThat(rejected(request.toString()).path("errors").path("card_number").asText())
        .isEqualTo("is required");
  }

  @ParameterizedTest
  @MethodSource("invalidFieldTypes")
  void rejectsMistypedFieldsWithoutEchoingRejectedValues(String field, String value)
      throws Exception {
    var request = validJson();
    request.set(field, json.readTree(value));
    assertThat(rejected(request.toString()).path("errors").isEmpty()).isTrue();
  }

  static Stream<Arguments> invalidFieldTypes() {
    return Stream.of(
        Arguments.of("card_number", "{\"value\":\"4111111111110001\",\"cvv\":\"012\"}"),
        Arguments.of("expiry_month", "1.0"),
        Arguments.of("expiry_month", "\"1\""),
        Arguments.of("expiry_month", "2147483648"),
        Arguments.of("amount", "1.5"),
        Arguments.of("amount", "\"1050\""),
        Arguments.of("amount", "9223372036854775808"),
        Arguments.of("cvv", "[]"));
  }

  @ParameterizedTest
  @MethodSource("invalidRequestDocuments")
  void rejectsInvalidRequestDocuments(String body) throws Exception {
    rejected(body);
  }

  static Stream<String> invalidRequestDocuments() {
    return Stream.of("", "null", "[]", "{", "{}", PaymentFixtures.JSON + " {}",
        "{\"card_number\":\"4111111111110001\"," + PaymentFixtures.JSON.trim().substring(1));
  }

  @Test
  void rejectsExpiredCardBeforeCallingTheBank() throws Exception {
    var response = rejected(validJson().put("expiry_month", 5).put("expiry_year", 2030).toString());
    assertThat(response.path("errors").path("expiry_month").asText())
        .isEqualTo("must be after the current month");
  }

  @Test
  void bankOutageReturns503WithoutStoringPaymentOverRealHttp() {
    respond(503, "{}");
    var stored = payments.count();
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    var response = http.postForEntity("/payment", new HttpEntity<>(PaymentFixtures.JSON, headers),
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isEqualTo(
        "{\"message\":\"Unable to process payment with acquiring bank\"}");
    assertThat(response.getHeaders().getLocation()).isNull();
    assertThat(BANK.getAllServeEvents()).hasSize(1);
    assertThat(payments.count()).isEqualTo(stored);
  }

  @Test
  void returnsSafeErrorsForMissingAndMalformedPaymentIdentifiers() throws Exception {
    mvc.perform(get("/payment/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
        .andExpect(content().json("{\"message\":\"Payment not found\"}", true));
    mvc.perform(get("/payment/not-a-uuid")).andExpect(status().isBadRequest())
        .andExpect(content().json("{\"message\":\"Invalid payment identifier\"}", true));
    assertThat(BANK.getAllServeEvents()).isEmpty();
  }

  @Test
  void identicalSubmissionsRemainIndependentlyRetrievablePayments() throws Exception {
    var first = json.readTree(submit(PaymentFixtures.JSON).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString());
    var second = json.readTree(submit(PaymentFixtures.JSON).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString());

    assertThat(first.path("id")).isNotEqualTo(second.path("id"));
    mvc.perform(get("/payment/{id}", first.path("id").asText())).andExpect(status().isOk())
        .andExpect(content().json(first.toString(), true));
    mvc.perform(get("/payment/{id}", second.path("id").asText())).andExpect(status().isOk())
        .andExpect(content().json(second.toString(), true));
    assertThat(BANK.getAllServeEvents()).hasSize(2);
  }

  @Test
  void publishesThePaymentHttpContractInOpenApi() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/payment'].post.responses['201']").exists())
        .andExpect(jsonPath("$.paths['/payment'].post.responses['503']").exists())
        .andExpect(jsonPath("$.paths['/payment/{id}'].get").exists())
        .andExpect(
            jsonPath("$.components.schemas.PaymentRequest.properties.card_number.type").value(
                "string"));

    assertThat(BANK.getAllServeEvents()).isEmpty();
  }

  private ResultActions submit(String body) throws Exception {
    return mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  private ObjectNode validJson() throws Exception {
    return (ObjectNode) json.readTree(PaymentFixtures.JSON);
  }

  private JsonNode rejected(String body) throws Exception {
    var stored = payments.count();
    var response = submit(body).andExpect(status().isBadRequest())
        .andExpect(header().doesNotExist("Location"))
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").isString())
        .andExpect(jsonPath("$.errors").isMap())
        .andReturn().getResponse().getContentAsString();
    assertThat(BANK.getAllServeEvents()).isEmpty();
    assertThat(payments.count()).isEqualTo(stored);
    assertThat(response).doesNotContain(PaymentFixtures.CARD_NUMBER, PaymentFixtures.CVV);
    var parsed = json.readTree(response);
    assertThat(parsed.size()).isEqualTo(3);
    return parsed;
  }

  private void respond(int status, String body) {
    BANK.stubFor(WireMock.post(WireMock.urlEqualTo("/payments")).willReturn(aResponse()
        .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
  }
}
