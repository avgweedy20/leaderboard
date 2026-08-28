-- ScoreBoard / Inter-House Sports Meet Supabase Schema
-- Supports Houses, Squads (Teams), Players with Roll Numbers, Matches with League/Final Stages,
-- Sport Scoring Templates, Per-Sport/Gender Standings, Overall House Standings, and Final Qualifiers.

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. HOUSES TABLE
CREATE TABLE IF NOT EXISTS public.houses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL UNIQUE,
    color_hex TEXT NOT NULL,
    short_code TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Seed default House configuration
INSERT INTO public.houses (name, color_hex, short_code) VALUES
('Karnali', '#10B981', 'KAR'),
('Koshi', '#0EA5E9', 'KOS'),
('Mahakali', '#8B5CF6', 'MAH'),
('Mechi', '#F97316', 'MEC')
ON CONFLICT (name) DO UPDATE SET
    color_hex = EXCLUDED.color_hex,
    short_code = EXCLUDED.short_code;

-- 2. SPORTS TABLE
CREATE TABLE IF NOT EXISTS public.sports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL CHECK (type IN ('cricket', 'football', 'basketball', 'generic')),
    level TEXT DEFAULT 'HS' CHECK (level IN ('ES', 'MS', 'HS', 'ALL')),
    is_lower_score_better BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Seed default Sports for Inter-House Meet
INSERT INTO public.sports (name, type, level) VALUES
('Futsal', 'football', 'HS'),
('Basketball', 'basketball', 'HS'),
('Cricksal', 'generic', 'HS')
ON CONFLICT (name) DO NOTHING;

-- 2b. TOURNAMENT GROUPS TABLE (per sport + gender format & points configuration)
CREATE TABLE IF NOT EXISTS public.tournament_groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sport_id UUID REFERENCES public.sports(id) ON DELETE CASCADE,
    gender TEXT CHECK (gender IN ('Boys', 'Girls', 'Mixed')),
    format TEXT CHECK (format IN ('round_robin', 'pool_to_semis')) DEFAULT 'round_robin',
    point_win NUMERIC DEFAULT 3,
    point_draw NUMERIC DEFAULT 1,
    point_loss NUMERIC DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_sport_gender_group UNIQUE (sport_id, gender)
);

-- Seed default Tournament Groups
INSERT INTO public.tournament_groups (sport_id, gender, format, point_win, point_draw, point_loss)
SELECT s.id, g.gender, g.format, 3, 1, 0
FROM public.sports s
CROSS JOIN (
    VALUES
        ('Boys', 'Futsal', 'pool_to_semis'),
        ('Girls', 'Futsal', 'round_robin'),
        ('Boys', 'Basketball', 'round_robin'),
        ('Girls', 'Basketball', 'round_robin'),
        ('Boys', 'Cricksal', 'pool_to_semis'),
        ('Girls', 'Cricksal', 'round_robin')
) AS g(gender, sport_name, format)
WHERE s.name = g.sport_name
ON CONFLICT (sport_id, gender) DO UPDATE SET
    format = EXCLUDED.format;

-- 3. TEAMS / SQUADS TABLE
CREATE TABLE IF NOT EXISTS public.teams (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    house_id UUID REFERENCES public.houses(id) ON DELETE CASCADE,
    gender TEXT CHECK (gender IN ('Boys', 'Girls', 'Mixed')),
    squad_label TEXT CHECK (squad_label IN ('A', 'B', 'C', 'D')),
    pool TEXT CHECK (pool IN ('A', 'B')),
    sport_id UUID REFERENCES public.sports(id) ON DELETE CASCADE,
    level TEXT DEFAULT 'HS' CHECK (level IN ('ES', 'MS', 'HS')),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Add unique constraint on house squads
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unique_house_squad_per_sport'
    ) THEN
        ALTER TABLE public.teams
        ADD CONSTRAINT unique_house_squad_per_sport
        UNIQUE (house_id, sport_id, gender, squad_label, level);
    END IF;
