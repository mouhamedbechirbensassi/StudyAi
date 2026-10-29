import os
import urllib.request
import urllib.error
import json
import subprocess
import time

BASE_URL = "http://localhost:8080/api/auth"
MYSQL_EXE = os.environ.get("MYSQL_EXE", r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe")
MYSQL_PWD = os.environ.get("DB_PASSWORD", "")

def make_request(url, method="GET", data=None, headers=None):
    if headers is None:
        headers = {}
    req = urllib.request.Request(url, method=method)
    for k, v in headers.items():
        req.add_header(k, v)
    if data is not None:
        req.add_header("Content-Type", "application/json")
        body = json.dumps(data).encode("utf-8")
        req.data = body

    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(err_body)
        except:
            return e.code, err_body

def query_mysql(sql):
    if not MYSQL_PWD:
        return ""
    cmd = [MYSQL_EXE, "-u", "root", f"-p{MYSQL_PWD}", "studyai_db", "-e", sql]
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()

print("="*60)
print("RUNNING LIVE END-TO-END TESTS AGAINST SPRING BOOT & MYSQL")
print("="*60)

# Test 1: Health check - unauthenticated /api/auth/me should return 401
status, body = make_request(f"{BASE_URL}/me", "GET")
print(f"[TEST 14] Unauthenticated /api/auth/me: Status {status}")
assert status == 401, f"Expected 401, got {status}"
print("-> PASS: Unauthenticated access rejected with 401")

# Test 2: Invalid registration (weak password, missing fields)
status, body = make_request(f"{BASE_URL}/register", "POST", {
    "fullName": "",
    "email": "not-an-email",
    "password": "weak"
})
print(f"[TEST 9] Invalid registration data: Status {status}")
assert status == 400, f"Expected 400, got {status}"
assert "validationErrors" in body, "Expected validation errors"
print("-> PASS: Invalid registration rejected with 400 and validation errors")

# Test 3: Register a new user
unique_email = f"teststudent_{int(time.time())}@studyai.com"
register_payload = {
    "fullName": "Karim Ben Ali",
    "email": unique_email,
    "password": "Password123!"
}
status, body = make_request(f"{BASE_URL}/register", "POST", register_payload)
print(f"[TEST 5] Register new user: Status {status}")
assert status == 201, f"Expected 201, got {status}"
assert "token" in body, "Expected JWT token in response"
assert body["user"]["email"] == unique_email, "User email mismatch"
assert body["user"]["role"] == "STUDENT", "Role must be STUDENT"
token = body["token"]
print("-> PASS: User registered successfully with JWT token and default role STUDENT")

# Test 4: Verify user in MySQL directly
db_out = query_mysql(f"SELECT email, full_name, role, password FROM users WHERE email='{unique_email}';")
print(f"[TEST 6 & 7] MySQL database inspection:\n{db_out}")
assert unique_email in db_out, "User not found in MySQL"
assert "Karim Ben Ali" in db_out, "User name not in MySQL"
assert "STUDENT" in db_out, "Role not in MySQL"
# Verify password is BCrypt hashed (starts with $2a$ or $2b$) and not plain text
assert "$2a$" in db_out or "$2b$" in db_out, "Password is not BCrypt hashed"
assert "Password123!" not in db_out, "Plain text password found in MySQL!"
print("-> PASS: User is in MySQL with BCrypt hashed password (no plain text)")

# Test 5: Reject duplicate email registration
status, body = make_request(f"{BASE_URL}/register", "POST", register_payload)
print(f"[TEST 8] Duplicate email registration: Status {status}")
assert status == 409, f"Expected 409, got {status}"
print("-> PASS: Duplicate email rejected with 409 Conflict")

# Test 6: Login with correct credentials
login_payload = {
    "email": unique_email,
    "password": "Password123!"
}
status, body = make_request(f"{BASE_URL}/login", "POST", login_payload)
print(f"[TEST 10 & 12] Login with valid credentials: Status {status}")
assert status == 200, f"Expected 200, got {status}"
assert "token" in body, "Token missing in login response"
login_token = body["token"]
assert body["user"]["fullName"] == "Karim Ben Ali"
print("-> PASS: Login successful with valid JWT generated")

# Test 7: Reject login with incorrect credentials
bad_login_payload = {
    "email": unique_email,
    "password": "WrongPassword999!"
}
status, body = make_request(f"{BASE_URL}/login", "POST", bad_login_payload)
print(f"[TEST 11] Login with bad credentials: Status {status}")
assert status == 401, f"Expected 401, got {status}"
print("-> PASS: Bad credentials rejected with 401 Unauthorized")

# Test 8: Current user endpoint with valid JWT
status, body = make_request(f"{BASE_URL}/me", "GET", headers={"Authorization": f"Bearer {login_token}"})
print(f"[TEST 15] Authenticated /api/auth/me: Status {status}")
assert status == 200, f"Expected 200, got {status}"
assert body["email"] == unique_email
assert body["fullName"] == "Karim Ben Ali"
assert body["role"] == "STUDENT"
assert "password" not in body, "Password leaked in /api/auth/me"
print("-> PASS: /api/auth/me returns correct safe profile for authenticated user")

# Test 9: Reject invalid / malformed JWT
status, body = make_request(f"{BASE_URL}/me", "GET", headers={"Authorization": "Bearer invalid.jwt.token"})
print(f"[TEST 13] Malformed JWT: Status {status}")
assert status == 401, f"Expected 401, got {status}"
print("-> PASS: Malformed JWT rejected with 401 Unauthorized")

# Test 10: Angular Frontend Availability
fe_req = urllib.request.Request("http://localhost:4200/")
with urllib.request.urlopen(fe_req) as fe_resp:
    fe_status = fe_resp.status
    fe_html = fe_resp.read().decode("utf-8")
print(f"[TEST 16] Angular frontend HTTP status: {fe_status}")
assert fe_status == 200, f"Frontend returned status {fe_status}"
assert "app-root" in fe_html, "Angular app-root tag missing"
print("-> PASS: Angular frontend server is serving application on port 4200")

print("\n" + "="*60)
print("ALL LIVE END-TO-END VERIFICATION TESTS PASSED SUCCESSFULLY!")
print("="*60)
