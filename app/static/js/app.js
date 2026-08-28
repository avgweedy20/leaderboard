/**
 * DSS Sports — Inter-House Sports Meet
 * Main application logic
 *
 * Implements:
 *  1. Reusable Toast Notification System (replacing all alerts)
 *  2. JWT Session Expiry: 6-hour duration, proactive expiry checks, auto-logout on expiry/401
 *  3. Fixture Score Editing: reliable modal pre-population, score update, and instant live UI refresh
 */

// ─── GLOBALS ───────────────────────────────────────────────────────────────
let currentToken = localStorage.getItem('sb_auth_token') || null;
let sportsData = [];
let housesData = [];
let squadsData = [];
let playersData = [];
let matchesData = [];
let selectedSportId = '';
let pendingCsvPlayers = [];

// Session duration: 6 hours (in milliseconds)
const SESSION_DURATION_MS = 6 * 60 * 60 * 1000; // 21,600,000 ms

// House color map (keyed by lowercase house name, used as fallback)
const HOUSE_COLORS = {
    karnali: '#10B981',
    koshi: '#0EA5E9',
    mahakali: '#8B5CF6',
    mechi: '#F97316'
};

// ─── INIT ──────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    checkAuthUI();
    checkDbHealth();
    startSessionExpiryChecker();
    initFooterTypewriter();
});

// ─── TOAST NOTIFICATION SYSTEM ─────────────────────────────────────────────
/**
 * Show a toast notification
 * @param {string} message - Toast text
 * @param {'success'|'error'|'info'} type - Type of toast
 * @param {Object} [options] - Options { title, duration, action: { label, onClick } }
 */
function showToast(message, type = 'info', options = {}) {
    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.setAttribute('role', 'alert');

    const iconId = type === 'success' ? 'icon-check'
        : type === 'error' ? 'icon-alert'
            : 'icon-info';

    const titleHtml = options.title ? `<div class="toast-title">${escapeHtml(options.title)}</div>` : '';
    let actionHtml = '';
    if (options.action && options.action.label && typeof options.action.onClick === 'function') {
        actionHtml = `<button type="button" class="btn btn-secondary" style="height:24px; font-size:11px; padding:0 8px; margin-top:6px;" id="toast-action-btn">${escapeHtml(options.action.label)}</button>`;
    }

    toast.innerHTML = `
        <svg class="toast-icon" width="16" height="16"><use href="#${iconId}"/></svg>
        <div class="toast-content">
            ${titleHtml}
            <div class="toast-message">${escapeHtml(message)}</div>
            ${actionHtml}
        </div>
        <button type="button" class="toast-close" aria-label="Dismiss">&times;</button>
    `;

    // Close button
    const closeBtn = toast.querySelector('.toast-close');
    closeBtn.onclick = () => dismissToast(toast);

    // Action button
    if (options.action && options.action.onClick) {
        const actionBtn = toast.querySelector('#toast-action-btn');
        if (actionBtn) {
            actionBtn.onclick = () => {
                options.action.onClick();
                dismissToast(toast);
            };
        }
    }

    container.appendChild(toast);

    // Auto-dismiss timing (errors stay slightly longer)
    const duration = options.duration || (type === 'error' ? 6000 : 4000);
    const timer = setTimeout(() => {
        dismissToast(toast);
    }, duration);

    toast._dismissTimer = timer;
    return toast;
}

function dismissToast(toast) {
    if (!toast || toast.classList.contains('toast-closing')) return;
    clearTimeout(toast._dismissTimer);
    toast.classList.add('toast-closing');
    setTimeout(() => {
        if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 160);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// ─── JWT / SESSION EXPIRY MANAGEMENT ───────────────────────────────────────
/**
 * Parse expiration time from JWT token string if possible
 */
function parseJwtExp(token) {
    if (!token || typeof token !== 'string') return null;
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;
        const payload = JSON.parse(atob(parts[1]));
        if (payload && payload.exp) {
            return payload.exp * 1000; // in milliseconds
        }
    } catch (e) { /* not a standard JWT or decoding failed */ }
    return null;
}

/**
 * Returns true if the stored auth token is expired or invalid
 */
function isTokenExpired() {
    if (!currentToken) return true;

    // Check explicit expires_at timestamp stored at login
    const storedExpiry = localStorage.getItem('sb_auth_expires_at');
    if (storedExpiry) {
        const expiryTime = parseInt(storedExpiry, 10);
        if (!isNaN(expiryTime) && Date.now() >= expiryTime) {
            return true;
        }
    }

    // Check JWT payload exp claim if present
    const jwtExp = parseJwtExp(currentToken);
    if (jwtExp && Date.now() >= jwtExp) {
        return true;
    }

    return false;
}

/**
 * Log out and clear session when token expires
 */
function handleSessionExpired() {
    if (!currentToken) return;

    currentToken = null;
    localStorage.removeItem('sb_auth_token');
    localStorage.removeItem('sb_auth_expires_at');
    checkAuthUI();

    showToast('Your session expired — please sign in again.', 'info', {
        title: 'Session Expired',
        duration: 7000
    });

    // If currently on an admin page, redirect home
    if (window.location.pathname.startsWith('/admin')) {
        setTimeout(() => {
            window.location.href = '/';
        }, 1200);
    }
}

/**
 * Periodic session expiration checker (runs every 10s & on window focus)
 */
function startSessionExpiryChecker() {
    // Immediate check on load
    if (currentToken && isTokenExpired()) {
        handleSessionExpired();
    }

    // Interval check
    setInterval(() => {
        if (currentToken && isTokenExpired()) {
            handleSessionExpired();
        }
    }, 10000);

    // Re-check on tab visibility change / focus
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible' && currentToken && isTokenExpired()) {
            handleSessionExpired();
        }
    });
    window.addEventListener('focus', () => {
        if (currentToken && isTokenExpired()) {
            handleSessionExpired();
        }
    });
}

/**
 * Authenticated API fetch wrapper — handles Bearer header and 401 auto-logout
 */
async function fetchWithAuth(url, options = {}) {
    if (currentToken && isTokenExpired()) {
        handleSessionExpired();
        throw new Error('Session expired');
    }

    const headers = options.headers ? { ...options.headers } : {};
    if (currentToken) {
        headers['Authorization'] = `Bearer ${currentToken}`;
    }

    const res = await fetch(url, { ...options, headers });

    if (res.status === 401) {
        handleSessionExpired();
        throw new Error('Unauthorized — session expired');
    }

    return res;
}

// ─── HEALTH CHECK ──────────────────────────────────────────────────────────
async function checkDbHealth() {
    const badge = document.getElementById('dbModeBadge');
    if (!badge) return;
    try {
        const res = await fetch('/api/health');
        const data = await res.json();
        badge.textContent = data.supabase_connected ? 'Live DB' : 'Mock DB';
        badge.className = 'badge badge-status-' + (data.supabase_connected ? 'completed' : 'scheduled');
    } catch (e) {
        badge.textContent = 'Offline';
        badge.className = 'badge';
        badge.style.color = '#F87171';
    }
}

// ─── THEME ─────────────────────────────────────────────────────────────────
function initTheme() {
    const saved = localStorage.getItem('sb_theme') || 'light';
    applyTheme(saved);
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme') || 'light';
    applyTheme(current === 'dark' ? 'light' : 'dark');
}

function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('sb_theme', theme);
    const icon = document.getElementById('themeIcon');
    if (icon) icon.innerHTML = `<use href="#icon-${theme === 'dark' ? 'moon' : 'sun'}"/>`;
}

// ─── AUTH UI ───────────────────────────────────────────────────────────────
function checkAuthUI() {
    const authBtnText = document.getElementById('authBtnText');
    const authIcon = document.getElementById('authIcon');
    const adminTab = document.getElementById('adminTabBtn');
    const mobileAdmin = document.getElementById('mobileAdminLink');

    if (currentToken && !isTokenExpired()) {
        if (authBtnText) authBtnText.textContent = 'Sign Out';
        if (authIcon) authIcon.innerHTML = `<use href="#icon-logout"/>`;
        if (adminTab) adminTab.style.display = 'inline-flex';
        if (mobileAdmin) mobileAdmin.style.display = 'flex';
    } else {
        currentToken = null;
        if (authBtnText) authBtnText.textContent = 'Admin Login';
        if (authIcon) authIcon.innerHTML = `<use href="#icon-login"/>`;
        if (adminTab) adminTab.style.display = 'none';
        if (mobileAdmin) mobileAdmin.style.display = 'none';
        if (window.location.pathname.startsWith('/admin')) {
            window.location.href = '/';
        }
    }
}

function openLoginModal() {
    if (currentToken && !isTokenExpired()) {
        currentToken = null;
        localStorage.removeItem('sb_auth_token');
        localStorage.removeItem('sb_auth_expires_at');
        checkAuthUI();
        showToast('Signed out successfully', 'info');
        if (window.location.pathname.startsWith('/admin')) {
            window.location.href = '/';
        }
    } else {
        openModal('loginModal');
    }
}

async function handleLogin(e) {
    e.preventDefault();
    const email = document.getElementById('loginEmail').value;
    const password = document.getElementById('loginPassword').value;
    try {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();
        if (res.ok && data.access_token) {
            currentToken = data.access_token;
            localStorage.setItem('sb_auth_token', currentToken);

            // Compute 6-hour expiry (21,600 seconds)
            const expiresInSec = data.expires_in || 21600;
            const expiresAtMs = Date.now() + (expiresInSec * 1000);
            localStorage.setItem('sb_auth_expires_at', String(expiresAtMs));

            closeModal('loginModal');
            checkAuthUI();
            showToast('Signed in successfully as Admin', 'success');
            window.location.href = '/admin';
        } else {
            showToast(data.error || 'Invalid credentials. Please try again.', 'error', { title: 'Sign In Failed' });
        }
    } catch (err) {
        showToast('Network error during sign in. Check backend connection.', 'error', { title: 'Connection Error' });
    }
}

