import os
import csv
import io
import re
import uuid
import json
import time
import sqlite3
import base64
import hashlib
import secrets
from functools import wraps
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

# Supabase client (None = in-memory mock DB mode)
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
            "is_lower_score_better": False
        },
        {
            "id": "22222222-2222-2222-2222-222222222222",
            "name": "Basketball",
            "type": "basketball",
            "level": "HS",
            "is_lower_score_better": False
        },
        {
            "id": "33333333-3333-3333-3333-333333333333",
            "name": "Cricksal",
            "type": "generic",
            "level": "HS",
            "is_lower_score_better": False
        }
    ],
    "tournament_groups": [
        {"id": "g1", "sport_id": "11111111-1111-1111-1111-111111111111", "gender": "Boys", "format": "pool_to_semis", "point_win": 3, "point_draw": 1, "point_loss": 0},
        {"id": "g2", "sport_id": "11111111-1111-1111-1111-111111111111", "gender": "Girls", "format": "round_robin", "point_win": 3, "point_draw": 1, "point_loss": 0},
        {"id": "g3", "sport_id": "22222222-2222-2222-2222-222222222222", "gender": "Boys", "format": "round_robin", "point_win": 3, "point_draw": 1, "point_loss": 0},
        {"id": "g4", "sport_id": "22222222-2222-2222-2222-222222222222", "gender": "Girls", "format": "round_robin", "point_win": 3, "point_draw": 1, "point_loss": 0},
        {"id": "g5", "sport_id": "33333333-3333-3333-3333-333333333333", "gender": "Boys", "format": "pool_to_semis", "point_win": 3, "point_draw": 1, "point_loss": 0},
        {"id": "g6", "sport_id": "33333333-3333-3333-3333-333333333333", "gender": "Girls", "format": "round_robin", "point_win": 3, "point_draw": 1, "point_loss": 0}
    ],
    "teams": [
    {
        "id": "t_karnali_futsal_boys_a",
        "name": "Karnali Boys Futsal A",
        "house_id": "h1",
        "gender": "Boys",
        "squad_label": "A",
        "pool": "B",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_karnali_futsal_boys_b",
        "name": "Karnali Boys Futsal B",
        "house_id": "h1",
        "gender": "Boys",
        "squad_label": "B",
        "pool": "A",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_karnali_futsal_girls_a",
        "name": "Karnali Girls Futsal A",
        "house_id": "h1",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_karnali_futsal_girls_b",
        "name": "Karnali Girls Futsal B",
        "house_id": "h1",
        "gender": "Girls",
        "squad_label": "B",
        "pool": None,
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_koshi_futsal_boys_a",
        "name": "Koshi Boys Futsal A",
        "house_id": "h2",
        "gender": "Boys",
        "squad_label": "A",
        "pool": "B",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_koshi_futsal_boys_b",
        "name": "Koshi Boys Futsal B",
        "house_id": "h2",
        "gender": "Boys",
        "squad_label": "B",
        "pool": "A",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_koshi_futsal_girls_a",
        "name": "Koshi",
        "house_id": "h2",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_mahakali_futsal_boys_a",
        "name": "Mahakali",
        "house_id": "h3",
        "gender": "Boys",
        "squad_label": "A",
        "pool": "A",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_mahakali_futsal_girls_a",
        "name": "Mahakali",
        "house_id": "h3",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_mechi_futsal_boys_a",
        "name": "Mechi Boys Futsal A",
        "house_id": "h4",
        "gender": "Boys",
        "squad_label": "A",
        "pool": "B",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_mechi_futsal_boys_b",
        "name": "Mechi Boys Futsal B",
        "house_id": "h4",
        "gender": "Boys",
        "squad_label": "B",
        "pool": "A",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_mechi_futsal_girls_a",
        "name": "Mechi",
        "house_id": "h4",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "level": "HS"
    },
    {
        "id": "t_karnali_basketball_boys_a",
        "name": "Karnali Boys Basketball A",
        "house_id": "h1",
        "gender": "Boys",
        "squad_label": "A",
        "pool": None,
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "level": "HS"
    },
    {
        "id": "t_karnali_basketball_boys_b",
        "name": "Karnali Boys Basketball B",
        "house_id": "h1",
        "gender": "Boys",
        "squad_label": "B",
        "pool": None,
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "level": "HS"
    },
    {
        "id": "t_karnali_basketball_girls_a",
        "name": "Karnali Girls Basketball A",
        "house_id": "h1",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "level": "HS"
    },
    {
        "id": "t_karnali_basketball_girls_b",
        "name": "Karnali Girls Basketball B",
        "house_id": "h1",
        "gender": "Girls",
        "squad_label": "B",
        "pool": None,
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "level": "HS"
    },
    {
        "id": "t_koshi_basketball_boys_a",
        "name": "Koshi",
        "house_id": "h2",
        "gender": "Boys",
        "squad_label": "A",
        "pool": None,
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "level": "HS"
    },
    {
        "id": "t_koshi_basketball_girls_a",
        "name": "Koshi",
        "house_id": "h2",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "level": "HS"
    },
    {
        "id": "t_mahakali_basketball_boys_a",
        "name": "Mahakali",
        "house_id": "h3",
        "gender": "Boys",
        "squad_label": "A",
        "pool": None,
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "level": "HS"
    },
    {
        "id": "t_mahakali_basketball_girls_a",
        "name": "Mahakali",
        "house_id": "h3",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "level": "HS"
    },
    {
        "id": "t_mechi_basketball_boys_a",
        "name": "Mechi",
        "house_id": "h4",
        "gender": "Boys",
        "squad_label": "A",
        "pool": None,
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "level": "HS"
    },
    {
        "id": "t_mechi_basketball_girls_a",
        "name": "Mechi",
        "house_id": "h4",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "level": "HS"
    },
    {
        "id": "t_karnali_cricksal_boys_a",
        "name": "Karnali Boys Cricksal A",
        "house_id": "h1",
        "gender": "Boys",
        "squad_label": "A",
        "pool": "B",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    },
    {
        "id": "t_karnali_cricksal_boys_b",
        "name": "Karnali Boys Cricksal B",
        "house_id": "h1",
        "gender": "Boys",
        "squad_label": "B",
        "pool": "A",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    },
    {
        "id": "t_karnali_cricksal_girls_a",
        "name": "Karnali",
        "house_id": "h1",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    },
    {
        "id": "t_koshi_cricksal_boys_a",
        "name": "Koshi Boys Cricksal A",
        "house_id": "h2",
        "gender": "Boys",
        "squad_label": "A",
        "pool": "B",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    },
    {
        "id": "t_koshi_cricksal_boys_b",
        "name": "Koshi Boys Cricksal B",
        "house_id": "h2",
        "gender": "Boys",
        "squad_label": "B",
        "pool": "A",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    },
    {
        "id": "t_koshi_cricksal_girls_a",
        "name": "Koshi",
        "house_id": "h2",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    },
    {
        "id": "t_mahakali_cricksal_boys_a",
        "name": "Mahakali",
        "house_id": "h3",
        "gender": "Boys",
        "squad_label": "A",
        "pool": "A",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    },
    {
        "id": "t_mahakali_cricksal_girls_a",
        "name": "Mahakali",
        "house_id": "h3",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    },
    {
        "id": "t_mechi_cricksal_boys_a",
        "name": "Mechi Boys Cricksal A",
        "house_id": "h4",
        "gender": "Boys",
        "squad_label": "A",
        "pool": "A",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    },
    {
        "id": "t_mechi_cricksal_boys_b",
        "name": "Mechi Boys Cricksal B",
        "house_id": "h4",
        "gender": "Boys",
        "squad_label": "B",
        "pool": "B",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    },
    {
        "id": "t_mechi_cricksal_girls_a",
        "name": "Mechi",
        "house_id": "h4",
        "gender": "Girls",
        "squad_label": "A",
        "pool": None,
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "level": "HS"
    }
],
    "matches": [
    {
        "id": "m_fg_1",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mechi_futsal_girls_a",
        "team_b_id": "t_koshi_futsal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "completed",
        "round_info": "League Game",
        "winner_team_id": "t_mechi_futsal_girls_a",
        "is_draw": False,
        "score_team_a": 6,
        "score_team_b": 1,
        "score_difference": 5,
        "score_summary": "6 - 1"
    },
    {
        "id": "m_fg_2",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_karnali_futsal_girls_a",
        "team_b_id": "t_mahakali_futsal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "completed",
        "round_info": "League Game",
        "winner_team_id": "t_karnali_futsal_girls_a",
        "is_draw": False,
        "score_team_a": 3,
        "score_team_b": 1,
        "score_difference": 2,
        "score_summary": "3 - 1"
    },
    {
        "id": "m_fg_3",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_karnali_futsal_girls_b",
        "team_b_id": "t_koshi_futsal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "completed",
        "round_info": "League Game",
        "winner_team_id": "t_karnali_futsal_girls_b",
        "is_draw": False,
        "score_team_a": 12,
        "score_team_b": 2,
        "score_difference": 10,
        "score_summary": "12 - 2"
    },
    {
        "id": "m_fg_4",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mechi_futsal_girls_a",
        "team_b_id": "t_mahakali_futsal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "completed",
        "round_info": "League Game",
        "winner_team_id": "t_mechi_futsal_girls_a",
        "is_draw": False,
        "score_team_a": 1,
        "score_team_b": 0,
        "score_difference": 1,
        "score_summary": "1 - 0"
    },
    {
        "id": "m_fg_5",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_karnali_futsal_girls_a",
        "team_b_id": "t_karnali_futsal_girls_b",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "completed",
        "round_info": "League Game",
        "winner_team_id": "t_karnali_futsal_girls_a",
        "is_draw": False,
        "score_team_a": 11,
        "score_team_b": 0,
        "score_difference": 11,
        "score_summary": "11 - 0"
    },
    {
        "id": "m_fg_6",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_koshi_futsal_girls_a",
        "team_b_id": "t_mahakali_futsal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fg_7",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mechi_futsal_girls_a",
        "team_b_id": "t_karnali_futsal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fg_8",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mahakali_futsal_girls_a",
        "team_b_id": "t_karnali_futsal_girls_b",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fg_9",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_karnali_futsal_girls_a",
        "team_b_id": "t_koshi_futsal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fg_10",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mechi_futsal_girls_a",
        "team_b_id": "t_karnali_futsal_girls_b",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bb_1",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_mechi_basketball_boys_a",
        "team_b_id": "t_koshi_basketball_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "completed",
        "round_info": "League Game",
        "winner_team_id": "t_mechi_basketball_boys_a",
        "is_draw": False,
        "score_team_a": 69,
        "score_team_b": 23,
        "score_difference": 46,
        "score_summary": "69 - 23"
    },
    {
        "id": "m_bb_2",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_karnali_basketball_boys_a",
        "team_b_id": "t_mahakali_basketball_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "completed",
        "round_info": "League Game",
        "winner_team_id": "t_karnali_basketball_boys_a",
        "is_draw": False,
        "score_team_a": 54,
        "score_team_b": 0,
        "score_difference": 54,
        "score_summary": "54 - 0"
    },
    {
        "id": "m_bb_3",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_karnali_basketball_boys_b",
        "team_b_id": "t_koshi_basketball_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "completed",
        "round_info": "League Game",
        "winner_team_id": "t_karnali_basketball_boys_b",
        "is_draw": False,
        "score_team_a": 30,
        "score_team_b": 16,
        "score_difference": 14,
        "score_summary": "30 - 16"
    },
    {
        "id": "m_bb_4",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_mechi_basketball_boys_a",
        "team_b_id": "t_mahakali_basketball_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "completed",
        "round_info": "League Game",
        "winner_team_id": "t_mechi_basketball_boys_a",
        "is_draw": False,
        "score_team_a": 61,
        "score_team_b": 9,
        "score_difference": 52,
        "score_summary": "61 - 9"
    },
    {
        "id": "m_bb_5",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_karnali_basketball_boys_a",
        "team_b_id": "t_karnali_basketball_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "completed",
        "round_info": "League Game",
        "winner_team_id": "t_karnali_basketball_boys_a",
        "is_draw": False,
        "score_team_a": 23,
        "score_team_b": 18,
        "score_difference": 5,
        "score_summary": "23 - 18"
    },
    {
        "id": "m_bb_6",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_koshi_basketball_boys_a",
        "team_b_id": "t_mahakali_basketball_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bb_7",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_mechi_basketball_boys_a",
        "team_b_id": "t_karnali_basketball_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bb_8",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_mahakali_basketball_boys_a",
        "team_b_id": "t_karnali_basketball_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bb_9",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_karnali_basketball_boys_a",
        "team_b_id": "t_koshi_basketball_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bb_10",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_mechi_basketball_boys_a",
        "team_b_id": "t_karnali_basketball_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bg_1",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_mechi_basketball_girls_a",
        "team_b_id": "t_koshi_basketball_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bg_2",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_karnali_basketball_girls_a",
        "team_b_id": "t_mahakali_basketball_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bg_3",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_karnali_basketball_girls_b",
        "team_b_id": "t_koshi_basketball_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bg_4",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_mechi_basketball_girls_a",
        "team_b_id": "t_mahakali_basketball_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bg_5",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_karnali_basketball_girls_a",
        "team_b_id": "t_karnali_basketball_girls_b",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bg_6",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_koshi_basketball_girls_a",
        "team_b_id": "t_mahakali_basketball_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bg_7",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_mechi_basketball_girls_a",
        "team_b_id": "t_karnali_basketball_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bg_8",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_mahakali_basketball_girls_a",
        "team_b_id": "t_karnali_basketball_girls_b",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bg_9",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_karnali_basketball_girls_a",
        "team_b_id": "t_koshi_basketball_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_bg_10",
        "sport_id": "22222222-2222-2222-2222-222222222222",
        "team_a_id": "t_mechi_basketball_girls_a",
        "team_b_id": "t_karnali_basketball_girls_b",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cg_1",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_mechi_cricksal_girls_a",
        "team_b_id": "t_koshi_cricksal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cg_2",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_karnali_cricksal_girls_a",
        "team_b_id": "t_mahakali_cricksal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cg_3",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_mechi_cricksal_girls_a",
        "team_b_id": "t_karnali_cricksal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cg_4",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_koshi_cricksal_girls_a",
        "team_b_id": "t_mahakali_cricksal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cg_5",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_karnali_cricksal_girls_a",
        "team_b_id": "t_koshi_cricksal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cg_6",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_mechi_cricksal_girls_a",
        "team_b_id": "t_mahakali_cricksal_girls_a",
        "gender": "Girls",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_1",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_koshi_cricksal_boys_b",
        "team_b_id": "t_mahakali_cricksal_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_2",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_koshi_cricksal_boys_a",
        "team_b_id": "t_karnali_cricksal_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_3",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_karnali_cricksal_boys_b",
        "team_b_id": "t_mechi_cricksal_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_4",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_mechi_cricksal_boys_b",
        "team_b_id": "t_koshi_cricksal_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_5",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_mahakali_cricksal_boys_a",
        "team_b_id": "t_karnali_cricksal_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_6",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_karnali_cricksal_boys_a",
        "team_b_id": "t_mechi_cricksal_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_7",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_koshi_cricksal_boys_b",
        "team_b_id": "t_mechi_cricksal_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_8",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_mechi_cricksal_boys_a",
        "team_b_id": "t_mahakali_cricksal_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_9",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": "t_koshi_cricksal_boys_b",
        "team_b_id": "t_karnali_cricksal_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_10",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": None,
        "team_b_id": None,
        "gender": "Boys",
        "stage": "semifinal",
        "level": "HS",
        "status": "scheduled",
        "round_info": "Semi Final Match",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_11",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": None,
        "team_b_id": None,
        "gender": "Boys",
        "stage": "semifinal",
        "level": "HS",
        "status": "scheduled",
        "round_info": "Semi Final Match",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_cb_12",
        "sport_id": "33333333-3333-3333-3333-333333333333",
        "team_a_id": None,
        "team_b_id": None,
        "gender": "Boys",
        "stage": "final",
        "level": "HS",
        "status": "scheduled",
        "round_info": "Final Match",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_1",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mechi_futsal_boys_b",
        "team_b_id": "t_karnali_futsal_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_2",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_koshi_futsal_boys_a",
        "team_b_id": "t_mechi_futsal_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_3",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mahakali_futsal_boys_a",
        "team_b_id": "t_koshi_futsal_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_4",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mechi_futsal_boys_a",
        "team_b_id": "t_karnali_futsal_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_5",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mechi_futsal_boys_b",
        "team_b_id": "t_mahakali_futsal_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_6",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_karnali_futsal_boys_a",
        "team_b_id": "t_koshi_futsal_boys_a",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_7",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_koshi_futsal_boys_b",
        "team_b_id": "t_karnali_futsal_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_8",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mechi_futsal_boys_b",
        "team_b_id": "t_koshi_futsal_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_9",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": "t_mahakali_futsal_boys_a",
        "team_b_id": "t_karnali_futsal_boys_b",
        "gender": "Boys",
        "stage": "league",
        "level": "HS",
        "status": "scheduled",
        "round_info": "League Game",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_10",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": None,
        "team_b_id": None,
        "gender": "Boys",
        "stage": "semifinal",
        "level": "HS",
        "status": "scheduled",
        "round_info": "Semi Final Match",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_11",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": None,
        "team_b_id": None,
        "gender": "Boys",
        "stage": "semifinal",
        "level": "HS",
        "status": "scheduled",
        "round_info": "Semi Final Match",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    },
    {
        "id": "m_fb_12",
        "sport_id": "11111111-1111-1111-1111-111111111111",
        "team_a_id": None,
        "team_b_id": None,
        "gender": "Boys",
        "stage": "final",
        "level": "HS",
        "status": "scheduled",
        "round_info": "Final Match",
        "winner_team_id": None,
        "is_draw": False,
        "score_team_a": 0,
        "score_team_b": 0,
        "score_difference": 0,
        "score_summary": ""
    }
],
    "brackets": [],
    "generic_results": [],
    "football_events": [],
    "basketball_quarters": [],
    "players": []
}

