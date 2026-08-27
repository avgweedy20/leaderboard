// DSS Sports Inter-House Meet Web Application Logic
let currentToken = localStorage.getItem('sb_auth_token') || null;
let activeTab = 'houseLeaderboardTab';
let sportsData = [];
let housesData = [];
let selectedSportId = '';

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    checkAuthUI();
    checkDbHealth();
    loadInitialData();
});

async function checkDbHealth() {
    const badge = document.getElementById('dbModeBadge');
    if (!badge) return;
    try {
        const res = await fetch('/api/health');
        const data = await res.json();
        if (data.supabase_connected || data.mode === 'supabase') {
            badge.textContent = 'Connected: Supabase DB';
            badge.style.color = 'var(--house-karnali)';
        } else {
            badge.textContent = 'Development: In-Memory DB';
            badge.style.color = 'var(--house-koshi)';
        }
    } catch (e) {
        badge.textContent = 'Offline';
        badge.style.color = 'var(--error)';
    }
}

function initTheme() {
    const savedTheme = localStorage.getItem('sb_theme') || 'dark';
    document.documentElement.setAttribute('data-theme', savedTheme);
    updateThemeUI(savedTheme);
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('sb_theme', next);
    updateThemeUI(next);
}

function updateThemeUI(theme) {
    const themeText = document.getElementById('themeText');
    const themeIcon = document.getElementById('themeIcon');
    if (themeText) themeText.textContent = theme === 'dark' ? 'Dark Mode' : 'Light Mode';
    if (themeIcon) themeIcon.innerHTML = `<use href="${theme === 'dark' ? '#icon-moon' : '#icon-sun'}"></use>`;
}

function checkAuthUI() {
    const authBtnText = document.getElementById('authBtnText');
    const authIcon = document.getElementById('authIcon');
    const adminTabBtn = document.getElementById('adminTabBtn');

    if (currentToken) {
        if (authBtnText) authBtnText.textContent = 'Sign Out';
        if (authIcon) authIcon.innerHTML = `<use href="#icon-logout"></use>`;
        if (adminTabBtn) adminTabBtn.style.display = 'inline-flex';
    } else {
        if (authBtnText) authBtnText.textContent = 'Admin Login';
        if (authIcon) authIcon.innerHTML = `<use href="#icon-login"></use>`;
        if (adminTabBtn) adminTabBtn.style.display = 'none';
        if (activeTab === 'adminTab') switchTab('houseLeaderboardTab');
    }
}

function openLoginModal() {
    if (currentToken) {
        currentToken = null;
        localStorage.removeItem('sb_auth_token');
        checkAuthUI();
        loadCurrentTabData();
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
            closeModal('loginModal');
            checkAuthUI();
            loadCurrentTabData();
        } else {
            alert(data.error || 'Login failed');
        }
    } catch (err) {
        alert('Network error during login');
    }
}

function openModal(id) { document.getElementById(id).classList.add('open'); }
function closeModal(id) { document.getElementById(id).classList.remove('open'); }

function switchTab(tabId) {
    activeTab = tabId;
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.style.display = 'none');

    const selectedBtn = Array.from(document.querySelectorAll('.tab-btn')).find(b => b.getAttribute('onclick').includes(tabId));
    if (selectedBtn) selectedBtn.classList.add('active');

    document.getElementById(tabId).style.display = 'block';
    loadCurrentTabData();
}

async function loadInitialData() {
    await fetchHouses();
    await fetchSports();
    loadCurrentTabData();
}

async function fetchHouses() {
    try {
        const res = await fetch('/api/houses');
        housesData = await res.json();
    } catch (e) {
        console.error('Error fetching houses', e);
    }
}

async function fetchSports() {
    try {
        const res = await fetch('/api/sports');
        sportsData = await res.json();
        renderSportFilterChips();
    } catch (e) {
        console.error('Error fetching sports', e);
    }
}

function renderSportFilterChips() {
    const chipGroup = document.getElementById('sportChipGroup');
    if (!chipGroup) return;

    let html = `<button type="button" class="chip ${selectedSportId === '' ? 'active' : ''}" onclick="selectSportChip('')">ALL SPORTS</button>`;
    sportsData.forEach(s => {
        const isActive = selectedSportId === s.id ? 'active' : '';
        html += `<button type="button" class="chip ${isActive}" onclick="selectSportChip('${s.id}')">${s.name.toUpperCase()}</button>`;
    });
    chipGroup.innerHTML = html;
}

