package com.studyai.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextCleaningServiceTest {

    private TextCleaningService cleaningService;

    @BeforeEach
    void setUp() {
        cleaningService = new TextCleaningService();
    }

    @Test
    @DisplayName("Should normalize carriage returns and line endings to Unix newline")
    void shouldNormalizeLineEndings() {
        String input = "Ligne 1\r\nLigne 2\rLigne 3\nLigne 4";
        String cleaned = cleaningService.cleanText(input);

        assertThat(cleaned).isEqualTo("Ligne 1\nLigne 2\nLigne 3\nLigne 4");
    }

    @Test
    @DisplayName("Should collapse consecutive horizontal spaces and tabs into single space")
    void shouldCollapseHorizontalWhitespace() {
        String input = "Algorithme   de    tri   \t  rapide (Quicksort)";
        String cleaned = cleaningService.cleanText(input);

        assertThat(cleaned).isEqualTo("Algorithme de tri rapide (Quicksort)");
    }

    @Test
    @DisplayName("Should reduce 3 or more consecutive newlines to clean double newline")
    void shouldReduceExcessiveNewlines() {
        String input = "Paragraphe 1\n\n\n\n\nParagraphe 2\n\n\nParagraphe 3";
        String cleaned = cleaningService.cleanText(input);

        assertThat(cleaned).isEqualTo("Paragraphe 1\n\nParagraphe 2\n\nParagraphe 3");
    }

    @Test
    @DisplayName("Should remove control characters but preserve accents, punctuation and math formulas")
    void shouldPreservePunctuationAndMathFormulas() {
        String input = "Théorème : Pour tout x \u0000dans \u0007R, f(x) = x^2 + 2x + 1 >= 0 ! C'est vrai ?";
        String cleaned = cleaningService.cleanText(input);

        assertThat(cleaned).isEqualTo("Théorème : Pour tout x dans R, f(x) = x^2 + 2x + 1 >= 0 ! C'est vrai ?");
    }

    @Test
    @DisplayName("Should detect placeholder for scanned/empty PDFs and return empty string")
    void shouldDetectEmptyScannedPdfPlaceholder() {
        String placeholder = "[Information] Ce document PDF ne contient aucun texte extractible (il s'agit probablement d'un document numérisé ou composé uniquement d'images). L'analyse OCR n'est pas encore implémentée.";
        String cleaned = cleaningService.cleanText(placeholder);

        assertThat(cleaned).isEmpty();
        assertThat(cleaningService.isTextExploitable(cleaned)).isFalse();
    }

    @Test
    @DisplayName("Should identify text exploitability correctly")
    void shouldIdentifyTextExploitability() {
        assertThat(cleaningService.isTextExploitable(null)).isFalse();
        assertThat(cleaningService.isTextExploitable("")).isFalse();
        assertThat(cleaningService.isTextExploitable("   \n\t  ")).isFalse();
        assertThat(cleaningService.isTextExploitable("--- === ***")).isFalse();
        assertThat(cleaningService.isTextExploitable("abc 123")).isFalse(); // Less than 15 alphanumeric chars
        assertThat(cleaningService.isTextExploitable("Introduction aux bases de données relationnelles SQL")).isTrue();
    }
}