# --- AUTH ------------------------------------------------------------------
# Admin accounts live OUTSIDE the codebase and OUTSIDE the environment:
#   * Supabase mode: admin accounts are real Supabase Auth users; an account
#     is an admin while its email exists in the public.admins table.
#   * Mock/dev mode: accounts are stored in a local SQLite database
#     (data/admins.db — override with ADMIN_DB_PATH) using PBKDF2-HMAC-SHA256
#     password hashing (600k iterations, per-account random salt).
# Sessions are server-side opaque tokens in BOTH modes: random, expiring,
# stored (SHA-256 hashed) in the SQLite store, revocable on logout, and
# immediately invalidated when the account is removed. No static or JWT-based
# shortcut is ever accepted.
SESSION_TTL = 21600  # seconds (6h), synced with the frontend session length
MAX_SESSIONS_PER_ADMIN = 5
_PBKDF2_ITERATIONS = 600_000
_PASSWORD_MIN_LENGTH = 12
_LOGIN_MAX_ATTEMPTS = 5
_LOGIN_WINDOW_SECONDS = 900  # 15 minutes

_login_failures = {}  # (ip, email) -> [failure timestamps]


def _admin_db_path():
    env_path = os.getenv("ADMIN_DB_PATH")
    if env_path:
        return env_path
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.normpath(os.path.join(here, "..", "data", "admins.db"))


