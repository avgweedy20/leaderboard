import os
import csv
import io
import uuid
import json
from functools import wraps
from flask import Flask, request, jsonify, render_template, send_from_directory
from flask_cors import CORS
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__, template_folder='templates', static_folder='static')
CORS(app)

# Env config
SUPABASE_URL = os.getenv("SUPABASE_URL", "https://mock.supabase.co")
SUPABASE_SERVICE_ROLE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "mock-service-key")
SUPABASE_ANON_KEY = os.getenv("SUPABASE_ANON_KEY", "mock-anon-key")

supabase_client = None
if SUPABASE_URL != "https://mock.supabase.co" and "mock" not in SUPABASE_SERVICE_ROLE_KEY:
    try:
        from supabase import create_client, Client
        supabase_client: Client = create_client(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
    except Exception as e:
        print(f"Warning: Could not initialize Supabase client: {e}")

# In-memory mock DB fallback for Inter-House Meet
MOCK_DB = {
    "houses": [
        {"id": "h1", "name": "Karnali", "color_hex": "#10B981", "short_code": "KAR"},
        {"id": "h2", "name": "Koshi", "color_hex": "#0EA5E9", "short_code": "KOS"},
        {"id": "h3", "name": "Mahakali", "color_hex": "#8B5CF6", "short_code": "MAH"},
        {"id": "h4", "name": "Mechi", "color_hex": "#F97316", "short_code": "MEC"}
    ],
    "sports": [
        {
            "id": "11111111-1111-1111-1111-111111111111",
            "name": "Futsal",
            "type": "football",
            "level": "HS",
            "point_win": 3,
            "point_draw": 1,
            "point_loss": 0,
            "is_lower_score_better": False
        },
        {
            "id": "22222222-2222-2222-2222-222222222222",
            "name": "Basketball",
            "type": "basketball",
            "level": "HS",
            "point_win": 3,
            "point_draw": 1,
            "point_loss": 0,
            "is_lower_score_better": False
        },
        {
            "id": "33333333-3333-3333-3333-333333333333",
            "name": "Cricksal",
            "type": "generic",
            "level": "HS",
            "point_win": 3,
            "point_draw": 1,
            "point_loss": 0,
            "is_lower_score_better": False
        }
    ],
    "teams": [
        {"id": "t1", "name": "Karnali Boys Futsal A", "house_id": "h1", "gender": "Boys", "squad_label": "A", "sport_id": "11111111-1111-1111-1111-111111111111", "level": "HS"},
        {"id": "t2", "name": "Koshi Boys Futsal A", "house_id": "h2", "gender": "Boys", "squad_label": "A", "sport_id": "11111111-1111-1111-1111-111111111111", "level": "HS"},
        {"id": "t3", "name": "Mahakali Boys Futsal A", "house_id": "h3", "gender": "Boys", "squad_label": "A", "sport_id": "11111111-1111-1111-1111-111111111111", "level": "HS"},
        {"id": "t4", "name": "Mechi Boys Futsal A", "house_id": "h4", "gender": "Boys", "squad_label": "A", "sport_id": "11111111-1111-1111-1111-111111111111", "level": "HS"}
    ],
    "players": [
        {"id": "p1", "name": "Aarav Sharma", "team_id": "t1", "roll_number": "101", "grade": "11", "section": "A", "gender": "Boys", "level": "HS"},
        {"id": "p2", "name": "Bikram Thapa", "team_id": "t2", "roll_number": "102", "grade": "12", "section": "B", "gender": "Boys", "level": "HS"}
    ],
    "matches": [
        {
            "id": "m1",
            "sport_id": "11111111-1111-1111-1111-111111111111",
            "team_a_id": "t1",
            "team_b_id": "t2",
            "gender": "Boys",
            "stage": "league",
            "level": "HS",
            "status": "completed",
            "round_info": "League Game",
            "winner_team_id": "t1",
            "is_draw": False,
            "score_team_a": 6,
            "score_team_b": 1,
            "score_difference": 5,
            "score_summary": "6 - 1"
        }
    ],
    "brackets": [],
    "generic_results": [],
    "football_events": [],
    "basketball_quarters": [],
    "seeder_logs": [
        {
            "id": "log-initial",
            "created_at": "2025-01-01T00:00:00Z",
            "houses_created": 4,
            "sports_created": 3,
            "squads_created": 4,
            "players_created": 2,
            "fixtures_created": 1,
            "unplayed_fixtures": 0,
            "unparseable_fixtures": 0,
            "status": "success",
            "details": "Initial seed complete"
        }
    ]
}

def req_admin_auth(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get("Authorization")
        if not auth_header or not auth_header.startswith("Bearer "):
            return jsonify({"error": "Unauthorized admin access required"}), 401

        token = auth_header.split(" ")[1]

        if supabase_client:
            try:
                user = supabase_client.auth.get_user(token)
                if not user:
                    return jsonify({"error": "Invalid auth token"}), 401
            except Exception as e:
                return jsonify({"error": str(e)}), 401
        else:
            if token != "mock-admin-token":
                return jsonify({"error": "Invalid dev auth token"}), 401

        return f(*args, **kwargs)
    return decorated


# --- ROUTES ---

@app.route("/")
def index_page():
    return render_template("index.html")

@app.route("/api/health", methods=["GET"])
def health_check():
    return jsonify({
        "status": "healthy",
        "supabase_connected": supabase_client is not None,
        "mode": "supabase" if supabase_client else "mock_in_memory"
    })

# AUTH ENDPOINT
@app.route("/api/auth/login", methods=["POST"])
def auth_login():
    data = request.get_json() or {}
    email = data.get("email")
    password = data.get("password")

    if not email or not password:
        return jsonify({"error": "Email and password required"}), 400

    if supabase_client:
        try:
            res = supabase_client.auth.sign_in_with_password({"email": email, "password": password})
            return jsonify({
                "access_token": res.session.access_token,
                "user": {"id": res.user.id, "email": res.user.email}
            })
        except Exception as e:
            return jsonify({"error": str(e)}), 400
    else:
        if email == "admin@scoreboard.com" and password == "admin123":
            return jsonify({
                "access_token": "mock-admin-token",
                "user": {"id": "admin-id-123", "email": "admin@scoreboard.com"}
            })
        return jsonify({"error": "Invalid email or password"}), 401


# HOUSES ENDPOINTS
@app.route("/api/houses", methods=["GET"])
def get_houses():
    if supabase_client:
        try:
            res = supabase_client.table("houses").select("*").order("name").execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500
    return jsonify(MOCK_DB["houses"])


# SPORTS ENDPOINTS
@app.route("/api/sports", methods=["GET"])
def get_sports():
    if supabase_client:
        try:
            res = supabase_client.table("sports").select("*").execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500
    return jsonify(MOCK_DB["sports"])

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
            res = supabase_client.table("sports").insert(record).execute()
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["sports"].append(record)
    return jsonify(record), 201


# TEAMS / SQUADS ENDPOINTS
@app.route("/api/teams", methods=["GET"])
def get_teams():
    sport_id = request.args.get("sport_id")
    house_id = request.args.get("house_id")
    gender = request.args.get("gender")

    if supabase_client:
        try:
            query = supabase_client.table("teams").select("*, houses(name, color_hex, short_code), sports(name)")
            if sport_id:
                query = query.eq("sport_id", sport_id)
            if house_id:
                query = query.eq("house_id", house_id)
            if gender:
                query = query.eq("gender", gender)
            res = query.execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    teams = MOCK_DB["teams"]
    if sport_id:
        teams = [t for t in teams if t.get("sport_id") == sport_id]
    if house_id:
        teams = [t for t in teams if t.get("house_id") == house_id]
    if gender:
        teams = [t for t in teams if t.get("gender") == gender]
    return jsonify(teams)

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
        for h in MOCK_DB["houses"]:
            if h["id"] == house_id: house_name = h["name"]
        for s in MOCK_DB["sports"]:
            if s["id"] == sport_id: sport_name = s["name"]
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
            res = supabase_client.table("teams").insert(record).execute()
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["teams"].append(record)
    return jsonify(record), 201

@app.route("/api/teams/<team_id>", methods=["PUT"])
@req_admin_auth
def update_team(team_id):
    data = request.get_json() or {}
    if supabase_client:
        try:
            res = supabase_client.table("teams").update(data).eq("id", team_id).execute()
            return jsonify(res.data[0] if res.data else data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    for t in MOCK_DB["teams"]:
        if t["id"] == team_id:
            t.update(data)
            return jsonify(t)
    return jsonify({"error": "Team not found"}), 404

@app.route("/api/teams/<team_id>", methods=["DELETE"])
@req_admin_auth
def delete_team(team_id):
    if supabase_client:
        try:
            supabase_client.table("teams").delete().eq("id", team_id).execute()
            return jsonify({"message": "Team deleted"})
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["teams"] = [t for t in MOCK_DB["teams"] if t["id"] != team_id]
    return jsonify({"message": "Team deleted"})


# PLAYERS ENDPOINTS
@app.route("/api/players", methods=["GET"])
def get_players():
    team_id = request.args.get("team_id")

    if supabase_client:
        try:
            query = supabase_client.table("players").select("*, teams(name, gender, squad_label, houses(name, color_hex))")
            if team_id:
                query = query.eq("team_id", team_id)
            res = query.execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    players = MOCK_DB["players"]
    if team_id:
        players = [p for p in players if p.get("team_id") == team_id]
    return jsonify(players)

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
            res = supabase_client.table("players").insert(record).execute()
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["players"].append(record)
    return jsonify(record), 201

@app.route("/api/players/<player_id>", methods=["PUT"])
@req_admin_auth
def update_player(player_id):
    data = request.get_json() or {}
    if supabase_client:
        try:
            res = supabase_client.table("players").update(data).eq("id", player_id).execute()
            return jsonify(res.data[0] if res.data else data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    for p in MOCK_DB["players"]:
        if p["id"] == player_id:
            p.update(data)
            return jsonify(p)
    return jsonify({"error": "Player not found"}), 404

@app.route("/api/players/<player_id>", methods=["DELETE"])
@req_admin_auth
def delete_player(player_id):
    if supabase_client:
        try:
            supabase_client.table("players").delete().eq("id", player_id).execute()
            return jsonify({"message": "Player deleted"})
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["players"] = [p for p in MOCK_DB["players"] if p["id"] != player_id]
    return jsonify({"message": "Player deleted"})

@app.route("/api/players/bulk", methods=["POST"])
@req_admin_auth
def bulk_upsert_players():
    items = request.get_json() or []
    if not isinstance(items, list):
        return jsonify({"error": "Expected a list of player objects"}), 400

    created_count = 0
    updated_count = 0

    if supabase_client:
        try:
            # Upsert using roll_number on conflict
            res = supabase_client.table("players").upsert(items, on_conflict="roll_number").execute()
            return jsonify({"message": f"Successfully processed {len(res.data or items)} players", "count": len(res.data or items)})
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    for item in items:
        roll = item.get("roll_number")
        existing = None
        if roll:
            existing = next((p for p in MOCK_DB["players"] if str(p.get("roll_number")) == str(roll)), None)

        if existing:
            existing.update(item)
            updated_count += 1
        else:
            rec = {
                "id": str(uuid.uuid4()),
                "name": item.get("name"),
                "team_id": item.get("team_id"),
                "roll_number": roll,
                "grade": item.get("grade"),
                "section": item.get("section"),
                "gender": item.get("gender", "Boys"),
                "level": item.get("level", "HS")
            }
            MOCK_DB["players"].append(rec)
            created_count += 1

    return jsonify({"message": f"Bulk import complete: {created_count} created, {updated_count} updated", "created": created_count, "updated": updated_count})


# MATCHES ENDPOINTS
@app.route("/api/matches", methods=["GET"])
def get_matches():
    sport_id = request.args.get("sport_id")
    gender = request.args.get("gender")
    stage = request.args.get("stage")

    if supabase_client:
        try:
            query = supabase_client.table("matches").select("*, sports(name, type)")
            if sport_id:
                query = query.eq("sport_id", sport_id)
            if gender:
                query = query.eq("gender", gender)
            if stage:
                query = query.eq("stage", stage)
            res = query.order("created_at").execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    matches = MOCK_DB["matches"]
    if sport_id:
        matches = [m for m in matches if m.get("sport_id") == sport_id]
    if gender:
        matches = [m for m in matches if m.get("gender") == gender]
    if stage:
        matches = [m for m in matches if m.get("stage") == stage]
    return jsonify(matches)

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
            res = supabase_client.table("matches").insert(record).execute()
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["matches"].append(record)
    return jsonify(record), 201

@app.route("/api/matches/<match_id>", methods=["PUT"])
@req_admin_auth
def update_match(match_id):
    data = request.get_json() or {}

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
            # Look up existing match record to get team IDs
            for m in MOCK_DB["matches"]:
                if m["id"] == match_id:
                    team_a_id = m.get("team_a_id")
                    team_b_id = m.get("team_b_id")
                    break

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
            res = supabase_client.table("matches").update(data).eq("id", match_id).execute()
            return jsonify(res.data[0] if res.data else data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    for m in MOCK_DB["matches"]:
        if m["id"] == match_id:
            m.update(data)
            return jsonify(m)
    return jsonify({"error": "Match not found"}), 404

@app.route("/api/matches/<match_id>", methods=["DELETE"])
@req_admin_auth
def delete_match(match_id):
    if supabase_client:
        try:
            supabase_client.table("matches").delete().eq("id", match_id).execute()
            return jsonify({"message": "Match deleted"})
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["matches"] = [m for m in MOCK_DB["matches"] if m["id"] != match_id]
    return jsonify({"message": "Match deleted"})


# SEEDER LOG ENDPOINTS
@app.route("/api/admin/seeder-logs", methods=["GET"])
def get_seeder_logs():
    if supabase_client:
        try:
            res = supabase_client.table("seeder_logs").select("*").order("created_at", desc=True).limit(10).execute()
            return jsonify(res.data)
        except Exception as e:
            # Fallback to mock log if table does not exist yet
            pass
    return jsonify(sorted(MOCK_DB.get("seeder_logs", []), key=lambda x: x.get("created_at", ""), reverse=True))

@app.route("/api/admin/run-seeder", methods=["POST"])
@req_admin_auth
def run_seeder_endpoint():
    try:
        from seeder import InterHouseSeeder
        import datetime

        excel_path = "seed-data/interhouse_meet.xlsx"
        seeder = InterHouseSeeder(excel_path, supabase_client)
        seeder.run()

        log_entry = {
            "id": str(uuid.uuid4()),
            "created_at": datetime.datetime.utcnow().isoformat() + "Z",
            "houses_created": seeder.stats["houses"]["created"],
            "sports_created": seeder.stats["sports"]["created"],
            "squads_created": seeder.stats["squads"]["created"],
            "players_created": seeder.stats["players"]["created"],
            "fixtures_created": seeder.stats["fixtures"]["created"],
            "unplayed_fixtures": seeder.stats["fixtures"]["unplayed"],
            "unparseable_fixtures": seeder.stats["fixtures"]["unparseable"],
            "status": "success",
            "details": f"Processed {seeder.stats['players']['created']} players, {seeder.stats['fixtures']['created']} fixtures"
        }

        if supabase_client:
            try:
                supabase_client.table("seeder_logs").insert(log_entry).execute()
            except Exception:
                pass

        MOCK_DB.setdefault("seeder_logs", []).append(log_entry)
        return jsonify(log_entry), 200

    except Exception as e:
        error_entry = {
            "id": str(uuid.uuid4()),
            "created_at": datetime.datetime.utcnow().isoformat() + "Z",
            "houses_created": 0,
            "sports_created": 0,
            "squads_created": 0,
            "players_created": 0,
            "fixtures_created": 0,
            "unplayed_fixtures": 0,
            "unparseable_fixtures": 0,
            "status": "error",
            "details": str(e)
        }
        MOCK_DB.setdefault("seeder_logs", []).append(error_entry)
        return jsonify(error_entry), 500


# OVERALL HOUSE STANDINGS API
@app.route("/api/leaderboard/overall", methods=["GET"])
def get_house_overall_standings():
    if supabase_client:
        try:
            res = supabase_client.table("house_overall_standings").select("*").execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    houses = MOCK_DB["houses"]
    standings = []
    for h in houses:
        standings.append({
            "house_id": h["id"],
            "house_name": h["name"],
            "color_hex": h["color_hex"],
            "short_code": h["short_code"],
            "total_squads": 4,
            "matches_played": 1,
            "total_wins": 1 if h["name"] == "Karnali" else 0,
            "total_draws": 0,
            "total_losses": 0 if h["name"] == "Karnali" else 1,
            "total_score_difference": 5 if h["name"] == "Karnali" else -5,
            "total_points": 3 if h["name"] == "Karnali" else 0,
            "rank": 1 if h["name"] == "Karnali" else 2
        })
    standings.sort(key=lambda x: x["total_points"], reverse=True)
    return jsonify(standings)


# PER-SPORT STANDINGS API
@app.route("/api/leaderboard", methods=["GET"])
def get_leaderboard():
    sport_id = request.args.get("sport_id")
    gender = request.args.get("gender")

    if supabase_client:
        try:
            query = supabase_client.table("leaderboard_view").select("*")
            if sport_id:
                query = query.eq("sport_id", sport_id)
            if gender:
                query = query.eq("gender", gender)
            res = query.execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    teams = MOCK_DB["teams"]
    sports = {s["id"]: s for s in MOCK_DB["sports"]}
    houses = {h["id"]: h for h in MOCK_DB["houses"]}
    matches = MOCK_DB["matches"]

    results = []
    for t in teams:
        if sport_id and t["sport_id"] != sport_id:
            continue
        if gender and t.get("gender") != gender:
            continue

        sport = sports.get(t["sport_id"], {"name": "Futsal", "type": "football"})
        house = houses.get(t["house_id"], {"name": "Karnali", "color_hex": "#10B981", "short_code": "KAR"})

        played, wins, draws, losses, pts, diff = 0, 0, 0, 0, 0, 0
        for m in matches:
            if m.get("status") != "completed":
                continue
            if m["team_a_id"] == t["id"]:
                played += 1
                if m.get("is_draw"): draws += 1
                elif m.get("winner_team_id") == t["id"]: wins += 1; pts += 3
                else: losses += 1
                diff += (m.get("score_team_a", 0) - m.get("score_team_b", 0))
            elif m["team_b_id"] == t["id"]:
                played += 1
                if m.get("is_draw"): draws += 1
                elif m.get("winner_team_id") == t["id"]: wins += 1; pts += 3
                else: losses += 1
                diff += (m.get("score_team_b", 0) - m.get("score_team_a", 0))

        results.append({
            "team_id": t["id"],
            "team_name": t["name"],
            "house_id": t["house_id"],
            "house_name": house["name"],
            "house_color": house["color_hex"],
            "house_short_code": house["short_code"],
            "gender": t.get("gender", "Boys"),
            "squad_label": t.get("squad_label", "A"),
            "sport_id": t["sport_id"],
            "sport_name": sport["name"],
            "sport_type": sport["type"],
            "played": played,
            "wins": wins,
            "draws": draws,
            "losses": losses,
            "score_difference": diff,
            "points": pts,
            "rank": 1
        })

    results.sort(key=lambda x: (x["points"], x["score_difference"]), reverse=True)
    for idx, r in enumerate(results, start=1):
        r["rank"] = idx
    return jsonify(results)


# FINAL QUALIFIERS API
@app.route("/api/leaderboard/qualifiers", methods=["GET"])
def get_final_qualifiers():
    if supabase_client:
        try:
            res = supabase_client.table("final_qualifiers_view").select("*").execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    # In mock mode return top 2 squads per sport+gender
    all_standings = json.loads(get_leaderboard().data)
    qualifiers = [r for r in all_standings if r.get("rank", 99) <= 2]
    return jsonify(qualifiers)


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
    app.run(host="0.0.0.0", port=5000, debug=True)