END $$;

-- 4. PLAYERS TABLE
CREATE TABLE IF NOT EXISTS public.players (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    team_id UUID REFERENCES public.teams(id) ON DELETE CASCADE,
    roll_number TEXT,
    grade TEXT,
    section TEXT,
    gender TEXT CHECK (gender IN ('Boys', 'Girls', 'Mixed')),
    level TEXT DEFAULT 'HS' CHECK (level IN ('ES', 'MS', 'HS')),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Ensure roll number index / constraint where available
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unique_player_roll_number'
    ) THEN
        ALTER TABLE public.players
        ADD CONSTRAINT unique_player_roll_number
        UNIQUE (roll_number);
    END IF;
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;

-- 5. MATCHES TABLE
CREATE TABLE IF NOT EXISTS public.matches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sport_id UUID REFERENCES public.sports(id) ON DELETE CASCADE,
    team_a_id UUID REFERENCES public.teams(id) ON DELETE CASCADE,
    team_b_id UUID REFERENCES public.teams(id) ON DELETE CASCADE,
    gender TEXT CHECK (gender IN ('Boys', 'Girls', 'Mixed')),
    stage TEXT DEFAULT 'league' CHECK (stage IN ('league', 'semifinal', 'final')),
    level TEXT DEFAULT 'HS' CHECK (level IN ('ES', 'MS', 'HS')),
    status TEXT DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'live', 'completed', 'cancelled')),
    round_info TEXT,
    winner_team_id UUID REFERENCES public.teams(id) ON DELETE SET NULL,
    is_draw BOOLEAN DEFAULT FALSE,
    score_team_a INT DEFAULT 0,
    score_team_b INT DEFAULT 0,
    score_difference INT DEFAULT 0,
    score_summary TEXT,
    scheduled_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Ensure stage constraint permits 'semifinal' for existing databases
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'matches_stage_check'
    ) THEN
        ALTER TABLE public.matches DROP CONSTRAINT matches_stage_check;
    END IF;
    ALTER TABLE public.matches ADD CONSTRAINT matches_stage_check CHECK (stage IN ('league', 'semifinal', 'final'));
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;

-- 6. CRICKET INNINGS & OVERS (Maintained for backward compatibility)
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

-- 7. FOOTBALL EVENTS
CREATE TABLE IF NOT EXISTS public.football_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id UUID REFERENCES public.matches(id) ON DELETE CASCADE,
    team_id UUID REFERENCES public.teams(id) ON DELETE CASCADE,
    player_id UUID REFERENCES public.players(id) ON DELETE SET NULL,
    event_type TEXT CHECK (event_type IN ('goal', 'yellow_card', 'red_card', 'own_goal')),
    minute INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 8. BASKETBALL QUARTERS
CREATE TABLE IF NOT EXISTS public.basketball_quarters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id UUID REFERENCES public.matches(id) ON DELETE CASCADE,
    quarter INT CHECK (quarter >= 1 AND quarter <= 10),
    team_a_score INT DEFAULT 0,
    team_b_score INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_quarter_per_match UNIQUE (match_id, quarter)
);

-- 9. GENERIC RESULTS
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

-- 10. TOURNAMENT BRACKETS
CREATE TABLE IF NOT EXISTS public.tournament_brackets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sport_id UUID REFERENCES public.sports(id) ON DELETE CASCADE,
    gender TEXT CHECK (gender IN ('Boys', 'Girls', 'Mixed')),
    level TEXT DEFAULT 'HS' CHECK (level IN ('ES', 'MS', 'HS')),
    type TEXT CHECK (type IN ('single_elimination', 'round_robin')),
    structure_json JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 11. MIGRATION LOGS TABLE (For unmappable legacy teams & seeder reporting)