// ─── MODALS ────────────────────────────────────────────────────────────────
function openModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.remove('hidden');
}

function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.add('hidden');
}

// Close modal when clicking overlay backdrop
document.addEventListener('click', e => {
    if (e.target.classList.contains('modal-overlay')) {
        e.target.classList.add('hidden');
    }
});

// ─── MOBILE NAV (HAMBURGER) ────────────────────────────────────────────────
function toggleMobileNav() {
    const nav = document.getElementById('mobileNav');
    const btn = document.getElementById('navToggleBtn');
    if (!nav || !btn) return;
    const open = nav.classList.toggle('hidden') === false;
    nav.setAttribute('aria-hidden', open ? 'false' : 'true');
    btn.setAttribute('aria-expanded', open ? 'true' : 'false');
}

function closeMobileNav() {
    const nav = document.getElementById('mobileNav');
    const btn = document.getElementById('navToggleBtn');
    if (!nav || !btn || nav.classList.contains('hidden')) return;
    nav.classList.add('hidden');
    nav.setAttribute('aria-hidden', 'true');
    btn.setAttribute('aria-expanded', 'false');
}

// Close on selecting an item or tapping outside (only matters below breakpoint)
document.addEventListener('click', e => {
    const nav = document.getElementById('mobileNav');
    const btn = document.getElementById('navToggleBtn');
    if (!nav || !btn) return;
    const isMenuClick = nav.contains(e.target) || btn.contains(e.target);
    if (isMenuClick) {
        if (e.target.closest('.mobile-nav-link')) closeMobileNav();
        return;
    }
    if (!nav.classList.contains('hidden')) closeMobileNav();
});

// ─── FOOTER TYPEWRITER ─────────────────────────────────────────────────────
function initFooterTypewriter() {
    const el = document.getElementById('footerType');
    if (!el) return;

    const variants = [
        'Made by Samir Ghimire',
        'Made by STEM Club President'
    ];

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduceMotion) {
        el.textContent = variants[0];
        return;
    }

    let variant = 0, charIndex = 0, deleting = false;
    const typeSpeed = 55, deleteSpeed = 28, holdTime = 1400;

    function tick() {
        const text = variants[variant];
        if (!deleting) {
            charIndex++;
            el.textContent = text.slice(0, charIndex);
            if (charIndex === text.length) {
                deleting = true;
                el.classList.add('typing-done');
                setTimeout(tick, holdTime);
                return;
            }
            setTimeout(tick, typeSpeed);
        } else {
            charIndex--;
            el.textContent = text.slice(0, charIndex);
            if (charIndex === 0) {
                deleting = false;
                el.classList.remove('typing-done');
                variant = (variant + 1) % variants.length;
                setTimeout(tick, 300);
                return;
            }
            setTimeout(tick, deleteSpeed);
        }
    }

    setTimeout(tick, 400);
}

// ─── DATA FETCHERS ─────────────────────────────────────────────────────────
async function fetchHouses() {
    try {
        const res = await fetch('/api/houses');
        housesData = await res.json();
    } catch (e) { console.error('fetchHouses:', e); }
}

async function fetchSports() {
    try {
        const res = await fetch('/api/sports');
        sportsData = await res.json();
    } catch (e) { console.error('fetchSports:', e); }
}

async function fetchSquads() {
    try {
        const res = await fetch('/api/teams');
        squadsData = await res.json();
    } catch (e) { console.error('fetchSquads:', e); }
}

async function fetchPlayers() {
    try {
        const res = await fetch('/api/players');
        playersData = await res.json();
    } catch (e) { console.error('fetchPlayers:', e); }
}

async function fetchMatches() {
    try {
        const res = await fetch('/api/matches');
        matchesData = await res.json();
    } catch (e) { console.error('fetchMatches:', e); }
}

// ─── SPORT FILTER CHIPS (standings page) ───────────────────────────────────
function renderSportFilterChips() {
    const group = document.getElementById('sportChipGroup');
    if (!group) return;

    let html = `<button class="chip ${selectedSportId === '' ? 'active' : ''}"
                        onclick="selectSportChip('', '')">All Sports</button>`;
    sportsData.forEach(s => {
        const active = selectedSportId === s.id;
        const slug = s.name.toLowerCase();
        html += `<button class="chip ${active ? 'active' : ''}"
                         onclick="selectSportChip('${s.id}','${slug}')">${s.name}</button>`;
    });
    group.innerHTML = html;
}

function selectSportChip(sportId, sportSlug) {
    selectedSportId = sportId;
    renderSportFilterChips();
    if (sportSlug) {
        window.history.replaceState({}, '', `/standings/${sportSlug}`);
    }
    loadPerSportStandings();
}

// ─── PAGE LOADERS ──────────────────────────────────────────────────────────
function loadHouseOverallStandingsPage() { loadHouseOverallStandings(); }
function loadPerSportStandingsPage() { loadPerSportStandings(); }
function loadFixturesPage() { /* fixtures.html manages its own state via loadAllFixtures() */ }
function loadAdminCenterPage() { loadAdminCenterData(); }

// ─── 1. OVERALL HOUSE STANDINGS ────────────────────────────────────────────
async function loadHouseOverallStandings() {
    const heroEl = document.getElementById('houseHeroContainer');
    const tableEl = document.getElementById('houseTableContainer');

    try {
        const res = await fetch('/api/leaderboard/overall');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const standings = await res.json();

        // ── Hero Cards ──
        if (!standings || standings.length === 0) {
            heroEl.innerHTML = renderSharedEmptyState('No standings data yet.', '');
        } else {
            heroEl.innerHTML = standings.map(h => {
                const color = h.color_hex || HOUSE_COLORS[h.house_name.toLowerCase()] || '#10B981';
                return `
                <div class="house-hero-card fade-in" style="border-left-color:${color};">
                    <div style="display:flex; align-items:flex-start; justify-content:space-between; gap:12px;">
                        <div style="min-width:0;">
                            <div class="house-name">${h.house_name}</div>
                            <div style="font-size:11px; color:var(--text-tertiary); margin-top:2px;">
                                ${h.total_squads} squad${h.total_squads !== 1 ? 's' : ''}
                            </div>
                        </div>
                        <span class="rank-number" style="color:${color}; flex-shrink:0;">#${h.rank}</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:flex-end;
                                padding-top:12px; border-top:1px solid var(--border);">
                        <div>
                            <div class="stat-label">Total Points</div>
                            <div class="stat-value" style="color:${color};">${h.total_points}</div>
                        </div>
                        <div style="text-align:right;">
                            <div class="stat-label">W &ndash; D &ndash; L</div>
                            <div class="stat-value-sm tabular">${h.total_wins}&ndash;${h.total_draws}&ndash;${h.total_losses}</div>
                        </div>
                    </div>
                </div>`;
            }).join('');
        }

        // ── Table ──
        if (!standings || standings.length === 0) {
            tableEl.innerHTML = renderSharedEmptyState('No data.', '');
            return;
        }

        tableEl.innerHTML = `
        <div class="table-wrap fade-in">
            <table>
                <thead>
                    <tr>
                        <th style="width:56px;">Rank</th>
                        <th>House</th>
                        <th>Squads</th>
                        <th>Played</th>
                        <th>W</th>
                        <th>D</th>
                        <th>L</th>
                        <th style="text-align:right;">Points</th>
                    </tr>
                </thead>
                <tbody>
                    ${standings.map(h => {
            const color = h.color_hex || HOUSE_COLORS[h.house_name.toLowerCase()] || '#10B981';
            return `
                        <tr style="border-left:3px solid ${color};">
                            <td style="font-weight:700; color:${color};">#${h.rank}</td>
                            <td>
                                <div style="display:flex; align-items:center; gap:8px;">
                                    <div style="width:8px; height:8px; border-radius:2px; background-color:${color}; flex-shrink:0;"></div>
                                    <span style="font-weight:700;">${h.house_name}</span>
                                </div>
                            </td>
                            <td class="tabular">${h.total_squads}</td>
                            <td class="tabular">${h.matches_played}</td>
                            <td class="tabular" style="color:var(--c-karnali);">${h.total_wins}</td>
                            <td class="tabular" style="color:var(--text-secondary);">${h.total_draws}</td>
                            <td class="tabular" style="color:#F87171;">${h.total_losses}</td>
                            <td style="text-align:right; font-weight:700; color:${color};" class="tabular">${h.total_points}</td>
                        </tr>`;
        }).join('')}
                </tbody>
            </table>
        </div>`;

    } catch (e) {
        heroEl.innerHTML = renderSharedErrorState('Failed to load house standings.', 'loadHouseOverallStandings()');
        tableEl.innerHTML = '';
    }
}

