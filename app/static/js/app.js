


let currentToken = localStorage.getItem('sb_auth_token') || null;
let currentAdminRole = null;
let sportsData = [];
let housesData = [];
let squadsData = [];
let playersData = [];
let matchesData = [];
let selectedSportId = '';
let pendingCsvPlayers = [];




(function () {
    const originalSetItem = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
        if (key === 'sb_auth_token' && String(value) === 'mock-admin-token') {
            console.warn('nice try');
            return undefined;
        }
        return originalSetItem.call(this, key, value);
    };
})();


const SESSION_DURATION_MS = 6 * 60 * 60 * 1000;


const HOUSE_COLORS = {
    karnali: '#A16207',
    koshi: '#5E7891',
    mahakali: '#A5534B',
    mechi: '#7B843F'
};


document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    checkAuthUI();
    validateSession();
    checkDbHealth();
    startSessionExpiryChecker();
    initFooterTypewriter();
});



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


    const closeBtn = toast.querySelector('.toast-close');
    closeBtn.onclick = () => dismissToast(toast);


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


function jsStrLiteral(str) {
    if (str === null || str === undefined) return '';
    return escapeHtml(String(str).replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/[\r\n]/g, ' '));
}


function cssColor(str) {
    const s = String(str || '').trim();
    return /^#?[0-9a-fA-F]{3,8}$/.test(s) ? s : '';
}



function parseJwtExp(token) {
    if (!token || typeof token !== 'string') return null;
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;
        const payload = JSON.parse(atob(parts[1]));
        if (payload && payload.exp) {
            return payload.exp * 1000;
        }
    } catch (e) { }
    return null;
}


function isTokenExpired() {
    if (!currentToken) return true;


    const storedExpiry = localStorage.getItem('sb_auth_expires_at');
    if (storedExpiry) {
        const expiryTime = parseInt(storedExpiry, 10);
        if (!isNaN(expiryTime) && Date.now() >= expiryTime) {
            return true;
        }
    }


    const jwtExp = parseJwtExp(currentToken);
    if (jwtExp && Date.now() >= jwtExp) {
        return true;
    }

    return false;
}


function handleSessionExpired() {
    if (!currentToken) return;

    serverLogout();
    currentToken = null;
    currentAdminRole = null;
    localStorage.removeItem('sb_auth_token');
    localStorage.removeItem('sb_auth_expires_at');
    checkAuthUI();

    showToast('Your session expired - please sign in again.', 'info', {
        title: 'Session Expired',
        duration: 7000
    });


    if (window.location.pathname.startsWith('/admin')) {
        setTimeout(() => {
            window.location.href = '/';
        }, 1200);
    }
}


