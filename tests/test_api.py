import pytest
import json
import io
import requests

from app.app import (
    app,
    supabase_client,
    add_admin,
    remove_admin,
    _auth_user_id_by_email,
    _clear_all_sessions,
    _login_failures,
    _login_failures_ip,
    _LOGIN_MAX_IP_ATTEMPTS,
)

ADMIN1 = "admin1@school.edu"
ADMIN2 = "admin2@school.edu"
ADMIN3 = "admin3@school.edu"
ADMIN4 = "admin4@school.edu"
ADMIN_PASSWORD = "Str0ng-Adm1n-Pass!42"

@pytest.fixture(scope="session", autouse=True)
def seed_admins():
    # ADMIN1 is the super admin; ADMIN2 is a plain admin.
    add_admin(ADMIN1, ADMIN_PASSWORD, role="superadmin")
    add_admin(ADMIN2, ADMIN_PASSWORD)

@pytest.fixture(autouse=True)
def reset_auth_state():
    _clear_all_sessions()
    _login_failures.clear()
    _login_failures_ip.clear()
    yield
    _clear_all_sessions()
    _login_failures.clear()
    _login_failures_ip.clear()

@pytest.fixture
def client():
    app.config['TESTING'] = True
    with app.test_client() as client:
        yield client

def _admin_headers(client, email=ADMIN1, password=ADMIN_PASSWORD):
    rv = client.post('/api/auth/login', json={"email": email, "password": password})
    assert rv.status_code == 200, rv.get_json()
    token = rv.get_json()["access_token"]
    return {"Authorization": f"Bearer {token}"}

def test_health_check(client):
    rv = client.get('/api/health')
    json_data = rv.get_json()
    assert rv.status_code == 200
    assert json_data['status'] == 'healthy'

def test_get_houses(client):
    rv = client.get('/api/houses')
    json_data = rv.get_json()
    assert rv.status_code == 200
    assert len(json_data) == 4

def test_get_sports(client):
    rv = client.get('/api/sports')
    json_data = rv.get_json()
    assert rv.status_code == 200
    assert len(json_data) >= 1

def test_create_sport_unauthorized(client):
    rv = client.post('/api/sports', json={"name": "Tennis"})
    assert rv.status_code == 401

def test_create_sport_authorized(client):
    headers = _admin_headers(client)
    rv = client.post('/api/sports', json={
        "name": "Badminton",
        "type": "generic",
        "level": "HS",
        "point_win": 3,
        "point_draw": 1,
        "point_loss": 0
    }, headers=headers)
    assert rv.status_code == 201
    json_data = rv.get_json()
    assert json_data['name'] == "Badminton"

def test_auth_login(client):
    rv = client.post('/api/auth/login', json={
        "email": ADMIN1,
        "password": ADMIN_PASSWORD
    })
    assert rv.status_code == 200
    json_data = rv.get_json()
    assert "access_token" in json_data
    assert json_data["expires_in"] == 21600

def test_auth_login_wrong_password(client):
    rv = client.post('/api/auth/login', json={
        "email": ADMIN1,
        "password": "wrong-password"
    })
    assert rv.status_code == 401

def test_auth_login_unknown_account(client):
    rv = client.post('/api/auth/login', json={
        "email": "noone@school.edu",
        "password": ADMIN_PASSWORD
    })
    assert rv.status_code == 401

def test_multiple_admins_can_login(client):
    h1 = _admin_headers(client, ADMIN1)
    h2 = _admin_headers(client, ADMIN2)
    r1 = client.post('/api/sports', json={
        "name": "Cricket2", "type": "generic", "level": "HS",
        "point_win": 3, "point_draw": 1, "point_loss": 0
    }, headers=h1)
    r2 = client.post('/api/sports', json={
        "name": "Volleyball", "type": "generic", "level": "HS",
        "point_win": 3, "point_draw": 1, "point_loss": 0
    }, headers=h2)
    assert r1.status_code == 201
    assert r2.status_code == 201

