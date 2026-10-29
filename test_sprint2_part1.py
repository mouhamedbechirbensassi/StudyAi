import requests
import io
import sys
import uuid

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://localhost:8080/api"

def create_valid_pdf_bytes(text: str) -> bytes:
    stream_content = f"BT /F1 12 Tf 50 700 Td ({text}) Tj ET"
    stream_bytes = stream_content.encode("latin-1")
    stream_len = len(stream_bytes)
    
    pdf_str = (
        "%PDF-1.4\n"
        "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
        "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
        "3 0 obj << /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /MediaBox [0 0 612 792] /Contents 5 0 R >> endobj\n"
        "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"
        f"5 0 obj << /Length {stream_len} >>\n"
        "stream\n"
        f"{stream_content}\n"
        "endstream\n"
        "endobj\n"
        "xref\n"
        "0 6\n"
        "0000000000 65535 f \n"
        "0000000009 00000 n \n"
        "0000000058 00000 n \n"
        "0000000115 00000 n \n"
        "0000000228 00000 n \n"
        "0000000305 00000 n \n"
        "trailer << /Size 6 /Root 1 0 R >>\n"
        "startxref\n"
        "420\n"
        "%%EOF\n"
    )
    return pdf_str.encode("latin-1")

def create_empty_pdf_bytes() -> bytes:
    # PDF with no text stream content
    stream_content = ""
    stream_bytes = b""
    pdf_str = (
        "%PDF-1.4\n"
        "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
        "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
        "3 0 obj << /Type /Page /Parent 2 0 R /Resources << >> /MediaBox [0 0 612 792] >> endobj\n"
        "xref\n"
        "0 4\n"
        "0000000000 65535 f \n"
        "0000000009 00000 n \n"
        "0000000058 00000 n \n"
        "0000000115 00000 n \n"
        "trailer << /Size 4 /Root 1 0 R >>\n"
        "startxref\n"
        "220\n"
        "%%EOF\n"
    )
    return pdf_str.encode("latin-1")