CREATE TABLE IF NOT EXISTS public.migration_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    log_type TEXT NOT NULL,
    message TEXT NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 12. COMPUTED VIEWS

-- PER-SPORT / GENDER LEADERBOARD VIEW
CREATE OR REPLACE VIEW public.leaderboard_view AS
WITH match_stats AS (
    SELECT
        m.id as match_id,
        m.sport_id,
        m.gender,
        m.level,
        m.stage,
        m.team_a_id as team_id,
        m.score_team_a as score_for,
        m.score_team_b as score_against,
        (m.score_team_a - m.score_team_b) as diff,
        CASE WHEN m.winner_team_id = m.team_a_id THEN 1 ELSE 0 END as won,
        CASE WHEN m.is_draw = TRUE THEN 1 ELSE 0 END as drawn,
        CASE WHEN m.winner_team_id IS NOT NULL AND m.winner_team_id != m.team_a_id AND m.is_draw = FALSE THEN 1 ELSE 0 END as lost
    FROM public.matches m
    WHERE m.status = 'completed' AND m.team_a_id IS NOT NULL AND m.stage = 'league'

    UNION ALL

    SELECT
        m.id as match_id,
        m.sport_id,
        m.gender,
        m.level,
        m.stage,
        m.team_b_id as team_id,
        m.score_team_b as score_for,
        m.score_team_a as score_against,
        (m.score_team_b - m.score_team_a) as diff,
        CASE WHEN m.winner_team_id = m.team_b_id THEN 1 ELSE 0 END as won,
        CASE WHEN m.is_draw = TRUE THEN 1 ELSE 0 END as drawn,
        CASE WHEN m.winner_team_id IS NOT NULL AND m.winner_team_id != m.team_b_id AND m.is_draw = FALSE THEN 1 ELSE 0 END as lost
    FROM public.matches m
    WHERE m.status = 'completed' AND m.team_b_id IS NOT NULL AND m.stage = 'league'
)
SELECT
    t.id as team_id,
    t.name as team_name,
    t.house_id,
    h.name as house_name,
    h.color_hex as house_color,
    h.short_code as house_short_code,
    COALESCE(t.gender, ms.gender, 'Boys') as gender,
    t.squad_label,
    t.pool,
    t.sport_id,
    s.name as sport_name,
    s.type as sport_type,
    t.level,
    COUNT(ms.match_id) as played,
    COALESCE(SUM(ms.won), 0) as wins,
    COALESCE(SUM(ms.drawn), 0) as draws,
    COALESCE(SUM(ms.lost), 0) as losses,
    COALESCE(SUM(ms.score_for), 0) as score_for,
    COALESCE(SUM(ms.score_against), 0) as score_against,
    COALESCE(SUM(ms.diff), 0) as score_difference,
    COALESCE(SUM(
        ms.won * COALESCE(tg.point_win, 3) +
        ms.drawn * COALESCE(tg.point_draw, 1) +
        ms.lost * COALESCE(tg.point_loss, 0)
    ), 0) as points,
    DENSE_RANK() OVER (
        PARTITION BY t.sport_id, COALESCE(t.gender, ms.gender, 'Boys'), t.level
        ORDER BY
            COALESCE(SUM(
                ms.won * COALESCE(tg.point_win, 3) +
                ms.drawn * COALESCE(tg.point_draw, 1) +
                ms.lost * COALESCE(tg.point_loss, 0)
            ), 0) DESC,
            COALESCE(SUM(ms.diff), 0) DESC,
            COALESCE(SUM(ms.score_for), 0) DESC
    ) as rank
FROM public.teams t
JOIN public.sports s ON t.sport_id = s.id
LEFT JOIN public.houses h ON t.house_id = h.id
LEFT JOIN public.tournament_groups tg ON tg.sport_id = t.sport_id AND tg.gender = t.gender
LEFT JOIN match_stats ms ON t.id = ms.team_id
GROUP BY t.id, t.name, t.house_id, h.name, h.color_hex, h.short_code, t.gender, ms.gender, t.squad_label, t.pool, t.sport_id, s.name, s.type, t.level, tg.point_win, tg.point_draw, tg.point_loss;

