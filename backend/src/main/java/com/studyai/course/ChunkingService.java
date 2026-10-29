package com.studyai.course;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @deprecated Replaced by the independent Python FastAPI AI microservice in ai-service/ (chunker.py).
 */
@Deprecated(since = "Sprint 2 - Hybrid Architecture", forRemoval = false)
@Service
public class ChunkingService {

    public static final int TARGET_CHUNK_SIZE = 800;
    public static final int CHUNK_OVERLAP = 120;
    public static final int MIN_CHUNK_SIZE = 40;

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    /**
     * Splits cleaned text into overlapping segments targeting ~800 characters with ~120 characters overlap,
     * adhering to natural linguistic boundaries (paragraphs, sentences, word endings).
     *
     * @param text Cleaned text to partition
     * @return Ordered list of chunk text segments
     */
    public List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        String cleaned = text.trim();
        if (cleaned.length() <= TARGET_CHUNK_SIZE) {
            chunks.add(cleaned);
            return chunks;
        }

        int start = 0;
        int textLength = cleaned.length();

        while (start < textLength) {
            int targetEnd = Math.min(start + TARGET_CHUNK_SIZE, textLength);

            if (targetEnd < textLength) {
                int splitPoint = findBestSplitPoint(cleaned, start, targetEnd);
                String chunk = cleaned.substring(start, splitPoint).trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }

                // Compute next start position taking overlap into account
                int nextStart = splitPoint - CHUNK_OVERLAP;
                if (nextStart <= start) {
                    nextStart = splitPoint;
                } else {
                    // Advance nextStart to the beginning of the next word to avoid starting mid-word
                    while (nextStart < splitPoint && !Character.isWhitespace(cleaned.charAt(nextStart))) {
                        nextStart++;
                    }
                    while (nextStart < splitPoint && Character.isWhitespace(cleaned.charAt(nextStart))) {
                        nextStart++;
                    }
                }
                start = Math.max(nextStart, start + 1);
            } else {
                // Final chunk handling
                String lastChunk = cleaned.substring(start).trim();
                if (!lastChunk.isEmpty()) {
                    // If the final chunk is tiny and we already have chunks, append it to the last chunk
                    if (lastChunk.length() < MIN_CHUNK_SIZE && !chunks.isEmpty()) {
                        int lastIdx = chunks.size() - 1;
                        String merged = chunks.get(lastIdx) + " " + lastChunk;
                        chunks.set(lastIdx, merged.trim());
                    } else {
                        chunks.add(lastChunk);
                    }
                }
                break;
            }
        }

        return chunks;
    }

    /**
     * Finds the most natural semantic break point between start and targetEnd.
     * Priority:
     * 1. Paragraph boundary (\n\n)
     * 2. Sentence boundary (. ! ? followed by space)
     * 3. Line break (\n)
     * 4. Word boundary (space)
     * 5. Hard break at targetEnd
     */
    private int findBestSplitPoint(String text, int start, int targetEnd) {
        int searchStart = Math.max(start + (TARGET_CHUNK_SIZE / 2), start + 80);
        if (searchStart >= targetEnd) {
            searchStart = start + (targetEnd - start) / 2;
        }

        String searchWindow = text.substring(searchStart, targetEnd);

        // 1. Paragraph break (\n\n)
        int paragraphIdx = searchWindow.lastIndexOf("\n\n");
        if (paragraphIdx != -1) {
            return searchStart + paragraphIdx + 2;
        }

        // 2. Sentence boundary (. ! ? followed by whitespace)
        int lastSentenceEnd = -1;
        Matcher matcher = SENTENCE_END.matcher(searchWindow);
        while (matcher.find()) {
            lastSentenceEnd = matcher.start();
        }
        if (lastSentenceEnd != -1) {
            return searchStart + lastSentenceEnd;
        }

        // 3. Line break (\n)
        int newlineIdx = searchWindow.lastIndexOf('\n');
        if (newlineIdx != -1) {
            return searchStart + newlineIdx + 1;
        }

        // 4. Word boundary (space)
        int spaceIdx = searchWindow.lastIndexOf(' ');
        if (spaceIdx != -1) {
            return searchStart + spaceIdx + 1;
        }

        // 5. Fallback hard split
        return targetEnd;
    }
}
