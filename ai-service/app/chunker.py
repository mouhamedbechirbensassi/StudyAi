"""
StudyAI - Semantic Text Chunking Service
"""
import re
from typing import List, Tuple
from .models import PageInput, ChunkItem
from .cleaner import clean_text, is_text_exploitable

SENTENCE_END = re.compile(r"(?<=[.!?])\s+")
MIN_CHUNK_SIZE = 40


def split_text_into_chunks(
    text: str | None,
    target_chunk_size: int = 800,
    chunk_overlap: int = 120
) -> List[str]:
    """
    Splits text into overlapping segments targeting ~target_chunk_size with ~chunk_overlap,
    prioritizing natural linguistic boundaries (paragraphs, sentences, word endings).
    """
    if not text or not text.strip():
        return []

    cleaned = text.strip()
    if len(cleaned) <= target_chunk_size:
        return [cleaned]

    chunks: List[str] = []
    start = 0
    text_len = len(cleaned)

    while start < text_len:
        target_end = min(start + target_chunk_size, text_len)

        if target_end < text_len:
            split_point = _find_best_split_point(cleaned, start, target_end, target_chunk_size)
            chunk = cleaned[start:split_point].strip()
            if chunk:
                chunks.append(chunk)

            # Advance with overlap
            next_start = split_point - chunk_overlap
            if next_start <= start:
                next_start = split_point
            else:
                # Advance next_start to the beginning of the next word to avoid starting mid-word
                while next_start < split_point and not cleaned[next_start].isspace():
                    next_start += 1
                while next_start < split_point and cleaned[next_start].isspace():
                    next_start += 1

            start = max(next_start, start + 1)
        else:
            # Trailing final chunk
            last_chunk = cleaned[start:].strip()
            if last_chunk:
                # If too small and previous chunks exist, merge with previous chunk
                if len(last_chunk) < MIN_CHUNK_SIZE and chunks:
                    chunks[-1] = (chunks[-1] + " " + last_chunk).strip()
                else:
                    chunks.append(last_chunk)
            break

    return chunks


def _find_best_split_point(text: str, start: int, target_end: int, target_chunk_size: int) -> int:
    """
    Finds the optimal cut point within [search_start, target_end] adhering to natural linguistic boundaries:
    1. Paragraph boundary (\n\n)
    2. Sentence boundary (. ! ? followed by space)
    3. Line break (\n)
    4. Word boundary (space)
    5. Hard break fallback
    """
    search_start = max(start + (target_chunk_size // 2), start + 80)
    if search_start >= target_end:
        search_start = start + (target_end - start) // 2

    window = text[search_start:target_end]

    # 1. Paragraph boundary
    paragraph_idx = window.rfind("\n\n")
    if paragraph_idx != -1:
        return search_start + paragraph_idx + 2

    # 2. Sentence boundary
    last_sentence_end = -1
    for match in SENTENCE_END.finditer(window):
        last_sentence_end = match.start()
    if last_sentence_end != -1:
        return search_start + last_sentence_end

    # 3. Line break
    newline_idx = window.rfind("\n")
    if newline_idx != -1:
        return search_start + newline_idx + 1

    # 4. Word boundary
    space_idx = window.rfind(" ")
    if space_idx != -1:
        return search_start + space_idx + 1

    # 5. Fallback hard cut
    return target_end


def process_pages_to_chunks(
    pages: List[PageInput],
    fallback_text: str | None,
    target_chunk_size: int = 800,
    chunk_overlap: int = 120
) -> Tuple[List[ChunkItem], str, bool]:
    """
    Orchestrates page-by-page cleaning and chunking.
    Returns: (chunks_list, combined_cleaned_text, is_exploitable)
    """
    # 1. Clean pages and accumulate full text
    cleaned_pages: List[Tuple[int | None, str]] = []
    full_text_parts: List[str] = []

    for page in pages:
        cleaned_page = clean_text(page.text)
        if cleaned_page:
            cleaned_pages.append((page.page_number, cleaned_page))
            full_text_parts.append(cleaned_page)

    # Fallback to single text if no pages provided or pages had no content
    if not cleaned_pages and fallback_text:
        cleaned_fallback = clean_text(fallback_text)
        if cleaned_fallback:
            cleaned_pages.append((1, cleaned_fallback))
            full_text_parts.append(cleaned_fallback)

    combined_text = "\n\n".join(full_text_parts).strip()
    exploitable = is_text_exploitable(combined_text)

    if not exploitable or not cleaned_pages:
        return [], combined_text, False

    # 2. Generate chunks maintaining global sequential index
    chunks: List[ChunkItem] = []
    global_index = 0

    for page_num, page_content in cleaned_pages:
        segments = split_text_into_chunks(page_content, target_chunk_size, chunk_overlap)
        for segment in segments:
            chunks.append(
                ChunkItem(
                    chunk_index=global_index,
                    content=segment,
                    page_number=page_num,
                    char_count=len(segment)
                )
            )
            global_index += 1

    # If per-page chunking produced no chunks but combined text was exploitable
    if not chunks and combined_text:
        segments = split_text_into_chunks(combined_text, target_chunk_size, chunk_overlap)
        for segment in segments:
            chunks.append(
                ChunkItem(
                    chunk_index=global_index,
                    content=segment,
                    page_number=1,
                    char_count=len(segment)
                )
            )
            global_index += 1

    return chunks, combined_text, bool(chunks)
