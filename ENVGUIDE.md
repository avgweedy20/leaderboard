# ScoreBoard Environment Variable & Secure Android Build Guide

This guide explains how to safely configure environment variables for **ScoreBoard** without committing secrets to public GitHub repositories, and how Admin authentication and database mode indicators work across both the Web app and native Android client.

---

## 1. Secure Architecture Overview

To maintain security while allowing Admin capabilities in both clients:

1. **Service-Role Key Security (Server-Side Only):**
   - The Supabase **`SUPABASE_SERVICE_ROLE_KEY`** is stored ONLY on the server (Flask backend in `app/app.py` or host environment like Vercel/Render).
   - It is **NEVER** embedded into client-side Web JS or bundled into the compiled Android `.apk` binary (preventing reverse-engineering / APK decompilation leaks).

2. **Admin Authentication in Web & Android:**
   - Both the Web frontend and native Android client log in via the backend endpoint `/api/auth/login` using email and password (or Supabase Auth).
   - Upon successful login, the backend issues an Auth JWT access token.
   - The Android app and Web app send this Bearer token in the `Authorization` header (`Authorization: Bearer <jwt_token>`) for write actions (scoring matches, creating sports, generating brackets, bulk CSV imports).

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

---

## 3. GitHub Repository Secrets for Automated Android APK Builds

When building the Android APK via GitHub Actions workflow (`.github/workflows/android.yml`), you can inject the backend API URL dynamically without exposing it in repository files:

### Step 1: Add GitHub Repository Secrets
1. Go to your GitHub repository -> **Settings** -> **Secrets and variables** -> **Actions**.
2. Click **New repository secret**.
3. Add the following secrets:
   - `API_BASE_URL`: Your hosted Flask backend URL (e.g. `https://your-scoreboard.vercel.app/`)
   - `SUPABASE_URL`: Your Supabase URL
   - `SUPABASE_ANON_KEY`: Your Supabase Anon Key

### Step 2: GitHub Actions Build Process
The workflow in `.github/workflows/android.yml` automatically compiles the Android APK using Gradle and attaches the generated `app-debug.apk` to an automated **GitHub Release** on every commit.

---

## 4. Backend & Database Mode Indicator (Supabase Postgres vs In-Memory Mock)

Both the Web frontend and Android app query the `/api/health` endpoint:
```json
{
  "status": "healthy",
  "supabase_connected": true,
  "mode": "supabase"
}
```

- **Postgres Mode (`mode: "supabase"`):** Displays `"Connected: Supabase Postgres DB"` in the app header/settings.
- **Development Mock Mode (`mode: "mock_in_memory"`):** Displays `"Development: In-Memory Mock DB"` when running locally without a live Supabase connection.
