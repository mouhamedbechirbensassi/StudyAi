import urllib.request
import urllib.error
import json
import sys
import time

BASE_URL = "http://localhost:8080/api"
FRONTEND_URL = "http://localhost:4200"

def log(msg, status="INFO"):
    print(f"[{status}] {msg}")

def http_json(method, url, payload=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            content = resp.read().decode("utf-8")
            return resp.status, json.loads(content) if content else {}
    except urllib.error.HTTPError as e:
        content = e.read().decode("utf-8")
        try:
            body = json.loads(content) if content else {}
        except Exception:
            body = {"raw": content}
        return e.code, body

def run_tests():
    results = []
    ts = int(time.time())

    email_a = f"alice_{ts}@studyai.test"
    email_b = f"bob_{ts}@studyai.test"
    pwd = "Password123!"

    print("=== STUDYAI SPRINT 1 PARTIE 3 : VERIFICATION E2E ===")

    # 1. Register & Login Student A
    log(f"1. Enregistrement de l'étudiante A ({email_a})...")
    status, body = http_json("POST", f"{BASE_URL}/auth/register", {"fullName": "Alice Dupont", "email": email_a, "password": pwd})
    if status == 201:
        token_a = body.get("token")
        results.append(("Enregistrement Étudiante A", True, f"ID={body['user']['id']}, Role={body['user']['role']}"))
        log("   Succès: Étudiante A enregistrée", "PASS")
    else:
        results.append(("Enregistrement Étudiante A", False, f"Status {status}: {body}"))
        return results

    # 2. Register & Login Student B
    log(f"2. Enregistrement de l'étudiant B ({email_b})...")
    status, body = http_json("POST", f"{BASE_URL}/auth/register", {"fullName": "Bob Martin", "email": email_b, "password": pwd})
    if status == 201:
        token_b = body.get("token")
        results.append(("Enregistrement Étudiant B", True, f"ID={body['user']['id']}, Role={body['user']['role']}"))
        log("   Succès: Étudiant B enregistré", "PASS")
    else:
        results.append(("Enregistrement Étudiant B", False, f"Status {status}: {body}"))
        return results

    # 3. Unauthenticated requests to /api/courses
    log("3. Test du rejet d'accès non authentifié sur /api/courses...")
    status, _ = http_json("GET", f"{BASE_URL}/courses")
    if status == 401:
        results.append(("Rejet GET /api/courses sans token (401)", True, "HTTP 401 Unauthorized"))
        log("   Succès: 401 reçu sans token", "PASS")
    else:
        results.append(("Rejet GET /api/courses sans token (401)", False, f"Attendu 401, reçu {status}"))

    status, _ = http_json("POST", f"{BASE_URL}/courses", {"title": "Test", "description": "Desc"})
    if status == 401:
        results.append(("Rejet POST /api/courses sans token (401)", True, "HTTP 401 Unauthorized"))
        log("   Succès: 401 reçu sur POST sans token", "PASS")
    else:
        results.append(("Rejet POST /api/courses sans token (401)", False, f"Attendu 401, reçu {status}"))

    # 4. Validation: reject blank course title
    log("4. Test de validation : rejet d'un titre de cours vide (400 Bad Request)...")
    status, body = http_json("POST", f"{BASE_URL}/courses", {"title": "   ", "description": "Sans titre"}, token=token_a)
    if status == 400 and "validationErrors" in body and "title" in body["validationErrors"]:
        results.append(("Validation titre obligatoire (400)", True, f"Message: {body['validationErrors']['title']}"))
        log("   Succès: 400 Bad Request avec message de validation", "PASS")
    else:
        results.append(("Validation titre obligatoire (400)", False, f"Attendu 400, reçu {status}: {body}"))

    # 5. Create valid course for Student A
    log("5. Création d'un cours valide par l'Étudiante A...")
    course_payload = {
        "title": "Algèbre Linéaire & Géométrie",
        "description": "Espaces vectoriels, matrices et réduction d'endomorphismes."
    }
    status, body = http_json("POST", f"{BASE_URL}/courses", course_payload, token=token_a)
    if status == 201 and body.get("id") and body.get("title") == course_payload["title"]:
        course_id_a = body["id"]
        results.append(("Création cours valide (201 Created)", True, f"Cours ID={course_id_a}, Titre='{body['title']}'"))
        log(f"   Succès: Cours créé avec ID={course_id_a}", "PASS")
    else:
        results.append(("Création cours valide (201 Created)", False, f"Status {status}: {body}"))
        return results

    # 6. Student A lists courses -> sees the course
    log("6. Consultation de la liste des cours par l'Étudiante A...")
    status, courses_a = http_json("GET", f"{BASE_URL}/courses", token=token_a)
    if status == 200 and any(c["id"] == course_id_a for c in courses_a):
        results.append(("Liste des cours de l'Étudiante A", True, f"{len(courses_a)} cours trouvés, contient ID={course_id_a}"))
        log("   Succès: Cours visible par son propriétaire", "PASS")
    else:
        results.append(("Liste des cours de l'Étudiante A", False, f"Status {status}: {courses_a}"))

    # 7. Student B lists courses -> does NOT see Student A's course
    log("7. Isolation : Consultation de la liste des cours par l'Étudiant B...")
    status, courses_b = http_json("GET", f"{BASE_URL}/courses", token=token_b)
    if status == 200 and not any(c["id"] == course_id_a for c in courses_b):
        results.append(("Isolation : Liste de l'Étudiant B isolée", True, f"0 cours de l'Étudiante A visibles par B"))
        log("   Succès: L'Étudiant B ne voit pas le cours de l'Étudiante A", "PASS")
    else:
        results.append(("Isolation : Liste de l'Étudiant B isolée", False, f"Fuite détectée ou erreur: {courses_b}"))

    # 8. Student A consults own course details (GET /api/courses/{id})
    log(f"8. Consultation du cours #{course_id_a} par son propriétaire (Étudiante A)...")
    status, course_detail = http_json("GET", f"{BASE_URL}/courses/{course_id_a}", token=token_a)
    if status == 200 and course_detail.get("id") == course_id_a:
        results.append(("Consultation cours par son propriétaire (200 OK)", True, f"Titre: '{course_detail['title']}'"))
        log("   Succès: Détails récupérés avec succès", "PASS")
    else:
        results.append(("Consultation cours par son propriétaire (200 OK)", False, f"Status {status}: {course_detail}"))

    # 9. Security isolation : Student B attempts to read Student A's course (expecting 404 Not Found)
    log(f"9. Sécurité : Tentative de lecture du cours #{course_id_a} par l'Étudiant B (attendu: 404)...")
    status, _ = http_json("GET", f"{BASE_URL}/courses/{course_id_a}", token=token_b)
    if status == 404:
        results.append(("Sécurité : Refus lecture cours d'un tiers (404)", True, "HTTP 404 Not Found - Confidentialité préservée"))
        log("   Succès: 404 renvoyé (non-divulgation d'existence)", "PASS")
    else:
        results.append(("Sécurité : Refus lecture cours d'un tiers (404)", False, f"Attendu 404, reçu {status}"))

    # 10. Security isolation : Student B attempts to update Student A's course (expecting 404 Not Found)
    log(f"10. Sécurité : Tentative de modification du cours #{course_id_a} par l'Étudiant B (attendu: 404)...")
    status, _ = http_json("PUT", f"{BASE_URL}/courses/{course_id_a}", {"title": "Piratage", "description": "Modif non autorisée"}, token=token_b)
    if status == 404:
        results.append(("Sécurité : Refus modification cours d'un tiers (404)", True, "HTTP 404 Not Found"))
        log("   Succès: Modification rejetée avec 404", "PASS")
    else:
        results.append(("Sécurité : Refus modification cours d'un tiers (404)", False, f"Attendu 404, reçu {status}"))

    # 11. Security isolation : Student B attempts to delete Student A's course (expecting 404 Not Found)
    log(f"11. Sécurité : Tentative de suppression du cours #{course_id_a} par l'Étudiant B (attendu: 404)...")
    status, _ = http_json("DELETE", f"{BASE_URL}/courses/{course_id_a}", token=token_b)
    if status == 404:
        results.append(("Sécurité : Refus suppression cours d'un tiers (404)", True, "HTTP 404 Not Found"))
        log("   Succès: Suppression rejetée avec 404", "PASS")
    else:
        results.append(("Sécurité : Refus suppression cours d'un tiers (404)", False, f"Attendu 404, reçu {status}"))

    # 12. Student A updates own course
    log(f"12. Modification du cours #{course_id_a} par son propriétaire (Étudiante A)...")
    updated_payload = {
        "title": "Algèbre Linéaire Avancée & Espaces Préhilbertiens",
        "description": "Programme complet semestriel."
    }
    status, updated_course = http_json("PUT", f"{BASE_URL}/courses/{course_id_a}", updated_payload, token=token_a)
    if status == 200 and updated_course.get("title") == updated_payload["title"]:
        results.append(("Modification autorisée du cours (200 OK)", True, f"Nouveau titre: '{updated_course['title']}'"))
        log("   Succès: Cours mis à jour avec succès", "PASS")
    else:
        results.append(("Modification autorisée du cours (200 OK)", False, f"Status {status}: {updated_course}"))

    # 13. Student A deletes own course (204 No Content)
    log(f"13. Suppression du cours #{course_id_a} par l'Étudiante A (attendu: 204)...")
    status, _ = http_json("DELETE", f"{BASE_URL}/courses/{course_id_a}", token=token_a)
    if status == 204:
        results.append(("Suppression autorisée du cours (204 No Content)", True, "HTTP 204 No Content"))
        log("   Succès: Cours supprimé avec 204", "PASS")
    else:
        results.append(("Suppression autorisée du cours (204 No Content)", False, f"Attendu 204, reçu {status}"))

    # 14. Student A tries to read deleted course -> 404
    log(f"14. Vérification que le cours #{course_id_a} n'existe plus...")
    status, _ = http_json("GET", f"{BASE_URL}/courses/{course_id_a}", token=token_a)
    if status == 404:
        results.append(("Vérification suppression effective (404)", True, "HTTP 404 Not Found"))
        log("   Succès: Le cours supprimé n'est plus accessible", "PASS")
    else:
        results.append(("Vérification suppression effective (404)", False, f"Attendu 404, reçu {status}"))

    # 15. Verify Part 1 & 2 profile endpoint still works
    log("15. Vérification du maintien des fonctionnalités Parties 1 & 2 (GET /api/auth/me)...")
    status, profile = http_json("GET", f"{BASE_URL}/auth/me", token=token_a)
    if status == 200 and profile.get("email") == email_a:
        results.append(("Maintien GET /api/auth/me (Parties 1 & 2)", True, f"Profil {profile['fullName']} ({profile['role']})"))
        log("   Succès: Profil étudiant toujours pleinement opérationnel", "PASS")
    else:
        results.append(("Maintien GET /api/auth/me (Parties 1 & 2)", False, f"Status {status}: {profile}"))

    # 16. Verify Frontend HTTP delivery
    log("16. Vérification de la disponibilité du frontend Angular...")
    try:
        with urllib.request.urlopen(FRONTEND_URL) as resp:
            content = resp.read().decode("utf-8")
            if "<app-root>" in content:
                results.append(("Frontend HTTP delivery", True, f"Status {resp.status}, <app-root> détecté"))
                log("   Succès: Serveur frontend disponible", "PASS")
            else:
                results.append(("Frontend HTTP delivery", False, "Balise <app-root> introuvable"))
    except Exception as e:
        results.append(("Frontend HTTP delivery", False, str(e)))
        log(f"   Erreur frontend: {e}", "FAIL")

    return results

if __name__ == "__main__":
    results = run_tests()
    print("\n" + "=" * 60)
    print("BILAN DES TESTS DU SPRINT 1 PARTIE 3")
    print("=" * 60)
    all_ok = True
    for name, passed, detail in results:
        status_str = "PASS" if passed else "FAIL"
        if not passed:
            all_ok = False
        print(f"[{status_str:4}] {name:50} | {detail}")
    print("=" * 60)
    if all_ok:
        print("TOUS LES TESTS DU SPRINT 1 PARTIE 3 ONT REUSSI !")
        sys.exit(0)
    else:
        print("CERTAINS TESTS ONT ECHOUE !")
        sys.exit(1)
