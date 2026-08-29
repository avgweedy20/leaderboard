import os
import re
import uuid
import time
import hashlib
import secrets
from functools import wraps
from datetime import datetime, timezone
from flask import Flask, request, jsonify, render_template, send_from_directory
from flask_cors import CORS
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__, template_folder='templates', static_folder='static')

# CORS is scoped to the API and locked to explicitly allowed origins.
# The web frontend is served same-origin by Flask (no CORS needed); native
# clients are unaffected. Set CORS_ALLOWED_ORIGINS to a comma-separated list
# (or "*") only if you host the frontend on a separate origin.
_cors_origins_raw = os.getenv("CORS_ALLOWED_ORIGINS", "").strip()
_cors_origins = []
if _cors_origins_raw:
    _cors_origins = [o.strip() for o in _cors_origins_raw.split(",") if o.strip()]
CORS(app, resources={r"/api/*": {"origins": _cors_origins}})

# Env config
SUPABASE_URL = os.getenv("SUPABASE_URL", "https://mock.supabase.co")
SUPABASE_SERVICE_ROLE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "mock-service-key")
SUPABASE_ANON_KEY = os.getenv("SUPABASE_ANON_KEY", "mock-anon-key")

# Supabase client (None = Supabase not configured; unless every endpoint that
# depends on it returns an explicit "Supabase not configured" 503 response, the
# app fails closed rather than serving stale in-memory data).
supabase_client = None
if SUPABASE_URL != "https://mock.supabase.co" and "mock" not in SUPABASE_SERVICE_ROLE_KEY:
    try:
        from supabase import create_client, Client
        supabase_client: Client = create_client(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
    except Exception as e:
        print(f"Warning: Could not initialize Supabase client: {e}")

# A dedicated service-role client for authoritative data reads/writes.
# `supabase_client.auth.sign_in_*` mutates the client's session to the signed-in
# user's token, which RLS then filters out of the `admins` table. Using a
# separate service-role client for admin lookups avoids that, so a valid login
# is never rejected because the auth check was run under the user's own token.
_SERVICE_CLIENT = None
def _service_client():
    global _SERVICE_CLIENT
    client = supabase_client
    if not client:
        return None
    # If the wired client is not a real supabase-py client (e.g. the test
    # FakeSupabase stand-in), there is no separate service-role session to worry
    # about, so just return it.
    if type(client).__name__ != "Client":
        return client
    if _SERVICE_CLIENT is None:
        try:
            _SERVICE_CLIENT = __import__("supabase").create_client(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
        except Exception:
            _SERVICE_CLIENT = client
    return _SERVICE_CLIENT

# AUTH ------------------------------------------------------------------
# Admin accounts are real Supabase Auth users (managed with the service-role
# client). An account is an "admin" while its email exists in public.admins.
# Sessions are opaque, expiring, server-side tokens stored (SHA-256 hashed) in
# the public.admin_sessions table; they are revoked on logout and immediately
# when the account is removed. No static or JWT-based shortcut is ever
# accepted. Admin actions are recorded in public.admin_audit_log.
SESSION_TTL = 21600  # seconds (6h), synced with the frontend session length
MAX_SESSIONS_PER_ADMIN = 5
_PASSWORD_MIN_LENGTH = 6
_LOGIN_MAX_ATTEMPTS = 5
_LOGIN_WINDOW_SECONDS = 300  # 5 minutes
_LOGIN_MAX_IP_ATTEMPTS = 20  # per-IP ceiling to blunt password spraying

_login_failures = {}  # (ip, email) -> [failure timestamps]
_login_failures_ip = {}  # ip -> [failure timestamps]


def _internal_error(exc):
    """Log an unexpected exception and return a generic client-facing message.

    Never leak internal exception details (DB/supabase internals, stack traces)
    to the client.
    """
    app.logger.error("Internal error: %s", exc, exc_info=True)
    return "An internal error occurred. Please try again."


# Columns admins are allowed to mutate per resource. Anything else in the
# request body is dropped, preventing mass-assignment of unrelated columns.
_ALLOWED_TEAM_FIELDS = {"name", "house_id", "sport_id", "gender", "squad_label", "level"}
_ALLOWED_PLAYER_FIELDS = {"name", "team_id", "roll_number", "grade", "section", "gender", "level"}
_ALLOWED_MATCH_FIELDS = {
    "sport_id", "team_a_id", "team_b_id", "gender", "stage", "level", "status",
    "round_info", "winner_team_id", "is_draw", "score_team_a", "score_team_b",
    "score_difference", "score_summary",
}


def _whitelist_fields(data, allowed):
    if not isinstance(data, dict):
        return {}
    return {k: v for k, v in data.items() if k in allowed}


def _admin_role(email):
    """Return an active admin's role ('admin' or 'superadmin'), or None.

    Reads through the service-role client so the lookup is never RLS-filtered
    by the caller's own session. Inactive accounts (is_active = FALSE) and
    unknown emails both resolve to None.
    """
    if not email:
        return None
    email = email.strip().lower()
    client = _service_client()
    if not client:
        return None
    try:
        res = client.table("admins").select("role, is_active").eq("email", email).limit(1).execute()
        if not res.data:
            return None
        row = res.data[0]
        if row.get("is_active", True) is False:
            return None
        return (row.get("role") or "admin").lower()
    except Exception:
        return None


def _is_admin_email(email):
    return _admin_role(email) in ("admin", "superadmin")


def _is_super_admin(email):
    return _admin_role(email) == "superadmin"


def _audit(action, actor_email=None, target_email=None, ip_address=None, details=None):
    """Append a row to public.admin_audit_log (best effort, never fatal)."""
    client = _service_client()
    if not client:
        return
    try:
        row = {
            "action": action,
            "actor_email": (actor_email or "").lower(),
            "target_email": (target_email or "").lower(),
            "ip_address": ip_address,
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
        if details is not None:
            row["details"] = details
        client.table("admin_audit_log").insert(row).execute()
    except Exception:
        pass


def add_admin(email, password, role="admin"):
    """Create (or update) an admin account. Raises ValueError on bad input."""
    role = (role or "admin").strip().lower()
    if role not in ("admin", "superadmin"):
        raise ValueError("role must be 'admin' or 'superadmin'")
    email = (email or "").strip().lower()
    if not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", email):
        raise ValueError("Invalid email address")
    if not password or len(password) < _PASSWORD_MIN_LENGTH:
        raise ValueError("Password must be at least %d characters" % _PASSWORD_MIN_LENGTH)
    if password.lower() == email:
        raise ValueError("Password must not be the email address")
    if not _service_client():
        raise RuntimeError("Supabase not configured")
    try:
        _service_client().auth.admin.create_user({
            "email": email, "password": password, "email_confirm": True
        })
    except Exception:
        pass  # Auth user may already exist
    _service_client().table("admins").upsert({"email": email, "role": role}, on_conflict="email").execute()


def remove_admin(email):
    """Remove an admin account entirely: drop the admins row, revoke all
    sessions immediately, and delete the Supabase Auth user best-effort."""
    email = (email or "").strip().lower()
    client = _service_client()
    if not client:
        raise RuntimeError("Supabase not configured")
    try:
        client.table("admins").delete().eq("email", email).execute()
    except Exception:
        pass
    _revoke_admin_sessions(email)
    # Delete the underlying Auth account so the user can never sign in again.
    user_id = _auth_user_id_by_email(email)
    if user_id:
        try:
            client.auth.admin.delete_user(user_id)
        except Exception:
            pass


def list_admins():
    """List admin accounts as [{"email": ..., "role": ..., "created_at": ...}]."""
    client = _service_client()
    if not client:
        return []
    try:
        res = client.table("admins").select("email, role, is_active, created_at").order("email").execute()
        return [dict(r) for r in (res.data or [])]
    except Exception:
        return []


def _auth_user_id_by_email(email):
    """Resolve a Supabase Auth user id from an email (service-role client)."""
    client = _service_client()
    if not client:
        return None
    try:
        users = client.auth.admin.list_users()
        for u in users:
            if (getattr(u, "email", "") or "").lower() == email:
                return getattr(u, "id", None)
    except Exception:
        return None
    return None


def _session_token_hash(token):
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _cleanup_sessions():
    if not _service_client():
        return
    try:
        _service_client().table("admin_sessions").delete().lt("expires_at", time.time()).execute()
    except Exception:
        pass


def _issue_session(email):
    token = secrets.token_urlsafe(48)
    if not _service_client():
        return token
    try:
        _service_client().table("admin_sessions").insert({
            "token_hash": _session_token_hash(token),
            "email": email,
            "expires_at": time.time() + SESSION_TTL,
        }).execute()
    except Exception:
        pass
    return token


def _session_email(token):
    if not token or not _service_client():
        return None
    _cleanup_sessions()
    try:
        res = _service_client().table("admin_sessions").select("email").eq("token_hash", _session_token_hash(token)).maybe_single().execute()
        return res.data.get("email") if isinstance(res.data, dict) else None
    except Exception:
        return None


def _revoke_session(token):
    if not token or not _service_client():
        return
    try:
        _service_client().table("admin_sessions").delete().eq("token_hash", _session_token_hash(token)).execute()
    except Exception:
        pass


def _revoke_admin_sessions(email):
    if not _service_client():
        return
    try:
        _service_client().table("admin_sessions").delete().eq("email", email).execute()
    except Exception:
        pass


def _clear_all_sessions():
    if not _service_client():
        return
    try:
        _service_client().table("admin_sessions").delete().neq("token_hash", "").execute()
    except Exception:
        pass


def _active_session_count(email):
    client = _service_client()
    if not client:
        return 0
    _cleanup_sessions()
    try:
        res = client.table("admin_sessions").select("token_hash", count="exact").eq("email", email).execute()
        return int(res.count or 0)
    except Exception:
        return 0


def _validate_session(token):
    email = _session_email(token)
    if not email:
        return None
    if not _is_admin_email(email):
        _revoke_session(token)
        return None
    return email


def _login_failure_window(key):
    now = time.time()
    return [ts for ts in _login_failures.get(key, []) if now - ts < _LOGIN_WINDOW_SECONDS]


def _login_failure_window_ip(ip):
    now = time.time()
    return [ts for ts in _login_failures_ip.get(ip, []) if now - ts < _LOGIN_WINDOW_SECONDS]


def _clear_login_failures(key):
    _login_failures.pop(key, None)


def _record_login_failure(key):
    timestamps = _login_failure_window(key)
    timestamps.append(time.time())
    _login_failures[key] = timestamps
    ip = key[0] if isinstance(key, tuple) and key else ""
    if ip:
        ip_ts = _login_failure_window_ip(ip)
        ip_ts.append(time.time())
        _login_failures_ip[ip] = ip_ts


def _login_blocked(key):
    if len(_login_failure_window(key)) >= _LOGIN_MAX_ATTEMPTS:
        return True
    ip = key[0] if isinstance(key, tuple) and key else ""
    return bool(ip) and len(_login_failure_window_ip(ip)) >= _LOGIN_MAX_IP_ATTEMPTS


def req_admin_auth(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get("Authorization", "")
        parts = auth_header.split(" ", 1)
        if len(parts) != 2 or parts[0].strip() != "Bearer" or not parts[1].strip():
            return jsonify({"error": "Unauthorized admin access required"}), 401
        token = parts[1].strip()
        if not _validate_session(token):
            return jsonify({"error": "Invalid or expired auth token"}), 401
        return f(*args, **kwargs)
    return decorated


def req_super_admin_auth(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get("Authorization", "")
        parts = auth_header.split(" ", 1)
        if len(parts) != 2 or parts[0].strip() != "Bearer" or not parts[1].strip():
            return jsonify({"error": "Unauthorized admin access required"}), 401
        token = parts[1].strip()
        email = _validate_session(token)
        if not email:
            return jsonify({"error": "Invalid or expired auth token"}), 401
        if not _is_super_admin(email):
            _audit("superadmin.denied", actor_email=email, ip_address=request.remote_addr,
                   details=request.path)
            return jsonify({"error": "Super admin privileges required"}), 403
        return f(*args, **kwargs)
    return decorated


def _current_actor():
    """Email of the currently authenticated admin from the Bearer token, or ''."""
    auth_header = request.headers.get("Authorization", "")
    if auth_header.startswith("Bearer "):
        return _session_email(auth_header[7:].strip()) or ""
    return ""


# --- HTML PAGE ENDPOINTS (MULTI-PAGE ARCHITECTURE) ---

@app.route("/")
def index_page():
    return render_template("index.html", active_page="overall")

@app.route("/standings")
@app.route("/standings/<sport_slug>")
def standings_page(sport_slug="futsal"):
    # Slugs are echoed into an inline <script> (JS-encoded via |tojson) on the
    # page; additionally restrict to a plain-safe charset as defense-in-depth.
    if not re.fullmatch(r"[A-Za-z0-9 _-]{1,80}", sport_slug):
        sport_slug = "futsal"
    return render_template("standings.html", active_page="standings", sport_slug=sport_slug)

@app.route("/fixtures")
def fixtures_page():
    return render_template("fixtures.html", active_page="fixtures")

@app.route("/admin")
def admin_page():
    return render_template("admin/index.html", active_page="admin", active_admin_sub="dashboard")

@app.route("/admin/squads")
def admin_squads_page():
    return render_template("admin/squads.html", active_page="admin_squads", active_admin_sub="squads")

@app.route("/admin/players")
def admin_players_page():
    return render_template("admin/players.html", active_page="admin_players", active_admin_sub="players")

@app.route("/api/health", methods=["GET"])
def health_check():
    return jsonify({
        "status": "healthy",
        "supabase_connected": supabase_client is not None,
        "mode": "supabase" if supabase_client else "unconfigured"
    })

# AUTH ENDPOINTS
@app.route("/api/auth/login", methods=["POST"])
def auth_login():
    ip = request.remote_addr or "0.0.0.0"
    data = request.get_json() or {}
    email = (data.get("email") or "").strip().lower()
    password = data.get("password") or ""

    if not email or not password:
        return jsonify({"error": "Email and password required"}), 400

    if not supabase_client:
        return jsonify({"error": "Supabase not configured"}), 503

    cred_key = (ip, email)
    if _login_blocked(cred_key):
        return jsonify({"error": "Too many failed attempts. Try again later."}), 429

    try:
        res = supabase_client.auth.sign_in_with_password({"email": email, "password": password})
        user_email = (getattr(res.user, "email", "") or "").lower()
    except Exception:
        _record_login_failure(cred_key)
        return jsonify({"error": "Invalid email or password"}), 401
    if user_email != email or not _is_admin_email(user_email):
        _record_login_failure(cred_key)
        return jsonify({"error": "Invalid email or password"}), 401

    active_sessions = _active_session_count(email)
    if active_sessions >= MAX_SESSIONS_PER_ADMIN:
        return jsonify({
            "error": "Too many active sessions for this account. Sign out elsewhere first."
        }), 429

    _clear_login_failures(cred_key)
    token = _issue_session(email)
    _audit("login", actor_email=email, ip_address=ip)
    return jsonify({
        "access_token": token,
        "expires_in": SESSION_TTL,
        "user": {"id": email, "email": email, "role": _admin_role(email)}
    })


@app.route("/api/auth/me", methods=["GET"])
@req_admin_auth
def auth_me():
    """Return the authenticated admin's identity for the current session."""
    token = request.headers.get("Authorization", "").split(" ", 1)[1].strip()
    email = _session_email(token) or ""
    return jsonify({"email": email, "role": _admin_role(email)})


@app.route("/api/auth/logout", methods=["POST"])
@req_admin_auth
def auth_logout():
    """Revoke the current session server-side."""
    email = _session_email(request.headers.get("Authorization", "").split(" ", 1)[1].strip()) or ""
    token = request.headers.get("Authorization", "").split(" ", 1)[1].strip()
    _revoke_session(token)
    _audit("logout", actor_email=email, ip_address=request.remote_addr)
    return jsonify({"status": "logged_out"})


# ADMIN MANAGEMENT ENDPOINTS
@app.route("/api/admin/list", methods=["GET"])
@req_super_admin_auth
def api_admin_list():
    """List all admin accounts and their roles (super-admin only)."""
    return jsonify({"admins": list_admins()})


@app.route("/api/admin/add", methods=["POST"])
@req_super_admin_auth
def api_admin_add():
    """Create a new admin account (super-admin only)."""
    actor = _current_actor()
    data = request.get_json() or {}
    email = (data.get("email") or "").strip()
    password = data.get("password") or ""
    role = (data.get("role") or "admin").strip().lower()
    if role not in ("admin", "superadmin"):
        return jsonify({"error": "role must be 'admin' or 'superadmin'"}), 400
    try:
        add_admin(email, password, role=role)
    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        return jsonify({"error": _internal_error(e)}), 500
    _audit("admin.add", actor_email=actor, target_email=(email or "").lower(),
           ip_address=request.remote_addr, details="role=%s" % role)
    return jsonify({"message": "Admin added", "email": (email or "").lower(), "role": role}), 201


@app.route("/api/admin/remove", methods=["POST"])
@req_super_admin_auth
def api_admin_remove():
    """Remove an admin account and revoke its sessions (super-admin only)."""
    actor = _current_actor()
    data = request.get_json() or {}
    email = (data.get("email") or "").strip().lower()
    if not email:
        return jsonify({"error": "email is required"}), 400
    if email == actor:
        return jsonify({"error": "You cannot remove your own account"}), 400

    remaining = list_admins()
    if len(remaining) <= 1:
        return jsonify({"error": "Cannot remove the last admin account"}), 400
    target_role = next((a.get("role") for a in remaining if (a.get("email") or "").lower() == email), None)
    if target_role is None:
        return jsonify({"error": "Target must be an existing admin"}), 404
    if target_role == "superadmin" and sum(1 for a in remaining if a.get("role") == "superadmin") <= 1:
        return jsonify({"error": "Cannot remove the last super admin"}), 400

    try:
        remove_admin(email)
    except Exception as e:
        return jsonify({"error": _internal_error(e)}), 500
    _audit("admin.remove", actor_email=actor, target_email=email,
           ip_address=request.remote_addr, details="role=%s" % target_role)
    return jsonify({"message": "Admin removed", "email": email})


@app.route("/api/admin/reset-password", methods=["POST"])
@req_super_admin_auth
def api_admin_reset_password():
    """Reset an admin's password via Supabase Auth (super-admin only).

    Plain admins cannot reset passwords at all, not even their own.
    """
    actor = _current_actor()
    data = request.get_json() or {}
    email = (data.get("email") or "").strip().lower()
    password = data.get("password") or ""
    if not email:
        return jsonify({"error": "email is required"}), 400
    if email not in {a.get("email") for a in list_admins()}:
        return jsonify({"error": "Target must be an existing admin"}), 404
    if not password or len(password) < _PASSWORD_MIN_LENGTH:
        return jsonify({"error": "Password must be at least %d characters" % _PASSWORD_MIN_LENGTH}), 400
    if not supabase_client:
        return jsonify({"error": "Supabase not configured"}), 503
    user_id = _auth_user_id_by_email(email)
    if not user_id:
        return jsonify({"error": "Auth user not found"}), 404
    try:
        _service_client().auth.admin.update_user_by_id(user_id, {"password": password})
    except Exception as e:
        return jsonify({"error": _internal_error(e)}), 500
    _revoke_admin_sessions(email)
    _audit("admin.reset_password", actor_email=actor, target_email=email, ip_address=request.remote_addr)
    return jsonify({"message": "Password updated"})


@app.route("/api/admin/log", methods=["GET"])
@req_super_admin_auth
def api_admin_log():
    """Return admin audit log entries with optional filters (super-admin only).

    Query params:
      action  substring match on action            (e.g. match.update, admin.add)
      actor   substring match on actor_email
      target  substring match on target_email
      details substring match on the details column
      from    ISO-8601 datetime (inclusive) lower bound on created_at
      to      ISO-8601 datetime (inclusive) upper bound on created_at
      limit   page size           (default 50, max 200)
      offset  row offset for paging
    """
    actor = _current_actor()
    _audit("admin.view_log", actor_email=actor, ip_address=request.remote_addr)
    if not _service_client():
        return jsonify({"error": "Supabase not configured"}), 503
    try:
        query = _service_client().table("admin_audit_log").select("*", count="exact").order("created_at", desc=True)

        action = request.args.get("action")
        if action:
            query = query.ilike("action", "%" + action + "%")
        actor_filter = request.args.get("actor")
        if actor_filter:
            query = query.ilike("actor_email", "%" + actor_filter + "%")
        target_filter = request.args.get("target")
        if target_filter:
            query = query.ilike("target_email", "%" + target_filter + "%")
        details_filter = request.args.get("details")
        if details_filter:
            query = query.ilike("details", "%" + details_filter + "%")

        def _parse_date(value, name):
            try:
                return datetime.fromisoformat(value.replace("Z", "+00:00")).isoformat()
            except (TypeError, ValueError):
                raise ValueError("Invalid '%s' date (use ISO 8601, e.g. 2026-08-01 or 2026-08-01T12:00:00)" % name)

        since = request.args.get("from")
        if since:
            query = query.gte("created_at", _parse_date(since, "from"))
        until = request.args.get("to")
        if until:
            query = query.lte("created_at", _parse_date(until, "to"))

        try:
            limit = max(1, min(int(request.args.get("limit", 50)), 200))
        except ValueError:
            limit = 50
        try:
            offset = max(0, int(request.args.get("offset", 0)))
        except ValueError:
            offset = 0

        query = query.range(offset, offset + limit - 1)
        res = query.execute()
        return jsonify({
            "entries": res.data,
            "total": res.count if res.count is not None else len(res.data),
            "limit": limit,
            "offset": offset,
        })
    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        return jsonify({"error": _internal_error(e)}), 500


@app.after_request
def add_security_headers(response):
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("X-Frame-Options", "DENY")
    response.headers.setdefault("Referrer-Policy", "no-referrer")
    response.headers.setdefault("X-Permitted-Cross-Domain-Policies", "none")
    # Defense-in-depth. The app relies on Tailwind's CDN and inline scripts &
    # handlers, so 'unsafe-inline' stays for script/style; the policy still
    # blocks object/embed/plugin execution, base-URI hijacking, form action
    # redirection, and framing from other origins.
    response.headers.setdefault(
        "Content-Security-Policy",
        "default-src 'self'; "
        "script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com; "
        "style-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com; "
        "img-src 'self' data:; font-src 'self'; "
        "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; "
        "connect-src 'self'",
    )
    if request.is_secure:
        response.headers.setdefault("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    if request.path.startswith("/api/"):
        response.headers.setdefault("Cache-Control", "no-store")
    return response


# HOUSES ENDPOINTS
@app.route("/api/houses", methods=["GET"])
def get_houses():
    if supabase_client:
        try:
            res = _service_client().table("houses").select("*").order("name").execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503


# SPORTS ENDPOINTS
@app.route("/api/sports", methods=["GET"])
def get_sports():
    if supabase_client:
        try:
            res = _service_client().table("sports").select("*").execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/sports", methods=["POST"])
@req_admin_auth
def create_sport():
    data = request.get_json() or {}
    name = data.get("name")
    sport_type = data.get("type", "generic")
    level = data.get("level", "HS")
    point_win = data.get("point_win", 3)
    point_draw = data.get("point_draw", 1)
    point_loss = data.get("point_loss", 0)
    is_lower = data.get("is_lower_score_better", False)

    if not name:
        return jsonify({"error": "Sport name required"}), 400

    record = {
        "id": str(uuid.uuid4()),
        "name": name,
        "type": sport_type,
        "level": level,
        "point_win": point_win,
        "point_draw": point_draw,
        "point_loss": point_loss,
        "is_lower_score_better": is_lower
    }

    if supabase_client:
        try:
            res = _service_client().table("sports").insert(record).execute()
            _audit("sport.create", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="created sport '%s' (type=%s)" % (name, sport_type))
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500

    return jsonify({"error": "Supabase not configured"}), 503


# TEAMS / SQUADS ENDPOINTS
@app.route("/api/teams", methods=["GET"])
def get_teams():
    sport_id = request.args.get("sport_id")
    house_id = request.args.get("house_id")
    gender = request.args.get("gender")

    if supabase_client:
        try:
            query = _service_client().table("teams").select("*, houses(name, color_hex, short_code), sports(name)")
            if sport_id:
                query = query.eq("sport_id", sport_id)
            if house_id:
                query = query.eq("house_id", house_id)
            if gender:
                query = query.eq("gender", gender)
            res = query.execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500

    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/teams", methods=["POST"])
@req_admin_auth
def create_team():
    data = request.get_json() or {}
    house_id = data.get("house_id")
    sport_id = data.get("sport_id")
    gender = data.get("gender", "Boys")
    squad_label = data.get("squad_label", "A")
    name = data.get("name")

    if not house_id or not sport_id:
        return jsonify({"error": "House and sport are required"}), 400

    # Auto generate name if missing
    if not name:
        house_name = "House"
        sport_name = "Sport"
        if supabase_client:
            try:
                hr = _service_client().table("houses").select("name").eq("id", house_id).maybe_single().execute()
                if isinstance(hr.data, dict):
                    house_name = hr.data.get("name", house_name)
                sr = _service_client().table("sports").select("name").eq("id", sport_id).maybe_single().execute()
                if isinstance(sr.data, dict):
                    sport_name = sr.data.get("name", sport_name)
            except Exception:
                pass
        name = f"{house_name} {gender} {sport_name} {squad_label}"

    record = {
        "id": str(uuid.uuid4()),
        "name": name,
        "house_id": house_id,
        "sport_id": sport_id,
        "gender": gender,
        "squad_label": squad_label,
        "level": data.get("level", "HS")
    }

    if supabase_client:
        try:
            res = _service_client().table("teams").insert(record).execute()
            _audit("team.create", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="created team '%s'" % name)
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500

    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/teams/<team_id>", methods=["PUT"])
@req_admin_auth
def update_team(team_id):
    data = _whitelist_fields(request.get_json() or {}, _ALLOWED_TEAM_FIELDS)
    if supabase_client:
        try:
            res = _service_client().table("teams").update(data).eq("id", team_id).execute()
            _audit("team.update", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="updated team %s [%s]" % (team_id, ",".join(sorted(data.keys()))))
            return jsonify(res.data[0] if res.data else data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/teams/<team_id>", methods=["DELETE"])
@req_admin_auth
def delete_team(team_id):
    if supabase_client:
        try:
            _service_client().table("teams").delete().eq("id", team_id).execute()
            _audit("team.delete", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="deleted team %s" % team_id)
            return jsonify({"message": "Team deleted"})
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503


# PLAYERS ENDPOINTS
@app.route("/api/players", methods=["GET"])
def get_players():
    team_id = request.args.get("team_id")

    if supabase_client:
        try:
            query = _service_client().table("players").select("*, teams(name, gender, squad_label, houses(name, color_hex))")
            if team_id:
                query = query.eq("team_id", team_id)
            res = query.execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/players", methods=["POST"])
@req_admin_auth
def create_player():
    data = request.get_json() or {}
    name = data.get("name")
    team_id = data.get("team_id")
    if not name or not team_id:
        return jsonify({"error": "Player name and team_id required"}), 400

    record = {
        "id": str(uuid.uuid4()),
        "name": name,
        "team_id": team_id,
        "roll_number": data.get("roll_number"),
        "grade": data.get("grade"),
        "section": data.get("section"),
        "gender": data.get("gender", "Boys"),
        "level": data.get("level", "HS")
    }

    if supabase_client:
        try:
            res = _service_client().table("players").insert(record).execute()
            _audit("player.create", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="created player '%s' (team %s)" % (name, team_id))
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/players/<player_id>", methods=["PUT"])
@req_admin_auth
def update_player(player_id):
    data = _whitelist_fields(request.get_json() or {}, _ALLOWED_PLAYER_FIELDS)
    if supabase_client:
        try:
            res = _service_client().table("players").update(data).eq("id", player_id).execute()
            _audit("player.update", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="updated player %s [%s]" % (player_id, ",".join(sorted(data.keys()))))
            return jsonify(res.data[0] if res.data else data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/players/<player_id>", methods=["DELETE"])
@req_admin_auth
def delete_player(player_id):
    if supabase_client:
        try:
            _service_client().table("players").delete().eq("id", player_id).execute()
            _audit("player.delete", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="deleted player %s" % player_id)
            return jsonify({"message": "Player deleted"})
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/players/bulk", methods=["POST"])
@req_admin_auth
def bulk_upsert_players():
    items = request.get_json() or []
    if not isinstance(items, list):
        return jsonify({"error": "Expected a list of player objects"}), 400
    items = [_whitelist_fields(i, _ALLOWED_PLAYER_FIELDS) for i in items]
    items = [i for i in items if i.get("name") and i.get("team_id")]

    if supabase_client:
        try:
            # Upsert using roll_number on conflict
            res = _service_client().table("players").upsert(items, on_conflict="roll_number").execute()
            count = len(res.data or items)
            _audit("player.bulk_upsert", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="bulk upsert of %d player(s)" % count)
            return jsonify({"message": f"Successfully processed {count} players", "count": count})
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500

    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/players/bulk-delete", methods=["POST"])
@req_admin_auth
def bulk_delete_players():
    data = request.get_json() or {}
    player_ids = data.get("player_ids", [])
    if not player_ids or not isinstance(player_ids, list):
        return jsonify({"error": "player_ids list is required"}), 400

    if supabase_client:
        try:
            _service_client().table("players").delete().in_("id", player_ids).execute()
            _audit("player.bulk_delete", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="bulk deleted %d player(s)" % len(player_ids))
            return jsonify({"message": f"Successfully deleted {len(player_ids)} players", "count": len(player_ids)})
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503


# MATCHES ENDPOINTS
@app.route("/api/matches", methods=["GET"])
def get_matches():
    sport_id = request.args.get("sport_id")
    gender = request.args.get("gender")
    stage = request.args.get("stage")

    if supabase_client:
        try:
            query = _service_client().table("matches").select(
                "*, sports(name, type), team_a:teams!matches_team_a_id_fkey(*, houses(name, color_hex, short_code)), team_b:teams!matches_team_b_id_fkey(*, houses(name, color_hex, short_code))"
            )
            if sport_id:
                query = query.eq("sport_id", sport_id)
            if gender:
                query = query.eq("gender", gender)
            if stage:
                query = query.eq("stage", stage)
            try:
                res = query.order("created_at").execute()
            except Exception:
                try:
                    res = query.order("id").execute()
                except Exception:
                    res = query.execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/matches", methods=["POST"])
@req_admin_auth
def create_match():
    data = request.get_json() or {}
    sport_id = data.get("sport_id")
    team_a_id = data.get("team_a_id")
    team_b_id = data.get("team_b_id")
    if not sport_id or not team_a_id or not team_b_id:
        return jsonify({"error": "sport_id, team_a_id, and team_b_id are required"}), 400

    record = {
        "id": str(uuid.uuid4()),
        "sport_id": sport_id,
        "team_a_id": team_a_id,
        "team_b_id": team_b_id,
        "gender": data.get("gender", "Boys"),
        "stage": data.get("stage", "league"),
        "level": data.get("level", "HS"),
        "status": data.get("status", "scheduled"),
        "round_info": data.get("round_info", "League Game"),
        "winner_team_id": data.get("winner_team_id"),
        "is_draw": data.get("is_draw", False),
        "score_team_a": data.get("score_team_a", 0),
        "score_team_b": data.get("score_team_b", 0),
        "score_difference": data.get("score_difference", 0),
        "score_summary": data.get("score_summary", "")
    }

    if supabase_client:
        try:
            res = _service_client().table("matches").insert(record).execute()
            _audit("match.create", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="created match %s vs %s (%s)" % (team_a_id, team_b_id, data.get("stage", "league")))
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/matches/<match_id>", methods=["PUT"])
@req_admin_auth
def update_match(match_id):
    data = request.get_json() or {}
    if not isinstance(data, dict):
        data = {}
    data = _whitelist_fields(data, _ALLOWED_MATCH_FIELDS)

    old = None
    if supabase_client:
        try:
            old_res = _service_client().table("matches").select("score_team_a, score_team_b, status").eq("id", match_id).single().execute()
            if isinstance(old_res.data, dict):
                old = old_res.data
        except Exception:
            pass

    # If scores are provided, compute match outcome fields
    if "score_team_a" in data and "score_team_b" in data:
        score_a = int(data["score_team_a"])
        score_b = int(data["score_team_b"])
        data["score_team_a"] = score_a
        data["score_team_b"] = score_b
        data["score_difference"] = abs(score_a - score_b)
        data["score_summary"] = f"{score_a} - {score_b}"
        data["status"] = "completed"

        # Determine winner using existing team_a_id / team_b_id
        team_a_id = data.get("team_a_id")
        team_b_id = data.get("team_b_id")

        if not team_a_id or not team_b_id:
            if supabase_client:
                try:
                    match_res = _service_client().table("matches").select("team_a_id, team_b_id").eq("id", match_id).single().execute()
                    if match_res.data:
                        team_a_id = team_a_id or match_res.data.get("team_a_id")
                        team_b_id = team_b_id or match_res.data.get("team_b_id")
                except Exception:
                    pass
            else:
                return jsonify({"error": "Supabase not configured"}), 503

        if score_a > score_b:
            data["winner_team_id"] = team_a_id
            data["is_draw"] = False
        elif score_b > score_a:
            data["winner_team_id"] = team_b_id
            data["is_draw"] = False
        else:
            data["winner_team_id"] = None
            data["is_draw"] = True

    if supabase_client:
        try:
            res = _service_client().table("matches").update(data).eq("id", match_id).execute()
            details = []
            if "score_team_a" in data and "score_team_b" in data:
                if old is not None:
                    details.append("score %s - %s -> %s - %s" % (
                        old.get("score_team_a", 0), old.get("score_team_b", 0),
                        data["score_team_a"], data["score_team_b"]))
                else:
                    details.append("score set to %s - %s" % (data["score_team_a"], data["score_team_b"]))
            other = [k for k in data if k not in ("score_team_a", "score_team_b", "score_difference",
                                                  "score_summary", "status", "winner_team_id", "is_draw")]
            if other:
                details.append("fields: " + ",".join(sorted(other)))
            _audit("match.update", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="match %s: %s" % (match_id, "; ".join(details) or "updated"))
            return jsonify(res.data[0] if res.data else data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503

@app.route("/api/matches/<match_id>", methods=["DELETE"])
@req_admin_auth
def delete_match(match_id):
    if supabase_client:
        try:
            _service_client().table("matches").delete().eq("id", match_id).execute()
            _audit("match.delete", actor_email=_current_actor(), ip_address=request.remote_addr,
                   details="deleted match %s" % match_id)
            return jsonify({"message": "Match deleted"})
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503


# OVERALL HOUSE STANDINGS API
@app.route("/api/leaderboard/overall", methods=["GET"])
def get_house_overall_standings():
    if supabase_client:
        try:
            res = _service_client().table("house_overall_standings").select("*").execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503


# PER-SPORT STANDINGS API
@app.route("/api/leaderboard", methods=["GET"])
def get_leaderboard():
    sport_id = request.args.get("sport_id")
    gender = request.args.get("gender")

    if supabase_client:
        try:
            query = _service_client().table("leaderboard_view").select("*")
            if sport_id:
                query = query.eq("sport_id", sport_id)
            if gender:
                query = query.eq("gender", gender)
            res = query.execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503


# FINAL QUALIFIERS API
@app.route("/api/leaderboard/qualifiers", methods=["GET"])
def get_final_qualifiers():
    if supabase_client:
        try:
            res = _service_client().table("final_qualifiers_view").select("*").execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": _internal_error(e)}), 500
    return jsonify({"error": "Supabase not configured"}), 503


# OTA UPDATE CHECK FOR ANDROID CLIENT
@app.route("/api/version", methods=["GET"])
def get_version_info():
    return jsonify({
        "version_code": 3,
        "version_name": "2.0.0",
        "min_sdk": 24,
        "apk_url": "https://github.com/scoreboard/scoreboard/releases/latest/download/scoreboard.apk",
        "release_notes": "Inter-House Sports Meet overhaul: House Standings, Squads, and Stage views.",
        "mandatory": False
    })


if __name__ == "__main__":
    _host = os.getenv("HOST", "0.0.0.0")
    _port = int(os.getenv("PORT", "5000"))
    _debug = os.getenv("FLASK_DEBUG", "").strip().lower() in ("1", "true", "yes", "on")
    if _debug and _host in ("0.0.0.0", "::", "::1", ""):
        print("WARNING: FLASK_DEBUG is enabled on a public bind address. The Werkzeug "
              "debugger allows remote code execution â€” never enable it in production.")
    app.run(host=_host, port=_port, debug=_debug, use_reloader=False)
