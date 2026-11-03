-- Scores and result state for player-level schedule matchups.
-- Runs after 003_schedule_matchups.sql. Also folded into supabase/schema.sql
-- for fresh installs.

ALTER TABLE public.schedule_matchups
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'scheduled',
    ADD COLUMN IF NOT EXISTS score_a TEXT,
    ADD COLUMN IF NOT EXISTS score_b TEXT,
    ADD COLUMN IF NOT EXISTS score_summary TEXT,
    ADD COLUMN IF NOT EXISTS winner_house TEXT;