def main():
    print("=== TEST STUDYAI SPRINT 2 PARTIE 1 : CHUNKING & RAG PIPELINE ===")

    # 1. Register Alice
    unique_suffix = uuid.uuid4().hex[:6]
    email_a = f"alice_rag_{unique_suffix}@studyai.test"
    password = "Password123!"
    reg_res = requests.post(f"{BASE_URL}/auth/register", json={
        "fullName": "Alice RAG Tester",
        "email": email_a,
        "password": password
    })
    assert reg_res.status_code in (200, 201), f"Failed register Alice: {reg_res.text}"
    token_a = reg_res.json()["token"]
    headers_a = {"Authorization": f"Bearer {token_a}"}

    # 2. Register Bob
    email_b = f"bob_rag_{unique_suffix}@studyai.test"
    reg_b = requests.post(f"{BASE_URL}/auth/register", json={
        "fullName": "Bob RAG Tester",
        "email": email_b,
        "password": password
    })
    assert reg_b.status_code in (200, 201), f"Failed register Bob: {reg_b.text}"
    token_b = reg_b.json()["token"]
    headers_b = {"Authorization": f"Bearer {token_b}"}

    print("✓ Authentification Alice & Bob : OK")

    # 3. Create course for Alice
    c_res = requests.post(f"{BASE_URL}/courses", json={
        "title": "Architecture RAG & Modeles Fondamentaux",
        "description": "Cours sur le traitement du langage naturel et la recherche semantique."
    }, headers=headers_a)
    assert c_res.status_code == 201, f"Failed create course: {c_res.text}"
    course_a = c_res.json()
    course_id = course_a["id"]
    print(f"✓ Cours cree #{course_id} pour Alice : OK")

    # 4. Generate long text to test chunking
    # ~1500 chars of educational text about RAG and ML
    long_text = (
        "Le Retrieval-Augmented Generation (RAG) combine un modele de langage avec une base de connaissances documentaire. "
        "La phase de preparation commence par l extraction des textes sources comme les supports de cours en format PDF. "
        "Apres extraction, un nettoyage rigoureux uniformise les sauts de ligne et elimine les caracteres de controle non imprimables. "
        "Le decoupage semantique en segments (chunks) decoupe le document avec une taille cible d environ 800 caracteres. "
        "Un recouvrement de 120 caracteres entre blocs adjacents assure qu aucune donnee conceptuelle n est interrompue. "
        "Toutes les frontieres naturelles de phrases et paragraphes sont respectees scrupuleusement. "
        "Les metadonnees incluant l identifiant du document, le cours, le numero d index et la page d origine sont persistees. "
        "Ce pipeline complet garantit des requetes semantiques optimales pour l assistant pedagogique intelligent de StudyAI."
    )
    pdf_bytes = create_valid_pdf_bytes(long_text)

    # 5. Upload document
    files = {"file": ("cours_rag_complet.pdf", io.BytesIO(pdf_bytes), "application/pdf")}
    up_res = requests.post(f"{BASE_URL}/courses/{course_id}/documents", files=files, headers=headers_a)
    assert up_res.status_code == 201, f"Failed upload: {up_res.text}"
    doc_data = up_res.json()
    doc_id = doc_data["id"]
    print(f"✓ Document #{doc_id} televerse. Status={doc_data['processingStatus']}, ChunksCount={doc_data['chunksCount']}")
    assert doc_data["processingStatus"] == "COMPLETED", f"Expected COMPLETED, got {doc_data['processingStatus']}"
    assert doc_data["chunksCount"] >= 1, "Expected at least 1 chunk"

    # 6. GET /chunks endpoint
    chunks_res = requests.get(f"{BASE_URL}/courses/{course_id}/documents/{doc_id}/chunks", headers=headers_a)
    assert chunks_res.status_code == 200, f"Failed get chunks: {chunks_res.text}"
    chunks = chunks_res.json()
    print(f"✓ Chunks recuperes ({len(chunks)} chunks) pour le document #{doc_id} : OK")
    assert len(chunks) == doc_data["chunksCount"], "Chunks count mismatch"

    for i, chunk in enumerate(chunks):
        assert chunk["chunkIndex"] == i, f"Expected chunkIndex {i}, got {chunk['chunkIndex']}"
        assert chunk["documentId"] == doc_id, "DocumentId mismatch"
        assert chunk["charCount"] == len(chunk["content"]), "charCount length mismatch"
        assert chunk["charCount"] > 0, "Chunk content must not be empty"
        print(f"   [Chunk #{chunk['chunkIndex']}] Page {chunk.get('pageNumber')} | {chunk['charCount']} car. | '{chunk['content'][:50]}...'")

    # 7. Reprocess Document endpoint
    reproc_res = requests.post(f"{BASE_URL}/courses/{course_id}/documents/{doc_id}/process", headers=headers_a)
    assert reproc_res.status_code == 200, f"Failed reprocess: {reproc_res.text}"
    reproc_data = reproc_res.json()
    assert reproc_data["processingStatus"] == "COMPLETED", "Reprocess status should be COMPLETED"
    assert reproc_data["chunksCount"] == len(chunks), "Reprocess must not duplicate chunks"

    # Verify no duplicate chunks in database
    chunks_res_after = requests.get(f"{BASE_URL}/courses/{course_id}/documents/{doc_id}/chunks", headers=headers_a)
    chunks_after = chunks_res_after.json()
    assert len(chunks_after) == len(chunks), "Chunks count should be strictly identical after reprocessing"
    print(f"✓ Retraitement POST /process #{doc_id} sans duplication : OK")

    # 8. Upload Scanned / Empty PDF (no text)
    empty_pdf = create_empty_pdf_bytes()
    files_empty = {"file": ("document_scanne.pdf", io.BytesIO(empty_pdf), "application/pdf")}
    up_empty_res = requests.post(f"{BASE_URL}/courses/{course_id}/documents", files=files_empty, headers=headers_a)
    assert up_empty_res.status_code == 201
    empty_doc = up_empty_res.json()
    print(f"✓ Document sans texte #{empty_doc['id']} : Status={empty_doc['processingStatus']}, Chunks={empty_doc['chunksCount']}")
    assert empty_doc["processingStatus"] == "NO_TEXT", f"Expected NO_TEXT, got {empty_doc['processingStatus']}"
    assert empty_doc["chunksCount"] == 0, f"Expected 0 chunks, got {empty_doc['chunksCount']}"

    # 9. Cross-student security check
    hacker_get = requests.get(f"{BASE_URL}/courses/{course_id}/documents/{doc_id}/chunks", headers=headers_b)
    assert hacker_get.status_code == 404, f"Security fail: Expected 404, got {hacker_get.status_code}"

    hacker_post = requests.post(f"{BASE_URL}/courses/{course_id}/documents/{doc_id}/process", headers=headers_b)
    assert hacker_post.status_code == 404, f"Security fail: Expected 404, got {hacker_post.status_code}"
    print("✓ Isolation stricte et securite multi-etudiants (404 sur document tiers) : OK")

    # 10. Delete document and check cascade deletion
    del_res = requests.delete(f"{BASE_URL}/courses/{course_id}/documents/{doc_id}", headers=headers_a)
    assert del_res.status_code == 204, f"Failed delete doc: {del_res.status_code}"

    # Check doc is gone
    check_doc = requests.get(f"{BASE_URL}/courses/{course_id}/documents/{doc_id}", headers=headers_a)
    assert check_doc.status_code == 404, "Deleted doc should return 404"

    # Check chunks endpoint returns 404 now that doc is deleted
    check_chunks = requests.get(f"{BASE_URL}/courses/{course_id}/documents/{doc_id}/chunks", headers=headers_a)
    assert check_chunks.status_code == 404, "Chunks of deleted doc should return 404"
    print(f"✓ Suppression et cascade delete des chunks pour le document #{doc_id} : OK")

    print("\n🎉 TOUS LES TESTS FONCTIONNELS ET DE SECURITE SONT VALIDES AVEC SUCCES (100%) ! 🎉")

if __name__ == "__main__":
    main()