def test_remove_admin_revokes_sessions(client):
    add_admin(ADMIN3, ADMIN_PASSWORD)
    headers = _admin_headers(client, ADMIN3)
    assert client.post('/api/sports', json={
        "name": "Athletics", "type": "generic", "level": "HS",
        "point_win": 3, "point_draw": 1, "point_loss": 0
    }, headers=headers).status_code == 201

    assert _auth_user_id_by_email(ADMIN3) is not None
    remove_admin(ADMIN3)
    # The underlying Auth account is deleted entirely, not just the admins row.
    assert _auth_user_id_by_email(ADMIN3) is None
    # Sessions of that admin are revoked immediately.
    assert client.post('/api/sports', json={
        "name": "Athletics2", "type": "generic", "level": "HS",
        "point_win": 3, "point_draw": 1, "point_loss": 0
    }, headers=headers).status_code == 401
    # And login no longer works.
    assert client.post('/api/auth/login', json={
        "email": ADMIN3, "password": ADMIN_PASSWORD
    }).status_code == 401

def test_add_admin_rejects_weak_password():
    with pytest.raises(ValueError):
        add_admin("weak@school.edu", "short")

def test_forged_static_token_rejected(client):
    """The old hardcoded 'mock-admin-token' must never be accepted again."""
    headers = {"Authorization": "Bearer mock-admin-token"}
    rv = client.post('/api/sports', json={"name": "Tennis"}, headers=headers)
    assert rv.status_code == 401

def test_random_token_rejected(client):
    headers = {"Authorization": "Bearer definitely-not-issued-by-login"}
    rv = client.post('/api/teams', json={}, headers=headers)
    assert rv.status_code == 401

def test_malformed_bearer_rejected(client):
    rv = client.post('/api/sports', json={"name": "Tennis"}, headers={"Authorization": "Bearer"})
    assert rv.status_code == 401
    rv = client.post('/api/sports', json={"name": "Tennis"}, headers={"Authorization": "Basic abc"})
    assert rv.status_code == 401

def test_auth_me_and_logout(client):
    headers = _admin_headers(client)
    token = headers["Authorization"].split(" ")[1]

    rv = client.get('/api/auth/me', headers=headers)
    assert rv.status_code == 200
    assert rv.get_json()["email"] == ADMIN1

    rv = client.post('/api/auth/logout', headers=headers)
    assert rv.status_code == 200
    rv = client.post('/api/sports', json={"name": "Table Tennis"}, headers=headers)
    assert rv.status_code == 401

def test_session_cap_per_admin(client):
    headers = []
    for _ in range(20):
        rv = client.post('/api/auth/login', json={
            "email": ADMIN1, "password": ADMIN_PASSWORD
        })
        assert rv.status_code == 200
        headers.append(rv.get_json()["access_token"])
    # 21st concurrent session for the same account is refused.
    rv = client.post('/api/auth/login', json={
        "email": ADMIN1, "password": ADMIN_PASSWORD
    })
    assert rv.status_code == 429

def test_login_rate_limit_blocks_after_failures(client):
    codes = []
    for _ in range(6):
        codes.append(client.post('/api/auth/login', json={
            "email": ADMIN1, "password": "bad-password"
        }).status_code)
    assert codes == [401, 401, 401, 401, 401, 429]

def test_get_house_overall_standings(client):
    rv = client.get('/api/leaderboard/overall')
    assert rv.status_code == 200
    json_data = rv.get_json()
    assert len(json_data) == 4
    assert json_data[0]['house_name'] == 'Karnali'

def test_get_leaderboard(client):
    rv = client.get('/api/leaderboard')
    assert rv.status_code == 200
    assert isinstance(rv.get_json(), list)

def test_version_ota_endpoint(client):
    rv = client.get('/api/version')
    assert rv.status_code == 200
    json_data = rv.get_json()
    assert "version_code" in json_data

