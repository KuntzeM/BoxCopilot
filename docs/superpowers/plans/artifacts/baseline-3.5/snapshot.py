#!/usr/bin/env python3
"""Seed a running BoxPilot backend and record redacted API snapshots (see README.md).

Env: BASE (default http://localhost:8080), ADMIN_USERNAME, ADMIN_PASSWORD, OUT_DIR,
     PG_CONTAINER + PG_USER + PG_DB (optional: dumps the oauth2_authorization row via docker exec/psql).
"""
import base64, hashlib, json, os, re, secrets, struct, subprocess, sys, zlib
from urllib.parse import urlparse, parse_qs, urlencode, quote
import requests

BASE = os.environ.get("BASE", "http://localhost:8080")
ADMIN_USER = os.environ.get("ADMIN_USERNAME", "admin")
ADMIN_PASS = os.environ["ADMIN_PASSWORD"]
OUT = os.environ.get("OUT_DIR", os.path.dirname(os.path.abspath(__file__)))
PG = os.environ.get("PG_CONTAINER")
REDIRECT = "http://localhost:41000/callback"
SNAP_USER, SNAP_PASS = "snapuser", "snap-throwaway-pw-1"

SECRETS = set()  # runtime secrets, scrubbed from every written file
KEEP_HEADERS = ("content-type", "cache-control", "www-authenticate", "location", "content-disposition",
                "x-content-type-options", "x-frame-options", "referrer-policy", "content-security-policy",
                "strict-transport-security", "x-xss-protection", "pragma", "expires", "vary", "allow")
REDACT_KEYS = {"token", "csrfToken", "imageToken", "access_token", "refresh_token", "id_token", "code",
               "client_secret", "password", "tokenValue", "clientId", "client_id", "url", "state"}
