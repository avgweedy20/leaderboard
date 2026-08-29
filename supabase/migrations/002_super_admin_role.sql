-- ScoreBoard super-admin role + richer audit log migration
-- =========================================================
-- Adds two capacity upgrades on TOP of 001 (no need to re-run schema.sql or
-- 001). Safe to re-run: every statement is idempotent.
--
--   1. public.admins.role
--        text, CHECK (role IN ('admin','superadmin')), DEFAULT 'admin'.
--        Existing accounts start as plain 'admin'. Promote your first super
--        admin manually (Supabase dashboard SQL editor or psql):
--
--          UPDATE public.admins
--             SET role = 'superadmin'
--           WHERE email = 'you@school.edu';
--
--   2. public.admin_audit_log.details
--        free-text column where the backend records what an action touched
--        (e.g. "score 1 - 0 -> 3 - 1", "created team 'Karnali Boys Futsal A'")
--        so the audit log can be filtered and understood.
--
-- Roles are enforced server-side (decorators in app/app.py) AND the admin
-- registry stays hidden from anon/authenticated clients (REVOKEs from 001),
-- so role data is not exposed through PostgREST.
--
-- Run with the Supabase dashboard (SQL editor) or psql:
--   psql "$DATABASE_URL" -f supabase/migrations/002_super_admin_role.sql

BEGIN;

ALTER TABLE public.admins
    ADD COLUMN IF NOT EXISTS role text NOT NULL DEFAULT 'admin'
    CHECK (role IN ('admin', 'superadmin'));

ALTER TABLE public.admin_audit_log
    ADD COLUMN IF NOT EXISTS details text;

COMMIT;