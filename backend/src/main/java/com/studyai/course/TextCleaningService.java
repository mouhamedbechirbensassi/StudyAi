package com.studyai.course;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * @deprecated Replaced by the independent Python FastAPI AI microservice in ai-service/ (cleaner.py).
 */
@Deprecated(since = "Sprint 2 - Hybrid Architecture", forRemoval = false)
@Service
public class TextCleaningService {

    private static final Pattern CARRIAGE_RETURNS = Pattern.compile("\r\n|\r");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("[ \t]+");
    private static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("\n{3,}");
    private static final Pattern LINE_START_END_SPACES = Pattern.compile("^[ \t]+|[ \t]+$", Pattern.MULTILINE);

    /**
     * Cleans and normalizes extracted text while preserving semantic structure,
     * paragraph breaks, punctuation, mathematical/code formulas and technical terms.
     */
    public String cleanText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        // 1. Check if the text matches our placeholder for empty/scanned PDFs
        if (rawText.trim().startsWith("[Information]")) {
            return "";
        }

        // 2. Normalize line endings to standard Unix newline '\n'
        String text = CARRIAGE_RETURNS.matcher(rawText).replaceAll("\n");

        // 3. Remove non-printable control characters except newline and tab
        text = text.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");

        // 4. Normalize multiple horizontal spaces and tabs to a single space
        text = MULTIPLE_SPACES.matcher(text).replaceAll(" ");

        // 5. Trim leading and trailing spaces on each line
        text = LINE_START_END_SPACES.matcher(text).replaceAll("");

        // 6. Reduce excessive blank lines (3 or more) to clean double newline (paragraph break)
        text = EXCESSIVE_NEWLINES.matcher(text).replaceAll("\n\n");

        return text.trim();
    }

    /**
     * Checks if the cleaned text contains meaningful, exploitable content for RAG indexing.
     */
    public boolean isTextExploitable(String cleanedText) {
        if (cleanedText == null || cleanedText.isBlank()) {
            return false;
        }

        // Must have at least 15 alphanumeric characters to be considered meaningful text
        long alphaNumericCount = cleanedText.chars()
                .filter(Character::isLetterOrDigit)
                .count();

        return alphaNumericCount >= 15;
    }
}
