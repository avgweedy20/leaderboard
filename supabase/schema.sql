-- ScoreBoard Supabase Schema & Migration
-- Supports Cricket, Football, Basketball, Generic Sports, Levels (ES, MS, HS),
-- CSV Bulk Imports, Dynamic Points, and Leaderboard calculations.

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. SPORTS TABLE
CREATE TABLE IF NOT EXISTS public.sports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL CHECK (type IN ('cricket', 'football', 'basketball', 'generic')),
    level TEXT DEFAULT 'ALL' CHECK (level IN ('ES', 'MS', 'HS', 'ALL')),
    point_win NUMERIC DEFAULT 3,
    point_draw NUMERIC DEFAULT 1,
    point_loss NUMERIC DEFAULT 0,
    is_lower_score_better BOOLEAN DEFAULT FALSE, -- True for time races (e.g. 100m)
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. TEAMS TABLE
CREATE TABLE IF NOT EXISTS public.teams (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    sport_id UUID REFERENCES public.sports(id) ON DELETE CASCADE,
    level TEXT DEFAULT 'HS' CHECK (level IN ('ES', 'MS', 'HS')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_team_per_sport_level UNIQUE (name, sport_id, level)
);

-- 3. PLAYERS TABLE
CREATE TABLE IF NOT EXISTS public.players (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    team_id UUID REFERENCES public.teams(id) ON DELETE CASCADE,
    grade TEXT,
    level TEXT DEFAULT 'HS' CHECK (level IN ('ES', 'MS', 'HS')),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. MATCHES TABLE
CREATE TABLE IF NOT EXISTS public.matches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sport_id UUID REFERENCES public.sports(id) ON DELETE CASCADE,
    team_a_id UUID REFERENCES public.teams(id) ON DELETE CASCADE,
    team_b_id UUID REFERENCES public.teams(id) ON DELETE CASCADE,
    level TEXT DEFAULT 'HS' CHECK (level IN ('ES', 'MS', 'HS')),
    status TEXT DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'live', 'completed', 'cancelled')),
    round_info TEXT, -- e.g. "Quarter-Final", "Round 1", "Final"
    winner_team_id UUID REFERENCES public.teams(id) ON DELETE SET NULL,
    is_draw BOOLEAN DEFAULT FALSE,
    score_summary TEXT,
    scheduled_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. CRICKET INNINGS & OVERS
CREATE TABLE IF NOT EXISTS public.cricket_innings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id UUID REFERENCES public.matches(id) ON DELETE CASCADE,
    team_id UUID REFERENCES public.teams(id) ON DELETE CASCADE,
    innings_number INT CHECK (innings_number IN (1, 2)),
    total_runs INT DEFAULT 0,
    total_wickets INT DEFAULT 0,
    total_overs NUMERIC(4, 1) DEFAULT 0.0,
    extras INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_innings_per_match UNIQUE (match_id, innings_number)
);

CREATE TABLE IF NOT EXISTS public.cricket_overs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    innings_id UUID REFERENCES public.cricket_innings(id) ON DELETE CASCADE,
    over_number INT NOT NULL,
    runs INT DEFAULT 0,
    wickets INT DEFAULT 0,
    extras INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_over_per_innings UNIQUE (innings_id, over_number)
);

-- 6. FOOTBALL EVENTS
CREATE TABLE IF NOT EXISTS public.football_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id UUID REFERENCES public.matches(id) ON DELETE CASCADE,
    team_id UUID REFERENCES public.teams(id) ON DELETE CASCADE,
    player_id UUID REFERENCES public.players(id) ON DELETE SET NULL,
    event_type TEXT CHECK (event_type IN ('goal', 'yellow_card', 'red_card', 'own_goal')),
    minute INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 7. BASKETBALL QUARTERS
CREATE TABLE IF NOT EXISTS public.basketball_quarters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id UUID REFERENCES public.matches(id) ON DELETE CASCADE,
    quarter INT CHECK (quarter >= 1 AND quarter <= 10), -- 1-4 for standard Qs, 5+ for OT
    team_a_score INT DEFAULT 0,
    team_b_score INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_quarter_per_match UNIQUE (match_id, quarter)
);

-- 8. GENERIC RESULTS
CREATE TABLE IF NOT EXISTS public.generic_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id UUID REFERENCES public.matches(id) ON DELETE CASCADE,
    team_id UUID REFERENCES public.teams(id) ON DELETE SET NULL,
    player_id UUID REFERENCES public.players(id) ON DELETE SET NULL,
    score NUMERIC NOT NULL,
    notes TEXT,
    rank INT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 9. TOURNAMENT BRACKETS
