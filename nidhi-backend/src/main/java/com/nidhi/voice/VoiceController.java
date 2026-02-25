package com.nidhi.voice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceService voiceService;

    @PostMapping("/parse")
    public ResponseEntity<VoiceService.VoiceParseResult> parse(
            @RequestBody VoiceParseRequest req) {

        log.info("Voice parse: lang={} hasAudio={} hasText={}",
                req.languageCode(),
                req.audioBase64() != null && !req.audioBase64().isBlank(),
                req.textFallback() != null && !req.textFallback().isBlank());

        return ResponseEntity.ok(
                voiceService.processInput(req.audioBase64(), req.textFallback(), req.languageCode()));
    }

    public record VoiceParseRequest(
            String audioBase64,
            String textFallback,
            String languageCode
    ) {}
}
