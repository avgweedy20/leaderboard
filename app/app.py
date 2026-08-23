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

# Try initializing Supabase Python Client
supabase_client = None
if SUPABASE_URL != "https://mock.supabase.co" and "mock" not in SUPABASE_SERVICE_ROLE_KEY:
    try:
        from supabase import create_client, Client
        supabase_client: Client = create_client(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
    except Exception as e:
        print(f"Warning: Could not initialize Supabase client: {e}")

# In-memory mock DB fallback for development/testing when Supabase is not attached
MOCK_DB = {
    "sports": [
        {
            "id": "11111111-1111-1111-1111-111111111111",
            "name": "Cricket",
            "type": "cricket",
            "level": "ALL",
            "point_win": 2,
            "point_draw": 1,
            "point_loss": 0,
            "is_lower_score_better": False
        },
        {
            "id": "22222222-2222-2222-2222-222222222222",
            "name": "Football",
            "type": "football",
            "level": "ALL",
            "point_win": 3,
            "point_draw": 1,
            "point_loss": 0,
            "is_lower_score_better": False
        },
        {
            "id": "33333333-3333-3333-3333-333333333333",
            "name": "Basketball",
            "type": "basketball",
            "level": "ALL",
            "point_win": 2,
            "point_draw": 0,
            "point_loss": 0,
            "is_lower_score_better": False
        },
        {
            "id": "44444444-4444-4444-4444-444444444444",
            "name": "100m Dash",
            "type": "generic",
            "level": "ALL",
            "point_win": 10,
            "point_draw": 5,
            "point_loss": 1,
            "is_lower_score_better": True
        }
    ],
    "teams": [
        {"id": "t1", "name": "Lions", "sport_id": "11111111-1111-1111-1111-111111111111", "level": "HS"},
        {"id": "t2", "name": "Tigers", "sport_id": "11111111-1111-1111-1111-111111111111", "level": "HS"},
        {"id": "t3", "name": "Eagles", "sport_id": "22222222-2222-2222-2222-222222222222", "level": "HS"},
        {"id": "t4", "name": "Hawks", "sport_id": "22222222-2222-2222-2222-222222222222", "level": "HS"}
    ],
    "players": [
        {"id": "p1", "name": "John Doe", "team_id": "t1", "grade": "11", "level": "HS"},
        {"id": "p2", "name": "Jane Smith", "team_id": "t2", "grade": "12", "level": "HS"}
    ],
    "matches": [
        {
            "id": "m1",
            "sport_id": "22222222-2222-2222-2222-222222222222",
            "team_a_id": "t3",
            "team_b_id": "t4",
            "level": "HS",
            "status": "completed",
            "round_info": "Finals",
            "winner_team_id": "t3",
            "is_draw": False,
            "score_summary": "3 - 1"
        }
    ],
    "brackets": [],
    "generic_results": [],
    "cricket_innings": [],
    "cricket_overs": [],
    "football_events": [],
    "basketball_quarters": []
}

def req_admin_auth(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get("Authorization")
        if not auth_header or not auth_header.startswith("Bearer "):
            # If supabase_client is enabled, strictly check auth header
            # For testing/demo fallback if header is 'Bearer mock-admin-token' or header present
            if auth_header and "mock-admin-token" in auth_header:
                return f(*args, **kwargs)
            return jsonify({"error": "Unauthorized admin access required"}), 401

        token = auth_header.split(" ")[1]
        if supabase_client:
            try:
                user = supabase_client.auth.get_user(token)
                if not user:
                    return jsonify({"error": "Invalid auth token"}), 401
            except Exception as e:
                # Fallback for dev token if needed
                if token != "mock-admin-token":
                    return jsonify({"error": str(e)}), 401
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
        # Mock login for testing/demo
        if email == "admin@scoreboard.com" and password == "admin123":
            return jsonify({
                "access_token": "mock-admin-token",
                "user": {"id": "admin-id-123", "email": "admin@scoreboard.com"}
            })
        return jsonify({"error": "Invalid email or password"}), 401


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
    level = data.get("level", "ALL")
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


# TEAMS ENDPOINTS
@app.route("/api/teams", methods=["GET"])
def get_teams():
    sport_id = request.args.get("sport_id")
    level = request.args.get("level")

    if supabase_client:
        try:
            query = supabase_client.table("teams").select("*, sports(name)")
            if sport_id:
                query = query.eq("sport_id", sport_id)
            if level and level != "ALL":
                query = query.eq("level", level)
            res = query.execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    teams = MOCK_DB["teams"]
    if sport_id:
        teams = [t for t in teams if t.get("sport_id") == sport_id]
    if level and level != "ALL":
        teams = [t for t in teams if t.get("level") == level]
    return jsonify(teams)

@app.route("/api/teams", methods=["POST"])
@req_admin_auth
def create_team():
    data = request.get_json() or {}
    name = data.get("name")
    sport_id = data.get("sport_id")
    level = data.get("level", "HS")

    if not name or not sport_id:
        return jsonify({"error": "Team name and sport_id required"}), 400

    record = {
        "id": str(uuid.uuid4()),
        "name": name,
        "sport_id": sport_id,
        "level": level
    }

    if supabase_client:
        try:
            res = supabase_client.table("teams").insert(record).execute()
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["teams"].append(record)
    return jsonify(record), 201


# PLAYERS & CSV IMPORT
@app.route("/api/players", methods=["GET"])
def get_players():
    team_id = request.args.get("team_id")
    level = request.args.get("level")

    if supabase_client:
        try:
            query = supabase_client.table("players").select("*, teams(name)")
            if team_id:
                query = query.eq("team_id", team_id)
            if level and level != "ALL":
                query = query.eq("level", level)
            res = query.execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    players = MOCK_DB["players"]
    if team_id:
        players = [p for p in players if p.get("team_id") == team_id]
    if level and level != "ALL":
        players = [p for p in players if p.get("level") == level]
    return jsonify(players)

@app.route("/api/players", methods=["POST"])
@req_admin_auth
def create_player():
    data = request.get_json() or {}
    name = data.get("name")
    team_id = data.get("team_id")
    grade = data.get("grade", "")
    level = data.get("level", "HS")

    if not name or not team_id:
        return jsonify({"error": "Player name and team_id required"}), 400

    record = {
        "id": str(uuid.uuid4()),
        "name": name,
        "team_id": team_id,
        "grade": grade,
        "level": level
    }

    if supabase_client:
        try:
            res = supabase_client.table("players").insert(record).execute()
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["players"].append(record)
    return jsonify(record), 201


@app.route("/api/players/import/preview", methods=["POST"])
@req_admin_auth
def import_players_preview():
    if "file" not in request.files:
        return jsonify({"error": "CSV file is required"}), 400

    file = request.files["file"]
    stream = io.StringIO(file.stream.read().decode("utf-8"), newline=None)
    csv_reader = csv.DictReader(stream)

    valid_rows = []
    errors = []

    for index, row in enumerate(csv_reader, start=1):
        player_name = row.get("player_name", "").strip()
        team_name = row.get("team_name", "").strip()
        sport_name = row.get("sport_name", "").strip()
        grade = row.get("grade", "").strip()
        level = row.get("level", "HS").strip().upper()

        if level not in ["ES", "MS", "HS"]:
            level = "HS"

        row_errors = []
        if not player_name:
            row_errors.append("Missing player_name")
        if not team_name:
            row_errors.append("Missing team_name")
        if not sport_name:
            row_errors.append("Missing sport_name")

        parsed_row = {
            "row_num": index,
            "player_name": player_name,
            "team_name": team_name,
            "sport_name": sport_name,
            "grade": grade,
            "level": level
        }

        if row_errors:
            errors.append({"row": index, "data": parsed_row, "errors": row_errors})
        else:
            valid_rows.append(parsed_row)

    return jsonify({
        "total": len(valid_rows) + len(errors),
        "valid_count": len(valid_rows),
        "error_count": len(errors),
        "valid_rows": valid_rows,
        "errors": errors
    })


@app.route("/api/players/import/commit", methods=["POST"])
@req_admin_auth
def import_players_commit():
    data = request.get_json() or {}
    rows = data.get("rows", [])

    if not rows:
        return jsonify({"error": "No rows provided for commit"}), 400

    created_players = 0
    created_teams = 0
    created_sports = 0

    # Cache existing sports and teams
    existing_sports = {s["name"].lower(): s for s in (get_sports().json or [])}
    existing_teams = {f"{t['name'].lower()}_{t['sport_id']}": t for t in (get_teams().json or [])}

    for r in rows:
        sport_name = r["sport_name"]
        team_name = r["team_name"]
        player_name = r["player_name"]
        grade = r.get("grade", "")
        level = r.get("level", "HS")

        # 1. Resolve or Create Sport
        sport_key = sport_name.lower()
        if sport_key in existing_sports:
            sport_obj = existing_sports[sport_key]
        else:
            new_sport = {
                "id": str(uuid.uuid4()),
                "name": sport_name,
                "type": "generic",
                "level": level,
                "point_win": 3,
                "point_draw": 1,
                "point_loss": 0,
                "is_lower_score_better": False
            }
            if supabase_client:
                res = supabase_client.table("sports").insert(new_sport).execute()
                sport_obj = res.data[0]
            else:
                MOCK_DB["sports"].append(new_sport)
                sport_obj = new_sport
            existing_sports[sport_key] = sport_obj
            created_sports += 1

        # 2. Resolve or Create Team
        team_key = f"{team_name.lower()}_{sport_obj['id']}"
        if team_key in existing_teams:
            team_obj = existing_teams[team_key]
        else:
            new_team = {
                "id": str(uuid.uuid4()),
                "name": team_name,
                "sport_id": sport_obj["id"],
                "level": level
            }
            if supabase_client:
                res = supabase_client.table("teams").insert(new_team).execute()
                team_obj = res.data[0]
            else:
                MOCK_DB["teams"].append(new_team)
                team_obj = new_team
            existing_teams[team_key] = team_obj
            created_teams += 1

        # 3. Create Player
        new_player = {
            "id": str(uuid.uuid4()),
            "name": player_name,
            "team_id": team_obj["id"],
            "grade": grade,
            "level": level
        }
        if supabase_client:
            supabase_client.table("players").insert(new_player).execute()
        else:
            MOCK_DB["players"].append(new_player)
        created_players += 1

    return jsonify({
        "success": True,
        "players_created": created_players,
        "teams_created": created_teams,
        "sports_created": created_sports
    })


# MATCHES & SPORT-SPECIFIC SCORING
@app.route("/api/matches", methods=["GET"])
def get_matches():
    sport_id = request.args.get("sport_id")
    level = request.args.get("level")

    if supabase_client:
        try:
            query = supabase_client.table("matches").select("*, sports(name, type)")
            if sport_id:
                query = query.eq("sport_id", sport_id)
            if level and level != "ALL":
                query = query.eq("level", level)
            res = query.execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    matches = MOCK_DB["matches"]
    if sport_id:
        matches = [m for m in matches if m.get("sport_id") == sport_id]
    if level and level != "ALL":
        matches = [m for m in matches if m.get("level") == level]
    return jsonify(matches)

@app.route("/api/matches", methods=["POST"])
@req_admin_auth
def create_match():
    data = request.get_json() or {}
    sport_id = data.get("sport_id")
    team_a_id = data.get("team_a_id")
    team_b_id = data.get("team_b_id")
    level = data.get("level", "HS")
    round_info = data.get("round_info", "Regular Match")

    if not sport_id:
        return jsonify({"error": "sport_id is required"}), 400

    record = {
        "id": str(uuid.uuid4()),
        "sport_id": sport_id,
        "team_a_id": team_a_id,
        "team_b_id": team_b_id,
        "level": level,
        "status": "scheduled",
        "round_info": round_info,
        "winner_team_id": None,
        "is_draw": False
    }

    if supabase_client:
        try:
            res = supabase_client.table("matches").insert(record).execute()
            return jsonify(res.data[0]), 201
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["matches"].append(record)
    return jsonify(record), 201


# CRICKET SCORING
@app.route("/api/matches/<match_id>/score/cricket", methods=["POST"])
@req_admin_auth
def score_cricket(match_id):
    data = request.get_json() or {}
    # Innings 1 data: team_id, runs, wickets, overs, extras
    # Innings 2 data: team_id, runs, wickets, overs, extras
    innings1 = data.get("innings1", {})
    innings2 = data.get("innings2", {})
    status = data.get("status", "completed")

    runs1, wickets1 = innings1.get("runs", 0), innings1.get("wickets", 0)
    runs2, wickets2 = innings2.get("runs", 0), innings2.get("wickets", 0)

    winner_id = None
    is_draw = False

    if runs1 > runs2:
        winner_id = innings1.get("team_id")
    elif runs2 > runs1:
        winner_id = innings2.get("team_id")
    else:
        is_draw = True

    score_summary = f"Team 1: {runs1}/{wickets1} | Team 2: {runs2}/{wickets2}"

    if supabase_client:
        try:
            # Update match winner & status
            supabase_client.table("matches").update({
                "status": status,
                "winner_team_id": winner_id,
                "is_draw": is_draw
            }).eq("id", match_id).execute()

            # Insert/upsert innings
            if innings1.get("team_id"):
                supabase_client.table("cricket_innings").upsert({
                    "match_id": match_id,
                    "team_id": innings1["team_id"],
                    "innings_number": 1,
                    "total_runs": runs1,
                    "total_wickets": wickets1,
                    "total_overs": innings1.get("overs", 20.0),
                    "extras": innings1.get("extras", 0)
                }).execute()

            if innings2.get("team_id"):
                supabase_client.table("cricket_innings").upsert({
                    "match_id": match_id,
                    "team_id": innings2["team_id"],
                    "innings_number": 2,
                    "total_runs": runs2,
                    "total_wickets": wickets2,
                    "total_overs": innings2.get("overs", 20.0),
                    "extras": innings2.get("extras", 0)
                }).execute()

            return jsonify({"success": True, "winner_team_id": winner_id, "is_draw": is_draw})
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    # Mock DB update
    for m in MOCK_DB["matches"]:
        if m["id"] == match_id:
            m["status"] = status
            m["winner_team_id"] = winner_id
            m["is_draw"] = is_draw
            m["score_summary"] = score_summary
    return jsonify({"success": True, "winner_team_id": winner_id, "is_draw": is_draw})


# FOOTBALL SCORING
@app.route("/api/matches/<match_id>/score/football", methods=["POST"])
@req_admin_auth
def score_football(match_id):
    data = request.get_json() or {}
    team_a_goals = data.get("team_a_goals", 0)
    team_b_goals = data.get("team_b_goals", 0)
    events = data.get("events", []) # list of goals/cards

    # Find match
    match_item = None
    if supabase_client:
        res = supabase_client.table("matches").select("*").eq("id", match_id).execute()
        if res.data:
            match_item = res.data[0]
    else:
        for m in MOCK_DB["matches"]:
            if m["id"] == match_id:
                match_item = m
                break

    if not match_item:
        return jsonify({"error": "Match not found"}), 404

    team_a_id = match_item["team_a_id"]
    team_b_id = match_item["team_b_id"]

    winner_id = None
    is_draw = False

    if team_a_goals > team_b_goals:
        winner_id = team_a_id
    elif team_b_goals > team_a_goals:
        winner_id = team_b_id
    else:
        is_draw = True

    score_summary = f"{team_a_goals} - {team_b_goals}"

    if supabase_client:
        try:
            supabase_client.table("matches").update({
                "status": "completed",
                "winner_team_id": winner_id,
                "is_draw": is_draw
            }).eq("id", match_id).execute()

            if events:
                for ev in events:
                    supabase_client.table("football_events").insert({
                        "match_id": match_id,
                        "team_id": ev.get("team_id"),
                        "player_id": ev.get("player_id"),
                        "event_type": ev.get("event_type", "goal"),
                        "minute": ev.get("minute", 0)
                    }).execute()

            return jsonify({"success": True, "score": score_summary, "winner_team_id": winner_id})
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    match_item["status"] = "completed"
    match_item["winner_team_id"] = winner_id
    match_item["is_draw"] = is_draw
    match_item["score_summary"] = score_summary
    return jsonify({"success": True, "score": score_summary, "winner_team_id": winner_id})


# BASKETBALL SCORING
@app.route("/api/matches/<match_id>/score/basketball", methods=["POST"])
@req_admin_auth
def score_basketball(match_id):
    data = request.get_json() or {}
    quarters = data.get("quarters", []) # list of {quarter: 1, team_a_score: x, team_b_score: y}

    total_a = sum(q.get("team_a_score", 0) for q in quarters)
    total_b = sum(q.get("team_b_score", 0) for q in quarters)

    match_item = None
    if supabase_client:
        res = supabase_client.table("matches").select("*").eq("id", match_id).execute()
        if res.data:
            match_item = res.data[0]
    else:
        for m in MOCK_DB["matches"]:
            if m["id"] == match_id:
                match_item = m
                break

    if not match_item:
        return jsonify({"error": "Match not found"}), 404

    winner_id = None
    is_draw = False

    if total_a > total_b:
        winner_id = match_item["team_a_id"]
    elif total_b > total_a:
        winner_id = match_item["team_b_id"]
    else:
        is_draw = True

    score_summary = f"{total_a} - {total_b}"

    if supabase_client:
        try:
            supabase_client.table("matches").update({
                "status": "completed",
                "winner_team_id": winner_id,
                "is_draw": is_draw
            }).eq("id", match_id).execute()

            for q in quarters:
                supabase_client.table("basketball_quarters").upsert({
                    "match_id": match_id,
                    "quarter": q.get("quarter", 1),
                    "team_a_score": q.get("team_a_score", 0),
                    "team_b_score": q.get("team_b_score", 0)
                }).execute()

            return jsonify({"success": True, "score": score_summary, "winner_team_id": winner_id})
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    match_item["status"] = "completed"
    match_item["winner_team_id"] = winner_id
    match_item["is_draw"] = is_draw
    match_item["score_summary"] = score_summary
    return jsonify({"success": True, "score": score_summary, "winner_team_id": winner_id})


# GENERIC SPORT SCORING (100m Dash, Tug of war, Chess, etc.)
@app.route("/api/matches/<match_id>/score/generic", methods=["POST"])
@req_admin_auth
def score_generic(match_id):
    data = request.get_json() or {}
    results = data.get("results", []) # list of {team_id, player_id, score, notes}

    if not results:
        return jsonify({"error": "Results list required"}), 400

    # Retrieve match & sport info to know if lower score is better
    match_item = None
    sport_item = None

    if supabase_client:
        m_res = supabase_client.table("matches").select("*, sports(*)").eq("id", match_id).execute()
        if m_res.data:
            match_item = m_res.data[0]
            sport_item = match_item.get("sports")
    else:
        for m in MOCK_DB["matches"]:
            if m["id"] == match_id:
                match_item = m
                break
        if match_item:
            for s in MOCK_DB["sports"]:
                if s["id"] == match_item["sport_id"]:
                    sport_item = s
                    break

    is_lower_better = sport_item.get("is_lower_score_better", False) if sport_item else False

    # Sort results to determine winner/ranks
    sorted_res = sorted(results, key=lambda x: float(x.get("score", 0)), reverse=not is_lower_better)

    winner_team_id = sorted_res[0].get("team_id") if sorted_res else None

    if supabase_client:
        try:
            supabase_client.table("matches").update({
                "status": "completed",
                "winner_team_id": winner_team_id,
                "is_draw": False
            }).eq("id", match_id).execute()

            for rank_idx, r in enumerate(sorted_res, start=1):
                supabase_client.table("generic_results").insert({
                    "match_id": match_id,
                    "team_id": r.get("team_id"),
                    "player_id": r.get("player_id"),
                    "score": float(r.get("score", 0)),
                    "notes": r.get("notes", ""),
                    "rank": rank_idx
                }).execute()

            return jsonify({"success": True, "winner_team_id": winner_team_id, "rankings": sorted_res})
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    if match_item:
        match_item["status"] = "completed"
        match_item["winner_team_id"] = winner_team_id
        match_item["score_summary"] = f"Top Score: {sorted_res[0].get('score')}"

    return jsonify({"success": True, "winner_team_id": winner_team_id, "rankings": sorted_res})


# BRACKETS / TIE SHEET GENERATION
@app.route("/api/brackets/generate", methods=["POST"])
@req_admin_auth
def generate_bracket():
    data = request.get_json() or {}
    sport_id = data.get("sport_id")
    level = data.get("level", "HS")
    bracket_type = data.get("type", "single_elimination") # single_elimination | round_robin

    if not sport_id:
        return jsonify({"error": "sport_id is required"}), 400

    # Fetch teams for this sport & level
    teams = []
    if supabase_client:
        res = supabase_client.table("teams").select("*").eq("sport_id", sport_id).eq("level", level).execute()
        teams = res.data
    else:
        teams = [t for t in MOCK_DB["teams"] if t.get("sport_id") == sport_id and t.get("level") == level]

    if len(teams) < 2:
        return jsonify({"error": "At least 2 teams are required to generate a bracket"}), 400

    generated_matches = []
    rounds = []

    if bracket_type == "single_elimination":
        # Pair teams
        import math
        num_teams = len(teams)
        round_1_pairs = []
        for i in range(0, num_teams, 2):
            team_a = teams[i]
            team_b = teams[i+1] if (i+1) < num_teams else None
            round_1_pairs.append({"team_a": team_a, "team_b": team_b})

            # Create match in DB
            m_rec = {
                "id": str(uuid.uuid4()),
                "sport_id": sport_id,
                "team_a_id": team_a["id"],
                "team_b_id": team_b["id"] if team_b else None,
                "level": level,
                "status": "scheduled" if team_b else "completed",
                "winner_team_id": team_a["id"] if not team_b else None, # Bye
                "round_info": "Round 1"
            }
            if supabase_client:
                supabase_client.table("matches").insert(m_rec).execute()
            else:
                MOCK_DB["matches"].append(m_rec)
            generated_matches.append(m_rec)

        rounds.append({"round_name": "Round 1", "pairs": round_1_pairs})

    elif bracket_type == "round_robin":
        # Round robin scheduling
        for i in range(len(teams)):
            for j in range(i + 1, len(teams)):
                team_a = teams[i]
                team_b = teams[j]
                m_rec = {
                    "id": str(uuid.uuid4()),
                    "sport_id": sport_id,
                    "team_a_id": team_a["id"],
                    "team_b_id": team_b["id"],
                    "level": level,
                    "status": "scheduled",
                    "round_info": f"Round Robin ({team_a['name']} vs {team_b['name']})"
                }
                if supabase_client:
                    supabase_client.table("matches").insert(m_rec).execute()
                else:
                    MOCK_DB["matches"].append(m_rec)
                generated_matches.append(m_rec)
        rounds.append({"round_name": "Round Robin Matches", "matches_count": len(generated_matches)})

    bracket_record = {
        "id": str(uuid.uuid4()),
        "sport_id": sport_id,
        "level": level,
        "type": bracket_type,
        "structure_json": {"rounds": rounds, "matches": generated_matches}
    }

    if supabase_client:
        supabase_client.table("tournament_brackets").insert(bracket_record).execute()
    else:
        MOCK_DB["brackets"].append(bracket_record)

    return jsonify({"success": True, "bracket": bracket_record, "created_matches": len(generated_matches)})


# LEADERBOARD QUERY API
@app.route("/api/leaderboard", methods=["GET"])
def get_leaderboard():
    sport_id = request.args.get("sport_id")
    level = request.args.get("level")

    if supabase_client:
        try:
            query = supabase_client.table("leaderboard_view").select("*")
            if sport_id:
                query = query.eq("sport_id", sport_id)
            if level and level != "ALL":
                query = query.eq("level", level)
            res = query.order("points", desc=True).execute()
            return jsonify(res.data)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    # In-memory leaderboard computing for mock mode
    all_sports = {s["id"]: s for s in MOCK_DB["sports"]}
    all_teams = MOCK_DB["teams"]
    all_matches = MOCK_DB["matches"]

    leaderboard = []

    for t in all_teams:
        if sport_id and t.get("sport_id") != sport_id:
            continue
        if level and level != "ALL" and t.get("level") != level:
            continue

        sport = all_sports.get(t["sport_id"], {"name": "Unknown", "type": "generic", "point_win": 3, "point_draw": 1, "point_loss": 0})

        played = 0
        wins = 0
        draws = 0
        losses = 0

        for m in all_matches:
            if m.get("status") != "completed":
                continue
            if m.get("team_a_id") == t["id"] or m.get("team_b_id") == t["id"]:
                played += 1
                if m.get("is_draw"):
                    draws += 1
                elif m.get("winner_team_id") == t["id"]:
                    wins += 1
                else:
                    losses += 1

        points = (wins * sport.get("point_win", 3)) + (draws * sport.get("point_draw", 1)) + (losses * sport.get("point_loss", 0))

        leaderboard.append({
            "team_id": t["id"],
            "team_name": t["name"],
            "sport_id": t["sport_id"],
            "sport_name": sport.get("name"),
            "sport_type": sport.get("type"),
            "level": t.get("level", "HS"),
            "played": played,
            "wins": wins,
            "draws": draws,
            "losses": losses,
            "points": points
        })

    leaderboard.sort(key=lambda x: x["points"], reverse=True)
    return jsonify(leaderboard)


# OTA UPDATE CHECK FOR ANDROID CLIENT
@app.route("/api/version", methods=["GET"])
def get_version_info():
    return jsonify({
        "version_code": 2,
        "version_name": "1.0.1",
        "min_sdk": 24,
        "apk_url": "https://github.com/scoreboard/scoreboard/releases/latest/download/scoreboard.apk",
        "release_notes": "Improved live leaderboard syncing, sport scoring fixes, and UI bugfixes.",
        "mandatory": False
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
