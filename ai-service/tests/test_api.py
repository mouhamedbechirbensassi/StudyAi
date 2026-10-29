import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_health_check_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["service"] == "studyai-ai-service"


def test_chunking_process_with_valid_text():
    payload = {
        "document_id": 42,
        "pages": [
            {
                "page_number": 1,
                "text": "Le modèle transformer utilise des mécanismes d'auto-attention pour capturer les relations distantes."
            },
            {
                "page_number": 2,
                "text": "L'architecture BERT est entraînée avec un objectif de Masked Language Modeling et Next Sentence Prediction."
            }
        ],
        "target_chunk_size": 800,
        "chunk_overlap": 120
    }

    response = client.post("/api/v1/chunking/process", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["status"] == "COMPLETED"
    assert data["is_exploitable"] is True
    assert data["total_chunks"] >= 2
    assert len(data["chunks"]) == data["total_chunks"]
    assert data["chunks"][0]["chunk_index"] == 0
    assert data["chunks"][0]["page_number"] == 1
    assert data["chunks"][1]["chunk_index"] == 1
    assert data["chunks"][1]["page_number"] == 2


def test_chunking_process_with_scanned_or_empty_text():
    payload = {
        "document_id": 99,
        "pages": [
            {
                "page_number": 1,
                "text": "[Information] Ce document PDF ne contient aucun texte extractible (il s'agit probablement d'un document numérisé ou composé uniquement d'images)."
            }
        ],
        "target_chunk_size": 800,
        "chunk_overlap": 120
    }

    response = client.post("/api/v1/chunking/process", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["status"] == "NO_TEXT"
    assert data["is_exploitable"] is False
    assert data["total_chunks"] == 0
    assert data["chunks"] == []


def test_chunking_validation_error():
    # target_chunk_size < 100 should fail Pydantic validation (422)
    payload = {
        "document_id": 10,
        "pages": [{"page_number": 1, "text": "Quelques mots"}],
        "target_chunk_size": 50,
        "chunk_overlap": 120
    }

    response = client.post("/api/v1/chunking/process", json=payload)
    assert response.status_code == 422
