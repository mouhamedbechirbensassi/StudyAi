"""
StudyAI - AI Microservice Models
"""
from typing import List, Optional
from pydantic import BaseModel, Field


class PageInput(BaseModel):
    page_number: Optional[int] = Field(default=None, description="1-based page number")
    text: str = Field(..., description="Extracted raw text for this page")


class ChunkingRequest(BaseModel):
    document_id: Optional[int] = Field(default=None, description="Course document identifier")
    pages: List[PageInput] = Field(default_factory=list, description="List of pages with extracted text")
    text: Optional[str] = Field(default=None, description="Fallback full text if page breakdown is absent")
    target_chunk_size: int = Field(default=800, ge=100, le=4000, description="Target chunk size in characters")
    chunk_overlap: int = Field(default=120, ge=0, le=1000, description="Overlap in characters between consecutive chunks")


class ChunkItem(BaseModel):
    chunk_index: int = Field(..., description="0-based sequential chunk index")
    content: str = Field(..., description="Cleaned chunk text content")
    page_number: Optional[int] = Field(default=None, description="Original page number where this chunk originates")
    char_count: int = Field(..., description="Length of the chunk content in characters")


class ChunkingResponse(BaseModel):
    status: str = Field(..., description="Processing status: COMPLETED, NO_TEXT, or FAILED")
    is_exploitable: bool = Field(..., description="Whether the input contains exploitable text for RAG")
    total_chunks: int = Field(..., description="Total count of generated chunks")
    chunks: List[ChunkItem] = Field(default_factory=list, description="Ordered list of generated chunks")
    error_message: Optional[str] = Field(default=None, description="Error details if processing failed")
