import pytest
import json
import io
from app.app import app, MOCK_DB

@pytest.fixture
def client():
    app.config['TESTING'] = True
    with app.test_client() as client:
        yield client

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
    headers = {"Authorization": "Bearer mock-admin-token"}
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
        "email": "admin@scoreboard.com",
        "password": "admin123"
    })
    assert rv.status_code == 200
    json_data = rv.get_json()
    assert "access_token" in json_data

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
    headers = {"Authorization": "Bearer mock-admin-token"}

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

def test_matches_and_seeder_logs(client):
    headers = {"Authorization": "Bearer mock-admin-token"}

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

    # Fetch Seeder Logs
    logs_res = client.get('/api/admin/seeder-logs')
    assert logs_res.status_code == 200
    assert isinstance(logs_res.get_json(), list)

    # Delete Match
    del_res = client.delete(f'/api/matches/{match_id}', headers=headers)
    assert del_res.status_code == 200
