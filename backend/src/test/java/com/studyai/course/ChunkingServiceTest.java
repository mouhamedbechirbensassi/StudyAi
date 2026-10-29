package com.studyai.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingService();
    }

    @Test
    @DisplayName("Should return a single chunk when text length is within TARGET_CHUNK_SIZE (800 chars)")
    void shouldReturnSingleChunkForShortText() {
        String shortText = "Les graphes orientés acycliques (DAG) sont utilisés dans les compilateurs et les systèmes de build.";
        List<String> chunks = chunkingService.splitIntoChunks(shortText);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(shortText);
    }

    @Test
    @DisplayName("Should return empty list for null or empty input")
    void shouldReturnEmptyForBlankInput() {
        assertThat(chunkingService.splitIntoChunks(null)).isEmpty();
        assertThat(chunkingService.splitIntoChunks("")).isEmpty();
        assertThat(chunkingService.splitIntoChunks("   \n\t  ")).isEmpty();
    }

    @Test
    @DisplayName("Should split long text (> 800 chars) into multiple chunks with overlap")
    void shouldSplitLongTextIntoOverlappingChunks() {
        StringBuilder sb = new StringBuilder();
        // Generate ~2000 characters of meaningful sentences
        for (int i = 1; i <= 25; i++) {
            sb.append("Le chapitre ").append(i)
              .append(" traite des structures de données avancées comme les arbres rouge et noir et les tas de Fibonacci. ");
        }
        String longText = sb.toString().trim();
        assertThat(longText.length()).isGreaterThan(2000);

        List<String> chunks = chunkingService.splitIntoChunks(longText);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);

        // Verify no empty chunks
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            assertThat(chunk).isNotBlank();
            // Each chunk should not drastically exceed target size + allowance
            assertThat(chunk.length()).isLessThanOrEqualTo(ChunkingService.TARGET_CHUNK_SIZE + 100);
        }

        // Verify overlap between consecutive chunks:
        // Chunk N and Chunk N+1 must share common words due to overlap
        for (int i = 0; i < chunks.size() - 1; i++) {
            String current = chunks.get(i);
            String next = chunks.get(i + 1);

            // The ending portion of 'current' should appear near the beginning of 'next'
            String endOfCurrent = current.substring(Math.max(0, current.length() - 80));
            // Find a common 20-character substring to confirm real semantic overlap
            boolean hasOverlap = false;
            for (int offset = 0; offset <= endOfCurrent.length() - 25; offset += 5) {
                String sub = endOfCurrent.substring(offset, offset + 25);
                if (next.contains(sub)) {
                    hasOverlap = true;
                    break;
                }
            }
            assertThat(hasOverlap)
                    .as("Chunk %d and chunk %d should overlap by ~120 characters", i, i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should prefer paragraph and sentence boundaries rather than cutting mid-sentence")
    void shouldCutAtSentenceBoundaries() {
        String paragraph1 = "Première phrase claire et nette. Deuxième phrase explicative sur l'analyse syntaxique. ";
        String paragraph2 = "Troisième phrase ouvrant une nouvelle section sur l'interprétation. Quatrième phrase conclusive. ";

        StringBuilder sb = new StringBuilder();
        while (sb.length() < 700) {
            sb.append(paragraph1);
        }
        sb.append("\n\n");
        while (sb.length() < 1400) {
            sb.append(paragraph2);
        }

        List<String> chunks = chunkingService.splitIntoChunks(sb.toString());

        assertThat(chunks).isNotEmpty();
        // First chunk should ideally end with a sentence terminator (. or !)
        String firstChunk = chunks.get(0).trim();
        assertThat(firstChunk.endsWith(".") || firstChunk.endsWith("!") || firstChunk.endsWith("?")).isTrue();
    }
}
