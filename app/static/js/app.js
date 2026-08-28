/**
 * DSS Sports — Inter-House Sports Meet
 * Main application logic
 *
 * Bug fixes included:
 *  1. loadCurrentTabData was undefined → replaced with page-aware re-fetch
 *  2. GSAP stagger left cards at opacity:0 → removed, CSS fade-in used instead
 *  3. getTeamName resolves from Supabase nested join data when squadsData misses
 */

// ─── GLOBALS ───────────────────────────────────────────────────────────────
let currentToken   = localStorage.getItem('sb_auth_token') || null;
let sportsData     = [];
let housesData     = [];
let squadsData     = [];
let playersData    = [];
let matchesData    = [];
let seederLogsData = [];
let selectedSportId = '';
let pendingCsvPlayers = [];

// House color map (keyed by lowercase house name, used as fallback)
const HOUSE_COLORS = {
    karnali:  '#10B981',
    koshi:    '#0EA5E9',
    mahakali: '#8B5CF6',
    mechi:    '#F97316'
};

// ─── INIT ──────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    checkAuthUI();
    checkDbHealth();
});

async function checkDbHealth() {
    const badge = document.getElementById('dbModeBadge');
    if (!badge) return;
    try {
        const res  = await fetch('/api/health');
        const data = await res.json();
        badge.textContent = data.supabase_connected ? 'Live DB' : 'Mock DB';
        badge.className   = 'badge badge-status-' + (data.supabase_connected ? 'completed' : 'scheduled');
    } catch (e) {
        badge.textContent = 'Offline';
        badge.className   = 'badge';
        badge.style.color = '#F87171';
    }
}

// ─── THEME ─────────────────────────────────────────────────────────────────
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

// ─── AUTH ──────────────────────────────────────────────────────────────────
function checkAuthUI() {
    const authBtnText = document.getElementById('authBtnText');
    const authIcon    = document.getElementById('authIcon');
    const adminTab    = document.getElementById('adminTabBtn');

    if (currentToken) {
        if (authBtnText) authBtnText.textContent = 'Sign Out';
        if (authIcon)    authIcon.innerHTML = `<use href="#icon-logout"/>`;
        if (adminTab)    adminTab.style.display = 'inline-flex';
    } else {
        if (authBtnText) authBtnText.textContent = 'Admin Login';
        if (authIcon)    authIcon.innerHTML = `<use href="#icon-login"/>`;
        if (adminTab)    adminTab.style.display = 'none';
        // Redirect away from admin pages if not logged in
        if (window.location.pathname.startsWith('/admin')) {
            window.location.href = '/';
        }
    }
}

function openLoginModal() {
    if (currentToken) {
        currentToken = null;
        localStorage.removeItem('sb_auth_token');
        checkAuthUI();
        window.location.reload();
    } else {
        openModal('loginModal');
    }
}

