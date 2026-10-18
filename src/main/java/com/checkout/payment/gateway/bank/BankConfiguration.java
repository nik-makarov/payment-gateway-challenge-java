package com.checkout.payment.gateway.bank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class BankConfiguration {

  @Bean
  public RestTemplate bankRestTemplate(
      RestTemplateBuilder builder,
      @Value("${bank.base-url}") String baseUrl,
      @Value("${bank.connect-timeout-ms}") int connectTimeout,
      @Value("${bank.read-timeout-ms}") int readTimeout) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    // Streaming mode prevents the JDK from replaying a POST after an I/O failure.
    factory.setOutputStreaming(true);
    return builder.rootUri(baseUrl).requestFactory(() -> factory).build();
  }
}
