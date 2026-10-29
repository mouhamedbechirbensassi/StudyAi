import os
import sys
import time
import uuid
import requests

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

BACKEND_URL = "http://localhost:8080"
AI_SERVICE_URL = "http://localhost:8000"

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
        "0000000010 00000 n \n"
        "0000000068 00000 n \n"
        "0000000125 00000 n \n"
        "0000000244 00000 n \n"
        f"0000000333 00000 n \n"
        "trailer << /Size 6 /Root 1 0 R >>\n"
        "startxref\n"
        f"{450 + stream_len}\n"
        "%%EOF"
    )
    return pdf_str.encode("latin-1")

def print_step(title):
    print(f"\n{'='*70}\n[STEP] {title}\n{'='*70}")

def print_pass(msg):
    print(f"  [PASS] {msg}")

def print_fail(msg):
    print(f"  [FAIL] {msg}")
    sys.exit(1)

def register_user(name, email, password):
    res = requests.post(f"{BACKEND_URL}/api/auth/register", json={
        "fullName": name,
        "email": email,
        "password": password,
        "role": "STUDENT"
    })
    if res.status_code != 201:
        print_fail(f"Failed to register user {email}: {res.status_code} - {res.text}")
    data = res.json()
    return data["token"], data.get("user", {})

def main():
    print_step("1. Verifying Services Health")
    # Backend
    try:
        r_back = requests.get(f"{BACKEND_URL}/api/courses")
        if r_back.status_code in [401, 403]:
            print_pass("Backend is responding on port 8080 (security active).")
        else:
            print_fail(f"Unexpected backend response: {r_back.status_code}")
    except Exception as e:
        print_fail(f"Backend unreachable: {e}")

    # FastAPI AI Service
    try:
        r_ai = requests.get(f"{AI_SERVICE_URL}/health")
        if r_ai.status_code == 200:
            print_pass("FastAPI AI service is healthy on port 8000.")
        else:
            print_fail(f"FastAPI health returned {r_ai.status_code}")
    except Exception as e:
        print_fail(f"FastAPI AI service unreachable: {e}")

    # -------------------------------------------------------------
    print_step("2. Testing Deterministic Consecutive Course Numbering for Student X")
    unique_id = uuid.uuid4().hex[:6]
    email_x = f"student.x.{unique_id}@studyai.test"
    token_x, _ = register_user("Etudiant X", email_x, "Password123!")
    headers_x = {"Authorization": f"Bearer {token_x}"}

    # Student X starts with 0 courses
    res = requests.get(f"{BACKEND_URL}/api/courses", headers=headers_x)
    assert res.status_code == 200
    assert len(res.json()) == 0
    print_pass("Student X initially has 0 courses.")

    # Student X creates Course 1
    r1 = requests.post(f"{BACKEND_URL}/api/courses", headers=headers_x, json={"title": "Cours 1 - Algebre", "description": "Desc 1"})
    assert r1.status_code == 201
    c1 = r1.json()
    assert c1["displayNumber"] == 1, f"Expected displayNumber 1, got {c1.get('displayNumber')}"
    print_pass(f"Course 1 created: ID={c1['id']}, displayNumber={c1['displayNumber']}")

    # Student X creates Course 2
    r2 = requests.post(f"{BACKEND_URL}/api/courses", headers=headers_x, json={"title": "Cours 2 - Analyse", "description": "Desc 2"})
    assert r2.status_code == 201
    c2 = r2.json()
    assert c2["displayNumber"] == 2, f"Expected displayNumber 2, got {c2.get('displayNumber')}"
    print_pass(f"Course 2 created: ID={c2['id']}, displayNumber={c2['displayNumber']}")

    # Student X creates Course 3, 4, 5
    r3 = requests.post(f"{BACKEND_URL}/api/courses", headers=headers_x, json={"title": "Cours 3 - Physique", "description": "Desc 3"})
    c3 = r3.json()
    r4 = requests.post(f"{BACKEND_URL}/api/courses", headers=headers_x, json={"title": "Cours 4 - Chimie", "description": "Desc 4"})
    c4 = r4.json()
    r5 = requests.post(f"{BACKEND_URL}/api/courses", headers=headers_x, json={"title": "Cours 5 - Informatique", "description": "Desc 5"})
    c5 = r5.json()

    assert c3["displayNumber"] == 3
    assert c4["displayNumber"] == 4
    assert c5["displayNumber"] == 5
    print_pass("Created 5 courses: displayNumbers are 1, 2, 3, 4, 5.")

    # List all courses for Student X
    r_list = requests.get(f"{BACKEND_URL}/api/courses", headers=headers_x)
    courses_x = r_list.json()
    assert len(courses_x) == 5
    display_nums = [c["displayNumber"] for c in courses_x]
    assert display_nums == [1, 2, 3, 4, 5], f"Expected [1, 2, 3, 4, 5], got {display_nums}"
    print_pass(f"List of courses for Student X has consecutive display numbers: {display_nums}")

    # Check each individual getCourseById
    for expected_num, course in enumerate([c1, c2, c3, c4, c5], start=1):
        r_single = requests.get(f"{BACKEND_URL}/api/courses/{course['id']}", headers=headers_x)
        assert r_single.status_code == 200
        single_data = r_single.json()
        assert single_data["displayNumber"] == expected_num, f"Expected {expected_num}, got {single_data.get('displayNumber')}"
    print_pass("getCourseById for each course matches its sequential display number (1 to 5).")

    # -------------------------------------------------------------
    print_step("3. Testing Renumbering after Deleting Course 2")
    # Delete Course 2 (c2['id'])
    r_del = requests.delete(f"{BACKEND_URL}/api/courses/{c2['id']}", headers=headers_x)
    assert r_del.status_code == 204
    print_pass(f"Deleted Course with MySQL ID {c2['id']} (which was Cours 2).")

    # List courses again: should have 4 courses renumbered 1 to 4
    r_list_after = requests.get(f"{BACKEND_URL}/api/courses", headers=headers_x)
    courses_after = r_list_after.json()
    assert len(courses_after) == 4, f"Expected 4 courses, got {len(courses_after)}"
    nums_after = [c["displayNumber"] for c in courses_after]
    titles_after = [c["title"] for c in courses_after]
    assert nums_after == [1, 2, 3, 4], f"Expected [1, 2, 3, 4], got {nums_after}"
    print_pass(f"Remaining 4 courses consecutively renumbered: {nums_after} ({titles_after})")

    # Verify Course 3 is now Cours 2
    r_c3 = requests.get(f"{BACKEND_URL}/api/courses/{c3['id']}", headers=headers_x)
    assert r_c3.json()["displayNumber"] == 2
    print_pass(f"Previous Course 3 (ID {c3['id']}) is now Cours 2.")

    # -------------------------------------------------------------
    print_step("4. Testing New Course Creation after Deletion")
    # Adding a new course should take number 5
    r_new = requests.post(f"{BACKEND_URL}/api/courses", headers=headers_x, json={"title": "Nouveau Cours - IA", "description": "Desc IA"})
    assert r_new.status_code == 201
    c_new = r_new.json()
    assert c_new["displayNumber"] == 5, f"Expected displayNumber 5, got {c_new.get('displayNumber')}"
    print_pass(f"Newly added course received Cours 5: ID={c_new['id']}, displayNumber={c_new['displayNumber']}")

    r_final = requests.get(f"{BACKEND_URL}/api/courses", headers=headers_x)
    assert [c["displayNumber"] for c in r_final.json()] == [1, 2, 3, 4, 5]
    print_pass("Course list now cleanly has display numbers [1, 2, 3, 4, 5].")

    # -------------------------------------------------------------
    print_step("5. Testing Strict Numbering Independence per Student")
    email_y = f"student.y.{unique_id}@studyai.test"
    token_y, _ = register_user("Etudiant Y", email_y, "Password123!")
    headers_y = {"Authorization": f"Bearer {token_y}"}

    # Student Y creates their first course -> MUST BE Cours 1!
    ry1 = requests.post(f"{BACKEND_URL}/api/courses", headers=headers_y, json={"title": "Cours Y - Biologie", "description": "Bio"})
    assert ry1.status_code == 201
    cy1 = ry1.json()
    assert cy1["displayNumber"] == 1, f"Expected Student Y's first course to be 1, got {cy1.get('displayNumber')}"
    print_pass(f"Student Y's first course is Cours 1 (ID={cy1['id']}). Independent of Student X!")

    # Student Y creates their second course -> MUST BE Cours 2!
    ry2 = requests.post(f"{BACKEND_URL}/api/courses", headers=headers_y, json={"title": "Cours Y - Geologie", "description": "Geo"})
    assert ry2.status_code == 201
    cy2 = ry2.json()
    assert cy2["displayNumber"] == 2, f"Expected Student Y's second course to be 2, got {cy2.get('displayNumber')}"
    print_pass(f"Student Y's second course is Cours 2 (ID={cy2['id']}).")

    # Student X still has 5 courses (1..5)
    rx_check = requests.get(f"{BACKEND_URL}/api/courses", headers=headers_x)
    assert [c["displayNumber"] for c in rx_check.json()] == [1, 2, 3, 4, 5]
    print_pass("Student X's course numbers remain [1, 2, 3, 4, 5], unaffected by Student Y.")

    # -------------------------------------------------------------
    print_step("6. Testing Document Upload, Background Chunking & Clean Student Status")
    # Upload sample PDF to Student X's course 1
    pdf_bytes = create_valid_pdf_bytes("Introduction a l intelligence artificielle et au traitement automatique du langage naturel.")
    
    r_upload = requests.post(
        f"{BACKEND_URL}/api/courses/{c1['id']}/documents",
        headers=headers_x,
        files={"file": ("sample_cours.pdf", pdf_bytes, "application/pdf")}
    )
    assert r_upload.status_code == 201, f"Upload failed: {r_upload.status_code} - {r_upload.text}"
    doc_data = r_upload.json()
    assert doc_data["processingStatus"] == "COMPLETED"
    print_pass(f"PDF uploaded and automatically processed in background: docId={doc_data['id']}, status={doc_data['processingStatus']}, chunksCount={doc_data.get('chunksCount')}")

    # Verify backend endpoints still exist and work
    r_chunks = requests.get(f"{BACKEND_URL}/api/courses/{c1['id']}/documents/{doc_data['id']}/chunks", headers=headers_x)
    assert r_chunks.status_code == 200
    chunks = r_chunks.json()
    assert len(chunks) > 0
    print_pass(f"Backend chunks endpoint preserved: {len(chunks)} chunks retrieved for doc #{doc_data['id']}.")

    # Verify document details endpoint returns extracted text
    r_doc_detail = requests.get(f"{BACKEND_URL}/api/courses/{c1['id']}/documents/{doc_data['id']}", headers=headers_x)
    assert r_doc_detail.status_code == 200
    detail = r_doc_detail.json()
    assert "intelligence" in detail.get("extractedText", "").lower()
    print_pass("Document detail endpoint correctly returns extracted text for student text viewer.")

    print_step("ALL AUTOMATED TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
