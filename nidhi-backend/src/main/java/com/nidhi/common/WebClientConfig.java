package com.nidhi.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("googleSpeechWebClient")
    public WebClient googleSpeechWebClient(
            @Value("${google.speech.url}") String speechUrl) {
        return WebClient.builder()
                .baseUrl(speechUrl)
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Bean("googleTranslateWebClient")
    public WebClient googleTranslateWebClient(
            @Value("${google.translate.url}") String translateUrl) {
        return WebClient.builder()
                .baseUrl(translateUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean("sidecarWebClient")
    public WebClient sidecarWebClient(
            @Value("${insect.sidecar.url}") String sidecarUrl) {
        return WebClient.builder()
                .baseUrl(sidecarUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean("bankWebClient")
    public WebClient bankWebClient(
            @Value("${bank.server.url:http://localhost:8082}") String bankUrl) {
        return WebClient.builder()
                .baseUrl(bankUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
