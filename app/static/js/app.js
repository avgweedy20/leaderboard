// ScoreBoard Web Application State & Logic
let currentToken = localStorage.getItem('sb_auth_token') || null;
let activeTab = 'leaderboardTab';
let sportsData = [];
let teamsData = [];
let playersData = [];
let matchesData = [];
let parsedCsvValidRows = [];

// DOM Loaded Initialization
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
            badge.textContent = 'Connected: Supabase Postgres DB';
            badge.style.color = 'var(--success-color)';
        } else {
            badge.textContent = 'Development: In-Memory Mock DB';
            badge.style.color = 'var(--warning-color)';
        }
    } catch (e) {
        badge.textContent = 'Offline';
    }
}

// THEME SYSTEM
function initTheme() {
    const savedTheme = localStorage.getItem('sb_theme') || 'light';
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
    if (themeText) {
        themeText.textContent = theme === 'dark' ? 'Dark Mode' : 'Light Mode';
    }
}

// AUTH SYSTEM
function checkAuthUI() {
    const authBtnText = document.getElementById('authBtnText');
    const adminTabBtn = document.getElementById('adminTabBtn');
    const addMatchBtn = document.getElementById('addMatchBtn');
    const generateBracketBtn = document.getElementById('generateBracketBtn');

    if (currentToken) {
        if (authBtnText) authBtnText.textContent = 'Admin Sign Out';
        if (adminTabBtn) adminTabBtn.style.display = 'block';
        if (addMatchBtn) addMatchBtn.style.display = 'inline-flex';
        if (generateBracketBtn) generateBracketBtn.style.display = 'inline-flex';
    } else {
        if (authBtnText) authBtnText.textContent = 'Admin Login';
        if (adminTabBtn) adminTabBtn.style.display = 'none';
        if (addMatchBtn) addMatchBtn.style.display = 'none';
        if (generateBracketBtn) generateBracketBtn.style.display = 'none';
        if (activeTab === 'adminTab') switchTab('leaderboardTab');
    }
}

