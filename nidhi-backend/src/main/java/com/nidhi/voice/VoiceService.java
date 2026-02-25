package com.nidhi.voice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceService {

    private final BhashiniClient bhashini;
    private final IntentParser parser;

    public VoiceParseResult processInput(String audioBase64, String textFallback, String languageCode) {
        String lang = languageCode != null ? languageCode : "hi";
        boolean usedAudio = false;

        String sourceText = null;

        if (audioBase64 != null && !audioBase64.isBlank()) {
            sourceText = bhashini.transcribeAudio(audioBase64, lang);
            if (sourceText != null && !sourceText.isBlank()) {
                usedAudio = true;
                log.info("ASR success [{}]: '{}'", lang, sourceText);
            } else {
                log.warn("ASR returned empty, falling back to text");
            }
        }

        if (sourceText == null || sourceText.isBlank()) {
            sourceText = textFallback;
        }

        if (sourceText == null || sourceText.isBlank()) {
            return VoiceParseResult.error("No input received. Please speak or type your transaction.");
        }

        String englishText = bhashini.translateToEnglish(sourceText, lang);
        log.debug("Translation [{}→en]: '{}' → '{}'", lang, sourceText, englishText);

        IntentParser.ParsedIntent intent = parser.parse(englishText);

        if (!intent.isActionable()) {
            return VoiceParseResult.error(intent.errorMessage() != null
                    ? intent.errorMessage()
                    : "Could not understand the transaction. Please try again.");
        }

        String confirmText = bhashini.generateConfirmationText(
                intent.recipient(), intent.amountPaise(), lang);

        log.info("Voice parse complete: {}  {} → {}",
                intent.type(), intent.formattedAmount(), intent.recipient());

        return new VoiceParseResult(true, sourceText, englishText, intent.type().name(),
                intent.recipient(), intent.amountPaise(), intent.formattedAmount(),
                confirmText, lang, intent.confidence(), usedAudio, null);
    }

    public record VoiceParseResult(
            boolean success,
            String originalText,
            String translatedText,
            String intentType,
            String recipient,
            long amountPaise,
            String formattedAmount,
            String confirmationText,
            String language,
            double confidence,
            boolean usedAudio,
            String errorMessage) {

        static VoiceParseResult error(String msg) {
            return new VoiceParseResult(false, null, null, null, null,
                    0, null, null, null, 0.0, false, msg);
        }
    }
}
