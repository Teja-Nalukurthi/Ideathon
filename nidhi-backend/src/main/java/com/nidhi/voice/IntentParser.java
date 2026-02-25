package com.nidhi.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
public class IntentParser {

    private static final Set<String> TRANSFER_WORDS = Set.of(
            "send", "transfer", "pay", "give", "remit", "deposit", "bhejo", "daalo");

    private static final Pattern DIGIT_AMOUNT = Pattern.compile(
            "(?:rs\\.?|rupees?|inr)?\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|rupees?|paise?)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PHONE = Pattern.compile("\\b([6-9]\\d{9})\\b");

    private static final Pattern RECIPIENT = Pattern.compile(
            "\\bto\\s+([a-zA-Z][a-zA-Z\\s]{1,30}|[6-9]\\d{9})",
            Pattern.CASE_INSENSITIVE);

    private static final LinkedHashMap<String, Long> WORDS = new LinkedHashMap<>() {{
        put("nineteen", 19L); put("eighteen", 18L); put("seventeen", 17L);
        put("sixteen", 16L); put("fifteen", 15L); put("fourteen", 14L);
        put("thirteen", 13L); put("twelve", 12L); put("eleven", 11L);
        put("crore", 10_000_000L); put("lakh", 100_000L); put("lakhs", 100_000L);
        put("thousand", 1_000L); put("hundred", 100L);
        put("ninety", 90L); put("eighty", 80L); put("seventy", 70L);
        put("sixty", 60L); put("fifty", 50L); put("forty", 40L);
        put("thirty", 30L); put("twenty", 20L); put("ten", 10L);
        put("nine", 9L); put("eight", 8L); put("seven", 7L); put("six", 6L);
        put("five", 5L); put("four", 4L); put("three", 3L); put("two", 2L);
        put("one", 1L); put("zero", 0L);
    }};

    public ParsedIntent parse(String text) {
        if (text == null || text.isBlank())
            return ParsedIntent.unknown("Empty input");

        String lower = text.toLowerCase().trim();
        log.debug("Parsing: '{}'", lower);

        IntentType type = IntentType.UNKNOWN;
        for (String w : TRANSFER_WORDS) {
            if (lower.contains(w)) { type = IntentType.TRANSFER; break; }
        }
        if (lower.contains("balance") || lower.contains("how much"))
            type = IntentType.BALANCE_CHECK;
        if (type == IntentType.UNKNOWN)
            return ParsedIntent.unknown("Cannot determine intent. Say: 'send 500 rupees to [name]'");
        if (type == IntentType.BALANCE_CHECK)
            return new ParsedIntent(IntentType.BALANCE_CHECK, null, 0, 0.9, null);

        long amountPaise = 0;
        double confidence = 0.0;

        Matcher dm = DIGIT_AMOUNT.matcher(lower);
        if (dm.find()) {
            try {
                double amount = Double.parseDouble(dm.group(1).replace(",", ""));
                amountPaise = Math.round(amount * 100);
                confidence = 0.85;
            } catch (NumberFormatException ignored) {}
        }

        if (amountPaise == 0) {
            long wordAmount = parseWordAmount(lower);
            if (wordAmount > 0) {
                amountPaise = wordAmount * 100;
                confidence = 0.75;
            }
        }

        String recipient = null;

        Matcher pm = PHONE.matcher(lower);
        if (pm.find()) {
            recipient = pm.group(1);
            confidence = Math.min(confidence + 0.10, 1.0);
        }

        if (recipient == null) {
            Matcher rm = RECIPIENT.matcher(lower);
            if (rm.find()) {
                recipient = capitalize(rm.group(1).trim());
                confidence = Math.min(confidence + 0.05, 1.0);
            }
        }

        if (amountPaise <= 0)
            return ParsedIntent.unknown("Could not understand amount. Say the number clearly.");
        if (recipient == null)
            return ParsedIntent.unknown("Could not understand recipient. Say 'to [name or number]'.");
        if (amountPaise > 1_000_000)
            return ParsedIntent.unknown("Amount exceeds Rs 10,000 limit.");

        log.info("Parsed: type={} recipient='{}' amount={}p confidence={}",
                type, recipient, amountPaise, confidence);

        return new ParsedIntent(type, recipient, amountPaise, confidence, null);
    }

    private long parseWordAmount(String text) {
        String[] words = text.split("[\\s-]+");
        long total = 0, current = 0;
        for (String word : words) {
            Long val = WORDS.get(word);
            if (val == null) continue;
            if (val == 100) { current = current == 0 ? 100 : current * 100; }
            else if (val >= 1000) { total += (current == 0 ? 1 : current) * val; current = 0; }
            else { current += val; }
        }
        return total + current;
    }

    private String capitalize(String s) {
        return Arrays.stream(s.split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
    }

    public enum IntentType { TRANSFER, BALANCE_CHECK, UNKNOWN }

    public record ParsedIntent(IntentType type, String recipient, long amountPaise,
                                double confidence, String errorMessage) {

        static ParsedIntent unknown(String reason) {
            return new ParsedIntent(IntentType.UNKNOWN, null, 0, 0.0, reason);
        }

        public boolean isActionable() {
            return type != IntentType.UNKNOWN && confidence >= 0.5;
        }

        public String formattedAmount() {
            if (amountPaise == 0) return "Rs 0";
            long r = amountPaise / 100, p = amountPaise % 100;
            return p > 0 ? String.format("Rs %d.%02d", r, p) : String.format("Rs %d", r);
        }
    }
}