def _get_admin_db():
    path = _admin_db_path()
    parent = os.path.dirname(path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout = 5000")
    conn.execute("""
        CREATE TABLE IF NOT EXISTS admins (
            email         TEXT PRIMARY KEY COLLATE NOCASE,
            password_hash TEXT NOT NULL,
            is_active     INTEGER NOT NULL DEFAULT 1,
            created_at    TEXT NOT NULL DEFAULT (datetime('now'))
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS sessions (
            token_hash TEXT PRIMARY KEY,
            email      TEXT NOT NULL,
            expires_at REAL NOT NULL
        )
    """)
    return conn


def _hash_password(password):
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, _PBKDF2_ITERATIONS)
    return "pbkdf2_sha256${}${}${}".format(
        _PBKDF2_ITERATIONS,
        base64.b64encode(salt).decode("ascii"),
        base64.b64encode(digest).decode("ascii"),
    )


def _verify_password(password, stored):
    try:
        algorithm, iterations, salt_b64, hash_b64 = stored.split("$")
        if algorithm != "pbkdf2_sha256":
            return False
        salt = base64.b64decode(salt_b64)
        expected = base64.b64decode(hash_b64)
        actual = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, int(iterations))
        return secrets.compare_digest(actual, expected)
    except Exception:
        return False