// ─── 2. PER-SPORT STANDINGS ────────────────────────────────────────────────
async function loadPerSportStandings() {
    const container = document.getElementById('perSportStandingsContainer');
    if (!container) return;

    // Show skeleton while loading
    container.innerHTML = `<div style="padding:16px; display:flex; flex-direction:column; gap:8px;">
        ${Array(5).fill('<div class="skeleton-table-row"></div>').join('')}
    </div>`;

    const gender = (document.getElementById('filterGender') || {}).value || '';

    try {
        const res = await fetch(`/api/leaderboard?sport_id=${selectedSportId}&gender=${gender}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const standings = await res.json();

        if (!standings || standings.length === 0) {
            container.innerHTML = renderSharedEmptyState(
                'No standings yet',
                'Select a sport and check back once league matches are recorded.'
            );
            return;
        }

        container.innerHTML = `
        <div class="table-wrap fade-in">
            <table>
                <thead>
                    <tr>
                        <th style="width:48px;">Rank</th>
                        <th>Squad</th>
                        <th>House</th>
                        <th>Sport</th>
                        <th>Gender</th>
                        <th>P</th>
                        <th>W</th>
                        <th>D</th>
                        <th>L</th>
                        <th>Diff</th>
                        <th style="text-align:right;">Pts</th>
                    </tr>
                </thead>
                <tbody>
                    ${standings.map(s => {
            const color = s.house_color || HOUSE_COLORS[(s.house_name || '').toLowerCase()] || '#10B981';
            const sign = s.score_difference > 0 ? '+' : '';
            const squadLabel = s.squad_label ? ` ${s.squad_label}` : '';
            return `
                        <tr style="border-left:3px solid ${color};">
                            <td style="font-weight:700;">#${s.rank}</td>
                            <td style="font-weight:700;">${s.team_name}</td>
                            <td>
                                <div style="display:flex; align-items:center; gap:6px;">
                                    <div style="width:6px; height:6px; border-radius:2px; background-color:${color}; flex-shrink:0;"></div>
                                    <span style="color:${color}; font-weight:700;">${s.house_name}${squadLabel}</span>
                                </div>
                            </td>
                            <td style="color:var(--text-secondary);">${s.sport_name}</td>
                            <td style="color:var(--text-secondary);">${s.gender}</td>
                            <td class="tabular">${s.played}</td>
                            <td class="tabular" style="color:var(--c-karnali);">${s.wins}</td>
                            <td class="tabular" style="color:var(--text-secondary);">${s.draws}</td>
                            <td class="tabular" style="color:#F87171;">${s.losses}</td>
                            <td class="tabular" style="color:var(--text-secondary);">${sign}${s.score_difference}</td>
                            <td style="text-align:right; font-weight:700; color:${color};" class="tabular">${s.points}</td>
                        </tr>`;
        }).join('')}
                </tbody>
            </table>
        </div>`;

    } catch (e) {
        container.innerHTML = renderSharedErrorState('Failed to load standings.', 'loadPerSportStandings()');
    }
}

// ─── 3. ADMIN CENTER ───────────────────────────────────────────────────────
async function loadAdminCenterData() {
    await fetchSports();
    await fetchHouses();
    await fetchSquads();
    await fetchPlayers();
    await fetchMatches();

    renderAdminSquadsTable();
    renderAdminPlayersTable();
    renderAdminFixturesTable();
}

// ─── ADMIN TABLE STATE ─────────────────────────────────────────────────────
const adminSquadsState = {
    sortCol: 'name',
    sortDir: 'asc',
    search: '',
    houseFilter: '',
    sportFilter: '',
    genderFilter: '',
    page: 1,
    pageSize: 10
};

const adminPlayersState = {
    sortCol: 'name',
    sortDir: 'asc',
    search: '',
    houseFilter: '',
    sportFilter: '',
    gradeFilter: '',
    genderFilter: '',
    page: 1,
    pageSize: 10,
    selectedIds: new Set()
};

const adminFixturesState = {
    sortCol: 'id',
    sortDir: 'asc',
    search: '',
    sportFilter: '',
    genderFilter: '',
    stageFilter: '',
    statusFilter: '',
    page: 1,
    pageSize: 10
};

function getSortIcon(currentCol, targetCol, currentDir) {
    if (currentCol !== targetCol) {
        return `<span class="sort-indicator"><svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.35"><polyline points="7 15 12 20 17 15"/><polyline points="7 9 12 4 17 9"/></svg></span>`;
    }
    if (currentDir === 'asc') {
        return `<span class="sort-indicator active"><svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="18 15 12 9 6 15"/></svg></span>`;
    }
    return `<span class="sort-indicator active"><svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"/></svg></span>`;
}

// ─── 1. SQUADS TABLE (SORTABLE, FILTERABLE, PAGINATED) ─────────────────────
function renderAdminSquadsTable() {
    const container = document.getElementById('adminSquadsList');
    const badge = document.getElementById('squadCountBadge');
    if (!container) return;
    if (badge) badge.textContent = `${squadsData.length} Squads`;

    if (!squadsData || squadsData.length === 0) {
        container.innerHTML = renderSharedEmptyState('No squads registered yet', '');
        return;
    }

    // Apply combined filters
    let list = [...squadsData];
    if (adminSquadsState.search) {
        const q = adminSquadsState.search.toLowerCase();
        list = list.filter(s => {
            const hName = ((s.houses || {}).name || getHouseName(s.house_id)).toLowerCase();
            const sName = ((s.sports || {}).name || getSportName(s.sport_id)).toLowerCase();
            return s.name.toLowerCase().includes(q) || hName.includes(q) || sName.includes(q);
        });
    }
    if (adminSquadsState.houseFilter) {
        list = list.filter(s => s.house_id === adminSquadsState.houseFilter);
    }
    if (adminSquadsState.sportFilter) {
        list = list.filter(s => s.sport_id === adminSquadsState.sportFilter);
    }
    if (adminSquadsState.genderFilter) {
        list = list.filter(s => s.gender === adminSquadsState.genderFilter);
    }

    // Sorting
    list.sort((a, b) => {
        let vA = '', vB = '';
        if (adminSquadsState.sortCol === 'name') {
            vA = (a.name || '').toLowerCase();
            vB = (b.name || '').toLowerCase();
        } else if (adminSquadsState.sortCol === 'house') {
            vA = ((a.houses || {}).name || getHouseName(a.house_id)).toLowerCase();
            vB = ((b.houses || {}).name || getHouseName(b.house_id)).toLowerCase();
        } else if (adminSquadsState.sortCol === 'sport') {
            vA = ((a.sports || {}).name || getSportName(a.sport_id)).toLowerCase();
            vB = ((b.sports || {}).name || getSportName(b.sport_id)).toLowerCase();
        } else if (adminSquadsState.sortCol === 'gender') {
            vA = (a.gender || '').toLowerCase();
            vB = (b.gender || '').toLowerCase();
        }
        if (vA < vB) return adminSquadsState.sortDir === 'asc' ? -1 : 1;
        if (vA > vB) return adminSquadsState.sortDir === 'asc' ? 1 : -1;
        return 0;
    });

    // Pagination
    const totalItems = list.length;
    const pageSize = adminSquadsState.pageSize === 'all' ? totalItems : parseInt(adminSquadsState.pageSize, 10);
    const totalPages = Math.max(1, Math.ceil(totalItems / (pageSize || 1)));
    if (adminSquadsState.page > totalPages) adminSquadsState.page = totalPages;
    const startIdx = (adminSquadsState.page - 1) * pageSize;
    const pageItems = adminSquadsState.pageSize === 'all' ? list : list.slice(startIdx, startIdx + pageSize);

    // Build House filter options
    const houseOptions = housesData.map(h =>
        `<option value="${h.id}" ${adminSquadsState.houseFilter === h.id ? 'selected' : ''}>${h.name}</option>`
    ).join('');

    // Build Sport filter options
    const sportOptions = sportsData.map(s =>
        `<option value="${s.id}" ${adminSquadsState.sportFilter === s.id ? 'selected' : ''}>${s.name}</option>`
    ).join('');

    container.innerHTML = `
    <!-- Toolbar -->
    <div class="table-toolbar">
        <div class="table-toolbar-left">
            <input type="text" class="table-search-input" placeholder="Search squads, houses..."
                   value="${escapeHtml(adminSquadsState.search)}"
                   oninput="adminSquadsState.search = this.value; adminSquadsState.page = 1; renderAdminSquadsTable();">
            <select class="table-filter-select" onchange="adminSquadsState.houseFilter = this.value; adminSquadsState.page = 1; renderAdminSquadsTable();">
                <option value="">All Houses</option>
                ${houseOptions}
            </select>
            <select class="table-filter-select" onchange="adminSquadsState.sportFilter = this.value; adminSquadsState.page = 1; renderAdminSquadsTable();">
                <option value="">All Sports</option>
                ${sportOptions}
            </select>
            <select class="table-filter-select" onchange="adminSquadsState.genderFilter = this.value; adminSquadsState.page = 1; renderAdminSquadsTable();">
                <option value="">All Genders</option>
                <option value="Boys" ${adminSquadsState.genderFilter === 'Boys' ? 'selected' : ''}>Boys</option>
                <option value="Girls" ${adminSquadsState.genderFilter === 'Girls' ? 'selected' : ''}>Girls</option>
            </select>
        </div>
        <div class="table-toolbar-right">
            <span style="font-size:11px; color:var(--text-secondary);">${totalItems} result${totalItems !== 1 ? 's' : ''}</span>
        </div>
    </div>

    <!-- Table -->
    <div class="table-wrap">
        <table>
            <thead>
                <tr>
                    <th class="th-sortable" onclick="toggleSquadsSort('name')">Squad ${getSortIcon(adminSquadsState.sortCol, 'name', adminSquadsState.sortDir)}</th>
                    <th class="th-sortable" onclick="toggleSquadsSort('house')">House ${getSortIcon(adminSquadsState.sortCol, 'house', adminSquadsState.sortDir)}</th>
                    <th class="th-sortable" onclick="toggleSquadsSort('sport')">Sport ${getSortIcon(adminSquadsState.sortCol, 'sport', adminSquadsState.sortDir)}</th>
                    <th class="th-sortable" onclick="toggleSquadsSort('gender')">Gender ${getSortIcon(adminSquadsState.sortCol, 'gender', adminSquadsState.sortDir)}</th>
                    <th style="text-align:right;">Actions</th>
                </tr>
            </thead>
            <tbody>
                ${pageItems.length === 0 ? `<tr><td colspan="5" style="text-align:center; padding:24px; color:var(--text-secondary);">No matching squads found</td></tr>` : ''}
                ${pageItems.map(s => {
        const hName = (s.houses || {}).name || getHouseName(s.house_id);
        const sName = (s.sports || {}).name || getSportName(s.sport_id);
        const color = (s.houses || {}).color_hex || HOUSE_COLORS[(hName || '').toLowerCase()] || '#10B981';
        return `
                    <tr style="border-left:3px solid ${color};">
                        <td style="font-weight:700;">${escapeHtml(s.name)}</td>
                        <td style="color:${color}; font-weight:700;">${escapeHtml(hName)}</td>
                        <td style="color:var(--text-secondary);">${escapeHtml(sName)}</td>
                        <td style="color:var(--text-secondary);">${escapeHtml(s.gender)} (${escapeHtml(s.squad_label || 'A')})</td>
                        <td style="text-align:right;">
                            <button onclick="openSquadModal('${s.id}')" class="btn btn-secondary btn-icon" title="Edit" style="height:26px; width:26px; margin-right:4px;">
                                <svg class="icon" width="12" height="12"><use href="#icon-edit"/></svg>
                            </button>
                            <button onclick="deleteSquad('${s.id}')" class="btn btn-secondary btn-icon" title="Delete"
                                    style="height:26px; width:26px; color:#F87171; border-color:#7F1D1D;">
                                <svg class="icon" width="12" height="12"><use href="#icon-trash"/></svg>
                            </button>
                        </td>
                    </tr>`;
    }).join('')}
            </tbody>
        </table>
    </div>

    <!-- Pagination -->
    <div class="pagination-bar">
        <div>
            Showing ${totalItems === 0 ? 0 : startIdx + 1}–${Math.min(startIdx + (pageSize || totalItems), totalItems)} of ${totalItems}
        </div>
        <div class="pagination-nav">
            <button class="pagination-btn" ${adminSquadsState.page <= 1 ? 'disabled' : ''} onclick="adminSquadsState.page--; renderAdminSquadsTable();">Prev</button>
            <span style="font-size:11px; padding:0 4px;">Page ${adminSquadsState.page} of ${totalPages}</span>
            <button class="pagination-btn" ${adminSquadsState.page >= totalPages ? 'disabled' : ''} onclick="adminSquadsState.page++; renderAdminSquadsTable();">Next</button>
            <select class="page-size-select" onchange="adminSquadsState.pageSize = this.value; adminSquadsState.page = 1; renderAdminSquadsTable();">
                <option value="10" ${adminSquadsState.pageSize == '10' ? 'selected' : ''}>10 / page</option>
                <option value="25" ${adminSquadsState.pageSize == '25' ? 'selected' : ''}>25 / page</option>
                <option value="all" ${adminSquadsState.pageSize == 'all' ? 'selected' : ''}>Show All</option>
            </select>
        </div>
    </div>`;
}

function toggleSquadsSort(col) {
    if (adminSquadsState.sortCol === col) {
        adminSquadsState.sortDir = adminSquadsState.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
        adminSquadsState.sortCol = col;
        adminSquadsState.sortDir = 'asc';
    }
    renderAdminSquadsTable();
}

// ─── 2. PLAYERS TABLE (SORTABLE, FILTERABLE, PAGINATED, BULK DELETION) ─────
function renderAdminPlayersTable() {
    const container = document.getElementById('adminPlayersList');
    const badge = document.getElementById('playerCountBadge');
    if (!container) return;
    if (badge) badge.textContent = `${playersData.length} Players`;

    if (!playersData || playersData.length === 0) {
        container.innerHTML = renderSharedEmptyState('No players registered yet', '');
        return;
    }

    // Apply combined filters
    let list = [...playersData];
    if (adminPlayersState.search) {
        const q = adminPlayersState.search.toLowerCase();
        list = list.filter(p => {
            const teamName = getTeamName(p.team_id).toLowerCase();
            return (p.name || '').toLowerCase().includes(q) ||
                String(p.roll_number || '').toLowerCase().includes(q) ||
                (p.section || '').toLowerCase().includes(q) ||
                teamName.includes(q);
        });
    }
    if (adminPlayersState.houseFilter) {
        list = list.filter(p => {
            const squad = squadsData.find(s => s.id === p.team_id);
            return squad && squad.house_id === adminPlayersState.houseFilter;
        });
    }
    if (adminPlayersState.sportFilter) {
        list = list.filter(p => {
            const squad = squadsData.find(s => s.id === p.team_id);
            return squad && squad.sport_id === adminPlayersState.sportFilter;
        });
    }
    if (adminPlayersState.gradeFilter) {
        list = list.filter(p => String(p.grade || '') === adminPlayersState.gradeFilter);
    }
    if (adminPlayersState.genderFilter) {
        list = list.filter(p => p.gender === adminPlayersState.genderFilter);
    }

    // Sorting
    list.sort((a, b) => {
        let vA = '', vB = '';
        if (adminPlayersState.sortCol === 'name') {
            vA = (a.name || '').toLowerCase();
            vB = (b.name || '').toLowerCase();
        } else if (adminPlayersState.sortCol === 'roll') {
            vA = parseInt(a.roll_number, 10) || 0;
            vB = parseInt(b.roll_number, 10) || 0;
            return adminPlayersState.sortDir === 'asc' ? vA - vB : vB - vA;
        } else if (adminPlayersState.sortCol === 'squad') {
            vA = getTeamName(a.team_id).toLowerCase();
            vB = getTeamName(b.team_id).toLowerCase();
        } else if (adminPlayersState.sortCol === 'grade') {
            vA = (a.grade || '').toLowerCase();
            vB = (b.grade || '').toLowerCase();
        }
        if (vA < vB) return adminPlayersState.sortDir === 'asc' ? -1 : 1;
        if (vA > vB) return adminPlayersState.sortDir === 'asc' ? 1 : -1;
        return 0;
    });

    // Pagination
    const totalItems = list.length;
    const pageSize = adminPlayersState.pageSize === 'all' ? totalItems : parseInt(adminPlayersState.pageSize, 10);
    const totalPages = Math.max(1, Math.ceil(totalItems / (pageSize || 1)));
    if (adminPlayersState.page > totalPages) adminPlayersState.page = totalPages;
    const startIdx = (adminPlayersState.page - 1) * pageSize;
    const pageItems = adminPlayersState.pageSize === 'all' ? list : list.slice(startIdx, startIdx + pageSize);

    // Build unique Grades for filter
    const grades = Array.from(new Set(playersData.map(p => String(p.grade || '')).filter(Boolean))).sort();
    const gradeOptions = grades.map(g =>
        `<option value="${g}" ${adminPlayersState.gradeFilter === g ? 'selected' : ''}>Grade ${g}</option>`
    ).join('');

    // Build House filter options
    const houseOptions = housesData.map(h =>
        `<option value="${h.id}" ${adminPlayersState.houseFilter === h.id ? 'selected' : ''}>${h.name}</option>`
    ).join('');

    // Build Sport filter options
    const sportOptions = sportsData.map(s =>
        `<option value="${s.id}" ${adminPlayersState.sportFilter === s.id ? 'selected' : ''}>${s.name}</option>`
    ).join('');

    const allCurrentSelected = pageItems.length > 0 && pageItems.every(p => adminPlayersState.selectedIds.has(p.id));
    const selectedCount = adminPlayersState.selectedIds.size;

    container.innerHTML = `
    <!-- Bulk actions bar -->
    ${selectedCount > 0 ? `
    <div class="bulk-actions-bar">
        <span style="font-weight:700;">${selectedCount} player${selectedCount !== 1 ? 's' : ''} selected</span>
        <button class="btn btn-secondary" style="height:24px; font-size:11px; padding:0 8px; color:#F87171; border-color:#7F1D1D;" onclick="bulkDeleteSelectedPlayers()">
            <svg class="icon" width="12" height="12"><use href="#icon-trash"/></svg> Delete Selected
        </button>
        <button class="btn btn-secondary" style="height:24px; font-size:11px; padding:0 8px;" onclick="adminPlayersState.selectedIds.clear(); renderAdminPlayersTable();">
            Deselect All
        </button>
    </div>` : ''}

    <!-- Toolbar -->
    <div class="table-toolbar">
        <div class="table-toolbar-left">
            <input type="text" class="table-search-input" placeholder="Search name, roll #, squad..."
                   value="${escapeHtml(adminPlayersState.search)}"
                   oninput="adminPlayersState.search = this.value; adminPlayersState.page = 1; renderAdminPlayersTable();">
            <select class="table-filter-select" onchange="adminPlayersState.houseFilter = this.value; adminPlayersState.page = 1; renderAdminPlayersTable();">
                <option value="">All Houses</option>
                ${houseOptions}
            </select>
            <select class="table-filter-select" onchange="adminPlayersState.sportFilter = this.value; adminPlayersState.page = 1; renderAdminPlayersTable();">
                <option value="">All Sports</option>
                ${sportOptions}
            </select>
            <select class="table-filter-select" onchange="adminPlayersState.gradeFilter = this.value; adminPlayersState.page = 1; renderAdminPlayersTable();">
                <option value="">All Grades</option>
                ${gradeOptions}
            </select>
            <select class="table-filter-select" onchange="adminPlayersState.genderFilter = this.value; adminPlayersState.page = 1; renderAdminPlayersTable();">
                <option value="">All Genders</option>
                <option value="Boys" ${adminPlayersState.genderFilter === 'Boys' ? 'selected' : ''}>Boys</option>
                <option value="Girls" ${adminPlayersState.genderFilter === 'Girls' ? 'selected' : ''}>Girls</option>
            </select>
        </div>
        <div class="table-toolbar-right">
            <span style="font-size:11px; color:var(--text-secondary);">${totalItems} result${totalItems !== 1 ? 's' : ''}</span>
        </div>
    </div>

    <!-- Table -->
    <div class="table-wrap">
        <table>
            <thead>
                <tr>
                    <th style="width:36px; text-align:center;">
                        <input type="checkbox" ${allCurrentSelected ? 'checked' : ''} onchange="toggleSelectAllPlayers(this.checked, ${JSON.stringify(pageItems.map(p => p.id))})">
                    </th>
                    <th class="th-sortable" style="width:64px;" onclick="togglePlayersSort('roll')">Roll ${getSortIcon(adminPlayersState.sortCol, 'roll', adminPlayersState.sortDir)}</th>
                    <th class="th-sortable" onclick="togglePlayersSort('name')">Name ${getSortIcon(adminPlayersState.sortCol, 'name', adminPlayersState.sortDir)}</th>
                    <th class="th-sortable" onclick="togglePlayersSort('squad')">Squad ${getSortIcon(adminPlayersState.sortCol, 'squad', adminPlayersState.sortDir)}</th>
                    <th class="th-sortable" onclick="togglePlayersSort('grade')">Grade ${getSortIcon(adminPlayersState.sortCol, 'grade', adminPlayersState.sortDir)}</th>
                    <th style="text-align:right;">Actions</th>
                </tr>
            </thead>
            <tbody>
                ${pageItems.length === 0 ? `<tr><td colspan="6" style="text-align:center; padding:24px; color:var(--text-secondary);">No matching players found</td></tr>` : ''}
                ${pageItems.map(p => {
        const teamName = getTeamName(p.team_id);
        const isSelected = adminPlayersState.selectedIds.has(p.id);
        return `
                    <tr style="${isSelected ? 'background-color: var(--bg-surface-3);' : ''}">
                        <td style="text-align:center;">
                            <input type="checkbox" ${isSelected ? 'checked' : ''} onchange="toggleSelectPlayer('${p.id}', this.checked)">
                        </td>
                        <td style="font-variant-numeric:tabular-nums; color:var(--text-secondary);">${escapeHtml(p.roll_number || '—')}</td>
                        <td style="font-weight:700;">${escapeHtml(p.name)}</td>
                        <td style="color:var(--text-secondary); font-size:12px;">${escapeHtml(teamName)}</td>
                        <td style="color:var(--text-secondary);">${escapeHtml(p.grade || '—')}${p.section ? ` (${escapeHtml(p.section)})` : ''}</td>
                        <td style="text-align:right;">
                            <button onclick="openPlayerModal('${p.id}')" class="btn btn-secondary btn-icon" title="Edit" style="height:26px; width:26px; margin-right:4px;">
                                <svg class="icon" width="12" height="12"><use href="#icon-edit"/></svg>
                            </button>
                            <button onclick="deletePlayer('${p.id}')" class="btn btn-secondary btn-icon" title="Delete"
                                    style="height:26px; width:26px; color:#F87171; border-color:#7F1D1D;">
                                <svg class="icon" width="12" height="12"><use href="#icon-trash"/></svg>
                            </button>
                        </td>
                    </tr>`;
    }).join('')}
            </tbody>
        </table>
    </div>

    <!-- Pagination -->
    <div class="pagination-bar">
        <div>
            Showing ${totalItems === 0 ? 0 : startIdx + 1}–${Math.min(startIdx + (pageSize || totalItems), totalItems)} of ${totalItems}
        </div>
        <div class="pagination-nav">
            <button class="pagination-btn" ${adminPlayersState.page <= 1 ? 'disabled' : ''} onclick="adminPlayersState.page--; renderAdminPlayersTable();">Prev</button>
            <span style="font-size:11px; padding:0 4px;">Page ${adminPlayersState.page} of ${totalPages}</span>
            <button class="pagination-btn" ${adminPlayersState.page >= totalPages ? 'disabled' : ''} onclick="adminPlayersState.page++; renderAdminPlayersTable();">Next</button>
            <select class="page-size-select" onchange="adminPlayersState.pageSize = this.value; adminPlayersState.page = 1; renderAdminPlayersTable();">
                <option value="10" ${adminPlayersState.pageSize == '10' ? 'selected' : ''}>10 / page</option>
                <option value="25" ${adminPlayersState.pageSize == '25' ? 'selected' : ''}>25 / page</option>
                <option value="50" ${adminPlayersState.pageSize == '50' ? 'selected' : ''}>50 / page</option>
                <option value="all" ${adminPlayersState.pageSize == 'all' ? 'selected' : ''}>Show All</option>
            </select>
        </div>
    </div>`;
}

function togglePlayersSort(col) {
    if (adminPlayersState.sortCol === col) {
        adminPlayersState.sortDir = adminPlayersState.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
        adminPlayersState.sortCol = col;
        adminPlayersState.sortDir = 'asc';
    }
    renderAdminPlayersTable();
}

function toggleSelectPlayer(id, checked) {
    if (checked) adminPlayersState.selectedIds.add(id);
    else adminPlayersState.selectedIds.delete(id);
    renderAdminPlayersTable();
}

function toggleSelectAllPlayers(checked, pageIds) {
    if (checked) {
        pageIds.forEach(id => adminPlayersState.selectedIds.add(id));
    } else {
        pageIds.forEach(id => adminPlayersState.selectedIds.delete(id));
    }
    renderAdminPlayersTable();
}

async function bulkDeleteSelectedPlayers() {
    const ids = Array.from(adminPlayersState.selectedIds);
    if (!ids.length) return;

    if (!confirm(`Are you sure you want to delete ${ids.length} selected player(s)?`)) return;

    try {
        const res = await fetchWithAuth('/api/players/bulk-delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ player_ids: ids })
        });
        if (res.ok) {
            adminPlayersState.selectedIds.clear();
            await fetchPlayers();
            renderAdminPlayersTable();
            showToast(`Deleted ${ids.length} player(s) successfully`, 'success');
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || 'Failed to delete selected players', 'error');
        }
    } catch (e) {
        if (!e.message.includes('Session expired')) {
            showToast('Network error during bulk delete', 'error');
        }
    }
}

