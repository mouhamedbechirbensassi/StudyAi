# StudyAI — Service IA (Python / FastAPI)

Microservice indépendant dédié au nettoyage textuel et au découpage sémantique (*chunking*) pour le pipeline RAG de StudyAI.

## Architecture

- **Framework :** FastAPI + Pydantic v2
- **Serveur ASGI :** Uvicorn
- **Port par défaut :** `8000` (configurable via variable d'environnement `PORT` ou flag `--port`)
- **Validation :** Modèles stricts Pydantic pour requêtes et réponses
- **Tests :** Pytest + HTTPX

## Endpoints

- `GET /health` : Vérification de la disponibilité du service.
- `POST /api/v1/chunking/process` : Nettoyage et découpage d'un document PDF découpé par pages.

### Contrat de requête (`POST /api/v1/chunking/process`) :

```json
{
  "document_id": 12,
  "pages": [
    {
      "page_number": 1,
      "text": "Texte extrait de la page 1..."
    }
  ],
  "text": null,
  "target_chunk_size": 800,
  "chunk_overlap": 120
}
```

### Contrat de réponse :

```json
{
  "status": "COMPLETED",
  "is_exploitable": true,
  "total_chunks": 2,
  "chunks": [
    {
      "chunk_index": 0,
      "content": "Contenu du premier chunk...",
      "page_number": 1,
      "char_count": 786
    }
  ],
  "error_message": null
}
```

## Commandes d'installation et de démarrage

### 1. Création de l'environnement virtuel et installation
```bash
cd ai-service
python -m venv venv
# Windows:
.\venv\Scripts\activate
pip install -r requirements.txt
```

### 2. Lancement des tests unitaires
```bash
.\venv\Scripts\pytest -v
```

### 3. Démarrage du serveur FastAPI
```bash
.\venv\Scripts\uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```
Accès à la documentation OpenAPI Swagger interactive : `http://localhost:8000/docs`
