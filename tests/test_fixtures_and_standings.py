import pytest
from app.app import app

@pytest.fixture
def client():
    app.config['TESTING'] = True
    with app.test_client() as client:
        yield client

def test_matches_60_fixtures_total(client):
    rv = client.get('/api/matches')
    assert rv.status_code == 200
    matches = rv.get_json()
    assert len(matches) == 60

    # Count by sport and gender
    group_counts = {}
    completed_count = 0
    scheduled_count = 0

    for m in matches:
        key = f"{m['gender']} {m['sport_id']}"
        group_counts[key] = group_counts.get(key, 0) + 1
        if m['status'] == 'completed':
            completed_count += 1
        elif m['status'] == 'scheduled':
            scheduled_count += 1

    assert completed_count == 10
    assert scheduled_count == 50

def test_overall_house_standings_and_squad_counts(client):
    rv = client.get('/api/leaderboard/overall')
    assert rv.status_code == 200
    standings = rv.get_json()
    assert len(standings) == 4

    standings_map = {s['house_name']: s for s in standings}

    # Karnali: 18 pts, 6W-0D-2L, 11 squads
    karnali = standings_map['Karnali']
    assert karnali['total_points'] == 18
    assert karnali['total_wins'] == 6
    assert karnali['total_draws'] == 0
    assert karnali['total_losses'] == 2
    assert karnali['total_squads'] == 11
    assert karnali['rank'] == 1

    # Mechi: 12 pts, 4W-0D-0L, 8 squads
    mechi = standings_map['Mechi']
    assert mechi['total_points'] == 12
    assert mechi['total_wins'] == 4
    assert mechi['total_draws'] == 0
    assert mechi['total_losses'] == 0
    assert mechi['total_squads'] == 8
    assert mechi['rank'] == 2

    # Koshi: 0 pts, 0W-0D-4L, 8 squads
    koshi = standings_map['Koshi']
    assert koshi['total_points'] == 0
    assert koshi['total_wins'] == 0
    assert koshi['total_draws'] == 0
    assert koshi['total_losses'] == 4
    assert koshi['total_squads'] == 8

    # Mahakali: 0 pts, 0W-0D-4L, 6 squads
    mahakali = standings_map['Mahakali']
    assert mahakali['total_points'] == 0
    assert mahakali['total_wins'] == 0
    assert mahakali['total_draws'] == 0
    assert mahakali['total_losses'] == 4
    assert mahakali['total_squads'] == 6

def test_squads_33_total(client):
    rv = client.get('/api/teams')
    assert rv.status_code == 200
    teams = rv.get_json()
    assert len(teams) == 33