// ─── 3. FIXTURES MANAGEMENT (SORTABLE, MULTI-FILTER, PAGINATED, INLINE QUICK SCORE) ──
function renderAdminFixturesTable() {
    const container = document.getElementById('adminFixturesContainer');
    if (!container) return;

    if (!matchesData || matchesData.length === 0) {
        container.innerHTML = `<p style="font-size:12px; color:var(--text-tertiary); padding:16px;">No matches in database.</p>`;
        return;
    }

    // Apply combined filters
    let list = [...matchesData];
    if (adminFixturesState.search) {
        const q = adminFixturesState.search.toLowerCase();
        list = list.filter(m => {
            const aName = getTeamName(m.team_a_id, m).toLowerCase();
            const bName = getTeamName(m.team_b_id, m).toLowerCase();
            const sName = getSportName(m.sport_id).toLowerCase();
            return aName.includes(q) || bName.includes(q) || sName.includes(q) || (m.round_info || '').toLowerCase().includes(q);
        });
    }
    if (adminFixturesState.sportFilter) {
        list = list.filter(m => m.sport_id === adminFixturesState.sportFilter);
    }
    if (adminFixturesState.genderFilter) {
        list = list.filter(m => m.gender === adminFixturesState.genderFilter);
    }
    if (adminFixturesState.stageFilter) {
        list = list.filter(m => m.stage === adminFixturesState.stageFilter);
    }
    if (adminFixturesState.statusFilter) {
        list = list.filter(m => m.status === adminFixturesState.statusFilter);
    }

    // Sorting
    list.sort((a, b) => {
        let vA = '', vB = '';
        if (adminFixturesState.sortCol === 'stage') {
            vA = (a.stage || '').toLowerCase();
            vB = (b.stage || '').toLowerCase();
        } else if (adminFixturesState.sortCol === 'status') {
            vA = (a.status || '').toLowerCase();
            vB = (b.status || '').toLowerCase();
        } else if (adminFixturesState.sortCol === 'sport') {
            vA = getSportName(a.sport_id).toLowerCase();
            vB = getSportName(b.sport_id).toLowerCase();
        } else {
            vA = (a.id || '').toLowerCase();
            vB = (b.id || '').toLowerCase();
        }
        if (vA < vB) return adminFixturesState.sortDir === 'asc' ? -1 : 1;
        if (vA > vB) return adminFixturesState.sortDir === 'asc' ? 1 : -1;
        return 0;
    });

    // Pagination
    const totalItems = list.length;
    const pageSize = adminFixturesState.pageSize === 'all' ? totalItems : parseInt(adminFixturesState.pageSize, 10);
    const totalPages = Math.max(1, Math.ceil(totalItems / (pageSize || 1)));
    if (adminFixturesState.page > totalPages) adminFixturesState.page = totalPages;
    const startIdx = (adminFixturesState.page - 1) * pageSize;
    const pageItems = adminFixturesState.pageSize === 'all' ? list : list.slice(startIdx, startIdx + pageSize);

    // Build Sport filter options
    const sportOptions = sportsData.map(s =>
        `<option value="${s.id}" ${adminFixturesState.sportFilter === s.id ? 'selected' : ''}>${s.name}</option>`
    ).join('');

    container.innerHTML = `
    <!-- Toolbar -->
    <div class="table-toolbar">
        <div class="table-toolbar-left">
            <input type="text" class="table-search-input" placeholder="Search teams, round..."
                   value="${escapeHtml(adminFixturesState.search)}"
                   oninput="adminFixturesState.search = this.value; adminFixturesState.page = 1; renderAdminFixturesTable();">
            <select class="table-filter-select" onchange="adminFixturesState.sportFilter = this.value; adminFixturesState.page = 1; renderAdminFixturesTable();">
                <option value="">All Sports</option>
                ${sportOptions}
            </select>
            <select class="table-filter-select" onchange="adminFixturesState.genderFilter = this.value; adminFixturesState.page = 1; renderAdminFixturesTable();">
                <option value="">All Genders</option>
                <option value="Boys" ${adminFixturesState.genderFilter === 'Boys' ? 'selected' : ''}>Boys</option>
                <option value="Girls" ${adminFixturesState.genderFilter === 'Girls' ? 'selected' : ''}>Girls</option>
            </select>
            <select class="table-filter-select" onchange="adminFixturesState.stageFilter = this.value; adminFixturesState.page = 1; renderAdminFixturesTable();">
                <option value="">All Stages</option>
                <option value="league" ${adminFixturesState.stageFilter === 'league' ? 'selected' : ''}>League</option>
                <option value="semifinal" ${adminFixturesState.stageFilter === 'semifinal' ? 'selected' : ''}>Semifinal</option>
                <option value="final" ${adminFixturesState.stageFilter === 'final' ? 'selected' : ''}>Final</option>
            </select>
            <select class="table-filter-select" onchange="adminFixturesState.statusFilter = this.value; adminFixturesState.page = 1; renderAdminFixturesTable();">
                <option value="">All Statuses</option>
                <option value="completed" ${adminFixturesState.statusFilter === 'completed' ? 'selected' : ''}>Completed</option>
                <option value="scheduled" ${adminFixturesState.statusFilter === 'scheduled' ? 'selected' : ''}>Scheduled</option>
            </select>
        </div>
        <div class="table-toolbar-right">
            <span style="font-size:11px; color:var(--text-secondary);">${totalItems} match${totalItems !== 1 ? 'es' : ''}</span>
        </div>
    </div>

    <!-- Table with Inline Score Entry -->
    <div class="table-wrap">
        <table>
            <thead>
                <tr>
                    <th>Matchup</th>
                    <th class="th-sortable" onclick="toggleFixturesSort('sport')">Sport / Div ${getSortIcon(adminFixturesState.sortCol, 'sport', adminFixturesState.sortDir)}</th>
                    <th class="th-sortable" onclick="toggleFixturesSort('stage')">Stage ${getSortIcon(adminFixturesState.sortCol, 'stage', adminFixturesState.sortDir)}</th>
                    <th class="th-sortable" onclick="toggleFixturesSort('status')">Status ${getSortIcon(adminFixturesState.sortCol, 'status', adminFixturesState.sortDir)}</th>
                    <th>Quick Score Entry</th>
                    <th style="text-align:right;">Actions</th>
                </tr>
            </thead>
            <tbody>
                ${pageItems.length === 0 ? `<tr><td colspan="6" style="text-align:center; padding:24px; color:var(--text-secondary);">No matching fixtures found</td></tr>` : ''}
                ${pageItems.map(m => {
        const aName = getTeamName(m.team_a_id, m);
        const bName = getTeamName(m.team_b_id, m);
        const sName = getSportName(m.sport_id);
        const done = m.status === 'completed';
        const scoreA = m.score_team_a ?? 0;
        const scoreB = m.score_team_b ?? 0;

        return `
                    <tr>
                        <td style="font-weight:700; font-size:12px;">
                            ${escapeHtml(aName)} <span style="font-size:10px; color:var(--text-tertiary);">vs</span> ${escapeHtml(bName)}
                        </td>
                        <td style="color:var(--text-secondary); font-size:12px;">${escapeHtml(sName)} &mdash; ${escapeHtml(m.gender)}</td>
                        <td><span class="badge badge-stage">${escapeHtml(m.stage || 'league')}</span></td>
                        <td><span class="badge ${done ? 'badge-status-completed' : 'badge-status-scheduled'}">${done ? 'FT' : 'Sched.'}</span></td>
                        <td>
                            <div class="inline-score-wrap">
                                <input type="number" min="0" class="inline-score-input" id="inline_a_${m.id}" value="${scoreA}" placeholder="0" aria-label="${escapeHtml(aName)} score">
                                <span style="font-size:11px; color:var(--text-tertiary);">&ndash;</span>
                                <input type="number" min="0" class="inline-score-input" id="inline_b_${m.id}" value="${scoreB}" placeholder="0" aria-label="${escapeHtml(bName)} score">
                                <button class="inline-save-btn" onclick="saveInlineMatchScore('${m.id}')" title="Quick Save Score">Save</button>
                            </div>
                        </td>
                        <td style="text-align:right;">
                            <button onclick="openMatchModal('${m.id}')" class="btn btn-secondary btn-icon" title="Full Edit" style="height:26px; width:26px;">
                                <svg class="icon" width="12" height="12"><use href="#icon-edit"/></svg>
                            </button>
                        </td>
                    </tr>`;
    }).join('')}
            </tbody>
        </table>
    </div>

    <!-- Pagination -->
    <div class="pagination-bar">
        <div>
            Showing ${totalItems === 0 ? 0 : startIdx + 1}–${Math.min(startIdx + (pageSize || totalItems), totalItems)} of ${totalItems}
        </div>
        <div class="pagination-nav">
            <button class="pagination-btn" ${adminFixturesState.page <= 1 ? 'disabled' : ''} onclick="adminFixturesState.page--; renderAdminFixturesTable();">Prev</button>
            <span style="font-size:11px; padding:0 4px;">Page ${adminFixturesState.page} of ${totalPages}</span>
            <button class="pagination-btn" ${adminFixturesState.page >= totalPages ? 'disabled' : ''} onclick="adminFixturesState.page++; renderAdminFixturesTable();">Next</button>
            <select class="page-size-select" onchange="adminFixturesState.pageSize = this.value; adminFixturesState.page = 1; renderAdminFixturesTable();">
                <option value="10" ${adminFixturesState.pageSize == '10' ? 'selected' : ''}>10 / page</option>
                <option value="25" ${adminFixturesState.pageSize == '25' ? 'selected' : ''}>25 / page</option>
                <option value="all" ${adminFixturesState.pageSize == 'all' ? 'selected' : ''}>Show All</option>
            </select>
        </div>
    </div>`;
}