def test_team_crud_and_bulk(client):
    headers = _admin_headers(client)

    # Create Team
    res = client.post('/api/teams', json={
        "house_id": "h1",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "gender": "Boys",
        "squad_label": "B"
    }, headers=headers)
    assert res.status_code == 201
    team_data = res.get_json()
    team_id = team_data["id"]

    # Create Player
    p_res = client.post('/api/players', json={
        "name": "Test Player",
        "team_id": team_id,
        "roll_number": "999",
        "grade": "10",
        "section": "A",
        "gender": "Boys"
    }, headers=headers)
    assert p_res.status_code == 201
    player_id = p_res.get_json()["id"]

    # Bulk Upsert Players
    bulk_res = client.post('/api/players/bulk', json=[
        {"name": "Test Player Updated", "team_id": team_id, "roll_number": "999", "grade": "10", "section": "B", "gender": "Boys"},
        {"name": "New Bulk Player", "team_id": team_id, "roll_number": "888", "grade": "11", "section": "C", "gender": "Boys"}
    ], headers=headers)
    assert bulk_res.status_code == 200

    # Delete Player
    del_p = client.delete(f'/api/players/{player_id}', headers=headers)
    assert del_p.status_code == 200

    # Delete Team
    del_t = client.delete(f'/api/teams/{team_id}', headers=headers)
    assert del_t.status_code == 200

def test_matches_flow(client):
    headers = _admin_headers(client)

    # Create Match
    m_res = client.post('/api/matches', json={
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t1",
        "team_b_id": "t2",
        "gender": "Boys",
        "stage": "league"
    }, headers=headers)
    assert m_res.status_code == 201
    match_id = m_res.get_json()["id"]

    # Update Match score
    u_res = client.put(f'/api/matches/{match_id}', json={
        "score_team_a": 3,
        "score_team_b": 1
    }, headers=headers)
    assert u_res.status_code == 200
    assert u_res.get_json()["winner_team_id"] == "t1"
    assert u_res.get_json()["score_summary"] == "3 - 1"

    # Delete Match
    del_res = client.delete(f'/api/matches/{match_id}', headers=headers)
    assert del_res.status_code == 200


def test_unconfigured_supabase_fails_closed(client):
    """With no Supabase client every data/admin endpoint must return 503."""
    import app.app as app_module
    saved = app_module.supabase_client
    try:
        app_module.supabase_client = None

        assert client.get('/api/houses').status_code == 503
        assert client.get('/api/sports').status_code == 503
        assert client.get('/api/teams').status_code == 503
        assert client.get('/api/players').status_code == 503
        assert client.get('/api/matches').status_code == 503
        assert client.get('/api/leaderboard/overall').status_code == 503
        assert client.get('/api/leaderboard').status_code == 503
        assert client.get('/api/leaderboard/qualifiers').status_code == 503
        rv = client.post('/api/auth/login', json={
            "email": ADMIN1, "password": ADMIN_PASSWORD
        })
        assert rv.status_code == 503
        rv = client.get('/api/health')
        assert rv.status_code == 200
        assert rv.get_json()["supabase_connected"] is False
    finally:
        app_module.supabase_client = saved


def test_health_mode_reports_supabase(client):
    rv = client.get('/api/health')
    assert rv.status_code == 200
    assert rv.get_json()["mode"] == "supabase"
    assert rv.get_json()["supabase_connected"] is True


def test_admin_api_requires_auth(client):
    assert client.get('/api/admin/list').status_code == 401
    assert client.post('/api/admin/add', json={
        "email": ADMIN4, "password": ADMIN_PASSWORD
    }).status_code == 401
    assert client.post('/api/admin/remove', json={"email": ADMIN4}).status_code == 401
    assert client.post('/api/admin/reset-password', json={
        "email": ADMIN2, "password": ADMIN_PASSWORD
    }).status_code == 401
    assert client.get('/api/admin/log').status_code == 401


