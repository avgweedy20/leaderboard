import os
import sys

import pytest

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
sys.path.insert(0, os.path.join(_HERE, "fixtures"))

from fake_supabase import FakeSupabase  # noqa: E402
from seed_data import SEED  # noqa: E402


@pytest.fixture(scope="session", autouse=True)
def wired_fake_supabase():
    """Wire an in-memory Supabase stand-in into the Flask app.

    The backend no longer ships a mock DB; test-only fixture data comes from
    tests/fixtures/seed_data.py (the frozen former mock) served through the
    FakeSupabase client so the Supabase code paths are what get exercised.
    """
    from app import app as app_module

    fake = FakeSupabase(SEED)
    app_module.supabase_client = fake
    return fake