# Equalizes login timing for unknown emails (no user enumeration).
_DUMMY_HASH = _hash_password("dummy-hash-for-timing-equalization")


def _is_admin_email(email):
    if not email:
        return False
    email = email.strip().lower()
    if supabase_client:
        try:
            res = supabase_client.table("admins").select("email").eq("email", email).limit(1).execute()
            return bool(res.data)
        except Exception:
            return False
    conn = _get_admin_db()
    try:
        row = conn.execute(
            "SELECT email FROM admins WHERE email = ? AND is_active = 1", (email,)
        ).fetchone()
        return row is not None
    finally:
        conn.close()


def _verify_admin_password(email, password):
    conn = _get_admin_db()
    try:
        row = conn.execute(
            "SELECT password_hash FROM admins WHERE email = ? AND is_active = 1", (email,)
        ).fetchone()
    finally:
        conn.close()
    if not row:
        _verify_password(password, _DUMMY_HASH)  # equalize timing
        return False
    return _verify_password(password, row["password_hash"])


def add_admin(email, password):
    """Create (or update) an admin account. Raises ValueError on bad input."""
    email = (email or "").strip().lower()
    if not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", email):
        raise ValueError("Invalid email address")
    if not password or len(password) < _PASSWORD_MIN_LENGTH:
        raise ValueError("Password must be at least %d characters" % _PASSWORD_MIN_LENGTH)
    if password.lower() == email:
        raise ValueError("Password must not be the email address")
    if supabase_client:
        try:
            supabase_client.auth.admin.create_user({
                "email": email, "password": password, "email_confirm": True
            })
        except Exception:
            pass  # Auth user may already exist
        supabase_client.table("admins").upsert({"email": email}, on_conflict="email").execute()
        return
    conn = _get_admin_db()
    try:
        conn.execute(
            "INSERT INTO admins (email, password_hash, is_active) VALUES (?, ?, 1) "
            "ON CONFLICT(email) DO UPDATE SET password_hash = excluded.password_hash, "
            "is_active = 1",
            (email, _hash_password(password)),
        )
        conn.commit()
    finally:
        conn.close()