function selectSportChip(sportId) {
    selectedSportId = sportId;
    renderSportFilterChips();
    loadCurrentTabData();
}

function loadCurrentTabData() {
    if (activeTab === 'houseLeaderboardTab') loadHouseOverallStandings();
    else if (activeTab === 'sportStandingsTab') loadPerSportStandings();
    else if (activeTab === 'fixturesTab') loadFixtures();
    else if (activeTab === 'adminTab') loadAdminLists();
}

// 1. OVERALL HOUSE STANDINGS
async function loadHouseOverallStandings() {
    const heroContainer = document.getElementById('houseHeroContainer');
    const tableContainer = document.getElementById('houseTableContainer');

    try {
        const res = await fetch('/api/leaderboard/overall');
        const standings = await res.json();

        let heroHtml = '';
        standings.forEach(h => {
            heroHtml += `
                <div class="house-hero-card" style="--house-color: ${h.color_hex}">
                    <span class="house-hero-rank">#${h.rank}</span>
                    <div>
                        <span class="house-badge" style="--house-color: ${h.color_hex}">${h.short_code} HOUSE</span>
                        <h2 style="margin-top: 12px; font-size: 28px;">${h.house_name}</h2>
                    </div>
                    <div style="margin-top: 24px; display: flex; justify-content: space-between; align-items: flex-end;">
                        <div>
                            <span class="mono-label" style="color: var(--text-secondary);">TOTAL POINTS</span>
                            <div class="display-score" style="font-size: 48px; color: ${h.color_hex};">${h.total_points}</div>
                        </div>
                        <div style="text-align: right;">
                            <span class="mono-label" style="color: var(--text-secondary);">W-D-L</span>
                            <div class="body-text" style="font-weight: 700;">${h.total_wins}-${h.total_draws}-${h.total_losses}</div>
                        </div>
                    </div>
                </div>
            `;
        });
        heroContainer.innerHTML = heroHtml;

        let tableHtml = `
            <table>
                <thead>
                    <tr>
                        <th style="width: 70px;">Rank</th>
                        <th>House Name</th>
                        <th>Squads</th>
                        <th>Played</th>
                        <th>Wins</th>
                        <th>Draws</th>
                        <th>Losses</th>
                        <th>Goal Diff</th>
                        <th>Total Points</th>
                    </tr>
                </thead>
                <tbody>
        `;

        standings.forEach(h => {
            tableHtml += `
                <tr>
                    <td><strong style="color: ${h.color_hex}; font-size: 18px;">#${h.rank}</strong></td>
                    <td>
                        <span style="display: inline-flex; align-items: center; gap: 8px; font-weight: 800;">
                            <span style="width: 12px; height: 12px; border-radius: 2px; background-color: ${h.color_hex}; display: inline-block;"></span>
                            ${h.house_name} House
                        </span>
                    </td>
                    <td>${h.total_squads}</td>
                    <td>${h.matches_played}</td>
                    <td>${h.total_wins}</td>
                    <td>${h.total_draws}</td>
                    <td>${h.total_losses}</td>
                    <td>${h.total_score_difference > 0 ? '+' + h.total_score_difference : h.total_score_difference}</td>
                    <td><strong style="font-size: 18px; color: ${h.color_hex};">${h.total_points} PTS</strong></td>
                </tr>
            `;
        });
        tableHtml += `</tbody></table>`;
        tableContainer.innerHTML = tableHtml;

    } catch (e) {
        heroContainer.innerHTML = `<p style="color: var(--error);">Error loading house standings.</p>`;
    }
}