async function serverLogout() {
    if (!currentToken) return;
    try {
        await fetch('/api/auth/logout', {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${currentToken}` }
        });
    } catch (e) { }
}


async function validateSession() {
    if (!currentToken || isTokenExpired()) return;
    try {
        const res = await fetch('/api/auth/me', {
            headers: { 'Authorization': `Bearer ${currentToken}` }
        });
        if (!res.ok) {
            handleSessionExpired();
            return;
        }
        const me = await res.json().catch(() => ({}));
        currentAdminRole = me.role || 'admin';
        applyAdminRoleUI();
    } catch (e) { }
}


async function ensureAdminRole() {
    if (!currentToken) return null;
    if (currentAdminRole) return currentAdminRole;
    try {
        const res = await fetchWithAuth('/api/auth/me');
        if (res.ok) {
            const me = await res.json();
            currentAdminRole = me.role || 'admin';
        }
    } catch (e) { }
    applyAdminRoleUI();
    return currentAdminRole;
}


function applyAdminRoleUI() {
    const allowed = currentAdminRole === 'superadmin';
    document.querySelectorAll('[data-superadmin-only]').forEach(el => {
        el.style.display = allowed ? '' : 'none';
    });
    document.querySelectorAll('[data-role-badge]').forEach(el => {
        el.style.display = currentAdminRole ? 'inline-flex' : 'none';
        if (currentAdminRole) {
            el.textContent = allowed ? 'Super Admin' : 'Admin';
        }
    });
}


function startSessionExpiryChecker() {

    if (currentToken && isTokenExpired()) {
        handleSessionExpired();
    }


    setInterval(() => {
        if (currentToken && isTokenExpired()) {
            handleSessionExpired();
        }
    }, 10000);


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
        throw new Error('Unauthorized - session expired');
    }

    return res;
}


async function checkDbHealth() {
    const badge = document.getElementById('dbModeBadge');
    if (!badge) return;
    try {
        const res = await fetch('/api/health');
        const data = await res.json();
        badge.textContent = data.supabase_connected ? 'Live DB' : 'Unconfigured';
        badge.className = 'badge badge-status-' + (data.supabase_connected ? 'completed' : 'scheduled');
    } catch (e) {
        badge.textContent = 'Offline';
        badge.className = 'badge';
        badge.style.color = '#F87171';
    }
}


function initTheme() {
    const saved = localStorage.getItem('sb_theme') || 'dark';
    applyTheme(saved);
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme') || 'dark';
    applyTheme(current === 'dark' ? 'light' : 'dark');
}

function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('sb_theme', theme);
    const icon = document.getElementById('themeIcon');
    if (icon) icon.innerHTML = `<use href="#icon-${theme === 'dark' ? 'moon' : 'sun'}"/>`;
}


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



let _busyCount = 0;


function setButtonLoading(btn, loading, label) {
    if (!btn) return;
    if (loading) {
        btn.dataset.origLabel = btn.textContent;
        btn.disabled = true;
        btn.innerHTML = `<span class="btn-spinner" aria-hidden="true"></span>${escapeHtml(label || '')}`;
    } else {
        btn.disabled = false;
        if (btn.dataset.origLabel) {
            btn.textContent = btn.dataset.origLabel;
            delete btn.dataset.origLabel;
        }
    }
}


function showBusy(label = 'Working…') {
    _busyCount++;
    const overlay = document.getElementById('globalBusyOverlay');
    if (overlay) {
        const labelEl = overlay.querySelector('.global-busy-label');
        if (labelEl) labelEl.textContent = label;
        overlay.classList.remove('hidden');
        overlay.setAttribute('aria-hidden', 'false');
    }
}

function hideBusy() {
    _busyCount = Math.max(0, _busyCount - 1);
    if (_busyCount === 0) {
        const overlay = document.getElementById('globalBusyOverlay');
        if (overlay) {
            overlay.classList.add('hidden');
            overlay.setAttribute('aria-hidden', 'true');
        }
    }
}


function openLoginModal() {
    if (currentToken && !isTokenExpired()) {
        serverLogout();
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
    const submitBtn = e.target.querySelector('button[type="submit"]');
    const email = document.getElementById('loginEmail').value;
    const password = document.getElementById('loginPassword').value;
    setButtonLoading(submitBtn, true, 'Signing in…');
    try {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();
        if (res.ok && data.access_token) {
            currentToken = data.access_token;
            currentAdminRole = (data.user && data.user.role) || 'admin';
            localStorage.setItem('sb_auth_token', currentToken);


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
    } finally {
        setButtonLoading(submitBtn, false);
    }
}


function openModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.remove('hidden');
}

function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.add('hidden');
}


document.addEventListener('click', e => {
    if (e.target.classList.contains('modal-overlay')) {
        e.target.classList.add('hidden');
    }
});


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


function initFooterTypewriter() {
    const el = document.getElementById('footerType');
    if (!el) return;

    if (window.DSSWidgets && window.DSSWidgets.ready) return;
    if (window.DSSEffects && typeof window.DSSEffects.textType === 'function') {
        window.DSSEffects.textType(el, {
            text: [

                '\u201CI am not led, I lead.\u201D \u2014 Alexander the Great',
                '\u201CWhile I breathe, there is hope.\u201D \u2014 Marcus Aurelius',
                '\u201CI\u2019ll do whatever it takes.\u201D \u2014 Cesare Borgia',
                '\u201CFortune favors the brave.\u201D \u2014 Pliny the Elder',
                'Made by STEM Club President',
                '\u201CIf I cannot bend the will of Heaven, I shall raise Hell.\u201D \u2014 Hannibal',
                '\u201CI came, I saw, I conquered.\u201D \u2014 Julius Caesar',
                '\u201CLet them hate, so long as they fear me.\u201D \u2014 Caligula',
                '\u201CFrom suffering comes glory.\u201D \u2014 Leonidas',
                'Made by Samir Ghimire',
                '\u201CIn this sign, you shall conquer.\u201D \u2014 Constantine',
            ],
            typingSpeed: 55,
            deletingSpeed: 28,
            pauseDuration: 1400,
            variableSpeed: { min: 35, max: 85 },
            showCursor: true
        });
    }
}


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


function renderSportFilterChips() {
    const group = document.getElementById('sportChipGroup');
    if (!group) return;

    let html = `<button class="chip ${selectedSportId === '' ? 'active' : ''}"
                        onclick="selectSportChip('', '')">All Sports</button>`;
    sportsData.forEach(s => {
        const active = selectedSportId === s.id;
        const slug = s.name.toLowerCase();
        html += `<button class="chip ${active ? 'active' : ''}"
                         onclick="selectSportChip('${jsStrLiteral(s.id)}','${jsStrLiteral(slug)}')">${escapeHtml(s.name)}</button>`;
    });
    group.innerHTML = html;
}

function selectSportChip(sportId, sportSlug) {
    selectedSportId = sportId;
    renderSportFilterChips();
    if (sportSlug) {
        window.history.replaceState({}, '', `/standings/${encodeURIComponent(sportSlug)}`);
    } else {
        window.history.replaceState({}, '', '/standings');
    }
    loadPerSportStandings();
}


function loadHouseOverallStandingsPage() { loadHouseOverallStandings(); }
function loadPerSportStandingsPage() { loadPerSportStandings(); }
function loadFixturesPage() { }
function loadAdminCenterPage() {
    applyAdminRoleUI();
    loadAdminCenterData();
}


let overallGender = '';

function filterOverallGender(gender, btn) {
    overallGender = gender;
    document.querySelectorAll('#overallGenderChips .chip').forEach(c => c.classList.remove('active'));
    if (btn) btn.classList.add('active');
    loadHouseOverallStandings();
}

async function loadHouseOverallStandings() {
    const heroEl = document.getElementById('houseHeroContainer');
    const tableEl = document.getElementById('houseTableContainer');

    const urls = {
        all: '/api/leaderboard/overall',
        girls: '/api/leaderboard/overall?gender=Girls',
        boys: '/api/leaderboard/overall?gender=Boys',
    };

    try {
        const results = await Promise.all(Object.entries(urls).map(async ([key, url]) => {
            const res = await fetch(url);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            return [key, Array.isArray(data) ? data : []];
        }));
        const byKey = Object.fromEntries(results);

        const standings = overallGender === 'Girls' ? byKey.girls
            : overallGender === 'Boys' ? byKey.boys
                : byKey.all;

        if (!standings || standings.length === 0) {
            heroEl.innerHTML = renderSharedEmptyState('No standings data yet.', '');
        } else {
            heroEl.innerHTML = standings.map(h => {
                const color = cssColor(h.color_hex) || HOUSE_COLORS[h.house_name.toLowerCase()] || '#A16207';
                return `
                <div class="house-hero-card tilt reveal ${h.rank === 1 ? 'is-leader' : ''}"
                     style="--accent:${color}; --d:${(h.rank - 1) * 70}ms;">
                    <div>
                        <span class="hero-medal">Rank #${h.rank}</span>
                        <div class="house-name" style="margin-top:8px;">${escapeHtml(h.house_name)}</div>
                        <div class="hero-sub">${h.total_squads} squad${h.total_squads !== 1 ? 's' : ''} registered</div>
                    </div>
                    <div class="hero-stats">
                        <div>
                            <div class="stat-label">Total Points</div>
                            <div class="stat-value" data-tick="${h.total_points}">0</div>
                        </div>
                        <div style="text-align:right;">
                            <div class="stat-value-sm tabular">
                                <span class="wl-good">${h.total_wins}</span><span style="color:var(--text-tertiary);">&nbsp;-&nbsp;</span><span class="wl-mid">${h.total_draws}</span><span style="color:var(--text-tertiary);">&nbsp;-&nbsp;</span><span class="wl-bad">${h.total_losses}</span>
                            </div>
                        </div>
                    </div>
                </div>`;
            }).join('');
        }


        if (!standings || standings.length === 0) {
            tableEl.innerHTML = renderSharedEmptyState('No data.', '');
            return;
        }

        tableEl.innerHTML = `
        <div class="table-wrap fade-in">
            <table>
                <thead>
                    <tr>
                        <th style="width:64px;">Rank</th>
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
            const color = cssColor(h.color_hex) || HOUSE_COLORS[h.house_name.toLowerCase()] || '#A16207';
            return `
                        <tr class="trow" style="--d:${(h.rank - 1) * 60}ms;">
                            <td>
                                <span class="rank-pill" style="background:${color};">#${h.rank}</span>
                            </td>
                            <td>
                                <div style="display:flex; align-items:center; gap:10px;">
                                    <div style="width:9px; height:9px; border-radius:3px; background:${color}; flex-shrink:0;"></div>
                                    <span style="font-family:var(--font-display); font-weight:620;">${escapeHtml(h.house_name)}</span>
                                </div>
                            </td>
                            <td class="tabular">${h.total_squads}</td>
                            <td class="tabular">${h.matches_played}</td>
                            <td class="tabular" style="color:var(--c-karnali); font-weight:600;">${h.total_wins}</td>
                            <td class="tabular" style="color:var(--text-tertiary);">${h.total_draws}</td>
                            <td class="tabular" style="color:#F87171; font-weight:600;">${h.total_losses}</td>
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


async function loadPerSportStandings() {
    const container = document.getElementById('perSportStandingsContainer');
    if (!container) return;


    container.innerHTML = `
    <div class="skeleton-table sk" aria-hidden="true">
        <div class="skeleton-table-header">
            <div class="sk-bar" style="height:12px; width:50px;"></div>
            <div class="sk-bar" style="height:12px; width:110px;"></div>
            <div class="sk-bar" style="height:12px; width:80px;"></div>
            <div class="sk-bar" style="height:12px; width:70px;"></div>
            <div class="sk-bar" style="height:12px; width:55px;"></div>
            <div class="sk-bar" style="height:12px; width:30px;"></div>
            <div class="sk-bar" style="height:12px; width:30px;"></div>
            <div class="sk-bar" style="height:12px; width:30px;"></div>
            <div class="sk-bar" style="height:12px; width:30px;"></div>
            <div class="sk-bar" style="height:12px; width:40px;"></div>
            <div class="sk-bar" style="height:12px; width:50px; margin-left:auto;"></div>
        </div>
        ${Array(4).fill(`
        <div class="skeleton-row">
            <div class="sk-bar" style="height:12px; width:45px;"></div>
            <div class="sk-bar" style="height:12px; width:100px;"></div>
            <div class="sk-bar" style="height:12px; width:70px;"></div>
            <div class="sk-bar" style="height:12px; width:60px;"></div>
            <div class="sk-bar" style="height:12px; width:45px;"></div>
            <div class="sk-bar" style="height:12px; width:28px;"></div>
            <div class="sk-bar" style="height:12px; width:28px;"></div>
            <div class="sk-bar" style="height:12px; width:28px;"></div>
            <div class="sk-bar" style="height:12px; width:28px;"></div>
            <div class="sk-bar" style="height:12px; width:35px;"></div>
            <div class="sk-bar" style="height:12px; width:42px; margin-left:auto;"></div>
        </div>`).join('')}
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
                        <th style="width:56px;">Rank</th>
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
                    ${standings.map((s, i) => {
            const color = cssColor(s.house_color) || HOUSE_COLORS[(s.house_name || '').toLowerCase()] || '#A16207';
            const sign = s.score_difference > 0 ? '+' : '';
            const squadLabel = s.squad_label ? ` ${s.squad_label}` : '';
            return `
                        <tr class="trow" style="--d:${i * 45}ms;">
                            <td>
                                <span class="rank-pill" style="background:${color};">#${s.rank}</span>
                            </td>
                            <td style="font-family:var(--font-display); font-weight:620;">${escapeHtml(s.team_name)}</td>
                            <td>
                                <div style="display:flex; align-items:center; gap:8px;">
                                    <span style="width:8px; height:8px; border-radius:3px; background:${color}; flex-shrink:0;"></span>
                                    <span style="color:${color}; font-weight:620;">${escapeHtml(s.house_name)}${escapeHtml(squadLabel)}</span>
                                </div>
                            </td>
                            <td style="color:var(--text-secondary); font-size:0.82rem;">${escapeHtml(s.sport_name)}</td>
                            <td style="color:var(--text-secondary); font-size:0.82rem;">${escapeHtml(s.gender)}</td>
                            <td class="tabular">${s.played}</td>
                            <td class="tabular" style="color:var(--c-karnali); font-weight:600;">${s.wins}</td>
                            <td class="tabular" style="color:var(--text-tertiary);">${s.draws}</td>
                            <td class="tabular" style="color:#F87171; font-weight:600;">${s.losses}</td>
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


async function loadAdminCenterData() {
    await fetchSports();
    await fetchHouses();
    await fetchSquads();
    await fetchPlayers();
    await fetchMatches();

    renderAdminSquadsTable();
    renderAdminPlayersTable();
    renderAdminFixturesTable();

    await ensureAdminRole();
    applyAdminRoleUI();
    if (currentAdminRole === 'superadmin') {
        await loadAdminAccounts();
        loadAdminAuditLog();
    }
}


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


function renderAdminSquadsTable() {
    const container = document.getElementById('adminSquadsList');
    const badge = document.getElementById('squadCountBadge');
    if (!container) return;
    if (badge) badge.textContent = `${squadsData.length} Squads`;

    if (!squadsData || squadsData.length === 0) {
        container.innerHTML = renderSharedEmptyState('No squads registered yet', '');
        return;
    }


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


    const totalItems = list.length;
    const pageSize = adminSquadsState.pageSize === 'all' ? totalItems : parseInt(adminSquadsState.pageSize, 10);
    const totalPages = Math.max(1, Math.ceil(totalItems / (pageSize || 1)));
    if (adminSquadsState.page > totalPages) adminSquadsState.page = totalPages;
    const startIdx = (adminSquadsState.page - 1) * pageSize;
    const pageItems = adminSquadsState.pageSize === 'all' ? list : list.slice(startIdx, startIdx + pageSize);


    const houseOptions = housesData.map(h =>
        `<option value="${h.id}" ${adminSquadsState.houseFilter === h.id ? 'selected' : ''}>${escapeHtml(h.name)}</option>`
    ).join('');


    const sportOptions = sportsData.map(s =>
        `<option value="${s.id}" ${adminSquadsState.sportFilter === s.id ? 'selected' : ''}>${escapeHtml(s.name)}</option>`
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
        const color = cssColor((s.houses || {}).color_hex) || HOUSE_COLORS[(hName || '').toLowerCase()] || '#A16207';
        return `
                    <tr style="border-left:3px solid ${color};">
                        <td style="font-weight:700;">${escapeHtml(s.name)}</td>
                        <td style="color:${color}; font-weight:700;">${escapeHtml(hName)}</td>
                        <td style="color:var(--text-secondary);">${escapeHtml(sName)}</td>
                        <td style="color:var(--text-secondary);">${escapeHtml(s.gender)} (${escapeHtml(s.squad_label || 'A')})</td>
                        <td style="text-align:right;">
                            <button onclick="openSquadModal('${jsStrLiteral(s.id)}')" class="btn btn-secondary btn-icon" title="Edit" style="height:26px; width:26px; margin-right:4px;">
                                <svg class="icon" width="12" height="12"><use href="#icon-edit"/></svg>
                            </button>
                            <button onclick="deleteSquad('${jsStrLiteral(s.id)}')" class="btn btn-secondary btn-icon" title="Delete"
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
            Showing ${totalItems === 0 ? 0 : startIdx + 1}-${Math.min(startIdx + (pageSize || totalItems), totalItems)} of ${totalItems}
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


function renderAdminPlayersTable() {
    const container = document.getElementById('adminPlayersList');
    const badge = document.getElementById('playerCountBadge');
    if (!container) return;
    if (badge) badge.textContent = `${playersData.length} Players`;

    if (!playersData || playersData.length === 0) {
        container.innerHTML = renderSharedEmptyState('No players registered yet', '');
        return;
    }


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


    const totalItems = list.length;
    const pageSize = adminPlayersState.pageSize === 'all' ? totalItems : parseInt(adminPlayersState.pageSize, 10);
    const totalPages = Math.max(1, Math.ceil(totalItems / (pageSize || 1)));
    if (adminPlayersState.page > totalPages) adminPlayersState.page = totalPages;
    const startIdx = (adminPlayersState.page - 1) * pageSize;
    const pageItems = adminPlayersState.pageSize === 'all' ? list : list.slice(startIdx, startIdx + pageSize);


    const grades = Array.from(new Set(playersData.map(p => String(p.grade || '')).filter(Boolean))).sort();
    const gradeOptions = grades.map(g =>
        `<option value="${g}" ${adminPlayersState.gradeFilter === g ? 'selected' : ''}>Grade ${g}</option>`
    ).join('');


    const houseOptions = housesData.map(h =>
        `<option value="${h.id}" ${adminPlayersState.houseFilter === h.id ? 'selected' : ''}>${escapeHtml(h.name)}</option>`
    ).join('');


    const sportOptions = sportsData.map(s =>
        `<option value="${s.id}" ${adminPlayersState.sportFilter === s.id ? 'selected' : ''}>${escapeHtml(s.name)}</option>`
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
                        <input type="checkbox" ${allCurrentSelected ? 'checked' : ''} onchange="toggleSelectAllPlayers(this.checked)">
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
                            <input type="checkbox" class="player-select" data-player-id="${jsStrLiteral(p.id)}" ${isSelected ? 'checked' : ''} onchange="toggleSelectPlayer('${jsStrLiteral(p.id)}', this.checked)">
                        </td>
                        <td style="font-variant-numeric:tabular-nums; color:var(--text-secondary);">${escapeHtml(p.roll_number || '-')}</td>
                        <td style="font-weight:700;">${escapeHtml(p.name)}</td>
                        <td style="color:var(--text-secondary); font-size:12px;">${escapeHtml(teamName)}</td>
                        <td style="color:var(--text-secondary);">${escapeHtml(p.grade || '-')}${p.section ? ` (${escapeHtml(p.section)})` : ''}</td>
                        <td style="text-align:right;">
                            <button onclick="openPlayerModal('${jsStrLiteral(p.id)}')" class="btn btn-secondary btn-icon" title="Edit" style="height:26px; width:26px; margin-right:4px;">
                                <svg class="icon" width="12" height="12"><use href="#icon-edit"/></svg>
                            </button>
                            <button onclick="deletePlayer('${jsStrLiteral(p.id)}')" class="btn btn-secondary btn-icon" title="Delete"
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
            Showing ${totalItems === 0 ? 0 : startIdx + 1}-${Math.min(startIdx + (pageSize || totalItems), totalItems)} of ${totalItems}
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

function toggleSelectAllPlayers(checked) {


    const boxes = document.querySelectorAll('.player-select');
    const pageIds = Array.from(boxes).map(b => b.getAttribute('data-player-id')).filter(Boolean);
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

    showBusy(`Deleting ${ids.length} player(s)…`);
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
    } finally {
        hideBusy();
    }
}


function renderAdminFixturesTable() {
    const container = document.getElementById('adminFixturesContainer');
    if (!container) return;

    if (!matchesData || matchesData.length === 0) {
        container.innerHTML = `<p style="font-size:12px; color:var(--text-tertiary); padding:16px;">No matches in database.</p>`;
        return;
    }


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


    const totalItems = list.length;
    const pageSize = adminFixturesState.pageSize === 'all' ? totalItems : parseInt(adminFixturesState.pageSize, 10);
    const totalPages = Math.max(1, Math.ceil(totalItems / (pageSize || 1)));
    if (adminFixturesState.page > totalPages) adminFixturesState.page = totalPages;
    const startIdx = (adminFixturesState.page - 1) * pageSize;
    const pageItems = adminFixturesState.pageSize === 'all' ? list : list.slice(startIdx, startIdx + pageSize);


    const sportOptions = sportsData.map(s =>
        `<option value="${s.id}" ${adminFixturesState.sportFilter === s.id ? 'selected' : ''}>${escapeHtml(s.name)}</option>`
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
                        <td style="color:var(--text-secondary); font-size:12px;">${escapeHtml(sName)} - ${escapeHtml(m.gender)}</td>
                        <td><span class="badge badge-stage">${escapeHtml(m.stage || 'league')}</span></td>
                        <td><span class="badge ${done ? 'badge-status-completed' : 'badge-status-scheduled'}">${done ? 'FT' : 'Sched.'}</span></td>
                        <td>
                            <div class="inline-score-wrap">
                                <input type="number" min="0" class="inline-score-input" id="inline_a_${m.id}" value="${scoreA}" placeholder="0" aria-label="${escapeHtml(aName)} score">
                                <span style="font-size:11px; color:var(--text-tertiary);">-</span>
                                <input type="number" min="0" class="inline-score-input" id="inline_b_${m.id}" value="${scoreB}" placeholder="0" aria-label="${escapeHtml(bName)} score">
                                <button class="inline-save-btn" onclick="saveInlineMatchScore('${jsStrLiteral(m.id)}')" title="Quick Save Score">Save</button>
                            </div>
                        </td>
                        <td style="text-align:right;">
                            <button onclick="openMatchModal('${jsStrLiteral(m.id)}')" class="btn btn-secondary btn-icon" title="Full Edit" style="height:26px; width:26px;">
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
            Showing ${totalItems === 0 ? 0 : startIdx + 1}-${Math.min(startIdx + (pageSize || totalItems), totalItems)} of ${totalItems}
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


async function loadAdminAccounts() {
    const container = document.getElementById('adminAccountsList');
    if (!container) return;
    try {
        const res = await fetchWithAuth('/api/admin/list');
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            container.innerHTML = renderSharedErrorState(err.error || 'Failed to load admins.', 'loadAdminAccounts()');
            return;
        }
        const data = await res.json();
        renderAdminAccounts(Array.isArray(data) ? data : (data.admins || []));
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            container.innerHTML = renderSharedErrorState('Failed to load admins.', 'loadAdminAccounts()');
        }
    }
}

function renderAdminAccounts(admins) {
    const container = document.getElementById('adminAccountsList');
    const badge = document.getElementById('adminCountBadge');
    if (!container) return;
    if (badge && admins) badge.textContent = `${admins.length} Admin${admins.length !== 1 ? 's' : ''}`;
    if (!admins || admins.length === 0) {
        container.innerHTML = renderSharedEmptyState('No admins registered yet', '');
        return;
    }
    container.innerHTML = `
        <div class="table-wrap fade-in">
            <table>
                <thead>
                    <tr>
                        <th>Email</th>
                        <th>Role</th>
                        <th>Added</th>
                        <th style="width:170px;"></th>
                    </tr>
                </thead>
                <tbody>
                    ${admins.map(a => `
                        <tr>
                            <td style="font-weight:700;">${escapeHtml(a.email)}</td>
                            <td>${(a.role === 'superadmin')
            ? '<span class="badge badge-status-completed">Super Admin</span>'
            : '<span class="badge badge-status-scheduled">Admin</span>'}</td>
                            <td style="color:var(--text-secondary);">${escapeHtml(a.created_at || '')}</td>
                            <td>
                                <div style="display:flex; gap:6px; justify-content:flex-end;">
                                    <button class="btn btn-secondary" style="height:26px; font-size:11px; padding:0 8px;"
                                        onclick="openResetPasswordModal('${jsStrLiteral(a.email)}')">Reset Password</button>
                                    <button class="btn btn-danger" style="height:26px; font-size:11px; padding:0 8px;"
                                        onclick="removeAdminAccount('${jsStrLiteral(a.email)}')">Remove</button>
                                </div>
                            </td>
                        </tr>`).join('')}
                </tbody>
            </table>
        </div>`;
}

function openAddAdminModal() {
    const emailInput = document.getElementById('newAdminEmail');
    const pwInput = document.getElementById('newAdminPassword');
    if (emailInput) emailInput.value = '';
    if (pwInput) pwInput.value = '';
    openModal('addAdminModal');
}

async function handleAddAdminSubmit(e) {
    e.preventDefault();
    const submitBtn = e.target.querySelector('button[type="submit"]');
    const email = document.getElementById('newAdminEmail').value.trim();
    const password = document.getElementById('newAdminPassword').value;
    const role = (document.getElementById('newAdminRole') || {}).value || 'admin';
    if (!email || !password) {
        showToast('Email and password are required', 'error');
        return;
    }
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Adding…';
    }
    try {
        const res = await fetchWithAuth('/api/admin/add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password, role })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            closeModal('addAdminModal');
            showToast(`${email} added as ${role === 'superadmin' ? 'super admin' : 'admin'}`, 'success');
            await loadAdminAccounts();
            loadAdminAuditLog();
        } else {
            showToast(data.error || 'Failed to add admin', 'error', { title: 'Add Failed' });
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast('Network error while adding admin', 'error');
        }
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Add Admin';
        }
    }
}

async function removeAdminAccount(email) {
    if (!confirm(`Remove ${email} from admin?`)) return;
    showBusy('Removing account…');
    try {
        const res = await fetchWithAuth('/api/admin/remove', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            showToast(`${email} removed from admin`, 'success');
            await loadAdminAccounts();
            loadAdminAuditLog();
        } else {
            showToast(data.error || 'Failed to remove admin', 'error', { title: 'Remove Failed' });
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast('Network error while removing admin', 'error');
        }
    } finally {
        hideBusy();
    }
}

function openResetPasswordModal(email) {
    document.getElementById('resetAdminEmail').value = email;
    const display = document.getElementById('resetAdminEmailDisplay');
    if (display) display.value = email;
    document.getElementById('resetAdminPassword').value = '';
    openModal('resetPasswordModal');
}

async function handleResetPasswordSubmit(e) {
    e.preventDefault();
    const submitBtn = e.target.querySelector('button[type="submit"]');
    const email = document.getElementById('resetAdminEmail').value.trim();
    const password = document.getElementById('resetAdminPassword').value;
    if (!password) {
        showToast('New password is required', 'error');
        return;
    }
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Resetting…';
    }
    try {
        const res = await fetchWithAuth('/api/admin/reset-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            closeModal('resetPasswordModal');
            showToast(`Password reset for ${email}`, 'success');
            loadAdminAuditLog();
        } else {
            showToast(data.error || 'Failed to reset password', 'error', { title: 'Reset Failed' });
        }
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            showToast('Network error while resetting password', 'error');
        }
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Reset Password';
        }
    }
}


const adminAuditState = {
    action: '', actor: '', target: '', details: '', from: '', to: '',
    limit: 50, offset: 0, total: 0
};

function applyAdminAuditFilters() {
    const read = id => (document.getElementById(id) || {}).value || '';
    adminAuditState.action = read('adminAuditFilterAction');
    adminAuditState.actor = read('adminAuditFilterActor');
    adminAuditState.target = read('adminAuditFilterTarget');
    adminAuditState.details = read('adminAuditFilterDetails');
    adminAuditState.from = read('adminAuditFilterFrom');
    adminAuditState.to = read('adminAuditFilterTo');
    adminAuditState.offset = 0;
    loadAdminAuditLog();
}

function resetAdminAuditFilters() {
    adminAuditState.action = adminAuditState.actor = adminAuditState.target = '';
    adminAuditState.details = adminAuditState.from = adminAuditState.to = '';
    adminAuditState.offset = 0;
    ['adminAuditFilterAction', 'adminAuditFilterActor', 'adminAuditFilterTarget',
        'adminAuditFilterDetails', 'adminAuditFilterFrom', 'adminAuditFilterTo'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = '';
        });
    loadAdminAuditLog();
}

function adminAuditPage(dir) {
    const maxOffset = Math.max(0, adminAuditState.total - adminAuditState.limit);
    adminAuditState.offset = Math.min(Math.max(0, adminAuditState.offset + dir * adminAuditState.limit), maxOffset);
    loadAdminAuditLog();
}

async function loadAdminAuditLog() {
    const container = document.getElementById('adminAuditLogList');
    const pager = document.getElementById('adminAuditLogPager');
    if (!container) return;
    try {
        const params = new URLSearchParams({
            limit: String(adminAuditState.limit),
            offset: String(adminAuditState.offset)
        });
        if (adminAuditState.action) params.set('action', adminAuditState.action);
        if (adminAuditState.actor) params.set('actor', adminAuditState.actor);
        if (adminAuditState.target) params.set('target', adminAuditState.target);
        if (adminAuditState.details) params.set('details', adminAuditState.details);
        if (adminAuditState.from) params.set('from', new Date(adminAuditState.from).toISOString().slice(0, 19));
        if (adminAuditState.to) params.set('to', new Date(adminAuditState.to).toISOString().slice(0, 19));
        params.forEach((v, k) => { if (!v) params.delete(k); });

        const res = await fetchWithAuth('/api/admin/log?' + params.toString());
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            container.innerHTML = renderSharedErrorState(err.error || 'Failed to load audit log.', 'loadAdminAuditLog()');
            return;
        }
        const data = await res.json();
        const entries = data.entries || [];
        adminAuditState.total = data.total || 0;

        if (pager) {
            const totalPages = Math.max(1, Math.ceil(adminAuditState.total / adminAuditState.limit));
            const curPage = Math.min(Math.floor(adminAuditState.offset / adminAuditState.limit) + 1, totalPages);
            pager.innerHTML = `
                <div style="display:flex; align-items:center; gap:10px; justify-content:flex-end; flex-wrap:wrap;">
                    <span class="badge badge-stage">${adminAuditState.total} event${adminAuditState.total !== 1 ? 's' : ''}</span>
                    <span style="color:var(--text-secondary); font-size:12px;">Page ${curPage} / ${totalPages}</span>
                    <button class="btn btn-secondary" style="height:26px; font-size:11px; padding:0 10px;"
                        ${curPage <= 1 ? 'disabled' : ''} onclick="adminAuditPage(-1)">Prev</button>
                    <button class="btn btn-secondary" style="height:26px; font-size:11px; padding:0 10px;"
                        ${curPage >= totalPages ? 'disabled' : ''} onclick="adminAuditPage(1)">Next</button>
                </div>`;
        }

        if (!entries || entries.length === 0) {
            container.innerHTML = renderSharedEmptyState('No audit events match the current filters.', '');
            return;
        }
        container.innerHTML = `
            <div class="table-wrap fade-in">
                <table>
                    <thead>
                        <tr>
                            <th>When</th>
                            <th>Action</th>
                            <th>Actor</th>
                            <th>Target</th>
                            <th>Details</th>
                            <th>IP</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${entries.map(x => `
                            <tr>
                                <td style="color:var(--text-secondary); white-space:nowrap;">${escapeHtml(x.created_at || '')}</td>
                                <td><span class="badge badge-stage">${escapeHtml(x.action)}</span></td>
                                <td>${escapeHtml(x.actor_email || '')}</td>
                                <td style="color:var(--text-secondary);">${escapeHtml(x.target_email || '-')}</td>
                                <td style="color:var(--text-secondary); white-space:normal; min-width:180px;">${escapeHtml(x.details || '-')}</td>
                                <td style="color:var(--text-tertiary);">${escapeHtml(x.ip_address || '-')}</td>
                            </tr>`).join('')}
                    </tbody>
                </table>
            </div>`;
    } catch (err) {
        if (!err.message.includes('Session expired')) {
            container.innerHTML = renderSharedErrorState('Failed to load audit log.', 'loadAdminAuditLog()');
        }
    }
}


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

    const row = aInput.closest('tr');
    const saveBtn = row ? row.querySelector('.inline-save-btn') : null;
    const origHtml = saveBtn ? saveBtn.innerHTML : '';
    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.innerHTML = `<span class="btn-spinner" aria-hidden="true"></span>Saving`;
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
    } finally {
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = origHtml;
        }
    }
}