TS = re.compile(r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?(Z|[+-]\d\d:?\d\d)?$")
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
counter = [0]


def scrub_text(s):
    for sec in sorted(SECRETS, key=len, reverse=True):
        if sec and len(sec) >= 6:
            s = s.replace(sec, "<secret>")
    s = UUID.sub("<uuid>", s)
    s = re.sub(r"(/public/items/)[^/\"]+(/image)", r"\1<token>\2", s)
    s = re.sub(r"\?t=\d+", "?t=<ts>", s)
    s = re.sub(r"([?&])(state|code|code_challenge|code_verifier|nonce|client_secret|access_token|refresh_token)=[^&\"\s]*",
               lambda m: f"{m.group(1)}{m.group(2)}=<{m.group(2)}>", s)
    return s


def scrub(v, key=None):
    if isinstance(v, dict):
        return {k: scrub(x, k) for k, x in v.items()}
    if isinstance(v, list):
        return [scrub(x) for x in v]
    if isinstance(v, str):
        if key in REDACT_KEYS and v:
            return "<redacted>"
        if TS.match(v):
            return "<timestamp>"
        if len(v) > 400 and re.fullmatch(r"[A-Za-z0-9+/=]+", v):
            return f"<base64:{len(v)} chars>"
        return scrub_text(v)
    return v


def rec(name, resp, req_body=None, note=None):
    counter[0] += 1
    ctype = resp.headers.get("content-type", "")
    if "json" in ctype or (resp.text[:1] in "{[" and resp.text):
        try:
            body = scrub(resp.json())
        except ValueError:
            body = scrub_text(resp.text)
    elif ctype.startswith(("image/",)) or "octet" in ctype:
        body = {"binary": True, "bytes": len(resp.content), "sha256": hashlib.sha256(resp.content).hexdigest(),
                "magic": resp.content[:4].hex()}
    elif "html" in ctype:
        body = {"html": True, "bytes": len(resp.content), "text": re.sub(r"\s+", " ", scrub_text(resp.text))[:600]}
    else:
        body = scrub_text(resp.text)[:2000]
    doc = {
        "request": {"method": resp.request.method, "path": scrub_text(urlparse(resp.request.url).path +
                                                                       ("?" + urlparse(resp.request.url).query
                                                                        if urlparse(resp.request.url).query else "")),
                    "body": scrub(req_body) if req_body is not None else None},
        "response": {"status": resp.status_code,
                     "headers": {h: scrub_text(resp.headers[h]) for h in KEEP_HEADERS if h in resp.headers},
                     "set-cookie-attributes": [re.sub(r"=[^;]*", "=<v>", c.split(";", 1)[0]) + ";" +
                                               ";".join(p.strip().split("=")[0] + ("=" + p.split("=")[1] if p.strip().lower().startswith("samesite") else "")
                                                        for p in c.split(";")[1:])
                                               for c in resp.raw.headers.getlist("Set-Cookie")],
                     "body": body},
    }
    if note:
        doc["note"] = note
    fn = f"{counter[0]:02d}-{name}.json"
    with open(os.path.join(OUT, fn), "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, indent=2, sort_keys=True, ensure_ascii=False)
        f.write("\n")
    print(f"{resp.status_code} {fn}")
    return resp


class Client:
    def __init__(self):
        self.s = requests.Session()

    def call(self, method, path, name, json_body=None, csrf=True, **kw):
        headers = kw.pop("headers", {})
        if csrf and method != "GET":
            headers["X-XSRF-TOKEN"] = self.fresh_token()
        r = self.s.request(method, BASE + path, json=json_body, headers=headers, allow_redirects=False, **kw)
        for c in self.s.cookies:
            if c.name in ("JSESSIONID", "SESSION", "XSRF-TOKEN"):
                SECRETS.add(c.value)
        if name:
            rec(name, r, json_body)
        return r

    def fresh_token(self):
        # Spring Security 6 masks the token: the header must carry the value /api/v1/csrf returns, not the cookie.
        tok = self.s.get(BASE + "/api/v1/csrf").json()["token"]
        SECRETS.add(tok)
        for c in self.s.cookies:
            SECRETS.add(c.value)
        return tok

    def prime_csrf(self):
        return self.fresh_token()

    def login(self, user, pw, name=None):
        self.prime_csrf()
        r = self.s.post(BASE + "/api/auth/login", data={"username": user, "password": pw},
                        headers={"X-XSRF-TOKEN": self.fresh_token()}, allow_redirects=False)
        if name:
            rec(name, r, {"username": user, "password": pw})
        for c in self.s.cookies:
            SECRETS.add(c.value)
        return r


def png(size=64):
    rows = b""
    for y in range(size):
        rows += b"\x00" + b"".join(bytes([(x * 4) % 256, (y * 4) % 256, 128]) for x in range(size))

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def main():
    os.makedirs(OUT, exist_ok=True)
    SECRETS.update([ADMIN_PASS, SNAP_PASS])

    # ---- anonymous ------------------------------------------------------------------------------------------
    anon = Client()
    anon.call("GET", "/api/v1/csrf", "anon-csrf")
    anon.call("GET", "/api/v1/me", "anon-me")
    anon.call("GET", "/api/v1/boxes", "err-boxes-anonymous")
    anon.call("GET", "/api/v1/admin/users", "err-admin-users-anonymous")
    anon.call("POST", "/api/v1/boxes", "err-post-without-csrf-anonymous", json_body={"currentRoom": "x"})
    anon.call("GET", "/api/v1/public/00000000-0000-0000-0000-000000000000", "err-public-preview-unknown")
    anon.call("GET", "/api/v1/public/items/not-a-token/image", "err-public-image-bad-token")
    anon.call("POST", "/api/v1/auth/magic-login", "err-magic-login-bad-token", json_body={"token": "nope-nope-nope"})
    anon.call("POST", "/api/v1/auth/magic-login", "err-magic-login-blank-token", json_body={"token": ""})
    anon.login(ADMIN_USER, "wrong-password", "err-login-bad-credentials")

    # ---- admin ----------------------------------------------------------------------------------------------
    adm = Client()
    adm.login(ADMIN_USER, ADMIN_PASS, "admin-login")
    adm.call("GET", "/api/v1/me", "admin-me")
    adm.call("GET", "/api/v1/csrf", "admin-csrf")
    adm.call("GET", "/api/v1/box-numbers/status", "admin-box-numbers-status")
    adm.call("GET", "/api/v1/admin/users", "admin-users-list")
    adm.call("GET", "/api/v1/admin/users?role=ADMIN&enabled=true", "admin-users-list-filtered")
    snap = adm.call("POST", "/api/v1/admin/users", "admin-users-create",
                    json_body={"username": SNAP_USER, "name": "Snapshot User", "role": "USER", "enabled": True}).json()
    scratch = adm.call("POST", "/api/v1/admin/users", "admin-users-create-second",
                       json_body={"username": "scratchuser", "name": "Scratch User", "role": "USER"}).json()
    adm.call("POST", "/api/v1/admin/users", "err-admin-users-create-invalid", json_body={"username": ""})
    adm.call("POST", "/api/v1/admin/users", "err-admin-users-create-duplicate",
             json_body={"username": SNAP_USER, "name": "Dup", "role": "USER"})
    uid, sid = snap["id"], scratch["id"]
    adm.call("GET", f"/api/v1/admin/users/{uid}", "admin-users-get")
    adm.call("GET", "/api/v1/admin/users/999999", "err-admin-users-get-unknown")
    adm.call("PUT", f"/api/v1/admin/users/{uid}", "admin-users-update",
             json_body={"name": "Snapshot User Renamed", "mcpAllowed": True})
    adm.call("PUT", f"/api/v1/admin/users/{uid}/password", "admin-users-set-password", json_body={"password": SNAP_PASS})
    adm.call("PUT", f"/api/v1/admin/users/{uid}/password", "err-admin-users-set-password-invalid",
             json_body={"password": ""})
    adm.call("PUT", f"/api/v1/admin/users/{uid}/unlock", "admin-users-unlock")
    ml = adm.call("POST", f"/api/v1/admin/users/{uid}/magic-link", "admin-users-magic-link-create",
                  json_body={"expiresInHours": 2})
    token = ml.json()["token"]
    SECRETS.add(token)
    adm.call("POST", f"/api/v1/admin/users/{uid}/magic-link", "err-admin-users-magic-link-invalid-hours",
             json_body={"expiresInHours": 1000})
    ml2 = adm.call("POST", f"/api/v1/admin/users/{sid}/magic-link", None, json_body={"expiresInHours": 1})
    SECRETS.add(ml2.json().get("token", ""))
    adm.call("DELETE", f"/api/v1/admin/users/{sid}/magic-link", "admin-users-magic-link-revoke")
    adm.call("DELETE", f"/api/v1/admin/users/{sid}", "admin-users-delete")
    adm.call("DELETE", f"/api/v1/admin/users/{sid}", "err-admin-users-delete-again")

    # ---- the seeded user logs in through the magic link -----------------------------------------------------
    usr = Client()
    usr.prime_csrf()
    usr.call("POST", "/api/v1/auth/magic-login", "magic-login", json_body={"token": token})
    usr.call("POST", "/api/v1/auth/magic-login", "err-magic-login-token-reused", json_body={"token": token})
    usr.call("GET", "/api/v1/me", "user-me")
    usr.call("GET", "/api/v1/admin/users", "err-admin-users-as-user")
    usr.call("GET", "/api/v1/box-numbers/status", "err-box-numbers-status-as-user")
    usr.call("POST", "/api/v1/boxes", "err-csrf-missing-as-user", json_body={"currentRoom": "x"}, csrf=False)

    # ---- boxes ----------------------------------------------------------------------------------------------
    b1 = usr.call("POST", "/api/v1/boxes", "boxes-create",
                  json_body={"currentRoom": "Cellar", "targetRoom": "Kitchen", "description": "Winter things",
                             "isFragile": True}).json()
    b2 = usr.call("POST", "/api/v1/boxes", "boxes-create-second",
                  json_body={"currentRoom": "Attic", "targetRoom": "Bedroom", "description": "Decorations"}).json()
    b3 = usr.call("POST", "/api/v1/boxes", None, json_body={"currentRoom": "Garage"}).json()
    usr.call("POST", "/api/v1/boxes", "err-boxes-create-room-too-long", json_body={"currentRoom": "x" * 300})
    usr.call("POST", "/api/v1/boxes", "err-boxes-create-malformed-json", data="{not json",
             headers={"Content-Type": "application/json"})
    usr.call("GET", "/api/v1/boxes", "boxes-list")
    usr.call("GET", "/api/v1/boxes?includeItems=true", "boxes-list-include-items")
    usr.call("GET", f"/api/v1/boxes/{b1['uuid']}", "boxes-get-by-uuid")
    usr.call("GET", "/api/v1/boxes/00000000-0000-0000-0000-000000000000", "err-boxes-get-unknown")
    usr.call("PUT", f"/api/v1/boxes/{b1['id']}", "boxes-update",
             json_body={"description": "Winter things (updated)", "labelPrinted": True, "noStack": True})
    usr.call("PUT", "/api/v1/boxes/999999", "err-boxes-update-unknown", json_body={"description": "x"})
    usr.call("DELETE", f"/api/v1/boxes/{b3['id']}", "boxes-delete")
    usr.call("DELETE", f"/api/v1/boxes/{b3['id']}", "err-boxes-delete-again")

    # ---- items ----------------------------------------------------------------------------------------------
    i1 = usr.call("POST", "/api/v1/items", "items-create", json_body={"name": "Reading lamp", "boxId": b1["id"]}).json()
    i2 = usr.call("POST", "/api/v1/items", "items-create-second", json_body={"name": "Wool blanket", "boxId": b1["id"]}).json()
    i3 = usr.call("POST", "/api/v1/items", None, json_body={"name": "Candle", "boxId": b2["id"]}).json()
    usr.call("POST", "/api/v1/items", "err-items-create-empty-body", json_body={})
    usr.call("POST", "/api/v1/items", "err-items-create-wrong-type", json_body={"name": "x", "boxId": "abc"})
    usr.call("POST", "/api/v1/items", "err-items-create-unknown-box", json_body={"name": "x", "boxId": 999999})
    usr.call("GET", "/api/v1/items", "items-list")
    usr.call("GET", f"/api/v1/items/box/{b1['uuid']}", "items-list-by-box")
    usr.call("GET", "/api/v1/items/search?q=lamp", "items-search")
    usr.call("GET", f"/api/v1/items/search?q=a&boxUuid={b1['uuid']}", "items-search-in-box")
    usr.call("GET", "/api/v1/items/search", "err-items-search-missing-q")
    usr.call("PUT", f"/api/v1/items/{i2['id']}", "items-update", json_body={"name": "Wool blanket (grey)"})
    usr.call("PUT", f"/api/v1/items/{i3['id']}/move", "items-move", json_body={"targetBoxId": b1["id"]})
    usr.call("PUT", f"/api/v1/items/{i3['id']}/move", "err-items-move-empty", json_body={})
    usr.call("PUT", "/api/v1/items/move-bulk", "items-move-bulk",
             json_body={"itemIds": [i2["id"], i3["id"]], "targetBoxId": b2["id"]})
    usr.call("PUT", "/api/v1/items/move-bulk", "err-items-move-bulk-empty", json_body={"itemIds": []})
    up = usr.call("POST", f"/api/v1/items/{i1['id']}/image", "items-upload-image",
                  files={"file": ("lamp.png", png(), "image/png")})
    m = re.search(r"/public/items/([^/]+)/image", up.json().get("imageUrl") or "")
    itok = m.group(1) if m else None
    if itok:
        SECRETS.add(itok)
        anon.call("GET", f"/api/v1/public/items/{itok}/image", "public-item-image")
        anon.call("GET", f"/api/v1/public/items/{itok}/image/large", "public-item-image-large")
    # NB: a rejected upload removes the item's stored image files (see README, "Findings"), so the public image
    # is fetched before the failing uploads.
    usr.call("POST", f"/api/v1/items/{i1['id']}/image", "err-items-upload-not-an-image",
             files={"file": ("x.txt", b"hello", "text/plain")})
    usr.call("POST", f"/api/v1/items/{i1['id']}/image", "err-items-upload-16mb",
             files={"file": ("big.png", png() + b"\0" * (16 * 1024 * 1024), "image/png")})
    usr.call("POST", f"/api/v1/items/{i1['id']}/image", "err-items-upload-no-file")
    anon.call("GET", f"/api/v1/public/{b1['uuid']}", "public-box-preview")
    usr.call("DELETE", f"/api/v1/items/{i1['id']}/image", "items-delete-image")
    usr.call("DELETE", f"/api/v1/items/{i2['id']}", "items-delete")
    usr.call("DELETE", f"/api/v1/items/{i2['id']}", "err-items-delete-again")
    usr.call("POST", f"/api/v1/items/{i1['id']}/image", None, files={"file": ("lamp.png", png(), "image/png")})

    # ---- MCP ------------------------------------------------------------------------------------------------
    mcp_snapshot(usr)

    # ---- logout ---------------------------------------------------------------------------------------------
    usr.call("POST", "/api/auth/logout", "logout")
    usr.call("GET", "/api/v1/boxes", "err-boxes-after-logout")


def mcp_snapshot(usr):
    anon = Client()
    anon.call("GET", "/.well-known/oauth-protected-resource/mcp", "mcp-protected-resource-metadata")
    anon.call("GET", "/.well-known/oauth-authorization-server", "mcp-authorization-server-metadata")
    anon.call("POST", "/mcp", "mcp-err-401-no-token",
              json_body={"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}},
              headers={"Accept": "application/json, text/event-stream"}, csrf=False)
    anon.call("POST", "/mcp", "mcp-err-401-bad-token",
              json_body={"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}},
              headers={"Accept": "application/json, text/event-stream", "Authorization": "Bearer nope"}, csrf=False)

    client = usr.call("POST", "/api/v1/me/mcp-client", "mcp-client-provision").json()
    client_id = client["clientId"]
    SECRETS.add(client_id)
    usr.call("POST", "/api/v1/me/mcp-client", None)  # idempotent re-provision
    tokens = authorize(usr, client_id, "mcp-")
    at = tokens["access_token"]

    def rpc(name, method, params=None, rid=1):
        r = requests.post(BASE + "/mcp", json={"jsonrpc": "2.0", "id": rid, "method": method, "params": params or {}},
                          headers={"Authorization": f"Bearer {at}", "Accept": "application/json, text/event-stream",
                                   "Content-Type": "application/json"})
        if r.headers.get("content-type", "").startswith("text/event-stream"):
            data = "".join(l[5:].strip() for l in r.text.splitlines() if l.startswith("data:"))
            r._content = data.encode()
            r.headers["content-type"] = "application/json"
        rec(name, r, {"method": method, "params": params})
        return r.json()

    rpc("mcp-initialize", "initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                                          "clientInfo": {"name": "baseline", "version": "1.0"}})
    tl = rpc("mcp-tools-list", "tools/list")
    names = sorted(t["name"] for t in tl["result"]["tools"])
    with open(os.path.join(OUT, "mcp-tool-names.txt"), "w", newline="\n") as f:
        f.write("\n".join(names) + "\n")
    print("tools:", names)
    rpc("mcp-call-search-items", "tools/call", {"name": "search_items", "arguments": {"query": "lamp", "limit": 10}})
    rpc("mcp-call-search-boxes", "tools/call", {"name": "search_boxes", "arguments": {"query": "Cellar", "limit": 10}})
    boxes = usr.call("GET", "/api/v1/boxes", None).json()
    cellar = next(b for b in boxes if b["currentRoom"] == "Cellar")
    rpc("mcp-call-get-box-contents", "tools/call", {"name": "get_box_contents", "arguments": {"boxNumber": cellar["boxNumber"]}})
    lamp = next(i for i in usr.call("GET", "/api/v1/items", None).json() if i["name"] == "Reading lamp")
    rpc("mcp-call-get-item-image", "tools/call", {"name": "get_item_image", "arguments": {"itemId": lamp["id"], "size": "thumbnail"}})
    rpc("mcp-err-call-unknown-tool", "tools/call", {"name": "nope", "arguments": {}})
    rpc("mcp-err-call-invalid-arguments", "tools/call", {"name": "search_items", "arguments": {}})

    # refresh + the authorization row, then revoke
    rt = requests.post(BASE + "/oauth2/token", data={"grant_type": "refresh_token", "refresh_token": tokens["refresh_token"],
                                                      "client_id": client_id})
    SECRETS.update([tokens["access_token"], tokens["refresh_token"]])
    rec("mcp-token-refresh", rt, {"grant_type": "refresh_token"})
    SECRETS.update([rt.json()["access_token"], rt.json()["refresh_token"]])
    dump_authorization_row()
    usr.call("POST", "/api/v1/me/mcp-client/regenerate", "mcp-client-regenerate")
    usr.call("DELETE", "/api/v1/me/mcp-connections", "mcp-connections-revoke")
    # leave one live authorization behind for pg_dump / upgrade rehearsal
    client2 = usr.call("POST", "/api/v1/me/mcp-client", None).json()
    SECRETS.add(client2["clientId"])
    authorize(usr, client2["clientId"], None)


def authorize(usr, client_id, prefix):
    verifier = "baseline-code-verifier-that-is-long-enough-0123456789-" + secrets.token_hex(4)
    SECRETS.add(verifier)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    url = ("/oauth2/authorize?response_type=code&client_id=" + quote(client_id) + "&state=baseline-state&redirect_uri=" +
           quote(REDIRECT, safe="") + "&scope=" + quote("mcp:read") + "&code_challenge=" + challenge +
           "&code_challenge_method=S256")
    r = usr.call("GET", url, prefix and prefix + "authorize", csrf=False)
    loc = r.headers["Location"]
    cstate = parse_qs(urlparse(loc).query)["state"][0]
    SECRETS.add(cstate)
    page = usr.call("GET", "/oauth2/consent?state=" + quote(cstate), prefix and prefix + "consent-page", csrf=False)
    csrf = re.search(r'name="_csrf" value="([^"]+)"', page.text).group(1)
    SECRETS.add(csrf)
    ap = usr.call("POST", "/oauth2/authorize", prefix and prefix + "consent-approve", csrf=False,
                  data={"client_id": client_id, "state": cstate, "scope": "mcp:read", "_csrf": csrf})
    code = parse_qs(urlparse(ap.headers["Location"]).query)["code"][0]
    SECRETS.add(code)
    t = requests.post(BASE + "/oauth2/token", data={"grant_type": "authorization_code", "code": code,
                                                    "redirect_uri": REDIRECT, "client_id": client_id,
                                                    "code_verifier": verifier})
    if prefix:
        body = dict(t.json())
        SECRETS.update([body.get("access_token", ""), body.get("refresh_token", "")])
        rec(prefix + "token-authorization-code", t)
    j = t.json()
    SECRETS.update([j.get("access_token", ""), j.get("refresh_token", "")])
    return j


def dump_authorization_row():
    if not PG:
        return
    cmd = ["docker", "exec", PG, "pg_dump", "-U", os.environ.get("PG_USER", "bp"), "-d", os.environ.get("PG_DB", "boxpilot"),
           "--data-only", "--column-inserts", "--no-owner", "-t", "oauth2_authorization", "-t", "oauth2_registered_client"]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True).stdout
    keep = [l for l in out.splitlines() if l.startswith("INSERT INTO")]
    with open(os.path.join(OUT, "legacy-authorization-row.sql"), "w", encoding="utf-8", newline="\n") as f:
        f.write("-- Produced by the Spring Boot 3.5.16 MCP authorization flow (throwaway Postgres 15, see README.md).\n"
                "-- Rows: the registered client (FK target) and the oauth2_authorization row as it was after the\n"
                "-- authorization-code + refresh flow. Values are throwaway; nothing here is a live credential.\n")
        f.write("\n".join(keep) + "\n")
    print("authorization row dumped:", len(keep), "inserts")


if __name__ == "__main__":
    main()
