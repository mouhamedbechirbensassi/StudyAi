import urllib.request
import urllib.error
import json
import sys
import time

BASE_URL = "http://localhost:8080/api/auth"
FRONTEND_URL = "http://localhost:4200"

def log(msg, status="INFO"):
    print(f"[{status}] {msg}")

def test_api():
    timestamp = int(time.time())
    test_email = f"student_{timestamp}@studyai.test"
    test_password = "Password123!"
    test_name = f"Yassine Amrani {timestamp % 1000}"

    results = []

    # 1. Test unauthenticated /api/auth/me -> Should return 401
    log("1. Testing GET /api/auth/me without token (expecting 401)...")
    req = urllib.request.Request(f"{BASE_URL}/me", method="GET")
    try:
        urllib.request.urlopen(req)
        results.append(("Unauthenticated GET /api/auth/me rejected", False, "Expected 401, but request succeeded"))
    except urllib.error.HTTPError as e:
        if e.code == 401:
            results.append(("Unauthenticated GET /api/auth/me rejected (401)", True, f"HTTP {e.code} as expected"))
            log(f"   Success: returned 401 Unauthorized", "PASS")
        else:
            results.append(("Unauthenticated GET /api/auth/me rejected", False, f"Unexpected HTTP status: {e.code}"))

    # 2. Test invalid token on /api/auth/me -> Should return 401
    log("2. Testing GET /api/auth/me with invalid Bearer token (expecting 401)...")
    req = urllib.request.Request(
        f"{BASE_URL}/me",
        headers={"Authorization": "Bearer invalid.jwt.token"},
        method="GET"
    )
    try:
        urllib.request.urlopen(req)
        results.append(("Invalid token GET /api/auth/me rejected", False, "Expected 401, but request succeeded"))
    except urllib.error.HTTPError as e:
        if e.code == 401:
            results.append(("Invalid token GET /api/auth/me rejected (401)", True, f"HTTP {e.code} as expected"))
            log(f"   Success: returned 401 Unauthorized", "PASS")
        else:
            results.append(("Invalid token GET /api/auth/me rejected", False, f"Unexpected HTTP status: {e.code}"))

    # 3. Test Registration
    log(f"3. Testing POST /api/auth/register for '{test_email}'...")
    reg_payload = json.dumps({
        "fullName": test_name,
        "email": test_email,
        "password": test_password
    }).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE_URL}/register",
        data=reg_payload,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            token = data.get("token")
            user = data.get("user")
            if resp.status in (200, 201) and token and user:
                results.append(("Student Registration with real MySQL DB", True, f"Created user ID {user.get('id')} with role {user.get('role')}"))
                log(f"   Success: User registered with ID={user.get('id')}, Role={user.get('role')}", "PASS")
            else:
                results.append(("Student Registration", False, f"Unexpected response: {data}"))
    except Exception as e:
        results.append(("Student Registration", False, str(e)))
        log(f"   Failed to register: {e}", "FAIL")
        return results

    # 4. Test Login with invalid credentials -> Should return 401
    log("4. Testing POST /api/auth/login with invalid password (expecting 401)...")
    bad_login_payload = json.dumps({
        "email": test_email,
        "password": "WrongPassword999!"
    }).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE_URL}/login",
        data=bad_login_payload,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    try:
        urllib.request.urlopen(req)
        results.append(("Invalid login credentials rejected", False, "Expected 401, but succeeded"))
    except urllib.error.HTTPError as e:
        if e.code in (401, 400):
            results.append(("Invalid login credentials rejected (401)", True, f"HTTP {e.code} as expected"))
            log(f"   Success: returned HTTP {e.code}", "PASS")
        else:
            results.append(("Invalid login credentials rejected", False, f"Status: {e.code}"))

    # 5. Test Login with valid credentials
    log(f"5. Testing POST /api/auth/login with valid credentials...")
    login_payload = json.dumps({
        "email": test_email,
        "password": test_password
    }).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE_URL}/login",
        data=login_payload,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    jwt_token = None
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            jwt_token = data.get("token")
            user = data.get("user")
            if resp.status == 200 and jwt_token and user:
                results.append(("Student Login & JWT issuance", True, f"Authenticated user ID {user.get('id')}"))
                log(f"   Success: Logged in, JWT token received ({len(jwt_token)} chars)", "PASS")
            else:
                results.append(("Student Login & JWT issuance", False, f"Response: {data}"))
    except Exception as e:
        results.append(("Student Login & JWT issuance", False, str(e)))
        log(f"   Failed to login: {e}", "FAIL")
        return results

    # 6. Test GET /api/auth/me with valid Bearer token
    log(f"6. Testing GET /api/auth/me with valid Bearer token...")
    req = urllib.request.Request(
        f"{BASE_URL}/me",
        headers={"Authorization": f"Bearer {jwt_token}"},
        method="GET"
    )
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            log(f"   Profile response: {data}")
            has_id = "id" in data and data["id"] is not None
            has_name = data.get("fullName") == test_name
            has_email = data.get("email") == test_email
            has_role = data.get("role") == "STUDENT"
            has_created_at = "createdAt" in data and data["createdAt"] is not None
            no_password = "password" not in data and "passwordHash" not in data

            all_ok = has_id and has_name and has_email and has_role and has_created_at and no_password
            if all_ok:
                results.append(("GET /api/auth/me student profile payload", True,
                    f"id={data['id']}, fullName='{data['fullName']}', email='{data['email']}', role={data['role']}, createdAt={data['createdAt']}, password excluded"))
                log("   Success: Profile contract verified with all required safe fields!", "PASS")
            else:
                results.append(("GET /api/auth/me student profile payload", False,
                    f"Verification failed: has_id={has_id}, has_name={has_name}, has_email={has_email}, has_role={has_role}, has_created_at={has_created_at}, no_password={no_password}"))
    except Exception as e:
        results.append(("GET /api/auth/me student profile payload", False, str(e)))
        log(f"   Failed to get profile: {e}", "FAIL")

    # 7. Test Frontend HTTP availability
    log("7. Testing Frontend HTTP availability on http://localhost:4200...")
    req = urllib.request.Request(FRONTEND_URL, method="GET")
    try:
        with urllib.request.urlopen(req) as resp:
            html = resp.read().decode("utf-8")
            if "<app-root>" in html:
                results.append(("Frontend HTTP delivery (<app-root> detected)", True, f"HTTP {resp.status}, content length: {len(html)}"))
                log("   Success: Frontend is serving Angular app properly!", "PASS")
            else:
                results.append(("Frontend HTTP delivery", False, "Missing <app-root> in HTML"))
    except Exception as e:
        results.append(("Frontend HTTP delivery", False, str(e)))
        log(f"   Frontend request failed: {e}", "FAIL")

    return results

if __name__ == "__main__":
    print("=== StudyAI Sprint 1 Part 2: End-to-End Test Suite ===")
    results = test_api()
    print("\n=== Summary of Test Results ===")
    all_passed = True
    for name, passed, detail in results:
        status_str = "PASS" if passed else "FAIL"
        if not passed:
            all_passed = False
        print(f"[{status_str}] {name} - {detail}")

    if all_passed:
        print("\nALL SPRINT 1 PART 2 VERIFICATION TESTS PASSED SUCCESSFULLY!")
        sys.exit(0)
    else:
        print("\nSOME TESTS FAILED!")
        sys.exit(1)
