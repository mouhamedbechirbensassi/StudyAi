import urllib.request
import urllib.error
import json
import uuid
import sys

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
    print("=" * 75)
    print(">>> STUDYAI - REFACTORING DES COURS & MULTI-DOCUMENTS : TESTS E2E <<<")
    print("=" * 75)
    
    # 1. Register 2 Students
    test_id = uuid.uuid4().hex[:6]
    email_a = f"alice_multi_{test_id}@test.com"
    email_b = f"bob_multi_{test_id}@test.com"
    pwd = "Password123!"

    print(f"\n1. Enregistrement Étudiante A ({email_a})...")
    status, res = api_request("/auth/register", "POST", {
        "fullName": "Alice MultiDocs",
        "email": email_a,
        "password": pwd
    })
    assert status in (200, 201), f"Erreur register A: {status} {res}"
    token_a = res["token"]
    user_a = res["user"]
    print(f"   [OK] Étudiante A enregistrée, ID: {user_a['id']}")

    print(f"\n2. Enregistrement Étudiant B ({email_b})...")
    status, res = api_request("/auth/register", "POST", {
        "fullName": "Bob MultiDocs",
        "email": email_b,
        "password": pwd
    })
    assert status in (200, 201), f"Erreur register B: {status} {res}"
    token_b = res["token"]
    user_b = res["user"]
    print(f"   [OK] Étudiant B enregistré, ID: {user_b['id']}")

    # 3. Create Course by Student A
    print("\n3. Création du cours 'Calcul Différentiel & Intégral'...")
    status, course = api_request("/courses", "POST", {
        "title": "Calcul Différentiel & Intégral",
        "description": "Analyse mathématique, séries de Fourier, équations différentielles."
    }, token=token_a)
    assert status in (200, 201), f"Erreur création cours: {status} {course}"
    course_id = course["id"]
    print(f"   [OK] Cours #{course_id} créé avec succès (documentsCount={course.get('documentsCount', 0)}).")

    # 4. Upload First PDF (Chapitre 1)
    print("\n4. Ajout du premier document PDF (Chapitre 1)...")
    text_chap1 = "Chapitre 1: Notions de limites, derivees et integrales de Riemann."
    pdf1_bytes = create_valid_pdf_bytes(text_chap1)
    status, doc1 = upload_multipart(f"/courses/{course_id}/documents", "chapitre_1_derivees.pdf", pdf1_bytes, token_a)
    assert status in (200, 201), f"Erreur upload doc 1: {status} {doc1}"
    doc1_id = doc1["id"]
    assert doc1_id is not None
    assert doc1["originalFilename"] == "chapitre_1_derivees.pdf"
    assert "limites" in doc1["extractedText"]
    print(f"   [OK] Chapitre 1 ajouté avec succès (ID: {doc1_id}, taille: {doc1['fileSize']} octets).")

    # 5. Upload Second PDF (Chapitre 2) to same course
    print("\n5. Ajout du deuxième document PDF (Chapitre 2) au même cours...")
    text_chap2 = "Chapitre 2: Formule de Taylor, developpements limites et integrales generalisees."
    pdf2_bytes = create_valid_pdf_bytes(text_chap2)
    status, doc2 = upload_multipart(f"/courses/{course_id}/documents", "chapitre_2_taylor.pdf", pdf2_bytes, token_a)
    assert status in (200, 201), f"Erreur upload doc 2: {status} {doc2}"
    doc2_id = doc2["id"]
    assert doc2_id is not None
    assert doc2["originalFilename"] == "chapitre_2_taylor.pdf"
    assert "Taylor" in doc2["extractedText"]
    print(f"   [OK] Chapitre 2 ajouté avec succès (ID: {doc2_id}, taille: {doc2['fileSize']} octets).")

    # 6. Verify Both Documents Coexist
    print("\n6. Vérification de la collection de documents (GET /api/courses/{courseId}/documents)...")
    status, docs_list = api_request(f"/courses/{course_id}/documents", "GET", token=token_a)
    assert status == 200, f"Erreur GET documents: {status} {docs_list}"
    assert len(docs_list) == 2, f"Attendu 2 documents, reçu: {len(docs_list)}"
    filenames = [d["originalFilename"] for d in docs_list]
    assert "chapitre_1_derivees.pdf" in filenames
    assert "chapitre_2_taylor.pdf" in filenames
    print(f"   [OK] 2 documents présents dans la collection: {filenames}")

    # Check Course summary documentsCount
    status, course_info = api_request(f"/courses/{course_id}", "GET", token=token_a)
    assert status == 200
    assert course_info["documentsCount"] == 2
    assert course_info["hasDocument"] is True
    print(f"   [OK] Le cours indique bien documentsCount=2 et hasDocument=True.")

    # 7. Consult Text of Each Document Individually
    print("\n7. Consultation individuelle du texte extrait de chaque document...")
    status, single_doc1 = api_request(f"/courses/{course_id}/documents/{doc1_id}", "GET", token=token_a)
    assert status == 200
    assert "limites" in single_doc1["extractedText"]
    print(f"   [OK] Document 1 texte extrait: \"{single_doc1['extractedText'][:40]}...\"")

    status, single_doc2 = api_request(f"/courses/{course_id}/documents/{doc2_id}", "GET", token=token_a)
    assert status == 200
    assert "Taylor" in single_doc2["extractedText"]
    print(f"   [OK] Document 2 texte extrait: \"{single_doc2['extractedText'][:40]}...\"")

    # 8. Security & Data Isolation
    print("\n8. Tests de sécurité & isolation (Étudiant B tente d'accéder aux documents de A)...")
    status, _ = api_request(f"/courses/{course_id}/documents", "GET", token=token_b)
    assert status == 404, f"FAIL: B a pu lister les documents de A! Status: {status}"
    print(f"   [OK] GET /api/courses/{course_id}/documents par B -> 404 Not Found.")

    status, _ = api_request(f"/courses/{course_id}/documents/{doc1_id}", "GET", token=token_b)
    assert status == 404, f"FAIL: B a pu consulter le doc 1 de A! Status: {status}"
    print(f"   [OK] GET /api/courses/{course_id}/documents/{doc1_id} par B -> 404 Not Found.")

    status, _ = upload_multipart(f"/courses/{course_id}/documents", "hack.pdf", pdf1_bytes, token_b)
    assert status == 404, f"FAIL: B a pu uploader dans le cours de A! Status: {status}"
    print(f"   [OK] POST /api/courses/{course_id}/documents par B -> 404 Not Found.")

    status, _ = api_request(f"/courses/{course_id}/documents/{doc1_id}", "DELETE", token=token_b)
    assert status == 404, f"FAIL: B a pu supprimer le doc 1 de A! Status: {status}"
    print(f"   [OK] DELETE /api/courses/{course_id}/documents/{doc1_id} par B -> 404 Not Found.")

    status, _ = api_request(f"/courses/{course_id}/documents", "GET")
    assert status == 401, f"FAIL: Requête sans JWT acceptée! Status: {status}"
    print(f"   [OK] Requête sans JWT -> 401 Unauthorized.")

    # 9. Individual Document Deletion
    print(f"\n9. Suppression individuelle du Chapitre 1 (ID: {doc1_id}) par Étudiante A...")
    status, _ = api_request(f"/courses/{course_id}/documents/{doc1_id}", "DELETE", token=token_a)
    assert status in (200, 204), f"Erreur DELETE doc 1: {status}"
    print(f"   [OK] DELETE /api/courses/{course_id}/documents/{doc1_id} -> 204 No Content.")

    # Verify doc 1 is gone but doc 2 remains
    status, _ = api_request(f"/courses/{course_id}/documents/{doc1_id}", "GET", token=token_a)
    assert status == 404, f"FAIL: Doc 1 existe encore après suppression! Status: {status}"
    print(f"   [OK] GET sur document supprimé -> 404 Not Found.")

    status, remaining_docs = api_request(f"/courses/{course_id}/documents", "GET", token=token_a)
    assert len(remaining_docs) == 1
    assert remaining_docs[0]["id"] == doc2_id
    print(f"   [OK] Le Chapitre 2 est resté intact dans le cours (1 document restant).")

    # 10. Add New Document After Deletion
    print("\n10. Ajout d'un nouveau document (Chapitre 3) après suppression...")
    text_chap3 = "Chapitre 3: Equations differentielles lineaires du premier et second ordre."
    pdf3_bytes = create_valid_pdf_bytes(text_chap3)
    status, doc3 = upload_multipart(f"/courses/{course_id}/documents", "chapitre_3_eqdiff.pdf", pdf3_bytes, token_a)
    assert status in (200, 201)
    doc3_id = doc3["id"]
    print(f"   [OK] Chapitre 3 ajouté (ID: {doc3_id}).")

    status, docs_after = api_request(f"/courses/{course_id}/documents", "GET", token=token_a)
    assert len(docs_after) == 2
    print(f"   [OK] La collection contient maintenant 2 documents: Chapitre 2 et Chapitre 3.")

    # 11. Delete Course Cleans Associated Documents
    print("\n11. Création puis suppression d'un cours avec documents...")
    status, c2 = api_request("/courses", "POST", {"title": "Cours Temporaire"}, token=token_a)
    c2_id = c2["id"]
    upload_multipart(f"/courses/{c2_id}/documents", "temp.pdf", pdf1_bytes, token_a)
    status, _ = api_request(f"/courses/{c2_id}", "DELETE", token=token_a)
    assert status in (200, 204)
    status, _ = api_request(f"/courses/{c2_id}/documents", "GET", token=token_a)
    assert status == 404
    print(f"   [OK] Cours temporaire et ses documents nettoyés avec succès.")

    # 12. Verification of Frontend Server
    print("\n12. Vérification de la disponibilité du serveur Angular...")
    try:
        fe_req = urllib.request.Request("http://localhost:4200")
        with urllib.request.urlopen(fe_req) as fe_res:
            assert fe_res.status == 200
            print("   [OK] Frontend disponible sur http://localhost:4200 (HTTP 200).")
    except Exception as e:
        print(f"   [ATTENTION] Frontend non joignable: {e}")

    print("\n" + "=" * 75)
    print(">>> TOUS LES TESTS DU REFACTORING MULTI-DOCUMENTS ONT RÉUSSI ! <<<")
    print("=" * 75)

if __name__ == "__main__":
    main()