function openLoginModal() {
    if (currentToken) {
        currentToken = null;
        localStorage.removeItem('sb_auth_token');
        checkAuthUI();
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

// MODAL CONTROLS
function openModal(id) {
    document.getElementById(id).classList.add('open');
}

function closeModal(id) {
    document.getElementById(id).classList.remove('open');
}

// TAB NAVIGATION
function switchTab(tabId) {
    activeTab = tabId;
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.style.display = 'none');

    const selectedBtn = Array.from(document.querySelectorAll('.tab-btn')).find(b => b.getAttribute('onclick').includes(tabId));
    if (selectedBtn) selectedBtn.classList.add('active');

    document.getElementById(tabId).style.display = 'block';
    loadCurrentTabData();
}

// DATA LOADING
async function loadInitialData() {
    await fetchSports();
    await fetchTeamsListSilently();
    loadCurrentTabData();
}

async function fetchSports() {
    try {
        const res = await fetch('/api/sports');
        sportsData = await res.json();
        populateSportDropdowns();
    } catch (e) {
        console.error('Error fetching sports', e);
    }
}

async function fetchTeamsListSilently() {
    try {
        const res = await fetch('/api/teams');
        teamsData = await res.json();
    } catch (e) {
        console.error('Error fetching teams list', e);
    }
}

function populateSportDropdowns() {
    const filterSport = document.getElementById('filterSport');
    const teamSportId = document.getElementById('teamSportId');
    const matchSportId = document.getElementById('matchSportId');
    const bracketSportId = document.getElementById('bracketSportId');

    let optionsHtml = '<option value="">All Sports</option>';
    let reqOptionsHtml = '';

    sportsData.forEach(s => {
        optionsHtml += `<option value="${s.id}">${s.name} (${s.type.toUpperCase()})</option>`;
        reqOptionsHtml += `<option value="${s.id}">${s.name} (${s.type.toUpperCase()})</option>`;
    });

    if (filterSport) filterSport.innerHTML = optionsHtml;
    if (teamSportId) teamSportId.innerHTML = reqOptionsHtml;
    if (matchSportId) matchSportId.innerHTML = reqOptionsHtml;
    if (bracketSportId) bracketSportId.innerHTML = reqOptionsHtml;
}

function loadCurrentTabData() {
    if (activeTab === 'leaderboardTab') loadLeaderboard();
    else if (activeTab === 'matchesTab') loadMatches();
    else if (activeTab === 'bracketsTab') loadBrackets();
    else if (activeTab === 'adminTab') loadAdminLists();
}

// 1. LEADERBOARD
async function loadLeaderboard() {
    const sportId = document.getElementById('filterSport').value;
    const level = document.getElementById('filterLevel').value;
    const container = document.getElementById('leaderboardContainer');

    container.innerHTML = `<p style="padding: 1.5rem; color: var(--text-secondary);">Loading leaderboard...</p>`;

    try {
        const res = await fetch(`/api/leaderboard?sport_id=${sportId}&level=${level}`);
        const data = await res.json();

        if (!data || data.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <svg viewBox="0 0 24 24"><path d="M12 2v20M2 12h20"/></svg>
                    <h3>No Tournament Results Yet</h3>
                    <p>There are no completed matches for the selected filters.</p>
                </div>
            `;
            return;
        }

        let html = `
            <table>
                <thead>
                    <tr>
                        <th style="width: 60px;">Rank</th>
                        <th>Team</th>
                        <th>Sport</th>
                        <th>Level</th>
                        <th>Played</th>
                        <th>Wins</th>
                        <th>Draws</th>
                        <th>Losses</th>
                        <th>Points</th>
                    </tr>
                </thead>
                <tbody>
        `;

        data.forEach((row, idx) => {
            const rank = idx + 1;
            const rankClass = rank <= 3 ? `rank-${rank}` : '';
            html += `
                <tr>
                    <td><span class="rank-badge ${rankClass}">${rank}</span></td>
                    <td><strong>${row.team_name}</strong></td>
                    <td>${row.sport_name}</td>
                    <td><span class="level-tag">${row.level}</span></td>
                    <td>${row.played}</td>
                    <td>${row.wins}</td>
                    <td>${row.draws}</td>
                    <td>${row.losses}</td>
                    <td><strong>${row.points} pts</strong></td>
                </tr>
            `;
        });

        html += `</tbody></table>`;
        container.innerHTML = html;
    } catch (err) {
        container.innerHTML = `<p style="padding: 1rem; color: var(--danger-color);">Failed to load leaderboard data.</p>`;
    }
}

// 2. MATCHES
async function loadMatches() {
    const sportId = document.getElementById('filterSport').value;
    const level = document.getElementById('filterLevel').value;
    const container = document.getElementById('matchesContainer');

    try {
        const res = await fetch(`/api/matches?sport_id=${sportId}&level=${level}`);
        matchesData = await res.json();

        if (!matchesData || matchesData.length === 0) {
            container.innerHTML = `
                <div class="empty-state" style="grid-column: 1 / -1;">
                    <svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                    <h3>No Scheduled Matches</h3>
                    <p>No tournament matches match the criteria.</p>
                </div>
            `;
            return;
        }

        const teamsMap = {};
        teamsData.forEach(t => teamsMap[t.id] = t.name);

        let html = '';
        matchesData.forEach(m => {
            const isCompleted = m.status === 'completed';
            const winnerBadge = isCompleted ? (m.is_draw ? '<span style="color:var(--warning-color)">Draw</span>' : `<span style="color:var(--success-color)">Completed</span>`) : `<span style="color:var(--text-secondary)">${m.status.toUpperCase()}</span>`;

            const teamAName = teamsMap[m.team_a_id] || 'Team A';
            const teamBName = teamsMap[m.team_b_id] || 'Team B';

            html += `
                <div class="card">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
                        <span class="level-tag">${m.level}</span>
                        ${winnerBadge}
                    </div>
                    <h4>${m.round_info || 'Match'}</h4>
                    <p style="margin: 0.5rem 0; font-size: 1.1rem; font-weight: 600;">
                        ${teamAName} vs ${teamBName}
                    </p>
                    ${m.score_summary ? `<p style="color:var(--accent-color); font-weight:700;">Score: ${m.score_summary}</p>` : ''}

                    ${currentToken ? `
                        <button class="btn btn-primary" style="margin-top: 1rem; width: 100%; justify-content: center;" onclick="openScoringModal('${m.id}')">
                            ${isCompleted ? 'Edit Score' : 'Score Match'}
                        </button>
                    ` : ''}
                </div>
            `;
        });
        container.innerHTML = html;
    } catch (err) {
        container.innerHTML = `<p style="color: var(--danger-color);">Failed to load matches.</p>`;
    }
}

// 3. TIE SHEET / BRACKETS
async function loadBrackets() {
    const sportId = document.getElementById('filterSport').value;
    const level = document.getElementById('filterLevel').value;
    const container = document.getElementById('bracketsContainer');

    try {
        const res = await fetch(`/api/brackets?sport_id=${sportId}&level=${level}`);
        const brackets = await res.json();

        if (!brackets || brackets.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <svg viewBox="0 0 24 24"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                    <h3>No Brackets Generated Yet</h3>
                    <p>Click "Generate Bracket" above as Admin to create tournament matchups.</p>
                </div>
            `;
            return;
        }

        let html = '<div class="card-grid">';
        brackets.forEach(b => {
            const struct = b.structure_json || {};
            const rounds = struct.rounds || [];

            html += `
                <div class="card" style="grid-column: span 2;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
                        <h3>${b.type === 'single_elimination' ? 'Single Elimination Bracket' : 'Round Robin Bracket'}</h3>
                        <span class="level-tag">${b.level}</span>
                    </div>
            `;

            rounds.forEach(r => {
                html += `<div style="margin-top: 0.75rem;"><strong>${r.round_name || 'Round'}</strong></div><div style="display: flex; gap: 0.5rem; flex-wrap: wrap; margin-top: 0.5rem;">`;
                if (r.pairs) {
                    r.pairs.forEach(p => {
                        html += `<div style="background: var(--bg-elevated); padding: 0.5rem 0.75rem; border-radius: 6px; font-size: 0.85rem;">${p.team_a_name} vs ${p.team_b_name}</div>`;
                    });
                } else if (r.matches_count) {
                    html += `<div style="background: var(--bg-elevated); padding: 0.5rem 0.75rem; border-radius: 6px; font-size: 0.85rem;">${r.matches_count} Round Robin Matchups Scheduled</div>`;
                }
                html += `</div>`;
            });

            html += `</div>`;
        });
        html += '</div>';
        container.innerHTML = html;
    } catch (err) {
        container.innerHTML = `<p style="color: var(--danger-color);">Failed to load brackets.</p>`;
    }
}

// 4. ADMIN DASHBOARD & CRUD
async function loadAdminLists() {
    await fetchTeamsList();
    await fetchPlayersList();
}

async function fetchTeamsList() {
    const listContainer = document.getElementById('adminTeamsList');
    try {
        const res = await fetch('/api/teams');
        teamsData = await res.json();

        let html = `<table><thead><tr><th>Team Name</th><th>Level</th></tr></thead><tbody>`;
        teamsData.forEach(t => {
            html += `<tr><td>${t.name}</td><td><span class="level-tag">${t.level}</span></td></tr>`;
        });
        html += `</tbody></table>`;
        listContainer.innerHTML = html;
        populatePlayerTeamSelector();
    } catch (e) {
        listContainer.innerHTML = `<p>Error loading teams.</p>`;
    }
}

async function fetchPlayersList() {
    const listContainer = document.getElementById('adminPlayersList');
    try {
        const res = await fetch('/api/players');
        playersData = await res.json();

        let html = `<table><thead><tr><th>Player Name</th><th>Grade</th><th>Level</th></tr></thead><tbody>`;
        playersData.forEach(p => {
            html += `<tr><td>${p.name}</td><td>${p.grade || '-'}</td><td><span class="level-tag">${p.level}</span></td></tr>`;
        });
        html += `</tbody></table>`;
        listContainer.innerHTML = html;
    } catch (e) {
        listContainer.innerHTML = `<p>Error loading players.</p>`;
    }
}

function populatePlayerTeamSelector() {
    const select = document.getElementById('playerTeamId');
    if (!select) return;
    let html = '';
    teamsData.forEach(t => {
        html += `<option value="${t.id}">${t.name} (${t.level})</option>`;
    });
    select.innerHTML = html;
}

// MODAL ACTIONS (SPORTS, TEAMS, PLAYERS)
function openCreateSportModal() { openModal('createSportModal'); }
async function handleCreateSport(e) {
    e.preventDefault();
    const payload = {
        name: document.getElementById('sportName').value,
        type: document.getElementById('sportType').value,
        point_win: parseInt(document.getElementById('sportWinPoints').value),
        point_draw: parseInt(document.getElementById('sportDrawPoints').value),
        point_loss: parseInt(document.getElementById('sportLossPoints').value),
        is_lower_score_better: document.getElementById('sportLowerBetter').checked
    };

    try {
        const res = await fetch('/api/sports', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            closeModal('createSportModal');
            await fetchSports();
            alert('Sport created successfully!');
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to create sport');
        }
    } catch (e) {
        alert('Network error creating sport');
    }
}

function openCreateTeamModal() { openModal('createTeamModal'); }
async function handleCreateTeam(e) {
    e.preventDefault();
    const payload = {
        name: document.getElementById('teamName').value,
        sport_id: document.getElementById('teamSportId').value,
        level: document.getElementById('teamLevel').value
    };

    try {
        const res = await fetch('/api/teams', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            closeModal('createTeamModal');
            loadAdminLists();
            alert('Team created successfully!');
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to create team');
        }
    } catch (e) {
        alert('Network error creating team');
    }
}

function openCreatePlayerModal() { openModal('createPlayerModal'); }
async function handleCreatePlayer(e) {
    e.preventDefault();
    const payload = {
        name: document.getElementById('playerName').value,
        team_id: document.getElementById('playerTeamId').value,
        grade: document.getElementById('playerGrade').value,
        level: document.getElementById('playerLevel').value
    };

    try {
        const res = await fetch('/api/players', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            closeModal('createPlayerModal');
            loadAdminLists();
            alert('Player created successfully!');
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to create player');
        }
    } catch (e) {
        alert('Network error creating player');
    }
}

// CSV IMPORT TOOL
function openCsvImportModal() {
    document.getElementById('csvPreviewContainer').style.display = 'none';
    document.getElementById('commitCsvBtn').style.display = 'none';
    openModal('csvImportModal');
}

async function handleCsvPreview() {
    const fileInput = document.getElementById('csvFileInput');
    if (!fileInput.files || fileInput.files.length === 0) return;

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);

    try {
        const res = await fetch('/api/players/import/preview', {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${currentToken}` },
            body: formData
        });
        const data = await res.json();

        if (res.ok) {
            parsedCsvValidRows = data.valid_rows;
            renderCsvPreviewTable(data);
        } else {
            alert(data.error || 'Failed to preview CSV');
        }
    } catch (e) {
        alert('Error parsing CSV file');
    }
}

function renderCsvPreviewTable(data) {
    const container = document.getElementById('csvPreviewContainer');
    const header = document.getElementById('csvStatsHeader');
    const tbody = document.querySelector('#csvPreviewTable tbody');
    const commitBtn = document.getElementById('commitCsvBtn');

    header.textContent = `CSV Preview: ${data.valid_count} Valid Rows, ${data.error_count} Rows with Errors`;
    tbody.innerHTML = '';

    data.valid_rows.forEach(r => {
        tbody.innerHTML += `
            <tr>
                <td>${r.row_num}</td>
                <td>${r.player_name}</td>
                <td>${r.team_name}</td>
                <td>${r.sport_name}</td>
                <td>${r.grade}</td>
                <td><span class="level-tag">${r.level}</span></td>
                <td><span style="color:var(--success-color); font-weight:bold;">Valid</span></td>
            </tr>
        `;
    });

    data.errors.forEach(e => {
        tbody.innerHTML += `
            <tr style="background-color: rgba(220, 38, 38, 0.1);">
                <td>${e.row}</td>
                <td>${e.data.player_name || '-'}</td>
                <td>${e.data.team_name || '-'}</td>
                <td>${e.data.sport_name || '-'}</td>
                <td>${e.data.grade || '-'}</td>
                <td>${e.data.level}</td>
                <td><span style="color:var(--danger-color); font-weight:bold;">${e.errors.join(', ')}</span></td>
            </tr>
        `;
    });

    container.style.display = 'block';
    if (data.valid_count > 0) {
        commitBtn.style.display = 'inline-flex';
    } else {
        commitBtn.style.display = 'none';
    }
}

async function handleCsvCommit() {
    if (!parsedCsvValidRows || parsedCsvValidRows.length === 0) return;

    try {
        const res = await fetch('/api/players/import/commit', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify({ rows: parsedCsvValidRows })
        });
        const data = await res.json();

        if (res.ok) {
            closeModal('csvImportModal');
            loadAdminLists();
            await fetchSports();
            alert(`Import Successful! Created ${data.players_created} players, ${data.teams_created} teams, and ${data.sports_created} sports.`);
        } else {
            alert(data.error || 'Failed to commit CSV import');
        }
    } catch (e) {
        alert('Network error committing import');
    }
}

// MATCH SCHEDULING & SCORING
function openCreateMatchModal() {
    populateMatchTeamSelectors();
    openModal('createMatchModal');
}

function populateMatchTeamSelectors() {
    const sportId = document.getElementById('matchSportId').value;
    const level = document.getElementById('matchLevel').value;
    const teamASelect = document.getElementById('matchTeamAId');
    const teamBSelect = document.getElementById('matchTeamBId');

    const filteredTeams = teamsData.filter(t => t.sport_id === sportId && t.level === level);

    let html = '';
    filteredTeams.forEach(t => {
        html += `<option value="${t.id}">${t.name}</option>`;
    });

    teamASelect.innerHTML = html;
    teamBSelect.innerHTML = html;
}

async function handleCreateMatch(e) {
    e.preventDefault();
    const payload = {
        sport_id: document.getElementById('matchSportId').value,
        team_a_id: document.getElementById('matchTeamAId').value,
        team_b_id: document.getElementById('matchTeamBId').value,
        level: document.getElementById('matchLevel').value,
        round_info: document.getElementById('matchRoundInfo').value
    };

    try {
        const res = await fetch('/api/matches', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            closeModal('createMatchModal');
            loadMatches();
            alert('Match scheduled successfully!');
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to schedule match');
        }
    } catch (e) {
        alert('Network error creating match');
    }
}

function openScoringModal(matchId) {
    const match = matchesData.find(m => m.id === matchId);
    if (!match) return;

    const sport = sportsData.find(s => s.id === match.sport_id) || { type: 'generic' };
    const container = document.getElementById('scoringFormContainer');
    document.getElementById('scoringModalTitle').textContent = `Score Match (${sport.type.toUpperCase()})`;

    if (sport.type === 'cricket') {
        container.innerHTML = `
            <form onsubmit="submitCricketScore(event, '${matchId}')">
                <div style="margin-bottom: 1rem;">
                    <h4>Innings 1</h4>
                    <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 0.5rem;">
                        <input type="number" id="cricket_r1" placeholder="Runs" required>
                        <input type="number" id="cricket_w1" placeholder="Wickets" required>
                        <input type="number" step="0.1" id="cricket_o1" placeholder="Overs" value="20.0">
                    </div>
                </div>
                <div style="margin-bottom: 1.5rem;">
                    <h4>Innings 2</h4>
                    <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 0.5rem;">
                        <input type="number" id="cricket_r2" placeholder="Runs" required>
                        <input type="number" id="cricket_w2" placeholder="Wickets" required>
                        <input type="number" step="0.1" id="cricket_o2" placeholder="Overs" value="20.0">
                    </div>
                </div>
                <div style="display: flex; justify-content: flex-end; gap: 0.5rem;">
                    <button type="button" class="btn" onclick="closeModal('scoringModal')">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save Score</button>
                </div>
            </form>
        `;
    } else if (sport.type === 'football') {
        container.innerHTML = `
            <form onsubmit="submitFootballScore(event, '${matchId}')">
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 1.5rem;">
                    <div class="form-group">
                        <label>Team A Goals</label>
                        <input type="number" id="fb_goals_a" value="0" required>
                    </div>
                    <div class="form-group">
                        <label>Team B Goals</label>
                        <input type="number" id="fb_goals_b" value="0" required>
                    </div>
                </div>
                <div style="display: flex; justify-content: flex-end; gap: 0.5rem;">
                    <button type="button" class="btn" onclick="closeModal('scoringModal')">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save Score</button>
                </div>
            </form>
        `;
    } else if (sport.type === 'basketball') {
        container.innerHTML = `
            <form onsubmit="submitBasketballScore(event, '${matchId}')">
                <p style="font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 1rem;">Enter per-quarter scores:</p>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; margin-bottom: 1rem;">
                    <input type="number" id="bk_q1_a" placeholder="Q1 Team A" value="0">
                    <input type="number" id="bk_q1_b" placeholder="Q1 Team B" value="0">
                    <input type="number" id="bk_q2_a" placeholder="Q2 Team A" value="0">
                    <input type="number" id="bk_q2_b" placeholder="Q2 Team B" value="0">
                    <input type="number" id="bk_q3_a" placeholder="Q3 Team A" value="0">
                    <input type="number" id="bk_q3_b" placeholder="Q3 Team B" value="0">
                    <input type="number" id="bk_q4_a" placeholder="Q4 Team A" value="0">
                    <input type="number" id="bk_q4_b" placeholder="Q4 Team B" value="0">
                </div>
                <div style="display: flex; justify-content: flex-end; gap: 0.5rem;">
                    <button type="button" class="btn" onclick="closeModal('scoringModal')">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save Score</button>
                </div>
            </form>
        `;
    } else {
        container.innerHTML = `
            <form onsubmit="submitGenericScore(event, '${matchId}')">
                <div class="form-group" style="margin-bottom: 1rem;">
                    <label>Team A Score / Time</label>
                    <input type="number" step="0.01" id="gen_score_a" placeholder="e.g. 10.52" required>
                </div>
                <div class="form-group" style="margin-bottom: 1.5rem;">
                    <label>Team B Score / Time</label>
                    <input type="number" step="0.01" id="gen_score_b" placeholder="e.g. 11.20" required>
                </div>
                <div style="display: flex; justify-content: flex-end; gap: 0.5rem;">
                    <button type="button" class="btn" onclick="closeModal('scoringModal')">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save Score</button>
                </div>
            </form>
        `;
    }

    openModal('scoringModal');
}

async function submitCricketScore(e, matchId) {
    e.preventDefault();
    const match = matchesData.find(m => m.id === matchId);
    const payload = {
        status: "completed",
        innings1: {
            team_id: match.team_a_id,
            runs: parseInt(document.getElementById('cricket_r1').value),
            wickets: parseInt(document.getElementById('cricket_w1').value),
            overs: parseFloat(document.getElementById('cricket_o1').value)
        },
        innings2: {
            team_id: match.team_b_id,
            runs: parseInt(document.getElementById('cricket_r2').value),
            wickets: parseInt(document.getElementById('cricket_w2').value),
            overs: parseFloat(document.getElementById('cricket_o2').value)
        }
    };

    await sendScoreUpdate(`/api/matches/${matchId}/score/cricket`, payload);
}

async function submitFootballScore(e, matchId) {
    e.preventDefault();
    const payload = {
        team_a_goals: parseInt(document.getElementById('fb_goals_a').value),
        team_b_goals: parseInt(document.getElementById('fb_goals_b').value)
    };
    await sendScoreUpdate(`/api/matches/${matchId}/score/football`, payload);
}

async function submitBasketballScore(e, matchId) {
    e.preventDefault();
    const payload = {
        quarters: [
            { quarter: 1, team_a_score: parseInt(document.getElementById('bk_q1_a').value), team_b_score: parseInt(document.getElementById('bk_q1_b').value) },
            { quarter: 2, team_a_score: parseInt(document.getElementById('bk_q2_a').value), team_b_score: parseInt(document.getElementById('bk_q2_b').value) },
            { quarter: 3, team_a_score: parseInt(document.getElementById('bk_q3_a').value), team_b_score: parseInt(document.getElementById('bk_q3_b').value) },
            { quarter: 4, team_a_score: parseInt(document.getElementById('bk_q4_a').value), team_b_score: parseInt(document.getElementById('bk_q4_b').value) }
        ]
    };
    await sendScoreUpdate(`/api/matches/${matchId}/score/basketball`, payload);
}

async function submitGenericScore(e, matchId) {
    e.preventDefault();
    const match = matchesData.find(m => m.id === matchId);
    const payload = {
        results: [
            { team_id: match.team_a_id, score: parseFloat(document.getElementById('gen_score_a').value) },
            { team_id: match.team_b_id, score: parseFloat(document.getElementById('gen_score_b').value) }
        ]
    };
    await sendScoreUpdate(`/api/matches/${matchId}/score/generic`, payload);
}

async function sendScoreUpdate(url, payload) {
    try {
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            closeModal('scoringModal');
            loadMatches();
            alert('Score saved successfully!');
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to save score');
        }
    } catch (e) {
        alert('Network error saving score');
    }
}

// BRACKETS
function openGenerateBracketModal() { openModal('generateBracketModal'); }
async function handleGenerateBracket(e) {
    e.preventDefault();
    const payload = {
        sport_id: document.getElementById('bracketSportId').value,
        level: document.getElementById('bracketLevel').value,
        type: document.getElementById('bracketType').value
    };

    try {
        const res = await fetch('/api/brackets/generate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${currentToken}` },
            body: JSON.stringify(payload)
        });
        const data = await res.json();

        if (res.ok) {
            closeModal('generateBracketModal');
            alert(`Generated tournament bracket with ${data.created_matches} scheduled matches!`);
            switchTab('bracketsTab');
        } else {
            alert(data.error || 'Failed to generate bracket');
        }
    } catch (e) {
        alert('Error generating bracket');
    }
}
