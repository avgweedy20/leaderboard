import os
import pytest
import openpyxl
from seeder import InterHouseSeeder

@pytest.fixture
def sample_excel_file(tmp_path):
    excel_file = tmp_path / "test_interhouse_meet.xlsx"
    wb = openpyxl.Workbook()

    # Default sheet
    ws_default = wb.active
    ws_default.title = "Form Responses 1"
    ws_default.append(["Timestamp", "Full name", "Roll Number", "Grade", "Section", "House", "Gender", "Do you want to participate in Futsal?"])

    # House Roster Sheet
    ws_karnali = wb.create_sheet(title="Karnali House")
    # Row 1: Sport Header
    ws_karnali.cell(row=1, column=1, value="Futsal Boys")
    # Row 2: Column Headers
    headers = ["SN", "Roll Number", "Name", "Grade", "Section", "Gender", "Team"]
    for c_idx, h in enumerate(headers, start=1):
        ws_karnali.cell(row=2, column=c_idx, value=h)

    # Row 3 & 4: Players
    ws_karnali.append([1, "101", "Aarav Sharma", "11", "A", "Boys", "A"])
    ws_karnali.append([2, "102", "Bikram Thapa", "12", "B", "Boys", "B"])

    # Tie-sheet sheet
    ws_tiesheet = wb.create_sheet(title="Tie-sheet & Scores Update")
    ws_tiesheet.append(["SN", "Karnali A", "Vs", "Koshi A", "6-1=5", "Karnali=3", "10:00 AM"])
    ws_tiesheet.append(["SN", "Karnali B", "Vs", "Mechi A", "", "", "11:00 AM"])

    wb.save(excel_file)
    return str(excel_file)

def test_seeder_parses_sample_file(sample_excel_file):
    seeder = InterHouseSeeder(sample_excel_file, supabase_client=None)
    seeder.run()

    # Verify houses
    assert len(seeder.houses_map) == 4
    assert "karnali" in seeder.houses_map

    # Verify sports
    assert len(seeder.sports_map) == 3
    assert "futsal" in seeder.sports_map

    # Verify stats
    assert seeder.stats["houses"]["created"] == 4
    assert seeder.stats["sports"]["created"] == 3
    assert seeder.stats["squads"]["created"] >= 1
    assert seeder.stats["players"]["created"] >= 2
    assert seeder.stats["fixtures"]["created"] == 2
    assert seeder.stats["fixtures"]["unplayed"] == 1
