"""
StudyAI - Text Cleaning & Normalization Service
"""
import re

CARRIAGE_RETURNS = re.compile(r"\r\n|\r")
MULTIPLE_SPACES = re.compile(r"[ \t]+")
EXCESSIVE_NEWLINES = re.compile(r"\n{3,}")
LINE_START_END_SPACES = re.compile(r"^[ \t]+|[ \t]+$", re.MULTILINE)
CONTROL_CHARS = re.compile(r"[^\x20-\x7E\t\n\u00A0-\uFFFF]")


def clean_text(raw_text: str | None) -> str:
    """
    Cleans and normalizes extracted text while strictly preserving semantic content,
    paragraphs, sentence structure, punctuation, accents, code, and mathematical formulas.
    """
    if raw_text is None or not raw_text.strip():
        return ""

    # 1. Ignore placeholder for empty/scanned PDFs
    stripped = raw_text.strip()
    if stripped.startswith("[Information]"):
        return ""

    # 2. Normalize Windows and old Mac carriage returns to standard Unix newline '\n'
    text = CARRIAGE_RETURNS.sub("\n", raw_text)

    # 3. Strip non-printable control characters (excluding newline and tab)
    text = CONTROL_CHARS.sub("", text)

    # 4. Collapse consecutive horizontal whitespace (spaces, tabs) into a single space
    text = MULTIPLE_SPACES.sub(" ", text)

    # 5. Trim leading and trailing spaces per line
    text = LINE_START_END_SPACES.sub("", text)

    # 6. Reduce 3 or more consecutive newlines to clean double newlines (paragraph boundary)
    text = EXCESSIVE_NEWLINES.sub("\n\n", text)

    return text.strip()


def is_text_exploitable(cleaned_text: str | None) -> bool:
    """
    Validates whether the cleaned text contains meaningful alphanumeric content for RAG indexing.
    Must contain at least 15 alphanumeric characters.
    """
    if not cleaned_text or not cleaned_text.strip():
        return False

    alnum_count = sum(1 for char in cleaned_text if char.isalnum())
    return alnum_count >= 15
