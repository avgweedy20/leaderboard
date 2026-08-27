#!/usr/bin/env python3
"""
seeder.py — Idempotent seeder script for DSS Sports Inter-House Meet.
Parses /seed-data/interhouse_meet.xlsx and seeds Supabase DB.
"""

import os
import re
import sys
import uuid
import argparse
from typing import Dict, Any, Optional, Tuple, List
import openpyxl
from dotenv import load_dotenv

load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_SERVICE_ROLE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY") or os.getenv("SUPABASE_KEY")

HOUSE_CONFIG = [
    {"name": "Karnali", "color_hex": "#10B981", "short_code": "KAR"},
    {"name": "Koshi", "color_hex": "#0EA5E9", "short_code": "KOS"},
    {"name": "Mahakali", "color_hex": "#8B5CF6", "short_code": "MAH"},
    {"name": "Mechi", "color_hex": "#F97316", "short_code": "MEC"}
]

SPORT_CONFIG = [
    {"name": "Futsal", "type": "football", "level": "HS", "point_win": 3, "point_draw": 1, "point_loss": 0},
    {"name": "Basketball", "type": "basketball", "level": "HS", "point_win": 3, "point_draw": 1, "point_loss": 0},
    {"name": "Cricksal", "type": "generic", "level": "HS", "point_win": 3, "point_draw": 1, "point_loss": 0}
]