async function handleLogin(e) {
    e.preventDefault();
    const email    = document.getElementById('loginEmail').value;
    const password = document.getElementById('loginPassword').value;
    try {
        const res  = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();
        if (res.ok && data.access_token) {
            currentToken = data.access_token;
            localStorage.setItem('sb_auth_token', currentToken);
            closeModal('loginModal');
            checkAuthUI();
            window.location.href = '/admin';
        } else {
            alert(data.error || 'Login failed');
        }
    } catch (err) {
        alert('Network error during login');
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

async function fetchSeederLogs() {
    try {
        const res = await fetch('/api/admin/seeder-logs');
        seederLogsData = await res.json();
    } catch (e) { console.error('fetchSeederLogs:', e); }
}

// ─── SPORT FILTER CHIPS (standings page) ───────────────────────────────────
function renderSportFilterChips() {
    const group = document.getElementById('sportChipGroup');
    if (!group) return;

    let html = `<button class="chip ${selectedSportId === '' ? 'active' : ''}"
                        onclick="selectSportChip('', '')">All Sports</button>`;
    sportsData.forEach(s => {
        const active = selectedSportId === s.id;
        const slug   = s.name.toLowerCase();
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
function loadPerSportStandingsPage()     { loadPerSportStandings(); }
function loadFixturesPage()              {
    // fixtures.html manages its own state via loadAllFixtures()
}
function loadAdminCenterPage()           { loadAdminCenterData(); }

// ─── 1. OVERALL HOUSE STANDINGS ────────────────────────────────────────────
async function loadHouseOverallStandings() {
    const heroEl  = document.getElementById('houseHeroContainer');
    const tableEl = document.getElementById('houseTableContainer');

    try {
        const res      = await fetch('/api/leaderboard/overall');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const standings = await res.json();

        // ── Hero Cards ──
        if (!standings || standings.length === 0) {
            heroEl.innerHTML = renderSharedEmptyState('No standings data yet.', '');
        } else {
            heroEl.innerHTML = standings.map(h => {
                const color = h.color_hex || HOUSE_COLORS[h.house_name.toLowerCase()] || '#10B981';
                const sign  = h.total_score_difference > 0 ? '+' : '';
                return `
                <div class="house-hero-card fade-in" style="border-left-color:${color};">
                    <div style="display:flex; align-items:flex-start; justify-content:space-between; gap:8px;">
                        <div class="badge" style="background-color:${color}; color:#fff;">
                            ${h.short_code || h.house_name.slice(0,3).toUpperCase()}
                        </div>
                        <span class="rank-number" style="color:${color};">#${h.rank}</span>
                    </div>
                    <div>
                        <div class="house-name">${h.house_name}</div>
                        <div style="font-size:11px; color:var(--text-tertiary); margin-top:2px;">
                            ${h.total_squads} squad${h.total_squads !== 1 ? 's' : ''}
                        </div>
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
                            <div style="font-size:11px; color:var(--text-tertiary); margin-top:2px;" class="tabular">
                                Diff: ${sign}${h.total_score_difference}
                            </div>
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
                        <th>Diff</th>
                        <th style="text-align:right;">Points</th>
                    </tr>
                </thead>
                <tbody>
                    ${standings.map(h => {
                        const color = h.color_hex || HOUSE_COLORS[h.house_name.toLowerCase()] || '#10B981';
                        const sign  = h.total_score_difference > 0 ? '+' : '';
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
                            <td class="tabular" style="color:var(--text-secondary);">${sign}${h.total_score_difference}</td>
                            <td style="text-align:right; font-weight:700; color:${color};" class="tabular">${h.total_points}</td>
                        </tr>`;
                    }).join('')}
                </tbody>
            </table>
        </div>`;

    } catch (e) {
        heroEl.innerHTML  = renderSharedErrorState('Failed to load house standings.', 'loadHouseOverallStandings()');
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
                        const color = s.house_color || HOUSE_COLORS[(s.house_name||'').toLowerCase()] || '#10B981';
                        const sign  = s.score_difference > 0 ? '+' : '';
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
    await fetchSeederLogs();

    renderAdminSquadsTable();
    renderAdminPlayersTable();
    renderAdminSeederLogs();
    renderAdminFixturesTable();
}

// SQUADS TABLE
function renderAdminSquadsTable() {
    const container = document.getElementById('adminSquadsList');
    const badge     = document.getElementById('squadCountBadge');
    if (!container) return;
    if (badge) badge.textContent = `${squadsData.length} Squads`;

    if (!squadsData || squadsData.length === 0) {
        container.innerHTML = renderSharedEmptyState('No squads yet', '');
        return;
    }

    container.innerHTML = `
    <table>
        <thead>
            <tr>
                <th>Squad</th><th>House</th><th>Sport</th><th>Gender</th><th style="text-align:right;">Actions</th>
            </tr>
        </thead>
        <tbody>
            ${squadsData.map(s => {
                const hName  = (s.houses||{}).name  || getHouseName(s.house_id);
                const sName  = (s.sports||{}).name  || getSportName(s.sport_id);
                const color  = (s.houses||{}).color_hex || HOUSE_COLORS[(hName||'').toLowerCase()] || '#10B981';
                return `
                <tr style="border-left:3px solid ${color};">
                    <td style="font-weight:700;">${s.name}</td>
                    <td style="color:${color}; font-weight:700;">${hName}</td>
                    <td style="color:var(--text-secondary);">${sName}</td>
                    <td style="color:var(--text-secondary);">${s.gender} (${s.squad_label})</td>
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
    </table>`;
}

// PLAYERS TABLE
function renderAdminPlayersTable() {
    const container = document.getElementById('adminPlayersList');
    const badge     = document.getElementById('playerCountBadge');
    if (!container) return;
    if (badge) badge.textContent = `${playersData.length} Players`;

    if (!playersData || playersData.length === 0) {
        container.innerHTML = renderSharedEmptyState('No players yet', '');
        return;
    }

    container.innerHTML = `
    <table>
        <thead>
            <tr>
                <th style="width:60px;">Roll</th><th>Name</th><th>Squad</th><th>Grade</th><th style="text-align:right;">Actions</th>
            </tr>
        </thead>
        <tbody>
            ${playersData.map(p => {
                const teamName = getTeamName(p.team_id);
                return `
                <tr>
                    <td style="font-variant-numeric:tabular-nums; color:var(--text-secondary);">${p.roll_number || '—'}</td>
                    <td style="font-weight:700;">${p.name}</td>
                    <td style="color:var(--text-secondary); font-size:12px;">${teamName}</td>
                    <td style="color:var(--text-secondary);">${p.grade || '—'}${p.section ? ` (${p.section})` : ''}</td>
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
    </table>`;
}

// SEEDER LOGS
function renderAdminSeederLogs() {
    const container = document.getElementById('adminSeederLogsContainer');
    if (!container) return;

    if (!seederLogsData || seederLogsData.length === 0) {
        container.innerHTML = `<p style="font-size:12px; color:var(--text-tertiary); padding:12px;">No seeder logs yet. Run the seeder to populate data.</p>`;
        return;
    }

    const latest = seederLogsData[0];
    const isOk   = latest.status === 'success';

    container.innerHTML = `
    <div style="display:flex; flex-wrap:wrap; justify-content:space-between; align-items:flex-start; gap:12px; padding:16px;">
        <div>
            <div style="display:flex; align-items:center; gap:8px; margin-bottom:6px;">
                <span class="badge ${isOk ? 'badge-status-completed' : ''}" style="${isOk ? '' : 'background-color:#1C0505; color:#F87171; border-color:#7F1D1D;'}">
                    ${latest.status.toUpperCase()}
                </span>
                <span style="font-size:11px; color:var(--text-tertiary);">${new Date(latest.created_at).toLocaleString()}</span>
            </div>
            <p style="font-size:13px; font-weight:700;">${latest.details || 'Seeder completed'}</p>
        </div>
        <div style="display:flex; gap:12px; flex-wrap:wrap;">
            ${[
                ['Houses',   latest.houses_created   || 0, 'var(--c-karnali)'],
                ['Sports',   latest.sports_created   || 0, 'var(--c-koshi)'],
                ['Squads',   latest.squads_created   || 0, 'var(--c-mahakali)'],
                ['Players',  latest.players_created  || 0, 'var(--c-mechi)'],
                ['Fixtures', latest.fixtures_created || 0, 'var(--text-primary)'],
            ].map(([label, val, color]) => `
                <div style="text-align:center;">
                    <div style="font-size:9px; font-weight:700; text-transform:uppercase; letter-spacing:.05em; color:var(--text-tertiary);">${label}</div>
                    <div style="font-size:20px; font-weight:700; color:${color}; font-variant-numeric:tabular-nums;">${val}</div>
                </div>
            `).join('')}
        </div>
    </div>`;
}

// FIXTURES MANAGEMENT TABLE
function renderAdminFixturesTable() {
    const container = document.getElementById('adminFixturesContainer');
    if (!container) return;

    if (!matchesData || matchesData.length === 0) {
        container.innerHTML = `<p style="font-size:12px; color:var(--text-tertiary); padding:16px;">No matches in database.</p>`;
        return;
    }

    container.innerHTML = `
    <table>
        <thead>
            <tr>
                <th>Match</th><th>Sport / Gender</th><th>Stage</th><th>Status</th><th>Score</th><th style="text-align:right;">Action</th>
            </tr>
        </thead>
        <tbody>
            ${matchesData.map(m => {
                const aName  = getTeamName(m.team_a_id, m);
                const bName  = getTeamName(m.team_b_id, m);
                const sName  = getSportName(m.sport_id);
                const done   = m.status === 'completed';
                return `
                <tr>
                    <td style="font-weight:700; font-size:12px;">${aName} vs ${bName}</td>
                    <td style="color:var(--text-secondary); font-size:12px;">${sName} &mdash; ${m.gender}</td>
                    <td><span class="badge badge-stage">${m.stage || 'league'}</span></td>
                    <td><span class="badge ${done ? 'badge-status-completed' : 'badge-status-scheduled'}">${done ? 'FT' : 'Sched.'}</span></td>
                    <td style="font-variant-numeric:tabular-nums; font-weight:700;">${m.score_summary || '—'}</td>
                    <td style="text-align:right;">
                        <button onclick="openMatchModal('${m.id}')" class="btn btn-secondary" style="height:26px; font-size:11px; padding:0 10px;">
                            Record Score
                        </button>
                    </td>
                </tr>`;
            }).join('')}
        </tbody>
    </table>`;
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
            document.getElementById('squadId').value       = sq.id;
            document.getElementById('squadHouseId').value  = sq.house_id;
            document.getElementById('squadSportId').value  = sq.sport_id;
            document.getElementById('squadGender').value   = sq.gender || 'Boys';
            document.getElementById('squadLabel').value    = sq.squad_label || 'A';
            if (titleEl) titleEl.textContent = 'Edit Squad';
        }
    } else {
        document.getElementById('squadId').value    = '';
        document.getElementById('squadLabel').value = 'A';
        if (titleEl) titleEl.textContent = 'Add House Squad';
    }
    openModal('squadModal');
}

async function handleSquadSubmit(e) {
    e.preventDefault();
    const squadId    = document.getElementById('squadId').value;
    const house_id   = document.getElementById('squadHouseId').value;
    const sport_id   = document.getElementById('squadSportId').value;
    const gender     = document.getElementById('squadGender').value;
    const squad_label = document.getElementById('squadLabel').value;

    const method = squadId ? 'PUT' : 'POST';
    const url    = squadId ? `/api/teams/${squadId}` : '/api/teams';

    try {
        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify({ house_id, sport_id, gender, squad_label })
        });
        if (res.ok) { closeModal('squadModal'); await loadAdminCenterData(); }
        else { const err = await res.json(); alert(err.error || 'Failed to save squad'); }
    } catch (err) { alert('Network error'); }
}

async function deleteSquad(id) {
    if (!confirm('Delete this squad?')) return;
    try {
        const res = await fetch(`/api/teams/${id}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${currentToken}` }
        });
        if (res.ok) await loadAdminCenterData();
        else alert('Failed to delete squad');
    } catch (err) { alert('Network error'); }
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
            document.getElementById('playerId').value      = p.id;
            document.getElementById('playerName').value    = p.name;
            document.getElementById('playerRoll').value    = p.roll_number || '';
            document.getElementById('playerTeamId').value  = p.team_id;
            document.getElementById('playerGrade').value   = p.grade || '';
            document.getElementById('playerSection').value = p.section || '';
            document.getElementById('playerGender').value  = p.gender || 'Boys';
            if (titleEl) titleEl.textContent = 'Edit Player';
        }
    } else {
        ['playerId','playerName','playerRoll','playerGrade','playerSection'].forEach(id => {
            const el = document.getElementById(id); if (el) el.value = '';
        });
        if (titleEl) titleEl.textContent = 'Add Player';
    }
    openModal('playerModal');
}

async function handlePlayerSubmit(e) {
    e.preventDefault();
    const playerId = document.getElementById('playerId').value;
    const name     = document.getElementById('playerName').value;
    const roll_number = document.getElementById('playerRoll').value;
    const team_id  = document.getElementById('playerTeamId').value;
    const grade    = document.getElementById('playerGrade').value;
    const section  = document.getElementById('playerSection').value;
    const gender   = document.getElementById('playerGender').value;

    const method = playerId ? 'PUT' : 'POST';
    const url    = playerId ? `/api/players/${playerId}` : '/api/players';

    try {
        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify({ name, roll_number, team_id, grade, section, gender })
        });
        if (res.ok) { closeModal('playerModal'); await loadAdminCenterData(); }
        else { const err = await res.json(); alert(err.error || 'Failed to save player'); }
    } catch (err) { alert('Network error'); }
}

async function deletePlayer(id) {
    if (!confirm('Delete this player?')) return;
    try {
        const res = await fetch(`/api/players/${id}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${currentToken}` }
        });
        if (res.ok) await loadAdminCenterData();
        else alert('Failed to delete player');
    } catch (err) { alert('Network error'); }
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
    const gender  = (document.getElementById('newMatchGender')  || {}).value;
    const aSel    = document.getElementById('newMatchTeamA');
    const bSel    = document.getElementById('newMatchTeamB');
    if (!aSel || !bSel) return;

    const matching = squadsData.filter(s => s.sport_id === sportId && s.gender === gender);
    if (!matching.length) {
        aSel.innerHTML = bSel.innerHTML = `<option value="">No squads available</option>`;
        return;
    }
    aSel.innerHTML = matching.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
    bSel.innerHTML = matching.map((s, i) => `<option value="${s.id}" ${i===1 ? 'selected' : ''}>${s.name}</option>`).join('');
}

async function handleCreateMatchSubmit(e) {
    e.preventDefault();
    const sport_id   = document.getElementById('newMatchSportId').value;
    const gender     = document.getElementById('newMatchGender').value;
    const team_a_id  = document.getElementById('newMatchTeamA').value;
    const team_b_id  = document.getElementById('newMatchTeamB').value;
    const stage      = document.getElementById('newMatchStage').value;
    const round_info = document.getElementById('newMatchRoundInfo').value || 'League Game';

    if (!team_a_id || !team_b_id || team_a_id === team_b_id) {
        alert('Please select two distinct teams.');
        return;
    }
    try {
        const res = await fetch('/api/matches', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify({ sport_id, gender, team_a_id, team_b_id, stage, round_info })
        });
        if (res.ok) {
            closeModal('createMatchModal');
            await loadAdminCenterData();
            alert('Match created!');
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to create match');
        }
    } catch (err) { alert('Network error'); }
}

function openMatchModal(matchId) {
    const match = matchesData.find(m => m.id === matchId);
    if (!match) return;

    document.getElementById('matchId').value = match.id;
    const aName = getTeamName(match.team_a_id, match);
    const bName = getTeamName(match.team_b_id, match);
    const summary = document.getElementById('matchModalSummary');
    const aLabel  = document.getElementById('teamALabel');
    const bLabel  = document.getElementById('teamBLabel');
    if (summary) summary.textContent = `${aName} vs ${bName}`;
    if (aLabel)  aLabel.textContent  = `${aName} Score`;
    if (bLabel)  bLabel.textContent  = `${bName} Score`;
    document.getElementById('matchScoreA').value = match.score_team_a ?? 0;
    document.getElementById('matchScoreB').value = match.score_team_b ?? 0;

    openModal('matchModal');
}

async function handleMatchSubmit(e) {
    e.preventDefault();
    const matchId      = document.getElementById('matchId').value;
    const score_team_a = document.getElementById('matchScoreA').value;
    const score_team_b = document.getElementById('matchScoreB').value;

    try {
        const res = await fetch(`/api/matches/${matchId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify({ score_team_a, score_team_b })
        });

        if (res.ok) {
            closeModal('matchModal');

            // ── BUG 1 FIX: "loadCurrentTabData" was never defined.
            // Now we do a targeted re-fetch based on which page is active.
            const path = window.location.pathname;

            if (path === '/fixtures' || path === '/') {
                // Re-fetch matches and re-render — no page reload needed
                await fetchMatches();
                // If fixtures page has its own reloader (it does), call it
                if (typeof reloadFixturesAfterUpdate === 'function') {
                    await reloadFixturesAfterUpdate();
                }
                // Also update overall standings if on homepage
                if (path === '/') {
                    loadHouseOverallStandings();
                }
            } else if (path.startsWith('/standings')) {
                loadPerSportStandings();
            } else if (path.startsWith('/admin')) {
                // On admin page: re-render the fixtures management table
                await fetchMatches();
                renderAdminFixturesTable();
            }
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to update score');
        }
    } catch (err) {
        alert('Network error while updating match score');
    }
}

// ─── SEEDER ────────────────────────────────────────────────────────────────
async function triggerSeederRun() {
    const btn = document.getElementById('runSeederBtn');
    if (btn) { btn.disabled = true; btn.textContent = 'Running...'; }
    try {
        const res = await fetch('/api/admin/run-seeder', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` }
        });
        const data = await res.json();
        await loadAdminCenterData();
        alert(`Seeder done. ${data.players_created||0} players, ${data.fixtures_created||0} fixtures.`);
    } catch (e) {
        alert(`Seeder failed: ${e.message}`);
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = `<svg class="icon" width="14" height="14"><use href="#icon-play"/></svg> Run Seeder`;
        }
    }
}

// ─── CSV IMPORT ────────────────────────────────────────────────────────────
function openCsvModal() {
    pendingCsvPlayers = [];
    const fileInput = document.getElementById('csvFileInput');
    const preview   = document.getElementById('csvPreviewContainer');
    const commitBtn = document.getElementById('commitCsvBtn');
    if (fileInput) fileInput.value = '';
    if (preview)   preview.classList.add('hidden');
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
    if (lines.length < 2) { alert('CSV is empty or missing headers'); return; }

    const headers = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, '').toLowerCase());
    const rows = [];

    for (let i = 1; i < lines.length; i++) {
        const cols = lines[i].split(',').map(c => c.trim().replace(/^"|"$/g, ''));
        if (!cols[0]) continue;

        let roll='', name='', grade='', section='', gender='Boys', squadStr='A';
        headers.forEach((h, idx) => {
            const v = cols[idx] || '';
            if (h.includes('roll')||h.includes('rn'))          roll = v;
            else if (h.includes('name'))                        name = v;
            else if (h.includes('grade'))                       grade = v;
            else if (h.includes('sec'))                         section = v;
            else if (h.includes('gen')||h.includes('sex'))      gender = v;
            else if (h.includes('squad')||h.includes('team'))   squadStr = v;
        });
        if (!name) continue;

        let team_id = squadsData[0] ? squadsData[0].id : '';
        const found = squadsData.find(s =>
            s.name.toLowerCase().includes(squadStr.toLowerCase()) ||
            (s.squad_label||'').toLowerCase() === squadStr.toLowerCase()
        );
        if (found) team_id = found.id;

        const isUpdate = roll ? playersData.some(p => String(p.roll_number) === String(roll)) : false;
        rows.push({ roll_number: roll, name, grade, section,
                    gender: gender.toLowerCase().includes('girl') ? 'Girls' : 'Boys',
                    team_id, isUpdate });
    }

    pendingCsvPlayers = rows;
    renderCsvPreviewTable(rows);
}

function renderCsvPreviewTable(rows) {
    const preview   = document.getElementById('csvPreviewContainer');
    const tbody     = document.getElementById('csvPreviewTableBody');
    const badge     = document.getElementById('csvPreviewCountBadge');
    const warnings  = document.getElementById('csvValidationWarnings');
    const commitBtn = document.getElementById('commitCsvBtn');

    if (badge)  badge.textContent = `${rows.length} records`;

    // Squad size warnings
    const squadCounts = {};
    rows.forEach(r => { squadCounts[r.team_id] = (squadCounts[r.team_id]||0)+1; });
    let warnHtml = '';
    Object.keys(squadCounts).forEach(tid => {
        const count = squadCounts[tid];
        const squad = squadsData.find(s => s.id === tid);
        if (squad) {
            const sp = getSportName(squad.sport_id).toLowerCase();
            let min=7, max=11;
            if (sp.includes('futsal'))     { min=11; max=14; }
            else if (sp.includes('basket')) { min=7;  max=10; }
            if (count < min || count > max) {
                warnHtml += `<div style="padding:8px 12px; border:1px solid #92400E; border-radius:8px; background-color:#1C1200; color:#FCD34D; font-size:12px;">
                    Warning: <strong>${squad.name}</strong> has ${count} players (recommended ${min}–${max}).
                </div>`;
            }
        }
    });
    if (warnings) warnings.innerHTML = warnHtml;

    if (tbody) {
        tbody.innerHTML = rows.map(r => `
        <tr>
            <td class="tabular">${r.roll_number || '—'}</td>
            <td style="font-weight:700;">${r.name}</td>
            <td>${r.grade||'—'}${r.section ? ` (${r.section})`:''}</td>
            <td>${r.gender}</td>
            <td>${getTeamName(r.team_id)}</td>
            <td>
                <span class="badge ${r.isUpdate ? '' : 'badge-status-completed'}"
                      style="${r.isUpdate ? 'background-color:#1C1200;color:#FCD34D;border-color:#92400E;' : ''}">
                    ${r.isUpdate ? 'Update' : 'New'}
                </span>
            </td>
        </tr>`).join('');
    }

    if (preview)   preview.classList.remove('hidden');
    if (commitBtn) { commitBtn.disabled = rows.length === 0; commitBtn.style.opacity = rows.length ? '1' : '0.5'; }
}

async function commitCsvImport() {
    if (!pendingCsvPlayers.length) return;
    const btn = document.getElementById('commitCsvBtn');
    if (btn) { btn.disabled = true; btn.textContent = 'Importing...'; }
    try {
        const res = await fetch('/api/players/bulk', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify(pendingCsvPlayers)
        });
        if (res.ok) {
            const data = await res.json();
            alert(data.message || 'Import complete');
            closeModal('csvModal');
            await loadAdminCenterData();
        } else {
            const err = await res.json();
            alert(err.error || 'Import failed');
        }
    } catch (err) { alert('Network error during import'); }
    finally {
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
            <p class="state-empty-title">${title}</p>
            ${desc ? `<p class="state-empty-desc">${desc}</p>` : ''}
        </div>
    </div>`;
}

/** Reusable error state HTML with retry button */
function renderSharedErrorState(message, retryCall) {
    return `
    <div class="state-error">
        <svg class="icon" width="20" height="20" style="color:#F87171;"><use href="#icon-alert"/></svg>
        <p class="state-error-title">${message}</p>
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
        if      (matchObj.team_a_id === teamId && matchObj.team_a) t = matchObj.team_a;
        else if (matchObj.team_b_id === teamId && matchObj.team_b) t = matchObj.team_b;
    }

    if (!t) return 'TBD';

    const houseId  = t.house_id;
    const sportId  = t.sport_id || (matchObj ? matchObj.sport_id : null);
    const gender   = t.gender   || (matchObj ? matchObj.gender   : null);
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