function toggleFixturesSort(col) {
    if (adminFixturesState.sortCol === col) {
        adminFixturesState.sortDir = adminFixturesState.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
        adminFixturesState.sortCol = col;
        adminFixturesState.sortDir = 'asc';
    }
    renderAdminFixturesTable();
}

/**
 * Fast inline score recording action directly from table row
 */
async function saveInlineMatchScore(matchId) {
    const aInput = document.getElementById(`inline_a_${matchId}`);
    const bInput = document.getElementById(`inline_b_${matchId}`);
    if (!aInput || !bInput) return;

    const valA = aInput.value.trim();
    const valB = bInput.value.trim();

    if (valA === '' || valB === '' || isNaN(valA) || isNaN(valB)) {
        showToast('Scores must be valid numbers', 'error');
        return;
    }

    const score_team_a = parseInt(valA, 10);
    const score_team_b = parseInt(valB, 10);

    if (score_team_a < 0 || score_team_b < 0) {
        showToast('Scores cannot be negative', 'error');
        return;
    }

    try {
        const res = await fetchWithAuth(`/api/matches/${matchId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ score_team_a, score_team_b })
        });

        if (res.ok) {
            await fetchMatches();
            renderAdminFixturesTable();
            showToast(`Match score saved: ${score_team_a} - ${score_team_b}`, 'success');
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || 'Failed to save score', 'error');
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast('Network error while saving score', 'error');
        }
    }
}

// ─── ADMIN CRUD: SQUADS ────────────────────────────────────────────────────
function openSquadModal(squadId = null) {
    const hSel = document.getElementById('squadHouseId');
    const sSel = document.getElementById('squadSportId');
    if (!hSel || !sSel) return;

    hSel.innerHTML = housesData.map(h => `<option value="${h.id}">${h.name}</option>`).join('');
    sSel.innerHTML = sportsData.map(s => `<option value="${s.id}">${s.name}</option>`).join('');

    const titleEl = document.getElementById('squadModalTitle');
    if (squadId) {
        const sq = squadsData.find(s => s.id === squadId);
        if (sq) {
            document.getElementById('squadId').value = sq.id;
            document.getElementById('squadHouseId').value = sq.house_id;
            document.getElementById('squadSportId').value = sq.sport_id;
            document.getElementById('squadGender').value = sq.gender || 'Boys';
            document.getElementById('squadLabel').value = sq.squad_label || 'A';
            if (titleEl) titleEl.textContent = 'Edit Squad';
        }
    } else {
        document.getElementById('squadId').value = '';
        document.getElementById('squadLabel').value = 'A';
        if (titleEl) titleEl.textContent = 'Add House Squad';
    }
    openModal('squadModal');
}

async function handleSquadSubmit(e) {
    e.preventDefault();
    const squadId = document.getElementById('squadId').value;
    const house_id = document.getElementById('squadHouseId').value;
    const sport_id = document.getElementById('squadSportId').value;
    const gender = document.getElementById('squadGender').value;
    const squad_label = document.getElementById('squadLabel').value;

    const method = squadId ? 'PUT' : 'POST';
    const url = squadId ? `/api/teams/${squadId}` : '/api/teams';

    try {
        const res = await fetchWithAuth(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ house_id, sport_id, gender, squad_label })
        });
        if (res.ok) {
            closeModal('squadModal');
            await loadAdminCenterData();
            showToast(squadId ? 'House squad updated successfully' : 'House squad created successfully', 'success');
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || 'Failed to save squad', 'error', { title: 'Squad Save Failed' });
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast(err.message || 'Network error while saving squad', 'error');
        }
    }
}

async function deleteSquad(id) {
    if (!confirm('Delete this squad?')) return;
    try {
        const res = await fetchWithAuth(`/api/teams/${id}`, {
            method: 'DELETE'
        });
        if (res.ok) {
            await loadAdminCenterData();
            showToast('Squad deleted successfully', 'success');
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || 'Failed to delete squad', 'error');
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast(err.message || 'Network error while deleting squad', 'error');
        }
    }
}

// ─── ADMIN CRUD: PLAYERS ───────────────────────────────────────────────────
function openPlayerModal(playerId = null) {
    const teamSel = document.getElementById('playerTeamId');
    if (!teamSel) return;
    teamSel.innerHTML = squadsData.map(s => `<option value="${s.id}">${s.name}</option>`).join('');

    const titleEl = document.getElementById('playerModalTitle');
    if (playerId) {
        const p = playersData.find(x => x.id === playerId);
        if (p) {
            document.getElementById('playerId').value = p.id;
            document.getElementById('playerName').value = p.name;
            document.getElementById('playerRoll').value = p.roll_number || '';
            document.getElementById('playerTeamId').value = p.team_id;
            document.getElementById('playerGrade').value = p.grade || '';
            document.getElementById('playerSection').value = p.section || '';
            document.getElementById('playerGender').value = p.gender || 'Boys';
            if (titleEl) titleEl.textContent = 'Edit Player';
        }
    } else {
        ['playerId', 'playerName', 'playerRoll', 'playerGrade', 'playerSection'].forEach(id => {
            const el = document.getElementById(id); if (el) el.value = '';
        });
        if (titleEl) titleEl.textContent = 'Add Player';
    }
    openModal('playerModal');
}

async function handlePlayerSubmit(e) {
    e.preventDefault();
    const playerId = document.getElementById('playerId').value;
    const name = document.getElementById('playerName').value;
    const roll_number = document.getElementById('playerRoll').value;
    const team_id = document.getElementById('playerTeamId').value;
    const grade = document.getElementById('playerGrade').value;
    const section = document.getElementById('playerSection').value;
    const gender = document.getElementById('playerGender').value;

    const method = playerId ? 'PUT' : 'POST';
    const url = playerId ? `/api/players/${playerId}` : '/api/players';

    try {
        const res = await fetchWithAuth(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, roll_number, team_id, grade, section, gender })
        });
        if (res.ok) {
            closeModal('playerModal');
            await loadAdminCenterData();
            showToast(playerId ? 'Player updated successfully' : 'Player added successfully', 'success');
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || 'Failed to save player', 'error', { title: 'Player Save Failed' });
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast(err.message || 'Network error while saving player', 'error');
        }
    }
}

async function deletePlayer(id) {
    if (!confirm('Delete this player?')) return;
    try {
        const res = await fetchWithAuth(`/api/players/${id}`, {
            method: 'DELETE'
        });
        if (res.ok) {
            await loadAdminCenterData();
            showToast('Player deleted successfully', 'success');
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || 'Failed to delete player', 'error');
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast(err.message || 'Network error while deleting player', 'error');
        }
    }
}

// ─── ADMIN CRUD: MATCHES ───────────────────────────────────────────────────
async function openCreateMatchModal() {
    const sportSel = document.getElementById('newMatchSportId');
    if (!sportSel) return;
    if (!sportsData.length) await fetchSports();
    if (!squadsData.length) await fetchSquads();
    sportSel.innerHTML = sportsData.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
    onNewMatchSportOrGenderChange();
    openModal('createMatchModal');
}

function onNewMatchSportOrGenderChange() {
    const sportId = (document.getElementById('newMatchSportId') || {}).value;
    const gender = (document.getElementById('newMatchGender') || {}).value;
    const aSel = document.getElementById('newMatchTeamA');
    const bSel = document.getElementById('newMatchTeamB');
    if (!aSel || !bSel) return;

    const matching = squadsData.filter(s => s.sport_id === sportId && s.gender === gender);
    if (!matching.length) {
        aSel.innerHTML = bSel.innerHTML = `<option value="">No squads available</option>`;
        return;
    }
    aSel.innerHTML = matching.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
    bSel.innerHTML = matching.map((s, i) => `<option value="${s.id}" ${i === 1 ? 'selected' : ''}>${s.name}</option>`).join('');
}

async function handleCreateMatchSubmit(e) {
    e.preventDefault();
    const sport_id = document.getElementById('newMatchSportId').value;
    const gender = document.getElementById('newMatchGender').value;
    const team_a_id = document.getElementById('newMatchTeamA').value;
    const team_b_id = document.getElementById('newMatchTeamB').value;
    const stage = document.getElementById('newMatchStage').value;
    const round_info = document.getElementById('newMatchRoundInfo').value || 'League Game';

    if (!team_a_id || !team_b_id || team_a_id === team_b_id) {
        showToast('Please select two distinct teams for the fixture.', 'error', { title: 'Invalid Teams' });
        return;
    }
    try {
        const res = await fetchWithAuth('/api/matches', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sport_id, gender, team_a_id, team_b_id, stage, round_info })
        });
        if (res.ok) {
            closeModal('createMatchModal');
            await loadAdminCenterData();
            showToast('Match fixture created successfully', 'success');
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || 'Failed to create match', 'error', { title: 'Match Creation Failed' });
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast(err.message || 'Network error while creating fixture', 'error');
        }
    }
}

// ─── FIXTURE SCORE EDITING (TASK 3 FIX) ─────────────────────────────────────
/**
 * Opens the match score recording modal and pre-populates it with real data.
 * Checks in-memory matches, local fixtures list, and falls back to API fetch.
 */
async function openMatchModal(matchId) {
    if (!matchId) return;

    // Ensure squad & house metadata is loaded for name resolution
    if (!squadsData.length) await fetchSquads();
    if (!housesData.length) await fetchHouses();

    // 1. Look up in global matchesData
    let match = matchesData.find(m => m.id === matchId);

    // 2. Fall back to local fixtures on fixtures page
    if (!match && typeof allFixtures !== 'undefined' && Array.isArray(allFixtures)) {
        match = allFixtures.find(m => m.id === matchId);
    }

    // 3. Fall back to fetching matches from backend if still not found
    if (!match) {
        await fetchMatches();
        match = matchesData.find(m => m.id === matchId);
    }

    if (!match) {
        showToast('Could not load fixture details. Please refresh and try again.', 'error');
        return;
    }

    // Pre-populate hidden match ID
    const matchIdInput = document.getElementById('matchId');
    if (matchIdInput) matchIdInput.value = match.id;

    // Resolve team names
    const aName = getTeamName(match.team_a_id, match);
    const bName = getTeamName(match.team_b_id, match);

    // Pre-populate modal labels and values
    const summary = document.getElementById('matchModalSummary');
    const aLabel = document.getElementById('teamALabel');
    const bLabel = document.getElementById('teamBLabel');
    if (summary) summary.textContent = `${aName} vs ${bName}`;
    if (aLabel) aLabel.textContent = `${aName} Score`;
    if (bLabel) bLabel.textContent = `${bName} Score`;

    const scoreAInput = document.getElementById('matchScoreA');
    const scoreBInput = document.getElementById('matchScoreB');
    if (scoreAInput) scoreAInput.value = match.score_team_a ?? 0;
    if (scoreBInput) scoreBInput.value = match.score_team_b ?? 0;

    openModal('matchModal');
}

/**
 * Handles match score submission, updates backend, and re-renders current view
 */
async function handleMatchSubmit(e) {
    e.preventDefault();
    const matchId = document.getElementById('matchId').value;
    const scoreAVal = document.getElementById('matchScoreA').value;
    const scoreBVal = document.getElementById('matchScoreB').value;

    if (scoreAVal === '' || scoreBVal === '' || isNaN(scoreAVal) || isNaN(scoreBVal)) {
        showToast('Scores must be valid numbers.', 'error', { title: 'Invalid Score' });
        return;
    }

    const score_team_a = parseInt(scoreAVal, 10);
    const score_team_b = parseInt(scoreBVal, 10);

    if (score_team_a < 0 || score_team_b < 0) {
        showToast('Scores cannot be negative numbers.', 'error', { title: 'Invalid Score' });
        return;
    }

    try {
        const res = await fetchWithAuth(`/api/matches/${matchId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ score_team_a, score_team_b })
        });

        if (res.ok) {
            closeModal('matchModal');
            showToast('Match score updated successfully', 'success');

            // Page-aware live UI refresh without page reload
            const path = window.location.pathname;

            if (path === '/fixtures' || path === '/') {
                await fetchMatches();
                if (typeof reloadFixturesAfterUpdate === 'function') {
                    await reloadFixturesAfterUpdate();
                }
                if (path === '/') {
                    loadHouseOverallStandings();
                }
            } else if (path.startsWith('/standings')) {
                loadPerSportStandings();
            } else if (path.startsWith('/admin')) {
                await fetchMatches();
                renderAdminFixturesTable();
            }
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || 'Failed to update score', 'error', {
                title: 'Update Failed',
                action: {
                    label: 'Retry',
                    onClick: () => handleMatchSubmit(e)
                }
            });
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast(err.message || 'Network error while updating match score', 'error', {
                title: 'Connection Error'
            });
        }
    }
}

