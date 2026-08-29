import os
import tempfile
import pytest
import json
import io

# Isolate the admin account store to a temporary SQLite database.
_ADMIN_TEST_DIR = tempfile.mkdtemp(prefix="scoreboard_admin_test_")
os.environ["ADMIN_DB_PATH"] = os.path.join(_ADMIN_TEST_DIR, "admins.db")

from app.app import app, MOCK_DB, add_admin, remove_admin, _clear_all_sessions, _login_failures

ADMIN1 = "admin1@school.edu"
ADMIN2 = "admin2@school.edu"
ADMIN3 = "admin3@school.edu"
ADMIN_PASSWORD = "Str0ng-Adm1n-Pass!42"

@pytest.fixture(scope="session", autouse=True)
def seed_admins():
    add_admin(ADMIN1, ADMIN_PASSWORD)
    add_admin(ADMIN2, ADMIN_PASSWORD)

@pytest.fixture(autouse=True)
def reset_auth_state():
    _clear_all_sessions()
    _login_failures.clear()
    yield
    _clear_all_sessions()
    _login_failures.clear()

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

    remove_admin(ADMIN3)
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
    for _ in range(5):
        rv = client.post('/api/auth/login', json={
            "email": ADMIN1, "password": ADMIN_PASSWORD
        })
        assert rv.status_code == 200
        headers.append(rv.get_json()["access_token"])
    # Sixth concurrent session for the same account is refused.
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