def test_admin_add_list_remove_flow(client):
    headers = _admin_headers(client)

    rv = client.post('/api/admin/add', json={
        "email": ADMIN4, "password": ADMIN_PASSWORD
    }, headers=headers)
    assert rv.status_code == 201

    rv = client.get('/api/admin/list', headers=headers)
    assert rv.status_code == 200
    emails = [a["email"] for a in rv.get_json()["admins"]]
    assert ADMIN4.lower() in emails

    rv = client.post('/api/admin/remove', json={"email": ADMIN4}, headers=headers)
    assert rv.status_code == 200

    rv = client.get('/api/admin/list', headers=headers)
    emails = [a["email"] for a in rv.get_json()["admins"]]
    assert ADMIN4.lower() not in emails

    # The removed admin can no longer log in.
    assert client.post('/api/auth/login', json={
        "email": ADMIN4, "password": ADMIN_PASSWORD
    }).status_code == 401


def test_admin_cannot_remove_self_or_last(client):
    headers = _admin_headers(client)
    rv = client.post('/api/admin/remove', json={"email": ADMIN1}, headers=headers)
    assert rv.status_code == 400

    # Reduce the roster to a single admin, then attempt to remove that last one.
    add_admin(ADMIN2, ADMIN_PASSWORD)
    rv = client.post('/api/admin/remove', json={"email": ADMIN2}, headers=headers)
    assert rv.status_code == 200
    add_admin(ADMIN3, ADMIN_PASSWORD)
    rv = client.post('/api/admin/remove', json={"email": ADMIN3}, headers=headers)
    assert rv.status_code == 200

    rv = client.post('/api/admin/remove', json={"email": ADMIN1}, headers=headers)
    assert rv.status_code == 400

    # Restore the roster for downstream tests.
    add_admin(ADMIN2, ADMIN_PASSWORD)
    add_admin(ADMIN3, ADMIN_PASSWORD)


def test_admin_add_rejects_bad_input(client):
    headers = _admin_headers(client)
    rv = client.post('/api/admin/add', json={"email": "not-an-email", "password": ADMIN_PASSWORD}, headers=headers)
    assert rv.status_code == 400
    rv = client.post('/api/admin/add', json={"email": ADMIN4, "password": "short"}, headers=headers)
    assert rv.status_code == 400


def test_admin_reset_password_flow(client):
    headers = _admin_headers(client)
    new_password = "Br4nd-N3w-Pass!77"
    rv = client.post('/api/admin/reset-password', json={
        "email": ADMIN2, "password": new_password
    }, headers=headers)
    assert rv.status_code == 200

    assert client.post('/api/auth/login', json={
        "email": ADMIN2, "password": ADMIN_PASSWORD
    }).status_code == 401
    assert client.post('/api/auth/login', json={
        "email": ADMIN2, "password": new_password
    }).status_code == 200

    # Restore the original password and revoke the recent session.
    rv = client.post('/api/admin/reset-password', json={
        "email": ADMIN2, "password": ADMIN_PASSWORD
    }, headers=headers)
    assert rv.status_code == 200
    _clear_all_sessions()


def test_admin_audit_log_accessible(client):
    headers = _admin_headers(client)
    rv = client.get('/api/admin/log', headers=headers)
    assert rv.status_code == 200
    data = rv.get_json()
    assert isinstance(data.get("entries"), list)
    assert data["total"] >= 1
    actions = {row["action"] for row in data["entries"]}
    assert "login" in actions


def test_admin_audit_log_filterable(client):
    headers = _admin_headers(client)
    rv = client.get('/api/admin/log?action=login', headers=headers)
    assert rv.status_code == 200
    data = rv.get_json()
    assert data["total"] >= 1
    assert all(row["action"] == "login" for row in data["entries"])

    rv = client.get('/api/admin/log?action=match.create&actor=nobody@nowhere.invalid', headers=headers)
    assert rv.status_code == 200
    assert rv.get_json()["total"] == 0

    rv = client.get('/api/admin/log?from=not-a-date', headers=headers)
    assert rv.status_code == 400