def remove_admin(email):
    """Remove an admin account and revoke all of its sessions immediately."""
    email = (email or "").strip().lower()
    if supabase_client:
        try:
            supabase_client.table("admins").delete().eq("email", email).execute()
        except Exception:
            pass
    else:
        conn = _get_admin_db()
        try:
            conn.execute("DELETE FROM admins WHERE email = ?", (email,))
            conn.commit()
        finally:
            conn.close()
    _revoke_admin_sessions(email)


def list_admins():
    """List admin accounts. Mock mode returns dicts, Supabase mode returns emails."""
    if supabase_client:
        try:
            res = supabase_client.table("admins").select("email").execute()
            return [r["email"] for r in (res.data or [])]
        except Exception:
            return []
    conn = _get_admin_db()
    try:
        rows = conn.execute("SELECT email, is_active FROM admins ORDER BY email").fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def _session_token_hash(token):
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _cleanup_sessions():
    conn = _get_admin_db()
    try:
        conn.execute("DELETE FROM sessions WHERE expires_at <= ?", (time.time(),))
        conn.commit()
    finally:
        conn.close()


def _issue_session(email):
    token = secrets.token_urlsafe(48)
    conn = _get_admin_db()
    try:
        conn.execute(
            "INSERT INTO sessions (token_hash, email, expires_at) VALUES (?, ?, ?)",
            (_session_token_hash(token), email, time.time() + SESSION_TTL),
        )
        conn.commit()
    finally:
        conn.close()
    return token