function openSquadModal(squadId = null) {
    const hSel = document.getElementById('squadHouseId');
    const sSel = document.getElementById('squadSportId');
    if (!hSel || !sSel) return;

    hSel.innerHTML = housesData.map(h => `<option value="${h.id}">${escapeHtml(h.name)}</option>`).join('');
    sSel.innerHTML = sportsData.map(s => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join('');

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
    const submitBtn = e.target.querySelector('button[type="submit"]');
    const squadId = document.getElementById('squadId').value;
    const house_id = document.getElementById('squadHouseId').value;
    const sport_id = document.getElementById('squadSportId').value;
    const gender = document.getElementById('squadGender').value;
    const squad_label = document.getElementById('squadLabel').value;

    const method = squadId ? 'PUT' : 'POST';
    const url = squadId ? `/api/teams/${squadId}` : '/api/teams';

    setButtonLoading(submitBtn, true, 'Saving…');
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
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

async function deleteSquad(id) {
    if (!confirm('Delete this squad?')) return;
    showBusy('Deleting squad…');
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
    } finally {
        hideBusy();
    }
}


function openPlayerModal(playerId = null) {
    const teamSel = document.getElementById('playerTeamId');
    if (!teamSel) return;
    teamSel.innerHTML = squadsData.map(s => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join('');

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
    const submitBtn = e.target.querySelector('button[type="submit"]');
    const playerId = document.getElementById('playerId').value;
    const name = document.getElementById('playerName').value;
    const roll_number = document.getElementById('playerRoll').value;
    const team_id = document.getElementById('playerTeamId').value;
    const grade = document.getElementById('playerGrade').value;
    const section = document.getElementById('playerSection').value;
    const gender = document.getElementById('playerGender').value;

    const method = playerId ? 'PUT' : 'POST';
    const url = playerId ? `/api/players/${playerId}` : '/api/players';

    setButtonLoading(submitBtn, true, 'Saving…');
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
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

async function deletePlayer(id) {
    if (!confirm('Delete this player?')) return;
    showBusy('Deleting player…');
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
    } finally {
        hideBusy();
    }
}


async function openCreateMatchModal() {
    const sportSel = document.getElementById('newMatchSportId');
    if (!sportSel) return;
    if (!sportsData.length) await fetchSports();
    if (!squadsData.length) await fetchSquads();
    sportSel.innerHTML = sportsData.map(s => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join('');
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
    aSel.innerHTML = matching.map(s => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join('');
    bSel.innerHTML = matching.map((s, i) => `<option value="${s.id}" ${i === 1 ? 'selected' : ''}>${escapeHtml(s.name)}</option>`).join('');
}

async function handleCreateMatchSubmit(e) {
    e.preventDefault();
    const submitBtn = e.target.querySelector('button[type="submit"]');
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
    setButtonLoading(submitBtn, true, 'Creating…');
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
    } finally {
        setButtonLoading(submitBtn, false);
    }
}



async function openMatchModal(matchId) {
    if (!matchId) return;


    if (!squadsData.length) await fetchSquads();
    if (!housesData.length) await fetchHouses();


    let match = matchesData.find(m => m.id === matchId);


    if (!match && typeof allFixtures !== 'undefined' && Array.isArray(allFixtures)) {
        match = allFixtures.find(m => m.id === matchId);
    }


    if (!match) {
        await fetchMatches();
        match = matchesData.find(m => m.id === matchId);
    }

    if (!match) {
        showToast('Could not load fixture details. Please refresh and try again.', 'error');
        return;
    }


    const matchIdInput = document.getElementById('matchId');
    if (matchIdInput) matchIdInput.value = match.id;


    const aName = getTeamName(match.team_a_id, match);
    const bName = getTeamName(match.team_b_id, match);


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


async function handleMatchSubmit(e) {
    e.preventDefault();
    const submitBtn = e.target.querySelector('button[type="submit"]');
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

    setButtonLoading(submitBtn, true, 'Saving…');
    try {
        const res = await fetchWithAuth(`/api/matches/${matchId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ score_team_a, score_team_b })
        });

        if (res.ok) {
            closeModal('matchModal');
            showToast('Match score updated successfully', 'success');


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
    } finally {
        setButtonLoading(submitBtn, false);
    }
}


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
                    Warning: <strong>${escapeHtml(squad.name)}</strong> has ${count} players (recommended ${min}-${max}).
                </div>`;
            }
        }
    });
    if (warnings) warnings.innerHTML = warnHtml;

    if (tbody) {
        tbody.innerHTML = rows.map(r => `
        <tr>
            <td class="tabular">${escapeHtml(r.roll_number || '-')}</td>
            <td style="font-weight:700;">${escapeHtml(r.name)}</td>
            <td>${escapeHtml(r.grade || '-')}${r.section ? ` (${escapeHtml(r.section)})` : ''}</td>
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



function getTeamName(teamId, matchObj = null) {
    if (!teamId) return 'TBD';


    let t = squadsData.find(s => s.id === teamId);


    if (!t && matchObj) {
        if (matchObj.team_a_id === teamId && matchObj.team_a) t = matchObj.team_a;
        else if (matchObj.team_b_id === teamId && matchObj.team_b) t = matchObj.team_b;
    }

    if (!t) return 'TBD';

    const houseId = t.house_id;
    const sportId = t.sport_id || (matchObj ? matchObj.sport_id : null);
    const gender = t.gender || (matchObj ? matchObj.gender : null);
    const houseName = (t.houses && t.houses.name) ? t.houses.name : getHouseName(houseId);


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
    if (housesObj && housesObj.color_hex) return cssColor(housesObj.color_hex) || 'var(--border)';
    const h = housesData.find(x => x.id === houseId);
    return h ? (cssColor(h.color_hex) || 'var(--border)') : 'var(--border)';
}

function getSportName(sportId) {
    const s = sportsData.find(x => x.id === sportId);
    return s ? s.name : 'Sport';
}



let _searchDebounce = null;
let _searchOverlayOpen = false;

function openSearchOverlay() {
    const overlay = document.getElementById('searchOverlay');
    const input = document.getElementById('searchOverlayInput');
    const results = document.getElementById('searchOverlayResults');
    if (!overlay) return;
    overlay.classList.remove('hidden');
    _searchOverlayOpen = true;
    if (results) {
        results.innerHTML = `
        <div class="search-overlay-hint">
            <p>Type to search across all data</p>
            <div class="search-overlay-hint-tags">
                <span class="search-hint-tag">Houses</span>
                <span class="search-hint-tag">Sports</span>
                <span class="search-hint-tag">Squads</span>
                <span class="search-hint-tag">Players</span>
                <span class="search-hint-tag">Matches</span>
            </div>
        </div>`;
    }
    if (input) {
        input.value = '';
        setTimeout(() => input.focus(), 100);
        input.addEventListener('input', _onSearchOverlayInput);
        input.addEventListener('keydown', _onSearchOverlayKeydown);
    }
}

function closeSearchOverlay() {
    const overlay = document.getElementById('searchOverlay');
    const input = document.getElementById('searchOverlayInput');
    if (overlay) overlay.classList.add('hidden');
    _searchOverlayOpen = false;
    if (input) {
        input.removeEventListener('input', _onSearchOverlayInput);
        input.removeEventListener('keydown', _onSearchOverlayKeydown);
    }
    if (_searchDebounce) { clearTimeout(_searchDebounce); _searchDebounce = null; }
}

function _onSearchOverlayInput(e) {
    const q = e.target.value.trim();
    if (_searchDebounce) clearTimeout(_searchDebounce);
    if (!q) {
        const results = document.getElementById('searchOverlayResults');
        if (results) {
            results.innerHTML = `
            <div class="search-overlay-hint">
                <p>Type to search across all data</p>
                <div class="search-overlay-hint-tags">
                    <span class="search-hint-tag">Houses</span>
                    <span class="search-hint-tag">Sports</span>
                    <span class="search-hint-tag">Squads</span>
                    <span class="search-hint-tag">Players</span>
                    <span class="search-hint-tag">Matches</span>
                </div>
            </div>`;
        }
        return;
    }
    _searchDebounce = setTimeout(() => _performOverlaySearch(q), 250);
}

function _onSearchOverlayKeydown(e) {
    if (e.key === 'Escape') {
        closeSearchOverlay();
        return;
    }
    if (e.key === 'Enter') {
        const q = (e.target.value || '').trim();
        if (q) {
            closeSearchOverlay();
            window.location.href = '/search?q=' + encodeURIComponent(q);
        }
    }
}

async function _performOverlaySearch(query) {
    const results = document.getElementById('searchOverlayResults');
    if (!results) return;

    results.innerHTML = `
    <div class="search-overlay-loading">
        <span class="btn-spinner" aria-hidden="true" style="width:16px; height:16px;"></span>
        Searching...
    </div>`;

    try {
        const res = await fetch('/api/search?q=' + encodeURIComponent(query));
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const data = await res.json();
        _renderOverlayResults(data.results || {}, query);
    } catch (e) {
        results.innerHTML = `
        <div class="search-overlay-no-results">
            <p>Search failed. Please try again.</p>
        </div>`;
    }
}

function _renderOverlayResults(results, query) {
    const container = document.getElementById('searchOverlayResults');
    if (!container) return;

    const categoryOrder = ['matches', 'squads', 'houses', 'players', 'sports'];
    const categoryMeta = {
        matches: { title: 'Matches', icon: 'icon-calendar', color: 'var(--text-secondary)' },
        squads: { title: 'Squads', icon: 'icon-shield', color: 'var(--c-mahakali)' },
        houses: { title: 'Houses', icon: 'icon-shield', color: 'var(--c-karnali)' },
        players: { title: 'Players', icon: 'icon-info', color: 'var(--c-mechi)' },
        sports: { title: 'Sports', icon: 'icon-football', color: 'var(--c-koshi)' },
    };

    let totalResults = 0;
    categoryOrder.forEach(k => { totalResults += (results[k] || []).length; });

    if (totalResults === 0) {
        container.innerHTML = `
        <div class="search-overlay-no-results">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                <line x1="8" y1="11" x2="14" y2="11"/>
            </svg>
            <p>No results for "<strong>${escapeHtml(query)}</strong>"</p>
        </div>`;
        return;
    }

    let html = '';

    categoryOrder.forEach(key => {
        const items = results[key] || [];
        if (items.length === 0) return;
        const meta = categoryMeta[key];

        html += `<div class="search-overlay-category">
            <div class="search-overlay-cat-header">
                <svg class="icon" width="12" height="12" style="color:${meta.color};"><use href="#${meta.icon}"/></svg>
                ${meta.title}
                <span class="search-overlay-cat-count">${items.length}</span>
            </div>`;

        const shown = items.slice(0, 5);

        if (key === 'matches') {
            html += '<div class="search-overlay-grid">';
            shown.forEach((r, i) => { html += _searchMatchCard(r.item, i); });
            html += '</div>';
        } else if (key === 'squads') {
            html += `<div class="table-wrap search-overlay-table"><table>
                <thead><tr>
                    <th style="width:48px;">Rank</th><th>Squad</th><th>House</th><th>Sport</th><th>Gender</th>
                    <th>P</th><th>W</th><th>D</th><th>L</th><th>Diff</th><th style="text-align:right;">Pts</th>
                </tr></thead><tbody>`;
            shown.forEach((r, i) => { html += _searchSquadRow(r.item, i); });
            html += '</tbody></table></div>';
        } else if (key === 'houses') {
            html += '<div class="house-hero-grid search-overlay-houses">';
            shown.forEach((r, i) => { html += _searchHouseCard(r.item, i); });
            html += '</div>';
        } else if (key === 'players') {
            html += `<div class="table-wrap search-overlay-table"><table>
                <thead><tr><th>Name</th><th>Team</th><th>Grade</th><th style="text-align:right;">Roll #</th></tr></thead><tbody>`;
            shown.forEach((r, i) => { html += _searchPlayerRow(r.item, i); });
            html += '</tbody></table></div>';
        } else if (key === 'sports') {
            html += '<div class="search-overlay-grid search-overlay-sports">';
            shown.forEach((r, i) => { html += _searchSportCard(r.item, i); });
            html += '</div>';
        }

        if (items.length > 5) {
            html += `<div style="text-align:center; padding:4px 0; font-size:0.72rem; color:var(--text-tertiary);">+${items.length - 5} more</div>`;
        }

        html += `</div>`;
    });

    html += `
    <a href="/search?q=${encodeURIComponent(query)}" class="search-overlay-view-all">
        View all ${totalResults} results
        <svg class="icon" width="14" height="14"><use href="#icon-play"/></svg>
    </a>`;

    container.innerHTML = html;
}


function _searchMatchCard(m, idx) {
    const isCompleted = m.status === 'completed';
    const teamAName = m.team_a && m.team_a.name ? m.team_a.name : getTeamName(m.team_a_id, m);
    const teamBName = m.team_b && m.team_b.name ? m.team_b.name : getTeamName(m.team_b_id, m);
    const teamA = m.team_a || null;
    const teamB = m.team_b || null;
    const colorA = getHouseColor(m.team_a_id, teamA ? teamA.houses : null);
    const colorB = getHouseColor(m.team_b_id, teamB ? teamB.houses : null);

    const stageLabel = m.stage === 'semifinal' ? 'Semifinal'
        : m.stage === 'final' ? 'Final'
            : 'League';

    const editBtn = currentToken && m.team_a_id && m.team_b_id ? `
        <button onclick="openMatchModal('${jsStrLiteral(m.id)}')" class="btn btn-secondary btn-icon" title="Edit score" style="height:26px; width:26px; border-radius:6px;">
            <svg class="icon" width="12" height="12"><use href="#icon-edit"/></svg>
        </button>` : '';

    let scoreSection = '';
    if (isCompleted && (m.score_summary || (m.score_team_a != null && m.score_team_b != null))) {
        const summary = m.score_summary || `${m.score_team_a} - ${m.score_team_b}`;
        scoreSection = `
        <div class="fixture-score">
            <span class="score-label">Final Score</span>
            <span class="score-text">${escapeHtml(summary)}</span>
        </div>`;
    } else if (!m.team_a_id || !m.team_b_id) {
        scoreSection = `<p class="fixture-unplayed" style="font-style:italic;">TBD - awaiting qualifiers</p>`;
    } else {
        scoreSection = `<p class="fixture-unplayed">Not yet played</p>`;
    }

    return `
    <div class="fixture-card reveal" style="--d:${(idx || 0) % 12 * 45}ms;">
        <div style="display:flex; align-items:center; justify-content:space-between; gap:8px;">
            <div style="display:flex; align-items:center; gap:6px;">
                <span class="badge badge-stage">${stageLabel}</span>
                <span class="badge ${isCompleted ? 'badge-status-completed' : 'badge-status-scheduled'}">
                    ${isCompleted ? 'FT' : 'Scheduled'}
                </span>
            </div>
            ${editBtn}
        </div>
        <div class="fixture-matchup">
            <div style="display:flex; align-items:center; gap:8px;">
                <span style="width:3px; height:28px; border-radius:2px; background-color:${colorA}; flex-shrink:0;"></span>
                <span class="fixture-team">${escapeHtml(teamAName)}</span>
            </div>
            <span class="fixture-vs">VS</span>
            <div style="display:flex; align-items:center; gap:8px; justify-content:flex-end;">
                <span class="fixture-team team-b">${escapeHtml(teamBName)}</span>
                <span style="width:3px; height:28px; border-radius:2px; background-color:${colorB}; flex-shrink:0;"></span>
            </div>
        </div>
        ${scoreSection}
    </div>`;
}


function _searchMatchSections(items) {
    if (!items || items.length === 0) return '';
    const groups = {};
    items.forEach(m => {
        const sportName = (m.sports && m.sports.name) ? m.sports.name : 'Unknown';
        const gender = m.gender || 'Unknown';
        const key = sportName + '__' + gender;
        if (!groups[key]) groups[key] = { sportName, gender, matches: [] };
        groups[key].matches.push(m);
    });

    const ORDER = ['Futsal', 'Basketball', 'Cricksal'];
    const GENDER_ORDER = ['Girls', 'Boys'];
    const keys = Object.keys(groups).sort((a, b) => {
        const [sA, gA] = a.split('__');
        const [sB, gB] = b.split('__');
        const oA = ORDER.indexOf(sA);
        const oB = ORDER.indexOf(sB);
        if ((oA === -1 ? 99 : oA) !== (oB === -1 ? 99 : oB)) return (oA === -1 ? 99 : oA) - (oB === -1 ? 99 : oB);
        return GENDER_ORDER.indexOf(gA) - GENDER_ORDER.indexOf(gB);
    });

    let html = '';
    keys.forEach(key => {
        const g = groups[key];
        const completedCount = g.matches.filter(m => m.status === 'completed').length;
        const sportIconId = g.sportName.toLowerCase().includes('basket') ? 'icon-basketball'
            : g.sportName.toLowerCase().includes('cricket') ? 'icon-cricket'
                : 'icon-football';

        html += `
        <div>
            <div class="section-header">
                <svg class="icon" width="16" height="16" style="color:var(--text-secondary);">
                    <use href="#${sportIconId}"/>
                </svg>
                <span class="section-title">${escapeHtml(g.sportName)} - ${escapeHtml(g.gender)}</span>
                <span class="badge badge-stage">${completedCount}/${g.matches.length} played</span>
            </div>
            <div class="card-grid">`;
        g.matches.forEach((m, i) => { html += _searchMatchCard(m, i); });
        html += `</div></div>`;
    });

    return html;
}


function _searchSquadRow(t, idx) {
    const stats = t._stats || {};
    const house = t.houses || {};
    const sport = t.sports || {};
    const color = getHouseColor(t.house_id, house) || 'var(--border)';
    const teamName = escapeHtml(t.name || 'Squad') + (t.squad_label ? ' ' + escapeHtml(t.squad_label) : '');
    const houseLabel = escapeHtml(house.name || '') + (t.squad_label ? ' ' + escapeHtml(t.squad_label) : '');
    const sportName = escapeHtml(sport.name || '');
    const gender = escapeHtml(t.gender || '');
    const played = stats.played || 0;
    const wins = stats.wins || 0;
    const draws = stats.draws || 0;
    const losses = stats.losses || 0;
    const diff = stats.score_difference != null ? stats.score_difference : 0;
    const diffStr = diff > 0 ? '+' + diff : String(diff);
    const points = stats.points || 0;
    const rank = stats.rank != null ? stats.rank : '\u2013';
    const slug = encodeURIComponent((sport.name || '').toLowerCase());

    return `
    <tr class="trow search-row-link" style="--d:${(idx || 0) % 10 * 40}ms;" onclick="location.href='/standings/${slug}'">
        <td><span class="rank-pill" style="background:${color};">#${rank}</span></td>
        <td style="font-family:var(--font-display); font-weight:620;">${teamName}</td>
        <td>
            <div style="display:flex; align-items:center; gap:8px;">
                <span style="width:8px; height:8px; border-radius:3px; background:${color};"></span>
                <span style="color:${color}; font-weight:620;">${houseLabel}</span>
            </div>
        </td>
        <td style="color:var(--text-secondary); font-size:0.82rem;">${sportName}</td>
        <td style="color:var(--text-secondary); font-size:0.82rem;">${gender}</td>
        <td class="tabular">${played}</td>
        <td class="tabular" style="color:var(--c-karnali); font-weight:600;">${wins}</td>
        <td class="tabular" style="color:var(--text-tertiary);">${draws}</td>
        <td class="tabular" style="color:#F87171; font-weight:600;">${losses}</td>
        <td class="tabular" style="color:var(--text-secondary);">${diffStr}</td>
        <td style="text-align:right; font-weight:700; color:${color};" class="tabular">${points}</td>
    </tr>`;
}


function _searchHouseCard(h, idx) {
    const s = h._overall || {};
    const color = escapeHtml(h.color_hex || 'var(--border)');
    const name = escapeHtml(h.name || 'House');
    const isLeader = s.rank === 1;
    const rank = s.rank != null ? s.rank : '\u2013';
    const squads = s.total_squads || 0;
    const played = s.matches_played || 0;
    const points = s.total_points || 0;
    const wins = s.total_wins || 0;
    const draws = s.total_draws || 0;
    const losses = s.total_losses || 0;

    return `
    <div class="house-hero-card tilt reveal${isLeader ? ' is-leader' : ''}" style="--accent:${color}; --d:${(idx || 0) % 4 * 60}ms;">
        <div>
            <span class="hero-medal">Rank #${rank}</span>
            <div class="house-name" style="margin-top:8px;">${name}</div>
            <div class="hero-sub">${squads} squad${squads !== 1 ? 's' : ''} registered \u00b7 ${played} match${played !== 1 ? 'es' : ''}</div>
        </div>
        <div class="hero-stats">
            <div>
                <div class="stat-label">Total Points</div>
                <div class="stat-value" data-tick="${points}">${points}</div>
            </div>
            <div style="text-align:right;">
                <div class="stat-value-sm tabular">
                    <span class="wl-good">${wins}</span>
                    <span style="color:var(--text-tertiary);">&nbsp;-&nbsp;</span>
                    <span class="wl-mid">${draws}</span>
                    <span style="color:var(--text-tertiary);">&nbsp;-&nbsp;</span>
                    <span class="wl-bad">${losses}</span>
                </div>
            </div>
        </div>
    </div>`;
}

function _searchPlayerRow(p, idx) {
    const team = p.teams || {};
    const teamName = escapeHtml(team.name || '');
    const playerName = escapeHtml(p.name || 'Player');
    const grade = p.grade != null ? escapeHtml(String(p.grade)) : '\u2013';
    const roll = p.roll_number != null ? escapeHtml(String(p.roll_number)) : '\u2013';

    return `
    <tr class="trow" style="--d:${(idx || 0) % 10 * 40}ms;">
        <td style="font-family:var(--font-display); font-weight:620;">${playerName}</td>
        <td style="color:var(--text-secondary); font-size:0.82rem;">${teamName}</td>
        <td class="tabular" style="color:var(--text-secondary); font-size:0.82rem;">${grade}</td>
        <td class="tabular" style="text-align:right; color:var(--text-tertiary); font-size:0.82rem;">${roll}</td>
    </tr>`;
}

function _searchSportCard(s, idx) {
    const type = s.type || 'generic';
    const icons = { football: 'icon-football', basketball: 'icon-basketball', cricket: 'icon-cricket' };
    const icon = icons[type] || 'icon-info';
    const colors = { football: 'var(--c-karnali)', basketball: 'var(--c-koshi)', cricket: 'var(--c-mahakali)' };
    const accent = colors[type] || 'var(--text-secondary)';
    const slug = encodeURIComponent((s.name || '').toLowerCase());

    return `
    <a href="/standings/${slug}" class="search-sport-card reveal" style="--accent:${accent}; --d:${(idx || 0) % 6 * 60}ms;">
        <div class="search-sport-icon" style="background:color-mix(in srgb, ${accent} 14%, transparent); color:${accent};">
            <svg class="icon" width="18" height="18"><use href="#${icon}"/></svg>
        </div>
        <div style="min-width:0; flex:1;">
            <div class="search-sport-name" style="font-family:var(--font-display); font-size:0.9rem; font-weight:620;">${escapeHtml(s.name || 'Sport')}</div>
            <div class="search-sport-type" style="font-size:0.72rem; color:var(--text-secondary); text-transform:capitalize;">${escapeHtml(type)}</div>
        </div>
        ${s.level ? `<span class="badge badge-stage">${escapeHtml(s.level)}</span>` : ''}
    </a>`;
}


document.addEventListener('keydown', function (e) {
    if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        if (_searchOverlayOpen) {
            closeSearchOverlay();
        } else {
            openSearchOverlay();
        }
    }
    if (e.key === 'Escape' && _searchOverlayOpen) {
        closeSearchOverlay();
    }
});


document.addEventListener('click', function (e) {
    if (!_searchOverlayOpen) return;
    if (e.target.closest('#searchBtn') || e.target.closest('#themeToggleBtn')) return;
    const overlay = document.getElementById('searchOverlay');
    const content = overlay ? overlay.querySelector('.search-overlay-content') : null;
    if (overlay && !overlay.classList.contains('hidden') && content && !content.contains(e.target)) {
        closeSearchOverlay();
    }
});