def test_mutations_are_audited(client):
    headers = _admin_headers(client)
    assert client.post('/api/sports', json={
        "name": "AuditedSport", "type": "generic", "level": "HS",
        "point_win": 3, "point_draw": 1, "point_loss": 0
    }, headers=headers).status_code == 201
    rv = client.get('/api/admin/log?action=sport.create&details=AuditedSport', headers=headers)
    assert rv.status_code == 200
    assert rv.get_json()["total"] >= 1
    assert all(("AuditedSport" in (e.get("details") or "")) for e in rv.get_json()["entries"])


def test_plain_admin_cannot_access_superadmin_endpoints(client):
    headers = _admin_headers(client, ADMIN2)

    assert client.get('/api/admin/list', headers=headers).status_code == 403
    assert client.post('/api/admin/add', json={
        "email": ADMIN4, "password": ADMIN_PASSWORD, "role": "admin"
    }, headers=headers).status_code == 403
    assert client.post('/api/admin/remove', json={"email": ADMIN1}, headers=headers).status_code == 403
    assert client.get('/api/admin/log', headers=headers).status_code == 403

    # Plain admins cannot reset passwords at all — not someone else's...
    rv = client.post('/api/admin/reset-password', json={
        "email": ADMIN1, "password": ADMIN_PASSWORD
    }, headers=headers)
    assert rv.status_code == 403
    # ...and not even their own.
    rv = client.post('/api/admin/reset-password', json={
        "email": ADMIN2, "password": ADMIN_PASSWORD
    }, headers=headers)
    assert rv.status_code == 403


def test_login_returns_role(client):
    rv = client.post('/api/auth/login', json={
        "email": ADMIN1, "password": ADMIN_PASSWORD
    })
    assert rv.status_code == 200
    user = rv.get_json()["user"]
    assert user["role"] == "superadmin"

    # ADMIN2's sessions from _admin_headers in other tests are cleared by the
    # autouse reset fixture; a fresh login here must not exceed the cap.
    _clear_all_sessions()
    rv = client.post('/api/auth/login', json={
        "email": ADMIN2, "password": ADMIN_PASSWORD
    })
    assert rv.status_code == 200
    assert rv.get_json()["user"]["role"] == "admin"


def test_superadmin_add_remove_superadmin(client):
    headers = _admin_headers(client)  # ADMIN1 (super admin)
    rv = client.post('/api/admin/add', json={
        "email": ADMIN4, "password": ADMIN_PASSWORD, "role": "superadmin"
    }, headers=headers)
    assert rv.status_code == 201

    rv = client.get('/api/admin/list', headers=headers)
    assert rv.status_code == 200
    roles = {a["email"]: a.get("role") for a in rv.get_json()["admins"]}
    assert roles[ADMIN4.lower()] == "superadmin"
    assert roles[ADMIN1.lower()] == "superadmin"
    assert roles[ADMIN2.lower()] == "admin"

    # Two super admins exist: removing one is fine.
    rv = client.post('/api/admin/remove', json={"email": ADMIN4}, headers=headers)
    assert rv.status_code == 200


def test_invalid_role_rejected(client):
    headers = _admin_headers(client)
    rv = client.post('/api/admin/add', json={
        "email": ADMIN4, "password": ADMIN_PASSWORD, "role": "root"
    }, headers=headers)
    assert rv.status_code == 400


def test_auth_me_returns_role(client):
    headers = _admin_headers(client)
    rv = client.get('/api/auth/me', headers=headers)
    assert rv.status_code == 200
    assert rv.get_json()["role"] == "superadmin"


def test_list_admins_includes_role(client):
    headers = _admin_headers(client)
    rv = client.get('/api/admin/list', headers=headers)
    assert rv.status_code == 200
    roles = {a["email"]: a["role"] for a in rv.get_json()["admins"]}
    assert roles[ADMIN1.lower()] == "superadmin"
    assert roles[ADMIN2.lower()] == "admin"