def _session_email(token):
    _cleanup_sessions()
    conn = _get_admin_db()
    try:
        row = conn.execute(
            "SELECT email FROM sessions WHERE token_hash = ?", (_session_token_hash(token),)
        ).fetchone()
        return row["email"] if row else None
    finally:
        conn.close()


def _revoke_session(token):
    conn = _get_admin_db()
    try:
        conn.execute("DELETE FROM sessions WHERE token_hash = ?", (_session_token_hash(token),))
        conn.commit()
    finally:
        conn.close()


def _revoke_admin_sessions(email):
    conn = _get_admin_db()
    try:
        conn.execute("DELETE FROM sessions WHERE email = ?", (email,))
        conn.commit()
    finally:
        conn.close()


def _clear_all_sessions():
    conn = _get_admin_db()
    try:
        conn.execute("DELETE FROM sessions")
        conn.commit()
    finally:
        conn.close()


def _active_session_count(email):
    _cleanup_sessions()
    conn = _get_admin_db()
    try:
        row = conn.execute(
            "SELECT COUNT(*) AS c FROM sessions WHERE email = ?", (email,)
        ).fetchone()
        return row["c"]
    finally:
        conn.close()


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


def _clear_login_failures(key):
    _login_failures.pop(key, None)


def _record_login_failure(key):
    timestamps = _login_failure_window(key)
    timestamps.append(time.time())
    _login_failures[key] = timestamps


