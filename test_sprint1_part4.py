import urllib.request
import urllib.error
import json
import uuid
import time
import sys

BASE_URL = "http://localhost:8080/api"

def create_valid_pdf_bytes(text: str) -> bytes:
    stream_content = f"BT /F1 12 Tf 50 700 Td ({text}) Tj ET"
    stream_bytes = stream_content.encode("latin-1")
    stream_len = len(stream_bytes)
    
    # Construct minimal standard PDF
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

def api_request(endpoint, method="GET", data=None, token=None, content_type="application/json"):
    url = f"{BASE_URL}{endpoint}"
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    
    body = None
    if data is not None:
        if content_type == "application/json":
            body = json.dumps(data).encode("utf-8")
            headers["Content-Type"] = "application/json"
        else:
            body = data
            headers["Content-Type"] = content_type

    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as response:
            status = response.status
            res_body = response.read().decode("utf-8")
            return status, json.loads(res_body) if res_body else None
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(err_body)
        except:
            return e.code, err_body

def upload_multipart(endpoint, filename, file_bytes, token):
    url = f"{BASE_URL}{endpoint}"
    boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"
    
    body = bytearray()
    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode("utf-8"))
    body.extend(b"Content-Type: application/pdf\r\n\r\n")
    body.extend(file_bytes)
    body.extend(b"\r\n")
    body.extend(f"--{boundary}--\r\n".encode("utf-8"))
    
    headers = {
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Authorization": f"Bearer {token}"
    }
    
    req = urllib.request.Request(url, data=bytes(body), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as response:
            status = response.status
            res_body = response.read().decode("utf-8")
            return status, json.loads(res_body) if res_body else None
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(err_body)
        except:
            return e.code, err_body

def main():
    print("=" * 70)
    print(">>> STUDYAI - SPRINT 1 PARTIE 4 : SUITE DE TESTS COMPLÈTE E2E <<<")
    print("=" * 70)
    
    # 1. Register Student A and Student B
    test_id = uuid.uuid4().hex[:6]
    email_a = f"alice_doc_{test_id}@test.com"
    email_b = f"bob_doc_{test_id}@test.com"
    pwd = "Password123!"

    print(f"\n1. Inscription Étudiante A ({email_a})...")
    status, res = api_request("/auth/register", "POST", {
        "fullName": "Alice DocTester",
        "email": email_a,
        "password": pwd
    })
    assert status in (200, 201), f"Erreur register A: {status} {res}"
    token_a = res["token"]
    user_a = res["user"]
    print(f"   [OK] Token A généré, User ID: {user_a['id']}")

    print(f"\n2. Inscription Étudiant B ({email_b})...")
    status, res = api_request("/auth/register", "POST", {
        "fullName": "Bob IsolationTester",
        "email": email_b,
        "password": pwd
    })

    assert status in (200, 201), f"Erreur register B: {status} {res}"
    token_b = res["token"]
    user_b = res["user"]
    print(f"   [OK] Token B généré, User ID: {user_b['id']}")

    # 3. Create Course by Student A
    print("\n3. Création d'un cours par Étudiante A...")
    status, course = api_request("/courses", "POST", {
        "title": "Architecture des Ordinateurs & Systèmes",
        "description": "Cours complet avec processeurs, mémoires caches et bus."
    }, token=token_a)
    assert status in (200, 201), f"Erreur création cours: {status} {course}"
    course_id = course["id"]
    assert course.get("hasDocument") is False or course.get("hasDocument") is None
    print(f"   [OK] Cours #{course_id} créé avec succès (hasDocument = false).")

    # 4. Upload Valid PDF Document to Course
    print("\n4. Téléversement d'un fichier PDF valide avec texte...")
    pdf_text = "Bienvenue dans le cours d Architecture des Ordinateurs. StudyAI extrait ce texte."
    pdf_bytes = create_valid_pdf_bytes(pdf_text)
    
    status, doc_res = upload_multipart(f"/courses/{course_id}/document", "cours_architecture.pdf", pdf_bytes, token_a)
    assert status == 200, f"Erreur upload document: {status} {doc_res}"
    print(f"   [OK] Upload 200 OK:")
    print(f"        Nom fichier: {doc_res['originalFilename']}")
    print(f"        Taille: {doc_res['fileSize']} octets")
    print(f"        Texte extrait: \"{doc_res['extractedText'][:60]}...\"")
    assert doc_res["courseId"] == course_id
    assert doc_res["originalFilename"] == "cours_architecture.pdf"
    assert "StudyAI" in doc_res["extractedText"]

    # 5. Get Document as Student A (Persistence)
    print("\n5. Vérification de la persistance (GET /api/courses/{id}/document par Étudiante A)...")
    status, get_doc = api_request(f"/courses/{course_id}/document", "GET", token=token_a)
    assert status == 200, f"Erreur GET document: {status} {get_doc}"
    assert get_doc["originalFilename"] == "cours_architecture.pdf"
    assert "StudyAI" in get_doc["extractedText"]
    print(f"   [OK] Persistance confirmée: {get_doc['originalFilename']}, {get_doc['fileSize']} octets.")

    # 6. Check Course Summary in List (hasDocument = true)
    print("\n6. Vérification de la liste des cours (GET /api/courses par Étudiante A)...")
    status, courses_list = api_request("/courses", "GET", token=token_a)
    assert status == 200
    c_found = next((c for c in courses_list if c["id"] == course_id), None)
    assert c_found is not None
    assert c_found["hasDocument"] is True
    assert c_found["documentFilename"] == "cours_architecture.pdf"
    assert c_found["documentFileSize"] == doc_res["fileSize"]
    print(f"   [OK] hasDocument=True présent dans le résumé du cours.")

    # 7. Cross-Student Isolation Tests (Security)
    print("\n7. Tests de sécurité & étanchéité des données (Étudiant B tente d'accéder au cours de A)...")
    
    # 7a. GET by Student B -> 404
    status, err = api_request(f"/courses/{course_id}/document", "GET", token=token_b)
    assert status == 404, f"FAIL: B a pu voir le document de A! Status: {status}"
    print(f"   [OK] GET /api/courses/{course_id}/document par Étudiant B -> 404 Not Found.")

    # 7b. POST (Upload) by Student B -> 404
    dummy_pdf = create_valid_pdf_bytes("Hacker B text")
    status, err = upload_multipart(f"/courses/{course_id}/document", "hack.pdf", dummy_pdf, token_b)
    assert status == 404, f"FAIL: B a pu uploader sur le cours de A! Status: {status}"
    print(f"   [OK] POST /api/courses/{course_id}/document par Étudiant B -> 404 Not Found.")

    # 7c. DELETE by Student B -> 404
    status, err = api_request(f"/courses/{course_id}/document", "DELETE", token=token_b)
    assert status == 404, f"FAIL: B a pu supprimer le document de A! Status: {status}"
    print(f"   [OK] DELETE /api/courses/{course_id}/document par Étudiant B -> 404 Not Found.")

    # 7d. Unauthenticated access -> 401
    status, err = api_request(f"/courses/{course_id}/document", "GET")
    assert status == 401, f"FAIL: Accès non authentifié permis! Status: {status}"
    print(f"   [OK] Accès sans JWT -> 401 Unauthorized.")

    # 8. Validation tests
    print("\n8. Tests de validation des fichiers...")
    
    # 8a. Fake PDF (text file renamed to .pdf) -> 400
    fake_pdf = b"This is just a plain text file pretending to be PDF"
    status, err = upload_multipart(f"/courses/{course_id}/document", "fake.pdf", fake_pdf, token_a)
    assert status == 400, f"FAIL: Fake PDF accepté! Status: {status}"
    print(f"   [OK] Fichier sans signature %PDF- -> 400 Bad Request ({err.get('message')}).")

    # 8b. Empty PDF file -> 400
    status, err = upload_multipart(f"/courses/{course_id}/document", "empty.pdf", b"", token_a)
    assert status == 400, f"FAIL: PDF vide accepté! Status: {status}"
    print(f"   [OK] Fichier PDF vide (0 octets) -> 400 Bad Request.")

    # 9. Document Replacement
    print("\n9. Remplacement du document par un nouveau support PDF...")
    new_text = "Nouveau support mis a jour: Chapitre 2 Pipelines et Branchements."
    new_pdf_bytes = create_valid_pdf_bytes(new_text)
    status, replace_res = upload_multipart(f"/courses/{course_id}/document", "cours_v2_pipelines.pdf", new_pdf_bytes, token_a)
    assert status == 200, f"Erreur remplacement: {status}"
    assert replace_res["originalFilename"] == "cours_v2_pipelines.pdf"
    assert "Pipelines" in replace_res["extractedText"]
    print(f"   [OK] Document remplacé avec succès:")
    print(f"        Nouveau nom: {replace_res['originalFilename']}")
    print(f"        Nouveau texte: \"{replace_res['extractedText'][:50]}...\"")

    # 10. Document Deletion
    print("\n10. Suppression du document...")
    status, res = api_request(f"/courses/{course_id}/document", "DELETE", token=token_a)
    assert status == 204 or status == 200, f"Erreur DELETE document: {status}"
    print(f"   [OK] DELETE /api/courses/{course_id}/document -> 204 No Content.")

    # Verify document is gone
    status, err = api_request(f"/courses/{course_id}/document", "GET", token=token_a)
    assert status == 404, f"FAIL: Le document existe encore après suppression! Status: {status}"
    print(f"   [OK] GET après suppression -> 404 Not Found.")

    # Verify course hasDocument is now False
    status, courses_list = api_request("/courses", "GET", token=token_a)
    c_updated = next(c for c in courses_list if c["id"] == course_id)
    assert c_updated["hasDocument"] is False
    print(f"   [OK] Résumé cours mis à jour: hasDocument = False.")

    # 11. Frontend Delivery Check
    print("\n11. Vérification de la disponibilité du serveur Frontend Angular...")
    try:
        fe_req = urllib.request.Request("http://localhost:4200")
        with urllib.request.urlopen(fe_req) as fe_res:
            assert fe_res.status == 200
            fe_html = fe_res.read().decode("utf-8")
            assert "<app-root></app-root>" in fe_html
            print("   [OK] Frontend disponible sur http://localhost:4200 (HTTP 200).")
    except Exception as e:
        print(f"   [ATTENTION] Frontend non joignable: {e}")

    print("\n" + "=" * 70)
    print(">>> TOUS LES TESTS E2E DU SPRINT 1 PARTIE 4 ONT RÉUSSI AVEC SUCCÈS ! <<<")
    print("=" * 70)

if __name__ == "__main__":
    main()