// ─── CSV IMPORT ────────────────────────────────────────────────────────────
function openCsvModal() {
    pendingCsvPlayers = [];
    const fileInput = document.getElementById('csvFileInput');
    const preview = document.getElementById('csvPreviewContainer');
    const commitBtn = document.getElementById('commitCsvBtn');
    if (fileInput) fileInput.value = '';
    if (preview) preview.classList.add('hidden');
    if (commitBtn) { commitBtn.disabled = true; commitBtn.style.opacity = '0.5'; }
    openModal('csvModal');
}

function handleCsvFileSelected(e) {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = evt => parseAndPreviewCsv(evt.target.result);
    reader.readAsText(file);
}

function parseAndPreviewCsv(csvText) {
    const lines = csvText.split(/\r\n|\n/).filter(l => l.trim());
    if (lines.length < 2) {
        showToast('CSV file is empty or missing headers.', 'error', { title: 'Invalid CSV' });
        return;
    }

    const headers = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, '').toLowerCase());
    const rows = [];

    for (let i = 1; i < lines.length; i++) {
        const cols = lines[i].split(',').map(c => c.trim().replace(/^"|"$/g, ''));
        if (!cols[0]) continue;

        let roll = '', name = '', grade = '', section = '', gender = 'Boys', squadStr = 'A';
        headers.forEach((h, idx) => {
            const v = cols[idx] || '';
            if (h.includes('roll') || h.includes('rn')) roll = v;
            else if (h.includes('name')) name = v;
            else if (h.includes('grade')) grade = v;
            else if (h.includes('sec')) section = v;
            else if (h.includes('gen') || h.includes('sex')) gender = v;
            else if (h.includes('squad') || h.includes('team')) squadStr = v;
        });
        if (!name) continue;

        let team_id = squadsData[0] ? squadsData[0].id : '';
        const found = squadsData.find(s =>
            s.name.toLowerCase().includes(squadStr.toLowerCase()) ||
            (s.squad_label || '').toLowerCase() === squadStr.toLowerCase()
        );
        if (found) team_id = found.id;

        const isUpdate = roll ? playersData.some(p => String(p.roll_number) === String(roll)) : false;
        rows.push({
            roll_number: roll, name, grade, section,
            gender: gender.toLowerCase().includes('girl') ? 'Girls' : 'Boys',
            team_id, isUpdate
        });
    }

    pendingCsvPlayers = rows;
    renderCsvPreviewTable(rows);
}

