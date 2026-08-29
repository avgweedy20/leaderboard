-- ScoreBoard incremental security migration
-- ===========================================
-- Applies the admin-gating and server-side-session changes on TOP of an
-- existing database that already holds live data (you do NOT need to re-run
-- supabase/schema.sql). Safe to re-run: every statement is idempotent.
--
-- What this does:
--   1. Creates public.admins (the admin registry) if missing.
--   2. Creates public.app_is_admin() used by RLS write policies.
--   3. Creates public.admin_sessions + public.admin_audit_log and locks down
--      admins / admin_sessions / admin_audit_log / migration_logs so that
--      anon and authenticated clients can never read or write them.
--   4. Enables RLS everywhere and REPLACES every existing table policy apart
--      from our own "Public Read ..." / "Admin Write ..." ones, so no stale
--      "auth.role() = 'authenticated'" write bypass survives.
--
-- Run with the Supabase dashboard (SQL editor) or psql:
--   psql "$DATABASE_URL" -f supabase/migrations/001_admin_security.sql

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. ADMIN REGISTRY
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.admins (
    email      text PRIMARY KEY,
    is_active  boolean NOT NULL DEFAULT TRUE,
    created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.admins ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT TRUE;
ALTER TABLE public.admins ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

-- ---------------------------------------------------------------------------
-- 2. ADMIN CHECK FUNCTION
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.app_is_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.admins a
    WHERE lower(a.email) = lower(auth.jwt() ->> 'email') AND a.is_active = TRUE
  );
$$;

-- ---------------------------------------------------------------------------
-- 3. ADMIN SESSIONS / AUDIT LOG + LOCKDOWNS
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.admin_sessions (
    token_hash text PRIMARY KEY,
    email      text NOT NULL,
    expires_at double precision NOT NULL
);
ALTER TABLE public.admin_sessions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.admin_sessions FROM anon, authenticated;

CREATE TABLE IF NOT EXISTS public.admin_audit_log (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    action       text NOT NULL,
    actor_email  text,
    target_email text,
    ip_address   text,
    created_at   timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.admin_audit_log ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.admin_audit_log FROM anon, authenticated;

REVOKE ALL ON public.admins FROM anon, authenticated;
REVOKE ALL ON public.migration_logs FROM anon, authenticated;

-- ---------------------------------------------------------------------------
-- 4. RLS ENABLE (idempotent)
-- ---------------------------------------------------------------------------
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
ALTER TABLE public.admins ENABLE ROW LEVEL SECURITY;

-- ---------------------------------------------------------------------------
-- 5. POLICY SWEEP
-- Remove any policy that is not one of our own on the public data tables,
-- then remove EVERY policy on the internal tables. This is what kills the old
-- "any authenticated Supabase account can write" bypass for good.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  tbl text;
  disp text;
  pol record;
BEGIN
  FOR tbl, disp IN SELECT v.t, v.d FROM (VALUES
      ('houses', 'Houses'),
      ('sports', 'Sports'),
      ('tournament_groups', 'Groups'),
      ('teams', 'Teams'),
      ('players', 'Players'),
      ('matches', 'Matches'),
      ('cricket_innings', 'Cricket Innings'),
      ('cricket_overs', 'Cricket Overs'),
      ('football_events', 'Football Events'),
      ('basketball_quarters', 'Basketball Quarters'),
      ('generic_results', 'Generic Results'),
      ('tournament_brackets', 'Brackets')
  ) AS v(t, d)
  LOOP
    FOR pol IN SELECT policyname FROM pg_policies
               WHERE schemaname = 'public' AND tablename = tbl
    LOOP
      IF pol.policyname <> 'Public Read ' || disp
         AND pol.policyname <> 'Admin Write ' || disp THEN
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', pol.policyname, tbl);
      END IF;
    END LOOP;
  END LOOP;

  FOREACH tbl IN ARRAY ARRAY['admins', 'admin_sessions', 'admin_audit_log', 'migration_logs']
  LOOP
    FOR pol IN SELECT policyname FROM pg_policies
               WHERE schemaname = 'public' AND tablename = tbl
    LOOP
      EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', pol.policyname, tbl);
    END LOOP;
  END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- 6. PUBLIC READS + ADMIN-ONLY WRITES
-- DROP-IF-EXISTS first so re-runs over a database that already has these
-- policies (e.g. schema.sql was applied earlier) never hit 42710.
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS "Public Read Houses" ON public.houses;
DROP POLICY IF EXISTS "Public Read Sports" ON public.sports;
DROP POLICY IF EXISTS "Public Read Groups" ON public.tournament_groups;
DROP POLICY IF EXISTS "Public Read Teams" ON public.teams;
DROP POLICY IF EXISTS "Public Read Players" ON public.players;
DROP POLICY IF EXISTS "Public Read Matches" ON public.matches;
DROP POLICY IF EXISTS "Public Read Cricket Innings" ON public.cricket_innings;
DROP POLICY IF EXISTS "Public Read Cricket Overs" ON public.cricket_overs;
DROP POLICY IF EXISTS "Public Read Football Events" ON public.football_events;
DROP POLICY IF EXISTS "Public Read Basketball Quarters" ON public.basketball_quarters;
DROP POLICY IF EXISTS "Public Read Generic Results" ON public.generic_results;
DROP POLICY IF EXISTS "Public Read Brackets" ON public.tournament_brackets;

DROP POLICY IF EXISTS "Admin Write Houses" ON public.houses;
DROP POLICY IF EXISTS "Admin Write Sports" ON public.sports;
DROP POLICY IF EXISTS "Admin Write Groups" ON public.tournament_groups;
DROP POLICY IF EXISTS "Admin Write Teams" ON public.teams;
DROP POLICY IF EXISTS "Admin Write Players" ON public.players;
DROP POLICY IF EXISTS "Admin Write Matches" ON public.matches;
DROP POLICY IF EXISTS "Admin Write Cricket Innings" ON public.cricket_innings;
DROP POLICY IF EXISTS "Admin Write Cricket Overs" ON public.cricket_overs;
DROP POLICY IF EXISTS "Admin Write Football Events" ON public.football_events;
DROP POLICY IF EXISTS "Admin Write Basketball Quarters" ON public.basketball_quarters;
DROP POLICY IF EXISTS "Admin Write Generic Results" ON public.generic_results;
DROP POLICY IF EXISTS "Admin Write Brackets" ON public.tournament_brackets;
DROP POLICY IF EXISTS "Admin Write Migration Logs" ON public.migration_logs;

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

CREATE POLICY "Admin Write Houses" ON public.houses FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Sports" ON public.sports FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Groups" ON public.tournament_groups FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Teams" ON public.teams FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Players" ON public.players FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Matches" ON public.matches FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Cricket Innings" ON public.cricket_innings FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Cricket Overs" ON public.cricket_overs FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Football Events" ON public.football_events FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Basketball Quarters" ON public.basketball_quarters FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Generic Results" ON public.generic_results FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Brackets" ON public.tournament_brackets FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());
CREATE POLICY "Admin Write Migration Logs" ON public.migration_logs FOR ALL
  USING (public.app_is_admin()) WITH CHECK (public.app_is_admin());

COMMIT;