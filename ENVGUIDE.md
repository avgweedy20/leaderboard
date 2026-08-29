# ScoreBoard Environment Variable & Secure Android Build Guide

This guide explains how to safely configure environment variables for **ScoreBoard** without committing secrets to public GitHub repositories, and how Admin authentication and database mode indicators work across both the Web app and native Android client.

---

## 1. Secure Architecture Overview

To maintain security while allowing Admin capabilities in both clients:

1. **Service-Role Key Security (Server-Side Only):**
   - The Supabase **`SUPABASE_SERVICE_ROLE_KEY`** is stored ONLY on the server (Flask backend in `app/app.py` or host environment like Vercel/Render).
   - It is **NEVER** embedded into client-side Web JS or bundled into the compiled Android `.apk` binary (preventing reverse-engineering / APK decompilation leaks).

2. **Admin Authentication in Web & Android:**
   - Both the Web frontend and native Android client log in via the backend endpoint `/api/auth/login` using email and password.
   - Multiple admin accounts are supported. Credentials are NEVER stored in code or environment variables. Admins are real Supabase Auth users whose email is registered in the `public.admins` table.
   - Two roles exist: **admin** (manages squads, players, scores) and **superadmin** (also manages admin accounts and views/filters the audit log). Create/remove admins with the CLI: `python manage_admins.py add <email> [--role superadmin]`.
   - Login is fail-closed (there is no default admin), throttled per IP + per email (5 tries / 5 min), and limited to 5 concurrent sessions per account.
   - Successful logins receive a random, expiring (6-hour), server-side registered token (stored in `public.admin_sessions`) that is revoked on logout and on account removal — no static token is ever accepted.
   - The Android app and Web app send this Bearer token in the `Authorization` header (`Authorization: Bearer <token>`) for write actions (scoring matches, creating sports, generating brackets, bulk CSV imports).

3. **Public Reads (Anon Key):**
   - Public users can view the live leaderboard, scheduled matches, and tournament brackets without logging in.

---

## 2. Setting Up Environment Variables Locally

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```

2. Open `.env` and fill in your Supabase project credentials:
   ```ini
   SUPABASE_URL=https://your-supabase-project.supabase.co
   SUPABASE_ANON_KEY=your-public-anon-key
   SUPABASE_SERVICE_ROLE_KEY=your-secret-service-role-key
   FLASK_SECRET_KEY=your-random-flask-key
   ```

3. Ensure `.env` is listed in your `.gitignore` (already configured in this repository).

## 2b. Managing Admin Accounts

Admin credentials are never placed in environment variables or code. Run the CLI on the server:

```bash
python manage_admins.py add admin1@school.edu     # prompts for a hidden password (min 6 chars)
python manage_admins.py add admin2@school.edu --stdin   # scripted setups
python manage_admins.py add admin@sports.com --role superadmin   # grant/require super-admin
python manage_admins.py reset-password admin1@school.edu
python manage_admins.py remove admin1@school.edu
python manage_admins.py list
```

- Admin credentials are never placed in environment variables or code. The CLI operates against Supabase Auth + the `public.admins` table; admin sessions are stored server-side in `public.admin_sessions`, and all admin-management actions (login, add, remove, reset-password, view log) are recorded in `public.admin_audit_log`.
- Roles: every account is `admin` or `superadmin`. Only superadmins can add/remove admins, reset other admins' passwords, and view/filter the audit log. A superadmin can never remove the last superadmin account. The first promotion is done by applying `supabase/migrations/002_super_admin_role.sql` (adds the `role` column + audit `details` column) and then either `UPDATE public.admins SET role='superadmin' WHERE email='...'` or `python manage_admins.py add <email> --role superadmin`.

---

## 3. GitHub Repository Secrets for Automated Android APK Builds

To configure your GitHub Actions workflow to build the APK pointing to your production backend API without committing URLs or secrets to code:

### Step 1: Add GitHub Repository Secrets
1. Go to your GitHub repository -> **Settings** -> **Secrets and variables** -> **Actions**.
2. Under **Repository secrets**, click **New repository secret**.
3. Create secret **`API_BASE_URL`**:
   - Value: `https://your-hosted-scoreboard.vercel.app/` (Must end with a trailing `/`)
4. Create secret **`SUPABASE_URL`**:
   - Value: `https://your-project.supabase.co`
5. Create secret **`SUPABASE_ANON_KEY`**:
   - Value: `your-anon-key`

### Step 2: Secret Injection During APK Compilation
The GitHub Actions workflow in `.github/workflows/android.yml` automatically passes the `API_BASE_URL` secret directly into Gradle during the build step:
```bash
./gradlew assembleDebug -PAPI_BASE_URL="${{ secrets.API_BASE_URL }}"
```
Gradle injects this into Android's `BuildConfig.API_BASE_URL`, allowing Retrofit to connect to your live backend server in the compiled APK.

---

## 4. Backend & Database Mode Indicator (Supabase Postgres vs Unconfigured)

Both the Web frontend and Android app query the `/api/health` endpoint:
```json
{
  "status": "healthy",
  "supabase_connected": true,
  "mode": "supabase"
}
```

- **Configured mode (`mode: "supabase"`):** Displays `"Connected: Postgres DB [DEBUG]"` in the app header/settings when Supabase credentials are present in `.env`.
- **Unconfigured mode (`mode: "unconfigured"`):** Displays an `"Unconfigured"` health badge and all data/admin endpoints fail closed with an `HTTP 503 Supabase not configured` response. The server no longer contains any in-memory or SQLite mock data — without a live Supabase connection the app serves only the static pages and API health, not fake data.