function renderCsvPreviewTable(rows) {
    const preview = document.getElementById('csvPreviewContainer');
    const tbody = document.getElementById('csvPreviewTableBody');
    const badge = document.getElementById('csvPreviewCountBadge');
    const warnings = document.getElementById('csvValidationWarnings');
    const commitBtn = document.getElementById('commitCsvBtn');

    if (badge) badge.textContent = `${rows.length} records`;

    // Squad size warnings
    const squadCounts = {};
    rows.forEach(r => { squadCounts[r.team_id] = (squadCounts[r.team_id] || 0) + 1; });
    let warnHtml = '';
    Object.keys(squadCounts).forEach(tid => {
        const count = squadCounts[tid];
        const squad = squadsData.find(s => s.id === tid);
        if (squad) {
            const sp = getSportName(squad.sport_id).toLowerCase();
            let min = 7, max = 11;
            if (sp.includes('futsal')) { min = 11; max = 14; }
            else if (sp.includes('basket')) { min = 7; max = 10; }
            if (count < min || count > max) {
                warnHtml += `<div style="padding:8px 12px; border:1px solid #92400E; border-radius:8px; background-color:#1C1200; color:#FCD34D; font-size:12px;">
                    Warning: <strong>${escapeHtml(squad.name)}</strong> has ${count} players (recommended ${min}–${max}).
                </div>`;
            }
        }
    });
    if (warnings) warnings.innerHTML = warnHtml;

    if (tbody) {
        tbody.innerHTML = rows.map(r => `
        <tr>
            <td class="tabular">${escapeHtml(r.roll_number || '—')}</td>
            <td style="font-weight:700;">${escapeHtml(r.name)}</td>
            <td>${escapeHtml(r.grade || '—')}${r.section ? ` (${escapeHtml(r.section)})` : ''}</td>
            <td>${escapeHtml(r.gender)}</td>
            <td>${escapeHtml(getTeamName(r.team_id))}</td>
            <td>
                <span class="badge ${r.isUpdate ? '' : 'badge-status-completed'}"
                      style="${r.isUpdate ? 'background-color:#1C1200;color:#FCD34D;border-color:#92400E;' : ''}">
                    ${r.isUpdate ? 'Update' : 'New'}
                </span>
            </td>
        </tr>`).join('');
    }

    if (preview) preview.classList.remove('hidden');
    if (commitBtn) { commitBtn.disabled = rows.length === 0; commitBtn.style.opacity = rows.length ? '1' : '0.5'; }
}