def _login_blocked(key):
    return len(_login_failure_window(key)) >= _LOGIN_MAX_ATTEMPTS


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


# --- HTML PAGE ENDPOINTS (MULTI-PAGE ARCHITECTURE) ---

@app.route("/")
def index_page():
    return render_template("index.html", active_page="overall")

@app.route("/standings")
@app.route("/standings/<sport_slug>")
def standings_page(sport_slug="futsal"):
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
        "mode": "supabase" if supabase_client else "mock_in_memory"
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

    cred_key = (ip, email)
    if _login_blocked(cred_key):
        return jsonify({"error": "Too many failed attempts. Try again later."}), 429

    if supabase_client:
        try:
            res = supabase_client.auth.sign_in_with_password({"email": email, "password": password})
            user_email = (getattr(res.user, "email", "") or "").lower()
        except Exception:
            _record_login_failure(cred_key)
            return jsonify({"error": "Invalid email or password"}), 401
        if user_email != email or not _is_admin_email(user_email):
            _record_login_failure(cred_key)
            return jsonify({"error": "Invalid email or password"}), 401
    else:
        if not _verify_admin_password(email, password):
            _record_login_failure(cred_key)
            return jsonify({"error": "Invalid email or password"}), 401

    active_sessions = _active_session_count(email)
    if active_sessions >= MAX_SESSIONS_PER_ADMIN:
        return jsonify({
            "error": "Too many active sessions for this account. Sign out elsewhere first."
        }), 429

    _clear_login_failures(cred_key)
    token = _issue_session(email)
    return jsonify({
        "access_token": token,
        "expires_in": SESSION_TTL,
        "user": {"id": email, "email": email}
    })


@app.route("/api/auth/me", methods=["GET"])
@req_admin_auth
def auth_me():
    """Return the authenticated admin's identity for the current session."""
    token = request.headers.get("Authorization", "").split(" ", 1)[1].strip()
    return jsonify({"email": _session_email(token) or ""})


@app.route("/api/auth/logout", methods=["POST"])
@req_admin_auth
def auth_logout():
    """Revoke the current session server-side (both modes)."""
    token = request.headers.get("Authorization", "").split(" ", 1)[1].strip()
    _revoke_session(token)
    return jsonify({"status": "logged_out"})


@app.after_request
def add_security_headers(response):
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("X-Frame-Options", "DENY")
    response.headers.setdefault("Referrer-Policy", "no-referrer")
    if request.path.startswith("/api/"):
        response.headers.setdefault("Cache-Control", "no-store")
    return response


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

@app.route("/api/players/bulk-delete", methods=["POST"])
@req_admin_auth
def bulk_delete_players():
    data = request.get_json() or {}
    player_ids = data.get("player_ids", [])
    if not player_ids or not isinstance(player_ids, list):
        return jsonify({"error": "player_ids list is required"}), 400

    if supabase_client:
        try:
            supabase_client.table("players").delete().in_("id", player_ids).execute()
            return jsonify({"message": f"Successfully deleted {len(player_ids)} players", "count": len(player_ids)})
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    MOCK_DB["players"] = [p for p in MOCK_DB["players"] if p["id"] not in player_ids]
    return jsonify({"message": f"Successfully deleted {len(player_ids)} players", "count": len(player_ids)})


