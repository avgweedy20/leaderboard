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
   - Default credentials are NOT auto-filled in forms.
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

## 4. Backend & Database Mode Indicator (Supabase Postgres vs In-Memory Mock)

Both the Web frontend and Android app query the `/api/health` endpoint:
```json
{
  "status": "healthy",
  "supabase_connected": true,
  "mode": "supabase"
}
```

- **Postgres Mode (`mode: "supabase"`):** Displays `"Connected: Postgres DB [DEBUG]"` in the app header/settings.
- **Development Mock Mode (`mode: "mock_in_memory"`):** Displays `"Development: In-Memory Mock DB"` when running locally without a live Supabase connection.
