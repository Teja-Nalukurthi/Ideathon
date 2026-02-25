package com.nidhi.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Voice / NLP client backed by Google Cloud APIs (replaces Bhashini).
 *
 * Setup:
 *   1. https://console.cloud.google.com/apis/library
 *      → Enable "Cloud Speech-to-Text API" + "Cloud Translation API"
 *   2. https://console.cloud.google.com/apis/credentials → Create API key
 *   3. PowerShell: $env:GOOGLE_API_KEY="AIzaSy..."
 *
 * Free tier (monthly): 60 min ASR + 500k chars translation — enough for a demo.
 */
@Slf4j
@Service
public class BhashiniClient {

    private final WebClient speechClient;
    private final WebClient translateClient;

    @Value("${google.api.key}") private String apiKey;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // Maps ISO 639-1 → BCP-47 language tags for Google Speech-to-Text
    private static final Map<String, String> SPEECH_LANG = Map.ofEntries(
            Map.entry("hi", "hi-IN"),
            Map.entry("te", "te-IN"),
            Map.entry("ta", "ta-IN"),
            Map.entry("kn", "kn-IN"),
            Map.entry("bn", "bn-IN"),
            Map.entry("mr", "mr-IN"),
            Map.entry("gu", "gu-IN"),
            Map.entry("pa", "pa-Guru-IN"),
            Map.entry("or", "or-IN"),
            Map.entry("en", "en-IN")
    );

    public BhashiniClient(
            @Qualifier("googleSpeechWebClient") WebClient speechClient,
            @Qualifier("googleTranslateWebClient") WebClient translateClient) {
        this.speechClient = speechClient;
        this.translateClient = translateClient;
    }

    /**
     * Transcribe audio using Google Cloud Speech-to-Text.
     * Audio must be base64-encoded WAV at 16kHz mono.
     */
    public String transcribeAudio(String audioBase64, String sourceLanguage) {
        try {
            String langCode = SPEECH_LANG.getOrDefault(sourceLanguage, "hi-IN");
            var body = Map.of(
                    "config", Map.of(
                            "encoding", "LINEAR16",
                            "sampleRateHertz", 16000,
                            "languageCode", langCode,
                            "model", "default"
                    ),
                    "audio", Map.of("content", audioBase64)
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = speechClient.post()
                    .uri("/v1/speech:recognize?key={key}", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            // response.results[0].alternatives[0].transcript
            var results = (List<?>) response.get("results");
            if (results == null || results.isEmpty()) {
                log.warn("Speech-to-Text returned no results for lang={}", langCode);
                return null;
            }
            var alts = (List<?>) ((Map<?, ?>) results.get(0)).get("alternatives");
            String transcript = (String) ((Map<?, ?>) alts.get(0)).get("transcript");
            log.info("Google ASR [{}]: '{}'", langCode, transcript);
            return transcript;
        } catch (Exception e) {
            log.error("Google Speech-to-Text failed [{}]: {}", sourceLanguage, e.getMessage());
            return null;
        }
    }

    /**
     * Translate text to English using Google Cloud Translation API.
     */
    public String translateToEnglish(String text, String sourceLanguage) {
        if ("en".equals(sourceLanguage)) return text;
        try {
            var body = Map.of(
                    "q", text,
                    "source", sourceLanguage,
                    "target", "en",
                    "format", "text"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = translateClient.post()
                    .uri("/language/translate/v2?key={key}", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            String translated = extractTranslation(response);
            return translated != null ? translated : text;
        } catch (Exception e) {
            log.warn("Google Translate failed, returning original: {}", e.getMessage());
            return text;
        }
    }

    /**
     * Translate English confirmation text back into the user's language.
     */
    public String generateConfirmationText(String recipient, long amountPaise, String targetLanguage) {
        long rupees = amountPaise / 100;
        long paise  = amountPaise % 100;
        String english = paise > 0
                ? String.format("Send %d rupees and %d paise to %s?", rupees, paise, recipient)
                : String.format("Send %d rupees to %s?", rupees, recipient);

        if ("en".equals(targetLanguage)) return english;

        try {
            var body = Map.of(
                    "q", english,
                    "source", "en",
                    "target", targetLanguage,
                    "format", "text"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = translateClient.post()
                    .uri("/language/translate/v2?key={key}", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            String translated = extractTranslation(response);
            return translated != null ? translated : english;
        } catch (Exception e) {
            log.warn("Google confirmation translation failed, using English: {}", e.getMessage());
            return english;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTranslation(Map<String, Object> response) {
        try {
            var data = (Map<?, ?>) response.get("data");
            var translations = (List<?>) data.get("translations");
            return (String) ((Map<?, ?>) translations.get(0)).get("translatedText");
        } catch (Exception e) {
            log.error("Failed to parse Google Translate response: {}", e.getMessage());
            return null;
        }
    }
}
