import pytest
from app.chunker import split_text_into_chunks, process_pages_to_chunks
from app.models import PageInput


def test_should_return_single_chunk_for_short_text():
    short_text = "Les graphes orientés acycliques (DAG) sont utilisés dans les compilateurs et les systèmes de build."
    chunks = split_text_into_chunks(short_text, target_chunk_size=800, chunk_overlap=120)
    assert len(chunks) == 1
    assert chunks[0] == short_text


def test_should_return_empty_for_blank_text():
    assert split_text_into_chunks(None) == []
    assert split_text_into_chunks("") == []
    assert split_text_into_chunks("   \n\t  ") == []


def test_should_split_long_text_into_overlapping_chunks():
    sentence = "Le chapitre traite des structures de données avancées comme les arbres rouge et noir et les tas de Fibonacci. "
    long_text = sentence * 25  # ~2750 chars

    chunks = split_text_into_chunks(long_text, target_chunk_size=800, chunk_overlap=120)
    assert len(chunks) >= 3

    # Check each chunk length and content
    for i, chunk in enumerate(chunks):
        assert chunk.strip() != ""
        assert len(chunk) <= 800 + 100

    # Verify overlap between consecutive chunks
    for i in range(len(chunks) - 1):
        curr_chunk = chunks[i]
        next_chunk = chunks[i + 1]

        # The end of curr_chunk must have text that also appears in next_chunk
        end_portion = curr_chunk[-80:]
        has_overlap = any(end_portion[j:j+25] in next_chunk for j in range(0, len(end_portion) - 25, 5))
        assert has_overlap, f"Overlap missing between chunk {i} and {i+1}"


def test_should_respect_sentence_and_paragraph_boundaries():
    p1 = "Première phrase claire et nette. Deuxième phrase explicative sur l'analyse syntaxique. "
    p2 = "Troisième phrase ouvrant une nouvelle section sur l'interprétation. Quatrième phrase conclusive. "

    text = (p1 * 8) + "\n\n" + (p2 * 8)
    chunks = split_text_into_chunks(text, target_chunk_size=800, chunk_overlap=120)

    assert len(chunks) >= 2
    first_chunk = chunks[0]
    # Should end at a sentence boundary
    assert first_chunk.endswith(".") or first_chunk.endswith("!") or first_chunk.endswith("?")


def test_should_process_pages_with_page_number_and_index():
    pages = [
        PageInput(page_number=1, text="Introduction au Machine Learning et aux concepts mathématiques fondamentaux."),
        PageInput(page_number=2, text="Les fonctions de perte, la descente de gradient stochastique et la rétropropagation.")
    ]

    chunks, combined, exploitable = process_pages_to_chunks(pages, fallback_text=None, target_chunk_size=800, chunk_overlap=120)

    assert exploitable is True
    assert len(chunks) == 2
    assert chunks[0].chunk_index == 0
    assert chunks[0].page_number == 1
    assert chunks[1].chunk_index == 1
    assert chunks[1].page_number == 2
    assert chunks[0].char_count == len(chunks[0].content)
    assert chunks[1].char_count == len(chunks[1].content)
