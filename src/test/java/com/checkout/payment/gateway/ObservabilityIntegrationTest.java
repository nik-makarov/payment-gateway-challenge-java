package com.checkout.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ObservabilityIntegrationTest {

  @Autowired
  private TestRestTemplate http;
  @Autowired
  private ObjectMapper json;

  @Test
  void servesPrometheusMetrics() {
    var response = http.getForEntity("/actuator/prometheus", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isNotNull();
    assertThat(
        response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
    assertThat(response.getBody()).contains("# HELP", "jvm_memory_used_bytes",
        "application=\"payment-gateway\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness",
      "/actuator/health/readiness"})
  void servesHealthProbes(String path) throws Exception {
    var response = http.getForEntity(path, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(json.readTree(response.getBody()).path("status").asText()).isEqualTo("UP");
  }

  @Test
  void writesJsonLogsToStandardOutput(CapturedOutput output) throws Exception {
    var message = "Observability integration test";
    LoggerFactory.getLogger(PaymentGatewayApplication.class).info(message);

    var lines = output.getOut().lines().filter(line -> line.contains(message)).toList();
    assertThat(lines).hasSize(1);
    var event = json.readTree(lines.get(0));
    assertThat(event.path("level").asText()).isEqualTo("INFO");
    assertThat(event.path("loggerName").asText()).isEqualTo(
        PaymentGatewayApplication.class.getName());
    assertThat(event.path("message").asText()).isEqualTo(message);
  }
}
