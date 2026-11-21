-- Ensure result columns exist, then refresh PostgREST's schema cache.
-- This safely repairs projects where migration 003 was applied without 004.
ALTER TABLE public.schedule_matchups
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'scheduled',
    ADD COLUMN IF NOT EXISTS score_a TEXT,
    ADD COLUMN IF NOT EXISTS score_b TEXT,
    ADD COLUMN IF NOT EXISTS score_summary TEXT,
    ADD COLUMN IF NOT EXISTS winner_house TEXT;

NOTIFY pgrst, 'reload schema';
