-- Refresh PostgREST's schema cache after schedule_matchups has been created.
-- This is also useful for projects where 003/004 were applied before the API
-- schema cache refreshed or where the application points at a different project.
NOTIFY pgrst, 'reload schema';