async function commitCsvImport() {
    if (!pendingCsvPlayers.length) return;
    const btn = document.getElementById('commitCsvBtn');
    if (btn) { btn.disabled = true; btn.textContent = 'Importing...'; }
    try {
        const res = await fetchWithAuth('/api/players/bulk', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(pendingCsvPlayers)
        });
        if (res.ok) {
            const data = await res.json();
            showToast(data.message || 'CSV player import completed successfully', 'success');
            closeModal('csvModal');
            await loadAdminCenterData();
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || 'CSV import failed', 'error', { title: 'Import Failed' });
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast(err.message || 'Network error during CSV import', 'error');
        }
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = 'Commit Import'; }
    }
}

// ─── SHARED STATE COMPONENTS ───────────────────────────────────────────────
/** Reusable empty state HTML — call from any page */
function renderSharedEmptyState(title, desc) {
    return `
    <div class="card">
        <div class="state-empty">
            <svg class="state-empty-icon" width="40" height="40"><use href="#icon-empty"/></svg>
            <p class="state-empty-title">${escapeHtml(title)}</p>
            ${desc ? `<p class="state-empty-desc">${escapeHtml(desc)}</p>` : ''}
        </div>
    </div>`;
}

/** Reusable error state HTML with retry button */
function renderSharedErrorState(message, retryCall) {
    return `
    <div class="state-error">
        <svg class="icon" width="20" height="20" style="color:#F87171;"><use href="#icon-alert"/></svg>
        <p class="state-error-title">${escapeHtml(message)}</p>
        <button class="btn btn-secondary" onclick="${retryCall}" style="margin-top:8px;">
            <svg class="icon" width="14" height="14"><use href="#icon-refresh"/></svg>
            Retry
        </button>
    </div>`;
}

// ─── HELPERS ───────────────────────────────────────────────────────────────
/**
 * Resolve team display name.
 * Priority: squadsData lookup → nested Supabase join data → 'TBD'
 * If a house has >1 squad for that sport+gender, appends squad_label (A/B).
 */
function getTeamName(teamId, matchObj = null) {
    if (!teamId) return 'TBD';

    // 1. Look up in squadsData
    let t = squadsData.find(s => s.id === teamId);

    // 2. Fall back to Supabase nested join data on the match object
    if (!t && matchObj) {
        if (matchObj.team_a_id === teamId && matchObj.team_a) t = matchObj.team_a;
        else if (matchObj.team_b_id === teamId && matchObj.team_b) t = matchObj.team_b;
    }

    if (!t) return 'TBD';

    const houseId = t.house_id;
    const sportId = t.sport_id || (matchObj ? matchObj.sport_id : null);
    const gender = t.gender || (matchObj ? matchObj.gender : null);
    const houseName = (t.houses && t.houses.name) ? t.houses.name : getHouseName(houseId);

    // Show squad label suffix only when the house has multiple squads in same sport+gender
    if (houseId && sportId && gender) {
        const multiSquad = squadsData.filter(
            sq => sq.house_id === houseId && sq.sport_id === sportId && sq.gender === gender
        ).length > 1;
        if (multiSquad) return `${houseName} ${t.squad_label || 'A'}`;
    }
    return houseName;
}

function getHouseName(houseId) {
    const h = housesData.find(x => x.id === houseId);
    return h ? h.name : 'House';
}

function getHouseColor(houseId, housesObj) {
    if (housesObj && housesObj.color_hex) return housesObj.color_hex;
    const h = housesData.find(x => x.id === houseId);
    return h ? h.color_hex : 'var(--border)';
}

function getSportName(sportId) {
    const s = sportsData.find(x => x.id === sportId);
    return s ? s.name : 'Sport';
}