# MATCHES ENDPOINTS
@app.route("/api/matches", methods=["GET"])
def get_matches():
    sport_id = request.args.get("sport_id")
    gender = request.args.get("gender")
    stage = request.args.get("stage")

    if supabase_client:
        try:
            query = supabase_client.table("matches").select(
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
            if supabase_client:
                try:
                    match_res = supabase_client.table("matches").select("team_a_id, team_b_id").eq("id", match_id).single().execute()
                    if match_res.data:
                        team_a_id = team_a_id or match_res.data.get("team_a_id")
                        team_b_id = team_b_id or match_res.data.get("team_b_id")
                except Exception:
                    pass
            else:
                for m in MOCK_DB["matches"]:
                    if m["id"] == match_id:
                        team_a_id = team_a_id or m.get("team_a_id")
                        team_b_id = team_b_id or m.get("team_b_id")
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
    teams = MOCK_DB["teams"]
    matches = MOCK_DB["matches"]

    # Calculate total squads per house
    squad_counts = {}
    for t in teams:
        h_id = t["house_id"]
        squad_counts[h_id] = squad_counts.get(h_id, 0) + 1

    # Aggregate match stats per team
    team_stats = {}
    for m in matches:
        if m.get("status") != "completed" or m.get("stage") != "league":
            continue
        team_a_id = m.get("team_a_id")
        team_b_id = m.get("team_b_id")
        score_a = m.get("score_team_a", 0)
        score_b = m.get("score_team_b", 0)

        if team_a_id:
            team_stats.setdefault(team_a_id, {"played": 0, "wins": 0, "draws": 0, "losses": 0, "diff": 0, "pts": 0})
            team_stats[team_a_id]["played"] += 1
            team_stats[team_a_id]["diff"] += (score_a - score_b)
            if m.get("is_draw"):
                team_stats[team_a_id]["draws"] += 1
                team_stats[team_a_id]["pts"] += 1
            elif m.get("winner_team_id") == team_a_id:
                team_stats[team_a_id]["wins"] += 1
                team_stats[team_a_id]["pts"] += 3
            else:
                team_stats[team_a_id]["losses"] += 1

        if team_b_id:
            team_stats.setdefault(team_b_id, {"played": 0, "wins": 0, "draws": 0, "losses": 0, "diff": 0, "pts": 0})
            team_stats[team_b_id]["played"] += 1
            team_stats[team_b_id]["diff"] += (score_b - score_a)
            if m.get("is_draw"):
                team_stats[team_b_id]["draws"] += 1
                team_stats[team_b_id]["pts"] += 1
            elif m.get("winner_team_id") == team_b_id:
                team_stats[team_b_id]["wins"] += 1
                team_stats[team_b_id]["pts"] += 3
            else:
                team_stats[team_b_id]["losses"] += 1

    # Aggregate team stats by house
    house_stats = {}
    for t in teams:
        h_id = t["house_id"]
        st = team_stats.get(t["id"], {"played": 0, "wins": 0, "draws": 0, "losses": 0, "diff": 0, "pts": 0})
        house_stats.setdefault(h_id, {"played": 0, "wins": 0, "draws": 0, "losses": 0, "diff": 0, "pts": 0})
        house_stats[h_id]["played"] += st["played"]
        house_stats[h_id]["wins"] += st["wins"]
        house_stats[h_id]["draws"] += st["draws"]
        house_stats[h_id]["losses"] += st["losses"]
        house_stats[h_id]["diff"] += st["diff"]
        house_stats[h_id]["pts"] += st["pts"]

    standings = []
    for h in houses:
        h_id = h["id"]
        hs = house_stats.get(h_id, {"played": 0, "wins": 0, "draws": 0, "losses": 0, "diff": 0, "pts": 0})
        standings.append({
            "house_id": h_id,
            "house_name": h["name"],
            "color_hex": h["color_hex"],
            "short_code": h["short_code"],
            "total_squads": squad_counts.get(h_id, 0),
            "matches_played": hs["played"],
            "total_wins": hs["wins"],
            "total_draws": hs["draws"],
            "total_losses": hs["losses"],
            "total_score_difference": hs["diff"],
            "total_points": hs["pts"],
            "rank": 1
        })

    standings.sort(key=lambda x: (x["total_points"], x["total_score_difference"], x["total_wins"]), reverse=True)
    for idx, r in enumerate(standings, start=1):
        r["rank"] = idx

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
    _host = os.getenv("HOST", "0.0.0.0")
    _port = int(os.getenv("PORT", "5000"))
    _debug = os.getenv("FLASK_DEBUG", "").strip().lower() in ("1", "true", "yes", "on")
    if _debug and _host in ("0.0.0.0", "::", "::1", ""):
        print("WARNING: FLASK_DEBUG is enabled on a public bind address. The Werkzeug "
              "debugger allows remote code execution — never enable it in production.")
    app.run(host=_host, port=_port, debug=_debug, use_reloader=False)
