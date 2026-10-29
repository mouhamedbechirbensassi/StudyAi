"""
StudyAI - AI Microservice (FastAPI)
Dedicated to Text Cleaning & Semantic Chunking for the RAG Pipeline.
"""
import logging
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from .models import ChunkingRequest, ChunkingResponse
from .chunker import process_pages_to_chunks

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("studyai-ai-service")

app = FastAPI(
    title="StudyAI AI Service",
    description="Independent microservice for document text cleaning and semantic chunking (Sprint 2 RAG).",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# Restrict to local network / Spring Boot origin
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://127.0.0.1:8080"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)


@app.get("/health", status_code=status.HTTP_200_OK, tags=["Monitoring"])
def health_check():
    """Health check endpoint used by orchestrator and monitoring."""
    return {
        "status": "UP",
        "service": "studyai-ai-service",
        "version": "1.0.0"
    }


@app.post(
    "/api/v1/chunking/process",
    response_model=ChunkingResponse,
    status_code=status.HTTP_200_OK,
    tags=["Chunking"]
)
def process_chunking(request: ChunkingRequest) -> ChunkingResponse:
    """
    Cleans raw document text, validates exploitability and splits it into
    overlapping semantic chunks according to sentence and paragraph boundaries.
    """
    try:
        logger.info(
            "Processing chunking request: doc_id=%s, pages_count=%d, target_size=%d, overlap=%d",
            request.document_id,
            len(request.pages),
            request.target_chunk_size,
            request.chunk_overlap
        )

        chunks, _, is_exploitable = process_pages_to_chunks(
            pages=request.pages,
            fallback_text=request.text,
            target_chunk_size=request.target_chunk_size,
            chunk_overlap=request.chunk_overlap
        )

        if not is_exploitable or not chunks:
            logger.info("Document doc_id=%s contains no exploitable text (scanned/empty PDF). Returning NO_TEXT.", request.document_id)
            return ChunkingResponse(
                status="NO_TEXT",
                is_exploitable=False,
                total_chunks=0,
                chunks=[]
            )

        logger.info("Successfully produced %d chunks for doc_id=%s", len(chunks), request.document_id)
        return ChunkingResponse(
            status="COMPLETED",
            is_exploitable=True,
            total_chunks=len(chunks),
            chunks=chunks
        )

    except Exception as e:
        logger.exception("Unexpected error during chunking processing: %s", str(e))
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Erreur interne lors du chunking : {str(e)}"
        )