CREATE TABLE IF NOT EXISTS public.tournament_brackets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sport_id UUID REFERENCES public.sports(id) ON DELETE CASCADE,
    level TEXT DEFAULT 'HS' CHECK (level IN ('ES', 'MS', 'HS')),
    type TEXT CHECK (type IN ('single_elimination', 'round_robin')),
    structure_json JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 10. LEADERBOARD VIEW
CREATE OR REPLACE VIEW public.leaderboard_view AS
WITH match_stats AS (
    -- Team A stats from completed matches
    SELECT
        m.id as match_id,
        m.sport_id,
        m.level,
        m.team_a_id as team_id,
        CASE
            WHEN m.winner_team_id = m.team_a_id THEN 1 ELSE 0
        END as won,
        CASE
            WHEN m.is_draw = TRUE THEN 1 ELSE 0
        END as drawn,
        CASE
            WHEN m.winner_team_id IS NOT NULL AND m.winner_team_id != m.team_a_id AND m.is_draw = FALSE THEN 1 ELSE 0
        END as lost
    FROM public.matches m
    WHERE m.status = 'completed' AND m.team_a_id IS NOT NULL

    UNION ALL

    -- Team B stats from completed matches
    SELECT
        m.id as match_id,
        m.sport_id,
        m.level,
        m.team_b_id as team_id,
        CASE
            WHEN m.winner_team_id = m.team_b_id THEN 1 ELSE 0
        END as won,
        CASE
            WHEN m.is_draw = TRUE THEN 1 ELSE 0
        END as drawn,
        CASE
            WHEN m.winner_team_id IS NOT NULL AND m.winner_team_id != m.team_b_id AND m.is_draw = FALSE THEN 1 ELSE 0
        END as lost
    FROM public.matches m
    WHERE m.status = 'completed' AND m.team_b_id IS NOT NULL
)
SELECT
    t.id as team_id,
    t.name as team_name,
    t.sport_id,
    s.name as sport_name,
    s.type as sport_type,
    t.level,
    COUNT(ms.match_id) as played,
    COALESCE(SUM(ms.won), 0) as wins,
    COALESCE(SUM(ms.drawn), 0) as draws,
    COALESCE(SUM(ms.lost), 0) as losses,
    COALESCE(SUM(
        ms.won * s.point_win +
        ms.drawn * s.point_draw +
        ms.lost * s.point_loss
    ), 0) as points
FROM public.teams t
JOIN public.sports s ON t.sport_id = s.id
LEFT JOIN match_stats ms ON t.id = ms.team_id
GROUP BY t.id, t.name, t.sport_id, s.name, s.type, t.level, s.point_win, s.point_draw, s.point_loss;

-- ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE public.sports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.players ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.matches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cricket_innings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cricket_overs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.football_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.basketball_quarters ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.generic_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tournament_brackets ENABLE ROW LEVEL SECURITY;

-- Public Read Policies
CREATE POLICY "Public Read Sports" ON public.sports FOR SELECT USING (true);
CREATE POLICY "Public Read Teams" ON public.teams FOR SELECT USING (true);
CREATE POLICY "Public Read Players" ON public.players FOR SELECT USING (true);
CREATE POLICY "Public Read Matches" ON public.matches FOR SELECT USING (true);
CREATE POLICY "Public Read Cricket Innings" ON public.cricket_innings FOR SELECT USING (true);
CREATE POLICY "Public Read Cricket Overs" ON public.cricket_overs FOR SELECT USING (true);
CREATE POLICY "Public Read Football Events" ON public.football_events FOR SELECT USING (true);
CREATE POLICY "Public Read Basketball Quarters" ON public.basketball_quarters FOR SELECT USING (true);
CREATE POLICY "Public Read Generic Results" ON public.generic_results FOR SELECT USING (true);
CREATE POLICY "Public Read Brackets" ON public.tournament_brackets FOR SELECT USING (true);

-- Admin Write Policies
CREATE POLICY "Admin Write Sports" ON public.sports FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Teams" ON public.teams FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Players" ON public.players FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Matches" ON public.matches FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Cricket Innings" ON public.cricket_innings FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Cricket Overs" ON public.cricket_overs FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Football Events" ON public.football_events FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Basketball Quarters" ON public.basketball_quarters FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Generic Results" ON public.generic_results FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Brackets" ON public.tournament_brackets FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