-- HOUSE OVERALL STANDINGS VIEW
CREATE OR REPLACE VIEW public.house_overall_standings AS
SELECT
    h.id as house_id,
    h.name as house_name,
    h.color_hex,
    h.short_code,
    COUNT(DISTINCT t.id) as total_squads,
    COALESCE(SUM(lv.played), 0) as matches_played,
    COALESCE(SUM(lv.wins), 0) as total_wins,
    COALESCE(SUM(lv.draws), 0) as total_draws,
    COALESCE(SUM(lv.losses), 0) as total_losses,
    COALESCE(SUM(lv.score_difference), 0) as total_score_difference,
    COALESCE(SUM(lv.points), 0) as total_points,
    DENSE_RANK() OVER (
        ORDER BY
            COALESCE(SUM(lv.points), 0) DESC,
            COALESCE(SUM(lv.score_difference), 0) DESC,
            COALESCE(SUM(lv.wins), 0) DESC
    ) as rank
FROM public.houses h
LEFT JOIN public.teams t ON t.house_id = h.id
LEFT JOIN public.leaderboard_view lv ON lv.team_id = t.id
GROUP BY h.id, h.name, h.color_hex, h.short_code;

-- FINAL QUALIFIERS VIEW
CREATE OR REPLACE VIEW public.final_qualifiers_view AS
SELECT *
FROM public.leaderboard_view
WHERE rank <= 2;

-- ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE public.houses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tournament_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.players ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.matches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cricket_innings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cricket_overs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.football_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.basketball_quarters ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.generic_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tournament_brackets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.migration_logs ENABLE ROW LEVEL SECURITY;

-- Public Read Policies
CREATE POLICY "Public Read Houses" ON public.houses FOR SELECT USING (true);
CREATE POLICY "Public Read Sports" ON public.sports FOR SELECT USING (true);
CREATE POLICY "Public Read Groups" ON public.tournament_groups FOR SELECT USING (true);
CREATE POLICY "Public Read Teams" ON public.teams FOR SELECT USING (true);
CREATE POLICY "Public Read Players" ON public.players FOR SELECT USING (true);
CREATE POLICY "Public Read Matches" ON public.matches FOR SELECT USING (true);
CREATE POLICY "Public Read Cricket Innings" ON public.cricket_innings FOR SELECT USING (true);
CREATE POLICY "Public Read Cricket Overs" ON public.cricket_overs FOR SELECT USING (true);
CREATE POLICY "Public Read Football Events" ON public.football_events FOR SELECT USING (true);
CREATE POLICY "Public Read Basketball Quarters" ON public.basketball_quarters FOR SELECT USING (true);
CREATE POLICY "Public Read Generic Results" ON public.generic_results FOR SELECT USING (true);
CREATE POLICY "Public Read Brackets" ON public.tournament_brackets FOR SELECT USING (true);
CREATE POLICY "Public Read Migration Logs" ON public.migration_logs FOR SELECT USING (true);

-- Admin Write Policies
CREATE POLICY "Admin Write Houses" ON public.houses FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Sports" ON public.sports FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Groups" ON public.tournament_groups FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Teams" ON public.teams FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Players" ON public.players FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Matches" ON public.matches FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Cricket Innings" ON public.cricket_innings FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Cricket Overs" ON public.cricket_overs FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Football Events" ON public.football_events FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Basketball Quarters" ON public.basketball_quarters FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Generic Results" ON public.generic_results FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Brackets" ON public.tournament_brackets FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
CREATE POLICY "Admin Write Migration Logs" ON public.migration_logs FOR ALL USING (auth.role() = 'authenticated' OR auth.role() = 'service_role');