class InterHouseSeeder:
    def __init__(self, excel_path: str, supabase_client=None):
        self.excel_path = excel_path
        self.client = supabase_client
        self.wb = None

        self.houses_map: Dict[str, Dict[str, Any]] = {}  # name.lower() -> dict
        self.sports_map: Dict[str, Dict[str, Any]] = {}  # name.lower() -> dict
        self.squads_map: Dict[str, Dict[str, Any]] = {}  # "house_id_sport_id_gender_squad_label" -> dict
        self.players_map: Dict[str, Dict[str, Any]] = {} # roll_number -> dict

        self.stats = {
            "houses": {"created": 0, "updated": 0, "skipped": 0},
            "sports": {"created": 0, "updated": 0, "skipped": 0},
            "squads": {"created": 0, "updated": 0, "skipped": 0},
            "players": {"created": 0, "updated": 0, "skipped": 0},
            "fixtures": {
                "created": 0,
                "updated": 0,
                "unplayed": 0,
                "unparseable": 0,
                "unparseable_details": []
            }
        }

    def load_workbook(self):
        if not os.path.exists(self.excel_path):
            raise FileNotFoundError(f"Excel file not found at: {self.excel_path}")
        self.wb = openpyxl.load_workbook(self.excel_path, data_only=True)

    def seed_houses(self):
        print("\n=== Seeding Houses ===")
        for house in HOUSE_CONFIG:
            key = house["name"].lower()
            record = {
                "id": str(uuid.uuid4()),
                "name": house["name"],
                "color_hex": house["color_hex"],
                "short_code": house["short_code"]
            }

            if self.client:
                try:
                    res = self.client.table("houses").upsert(
                        record, on_conflict="name"
                    ).execute()
                    if res.data:
                        record = res.data[0]
                    self.stats["houses"]["created"] += 1
                except Exception as e:
                    print(f"Error upserting house {house['name']}: {e}")
                    self.stats["houses"]["skipped"] += 1
            else:
                self.stats["houses"]["created"] += 1

            self.houses_map[key] = record
            print(f"  House: {record['name']} ({record['short_code']}) -> {record['color_hex']}")

    def seed_sports(self):
        print("\n=== Seeding Sports ===")
        for sport in SPORT_CONFIG:
            key = sport["name"].lower()
            record = {
                "id": str(uuid.uuid4()),
                "name": sport["name"],
                "type": sport["type"],
                "level": sport["level"],
                "point_win": sport["point_win"],
                "point_draw": sport["point_draw"],
                "point_loss": sport["point_loss"]
            }

            if self.client:
                try:
                    res = self.client.table("sports").upsert(
                        record, on_conflict="name"
                    ).execute()
                    if res.data:
                        record = res.data[0]
                    self.stats["sports"]["created"] += 1
                except Exception as e:
                    print(f"Error upserting sport {sport['name']}: {e}")
                    self.stats["sports"]["skipped"] += 1
            else:
                self.stats["sports"]["created"] += 1

            self.sports_map[key] = record
            print(f"  Sport: {record['name']} ({record['type']})")

    def ensure_default_squads(self):
        # Ensure default squads exist for all house x sport x gender x squad_label combinations
        for h_key, house_obj in self.houses_map.items():
            for s_key, sport_obj in self.sports_map.items():
                for gender in ["Boys", "Girls"]:
                    for squad_label in ["A", "B"]:
                        squad_key = f"{house_obj['id']}_{sport_obj['id']}_{gender}_{squad_label}"
                        if squad_key not in self.squads_map:
                            squad_name = f"{house_obj['name']} {gender} {sport_obj['name']} {squad_label}"
                            squad_record = {
                                "id": str(uuid.uuid4()),
                                "name": squad_name,
                                "house_id": house_obj["id"],
                                "gender": gender,
                                "squad_label": squad_label,
                                "sport_id": sport_obj["id"],
                                "level": "HS"
                            }
                            if self.client:
                                try:
                                    res = self.client.table("teams").upsert(
                                        squad_record, on_conflict="house_id,sport_id,gender,squad_label,level"
                                    ).execute()
                                    if res.data:
                                        squad_record = res.data[0]
                                    self.stats["squads"]["created"] += 1
                                except Exception:
                                    self.stats["squads"]["skipped"] += 1
                            else:
                                self.stats["squads"]["created"] += 1

                            self.squads_map[squad_key] = squad_record

    def parse_house_rosters(self):
        print("\n=== Parsing House Rosters (Squads & Players) ===")
        house_sheets = [s for s in self.wb.sheetnames if "house" in s.lower() and s != "House Members"]

        for sheet_name in house_sheets:
            sheet = self.wb[sheet_name]
            house_name = sheet_name.replace("House", "").strip()
            house_key = house_name.lower()
            house_obj = self.houses_map.get(house_key)

            if not house_obj:
                print(f"  Skipping sheet '{sheet_name}': House '{house_name}' not in house map")
                continue

            print(f"  Processing sheet: {sheet_name} (House: {house_obj['name']})")
            max_cols = sheet.max_column

            block_starts = []
            for col in range(1, max_cols + 1):
                val = sheet.cell(row=1, column=col).value
                if val and isinstance(val, str):
                    for s_key in self.sports_map.keys():
                        if s_key in val.lower():
                            block_starts.append((col, self.sports_map[s_key]))
                            break

            if not block_starts:
                for col in range(1, max_cols + 1):
                    val = sheet.cell(row=2, column=col).value
                    if val and isinstance(val, str):
                        for s_key in self.sports_map.keys():
                            if s_key in val.lower():
                                block_starts.append((col, self.sports_map[s_key]))
                                break

            print(f"    Found {len(block_starts)} sport blocks in {sheet_name}")

            for start_col, sport_obj in block_starts:
                header_row = 2
                col_indices = {}
                for c in range(start_col, min(start_col + 10, max_cols + 1)):
                    cell_val = sheet.cell(row=header_row, column=c).value
                    if not cell_val:
                        cell_val = sheet.cell(row=header_row + 1, column=c).value
                    if cell_val and isinstance(cell_val, str):
                        c_lower = cell_val.strip().lower()
                        if "roll" in c_lower or "rn" in c_lower:
                            col_indices["roll"] = c
                        elif "name" in c_lower:
                            col_indices["name"] = c
                        elif "grade" in c_lower:
                            col_indices["grade"] = c
                        elif "sec" in c_lower:
                            col_indices["section"] = c
                        elif "gen" in c_lower or "sex" in c_lower:
                            col_indices["gender"] = c
                        elif "team" in c_lower or "squad" in c_lower:
                            col_indices["squad"] = c

                data_start_row = 3 if "name" in [str(sheet.cell(row=2, column=c).value).lower() for c in range(start_col, start_col + 8)] else 4

                for r in range(data_start_row, sheet.max_row + 1):
                    name_col = col_indices.get("name", start_col + 2)
                    name_val = sheet.cell(row=r, column=name_col).value
                    if not name_val or not str(name_val).strip() or str(name_val).strip().isdigit():
                        continue

                    p_name = str(name_val).strip()
                    roll_num = str(sheet.cell(row=r, column=col_indices.get("roll", start_col + 1)).value or "").strip()
                    grade = str(sheet.cell(row=r, column=col_indices.get("grade", start_col + 3)).value or "").strip()
                    section = str(sheet.cell(row=r, column=col_indices.get("section", start_col + 4)).value or "").strip()
                    gender_raw = str(sheet.cell(row=r, column=col_indices.get("gender", start_col + 5)).value or "").strip()
                    squad_label_raw = str(sheet.cell(row=r, column=col_indices.get("squad", start_col + 6)).value or "").strip().upper()

                    gender = "Girls" if "girl" in gender_raw.lower() or "f" in gender_raw.lower() else "Boys"
                    squad_label = squad_label_raw if squad_label_raw in ["A", "B", "C", "D"] else "A"

                    squad_key = f"{house_obj['id']}_{sport_obj['id']}_{gender}_{squad_label}"

                    if squad_key not in self.squads_map:
                        squad_name = f"{house_obj['name']} {gender} {sport_obj['name']} {squad_label}"
                        squad_record = {
                            "id": str(uuid.uuid4()),
                            "name": squad_name,
                            "house_id": house_obj["id"],
                            "gender": gender,
                            "squad_label": squad_label,
                            "sport_id": sport_obj["id"],
                            "level": "HS"
                        }
                        if self.client:
                            try:
                                res = self.client.table("teams").upsert(
                                    squad_record, on_conflict="house_id,sport_id,gender,squad_label,level"
                                ).execute()
                                if res.data:
                                    squad_record = res.data[0]
                                self.stats["squads"]["created"] += 1
                            except Exception as e:
                                self.stats["squads"]["skipped"] += 1
                        else:
                            self.stats["squads"]["created"] += 1

                        self.squads_map[squad_key] = squad_record

                    squad_obj = self.squads_map[squad_key]

                    p_key = roll_num if roll_num else f"{p_name}_{squad_obj['id']}"
                    p_record = {
                        "id": str(uuid.uuid4()),
                        "name": p_name,
                        "team_id": squad_obj["id"],
                        "roll_number": roll_num if roll_num else None,
                        "grade": grade,
                        "section": section,
                        "gender": gender,
                        "level": "HS"
                    }

                    if self.client:
                        try:
                            res = self.client.table("players").upsert(
                                p_record, on_conflict="roll_number" if roll_num else "id"
                            ).execute()
                            if res.data:
                                p_record = res.data[0]
                            self.stats["players"]["created"] += 1
                        except Exception:
                            self.stats["players"]["skipped"] += 1
                    else:
                        self.stats["players"]["created"] += 1

                    self.players_map[p_key] = p_record

    def parse_fixtures_and_scores(self):
        print("\n=== Parsing Fixtures & Scores ('Tie-sheet & Scores Update') ===")
        if "Tie-sheet & Scores Update" not in self.wb.sheetnames:
            print("  Sheet 'Tie-sheet & Scores Update' not found!")
            return

        sheet = self.wb["Tie-sheet & Scores Update"]

        for r in range(1, sheet.max_row + 1):
            row_vals = [sheet.cell(row=r, column=c).value for c in range(1, sheet.max_column + 1)]
            row_str = " ".join([str(v) for v in row_vals if v is not None])

            if " vs " in row_str.lower() or " vs. " in row_str.lower():
                team_a_str = ""
                team_b_str = ""
                score_str = ""
                sport_name = "Futsal"
                gender = "Boys"

                for idx, val in enumerate(row_vals):
                    if val is None:
                        continue
                    v_str = str(val).strip()
                    if "vs" in v_str.lower():
                        if idx > 0 and row_vals[idx - 1]:
                            team_a_str = str(row_vals[idx - 1]).strip()
                        if idx + 1 < len(row_vals) and row_vals[idx + 1]:
                            team_b_str = str(row_vals[idx + 1]).strip()
                    if "=" in v_str or "-" in v_str:
                        if re.search(r'\d+\s*[-–]\s*\d+', v_str):
                            score_str = v_str

                if not team_a_str or not team_b_str:
                    continue

                for s_key, s_obj in self.sports_map.items():
                    if s_key in row_str.lower():
                        sport_name = s_obj["name"]
                        break

                if "girl" in row_str.lower():
                    gender = "Girls"

                sport_obj = self.sports_map.get(sport_name.lower(), list(self.sports_map.values())[0])

                squad_a = self.resolve_squad(team_a_str, sport_obj["id"], gender)
                squad_b = self.resolve_squad(team_b_str, sport_obj["id"], gender)

                if not squad_a or not squad_b:
                    self.stats["fixtures"]["unparseable"] += 1
                    self.stats["fixtures"]["unparseable_details"].append(
                        f"Row {r}: Could not resolve squad for '{team_a_str}' or '{team_b_str}'"
                    )
                    continue

                status = "scheduled"
                winner_id = None
                is_draw = False
                score_a = 0
                score_b = 0
                score_diff = 0
                summary = ""

                if not score_str:
                    self.stats["fixtures"]["unplayed"] += 1
                else:
                    match_score = re.search(r'(\d+)\s*[-–]\s*(\d+)', score_str)
                    if match_score:
                        score_a = int(match_score.group(1))
                        score_b = int(match_score.group(2))
                        score_diff = abs(score_a - score_b)
                        status = "completed"
                        summary = f"{score_a} - {score_b}"

                        if score_a > score_b:
                            winner_id = squad_a["id"]
                        elif score_b > score_a:
                            winner_id = squad_b["id"]
                        else:
                            is_draw = True
                    else:
                        status = "scheduled"
                        self.stats["fixtures"]["unparseable"] += 1
                        self.stats["fixtures"]["unparseable_details"].append(
                            f"Row {r}: Unparseable score string '{score_str}' for match {squad_a['name']} vs {squad_b['name']}"
                        )

                match_record = {
                    "id": str(uuid.uuid4()),
                    "sport_id": sport_obj["id"],
                    "team_a_id": squad_a["id"],
                    "team_b_id": squad_b["id"],
                    "gender": gender,
                    "stage": "league",
                    "level": "HS",
                    "status": status,
                    "round_info": "League Game",
                    "winner_team_id": winner_id,
                    "is_draw": is_draw,
                    "score_team_a": score_a,
                    "score_team_b": score_b,
                    "score_difference": score_diff,
                    "score_summary": summary
                }

                if self.client:
                    try:
                        self.client.table("matches").insert(match_record).execute()
                        self.stats["fixtures"]["created"] += 1
                    except Exception as e:
                        print(f"    Error inserting match: {e}")
                else:
                    self.stats["fixtures"]["created"] += 1

    def resolve_squad(self, team_str: str, sport_id: str, gender: str) -> Optional[Dict[str, Any]]:
        for h_key, h_obj in self.houses_map.items():
            if h_key in team_str.lower():
                squad_label = "B" if " b" in team_str.lower() or "(b)" in team_str.lower() else "A"
                squad_key = f"{h_obj['id']}_{sport_id}_{gender}_{squad_label}"
                if squad_key in self.squads_map:
                    return self.squads_map[squad_key]

                fallback_key = f"{h_obj['id']}_{sport_id}_{gender}_A"
                if fallback_key in self.squads_map:
                    return self.squads_map[fallback_key]
        return None

    def print_summary(self):
        print("\n================ SEEDING SUMMARY REPORT ================")
        print(f"  Houses:   {self.stats['houses']['created']} created/upserted")
        print(f"  Sports:   {self.stats['sports']['created']} created/upserted")
        print(f"  Squads:   {self.stats['squads']['created']} created/upserted")
        print(f"  Players:  {self.stats['players']['created']} created/upserted")
        print(f"  Fixtures: {self.stats['fixtures']['created']} created")
        print(f"    - Unplayed fixtures (scheduled):   {self.stats['fixtures']['unplayed']}")
        print(f"    - Unparseable scores (logged):      {self.stats['fixtures']['unparseable']}")

        if self.stats["fixtures"]["unparseable_details"]:
            print("\n  Unparseable Fixtures Details:")
            for detail in self.stats["fixtures"]["unparseable_details"]:
                print(f"    * {detail}")
        print("========================================================")

    def run(self):
        self.load_workbook()
        self.seed_houses()
        self.seed_sports()
        self.ensure_default_squads()
        self.parse_house_rosters()
        self.parse_fixtures_and_scores()
        self.print_summary()

def main():
    parser = argparse.ArgumentParser(description="Seed DSS Sports Inter-House Meet data.")
    parser.add_argument("--file", default="seed-data/interhouse_meet.xlsx", help="Path to .xlsx file")
    args = parser.parse_args()

    supabase_client = None
    if SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY and "mock" not in SUPABASE_URL:
        try:
            from supabase import create_client
            supabase_client = create_client(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
            print(f"Connected to Supabase at {SUPABASE_URL}")
        except Exception as e:
            print(f"Could not initialize Supabase client: {e}. Running in dry-run mode.")

    seeder = InterHouseSeeder(args.file, supabase_client)
    seeder.run()

if __name__ == "__main__":
    main()
