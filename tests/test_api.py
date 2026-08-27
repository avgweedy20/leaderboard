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
