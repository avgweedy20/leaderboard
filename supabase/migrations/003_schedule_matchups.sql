-- Player-level schedule matchups
-- Powers the individual/doubles pairing cards on /schedule and the
-- /admin/matchups editor (manual entry + CSV import).

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS public.schedule_matchups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_key TEXT NOT NULL,
    stage TEXT,
    side_a_house TEXT NOT NULL,
    side_a_player_1 TEXT NOT NULL,
    side_a_player_2 TEXT,
    side_b_house TEXT NOT NULL,
    side_b_player_1 TEXT NOT NULL,
    side_b_player_2 TEXT,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_schedule_matchups_event
    ON public.schedule_matchups (event_key, sort_order);

ALTER TABLE public.schedule_matchups ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public Read Schedule Matchups" ON public.schedule_matchups;
CREATE POLICY "Public Read Schedule Matchups" ON public.schedule_matchups
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "Admin Write Schedule Matchups" ON public.schedule_matchups;
CREATE POLICY "Admin Write Schedule Matchups" ON public.schedule_matchups
    FOR ALL USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());

GRANT SELECT ON public.schedule_matchups TO anon, authenticated;
GRANT ALL ON public.schedule_matchups TO service_role;
