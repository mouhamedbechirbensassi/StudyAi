import pytest
from app.cleaner import clean_text, is_text_exploitable


def test_should_normalize_line_endings():
    raw = "Ligne 1\r\nLigne 2\rLigne 3\nLigne 4"
    cleaned = clean_text(raw)
    assert cleaned == "Ligne 1\nLigne 2\nLigne 3\nLigne 4"


def test_should_collapse_horizontal_whitespace():
    raw = "Algorithme   de    tri   \t  rapide (Quicksort)"
    cleaned = clean_text(raw)
    assert cleaned == "Algorithme de tri rapide (Quicksort)"


def test_should_reduce_excessive_newlines():
    raw = "Paragraphe 1\n\n\n\n\nParagraphe 2\n\n\nParagraphe 3"
    cleaned = clean_text(raw)
    assert cleaned == "Paragraphe 1\n\nParagraphe 2\n\nParagraphe 3"


def test_should_preserve_punctuation_math_formulas_and_accents():
    raw = "Théorème : Pour tout x \x00dans \x07R, f(x) = x^2 + 2x + 1 >= 0 ! C'est vrai ?"
    cleaned = clean_text(raw)
    assert cleaned == "Théorème : Pour tout x dans R, f(x) = x^2 + 2x + 1 >= 0 ! C'est vrai ?"


def test_should_detect_scanned_pdf_placeholder():
    placeholder = "[Information] Ce document PDF ne contient aucun texte extractible (il s'agit probablement d'un document numérisé ou composé uniquement d'images). L'analyse OCR n'est pas encore implémentée."
    cleaned = clean_text(placeholder)
    assert cleaned == ""
    assert is_text_exploitable(cleaned) is False


def test_should_validate_text_exploitability():
    assert is_text_exploitable(None) is False
    assert is_text_exploitable("") is False
    assert is_text_exploitable("   \n\t  ") is False
    assert is_text_exploitable("--- === ***") is False
    assert is_text_exploitable("abc 123") is False  # Less than 15 alphanumeric chars
    assert is_text_exploitable("Introduction aux bases de données relationnelles SQL") is True