// 2. PER-SPORT STANDINGS
async function loadPerSportStandings() {
    const gender = document.getElementById('filterGender').value;
    const container = document.getElementById('perSportStandingsContainer');

    try {
        const res = await fetch(`/api/leaderboard?sport_id=${selectedSportId}&gender=${gender}`);
        const standings = await res.json();

        if (!standings || standings.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <svg class="icon"><use href="#icon-empty"></use></svg>
                    <h3>No Per-Sport Standings Available</h3>
                </div>
            `;
            return;
        }

        let html = `
            <table>
                <thead>
                    <tr>
                        <th style="width: 60px;">Rank</th>
                        <th>Squad Name</th>
                        <th>House</th>
                        <th>Sport</th>
                        <th>Gender</th>
                        <th>Played</th>
                        <th>W</th>
                        <th>D</th>
                        <th>L</th>
                        <th>Diff</th>
                        <th>Points</th>
                    </tr>
                </thead>
                <tbody>
        `;

        standings.forEach(s => {
            html += `
                <tr>
                    <td><strong>#${s.rank}</strong></td>
                    <td><strong>${s.team_name}</strong></td>
                    <td>
                        <span style="display: inline-flex; align-items: center; gap: 6px; color: ${s.house_color}; font-weight: 700;">
                            <span style="width: 10px; height: 10px; border-radius: 2px; background-color: ${s.house_color};"></span>
                            ${s.house_name}
                        </span>
                    </td>
                    <td>${s.sport_name}</td>
                    <td>${s.gender}</td>
                    <td>${s.played}</td>
                    <td>${s.wins}</td>
                    <td>${s.draws}</td>
                    <td>${s.losses}</td>
                    <td>${s.score_difference > 0 ? '+' + s.score_difference : s.score_difference}</td>
                    <td><strong>${s.points} PTS</strong></td>
                </tr>
            `;
        });
        html += `</tbody></table>`;
        container.innerHTML = html;

    } catch (e) {
        container.innerHTML = `<p style="color: var(--error);">Failed to load per-sport standings.</p>`;
    }
}

// 3. FIXTURES
async function loadFixtures() {
    const container = document.getElementById('fixturesContainer');
    try {
        const res = await fetch(`/api/matches?sport_id=${selectedSportId}`);
        const matches = await res.json();

        if (!matches || matches.length === 0) {
            container.innerHTML = `
                <div class="empty-state" style="grid-column: 1 / -1;">
                    <svg class="icon"><use href="#icon-calendar"></use></svg>
                    <h3>No Scheduled Fixtures</h3>
                </div>
            `;
            return;
        }

        let html = '';
        matches.forEach(m => {
            const isCompleted = m.status === 'completed';
            html += `
                <div class="card">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                        <span class="mono-label" style="font-size: 11px;">${m.gender || 'Boys'} • ${m.stage.toUpperCase()}</span>
                        <span class="status-pill status-final">${m.status.toUpperCase()}</span>
                    </div>
                    <h4 style="margin-bottom: 12px;">${m.round_info || 'League Game'}</h4>
                    ${m.score_summary ? `
                        <div style="background-color: var(--surface-container-low); padding: 12px; border-radius: 6px; border: 1px solid var(--border-color); text-align: center; margin-bottom: 12px;">
                            <span class="mono-label" style="font-size: 11px; color: var(--text-secondary);">RAW MATCH RESULT</span>
                            <div class="headline-md tabular-nums" style="color: var(--primary-container); margin-top: 4px;">${m.score_summary}</div>
                        </div>
                    ` : '<p style="color: var(--text-secondary); margin-bottom: 12px;">Match Unplayed</p>'}
                </div>
            `;
        });
        container.innerHTML = html;
    } catch (e) {
        container.innerHTML = `<p style="color: var(--error);">Error loading fixtures.</p>`;
    }
}

// 4. ADMIN
async function loadAdminLists() {
    fetchSquadsList();
    fetchPlayersList();
}

async function fetchSquadsList() {
    const container = document.getElementById('adminSquadsList');
    try {
        const res = await fetch('/api/teams');
        const squads = await res.json();
        let html = `<table><thead><tr><th>Squad Name</th><th>House</th><th>Gender</th></tr></thead><tbody>`;
        squads.forEach(s => {
            html += `<tr><td><strong>${s.name}</strong></td><td>${(s.houses || {}).name || '-'}</td><td>${s.gender || '-'}</td></tr>`;
        });
        html += `</tbody></table>`;
        container.innerHTML = html;
    } catch (e) {
        container.innerHTML = `<p>Error loading squads.</p>`;
    }
}

async function fetchPlayersList() {
    const container = document.getElementById('adminPlayersList');
    try {
        const res = await fetch('/api/players');
        const players = await res.json();
        let html = `<table><thead><tr><th>RN</th><th>Player Name</th><th>Grade</th><th>Sec</th></tr></thead><tbody>`;
        players.forEach(p => {
            html += `<tr><td>${p.roll_number || '-'}</td><td><strong>${p.name}</strong></td><td>${p.grade || '-'}</td><td>${p.section || '-'}</td></tr>`;
        });
        html += `</tbody></table>`;
        container.innerHTML = html;
    } catch (e) {
        container.innerHTML = `<p>Error loading players.</p>`;
    }
}