def test_admin_page_renders_role_gated_markup(client):
    rv = client.get('/admin')
    assert rv.status_code == 200
    html = rv.get_data(as_text=True)
    for marker in ('data-superadmin-only', 'adminAuditFilterAction', 'adminAuditFilterActor',
                   'adminAuditFilterTarget', 'adminAuditFilterDetails', 'adminAuditFilterFrom',
                   'adminAuditFilterTo', 'adminAuditLogPager', 'newAdminRole', 'data-role-badge',
                   'addAdminModal', 'resetPasswordModal'):
        assert marker in html


# ─── SECURITY REGRESSION TESTS ──────────────────────────────────────────────

def test_security_headers_present(client):
    rv = client.get('/')
    assert rv.status_code == 200
    assert rv.headers.get('Content-Security-Policy')
    assert rv.headers.get('X-Content-Type-Options') == 'nosniff'
    assert rv.headers.get('X-Frame-Options') == 'DENY'
    assert 'no-referrer' in (rv.headers.get('Referrer-Policy') or '')
    assert "object-src 'none'" in rv.headers.get('Content-Security-Policy')
    assert 'frame-ancestors' in rv.headers.get('Content-Security-Policy')
    csp = rv.headers.get('Content-Security-Policy')
    assert 'https://va.vercel-scripts.com' in csp  # Speed Insights script + beacon
    assert 'https://cdn.tailwindcss.com' in csp


def test_api_responses_not_cached(client):
    rv = client.get('/api/health')
    assert rv.headers.get('Cache-Control') == 'no-store'


def test_hsts_only_over_https(client):
    rv_http = client.get('/')
    assert not rv_http.headers.get('Strict-Transport-Security')
    rv_https = client.get('/', environ_overrides={'wsgi.url_scheme': 'https'})
    assert (rv_https.headers.get('Strict-Transport-Security') or '').startswith('max-age=')


def test_standings_slug_reflected_xss_blocked(client):
    payload = 'futsal";alert(1)'
    rv = client.get('/standings/' + requests.utils.quote(payload, safe=''))
    assert rv.status_code == 200
    body = rv.get_data(as_text=True)
    # The payload must never survive to the page unescaped: the route rejects
    # non-plain slugs, so the page falls back to "all sports" and no `alert` runs.
    assert 'const currentSportSlug = "";' in body
    assert 'alert(1)' not in body


def test_update_team_mass_assignment_blocked(client):
    headers = _admin_headers(client)
    rv = client.post('/api/teams', json={
        "house_id": "h1",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "gender": "Boys",
        "squad_label": "A"
    }, headers=headers)
    assert rv.status_code == 201
    team_id = rv.get_json()["id"]

    # An admin can rename the squad, but cannot overwrite the server-generated
    # row id with an arbitrary value (mass-assignment is blocked).
    rv = client.put(f'/api/teams/{team_id}', json={
        "name": "Renamed",
        "id": "attacker-controlled-id",
        "squad_label": "Z"
    }, headers=headers)
    assert rv.status_code == 200

    teams = client.get('/api/teams').get_json()
    updated = next(t for t in teams if t["id"] == team_id)
    assert updated["name"] == "Renamed"
    assert updated["squad_label"] == "Z"
    assert updated["id"] == team_id

    # Clean up so shared seed data stays untouched for later test files.
    assert client.delete(f'/api/teams/{team_id}', headers=headers).status_code == 200


def test_login_blocked_by_ip_spraying(client):
    codes = []
    for _ in range(_LOGIN_MAX_IP_ATTEMPTS + 1):
        codes.append(client.post('/api/auth/login', json={
            "email": "spray%02d@school.edu" % _, "password": "bad-password"
        }).status_code)
    assert codes[:20].count(401) == 20
    assert codes[20] == 429