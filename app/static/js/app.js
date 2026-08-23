// ScoreBoard Web Application State & Logic
let currentToken = localStorage.getItem('sb_auth_token') || null;
let activeTab = 'leaderboardTab';
let sportsData = [];
let teamsData = [];
let playersData = [];
let matchesData = [];
let parsedCsvValidRows = [];
let selectedSportId = '';

// DOM Loaded Initialization
document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    checkAuthUI();
    checkDbHealth();
    loadInitialData();
    setupDragAndDrop();
});

async function checkDbHealth() {
    const badge = document.getElementById('dbModeBadge');
    if (!badge) return;
    try {
        const res = await fetch('/api/health');
        const data = await res.json();
        if (data.supabase_connected || data.mode === 'supabase') {
            badge.textContent = 'Connected: Supabase Postgres DB';
            badge.className = 'status-pill status-final';
            badge.style.color = 'var(--primary-container)';
        } else {
            badge.textContent = 'Development: In-Memory Mock DB';
            badge.className = 'status-pill status-live';
        }
    } catch (e) {
        badge.textContent = 'Offline';
        badge.className = 'status-pill';
        badge.style.backgroundColor = 'var(--error)';
        badge.style.color = '#ffffff';
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
    const themeIcon = document.getElementById('themeIcon');
    if (themeText) {
        themeText.textContent = theme === 'dark' ? 'Dark Mode' : 'Light Mode';
    }
    if (themeIcon) {
        const iconHref = theme === 'dark' ? '#icon-moon' : '#icon-sun';
        themeIcon.innerHTML = `<use href="${iconHref}"></use>`;
    }
}

// AUTH SYSTEM
function checkAuthUI() {
    const authBtnText = document.getElementById('authBtnText');
    const authIcon = document.getElementById('authIcon');
    const adminTabBtn = document.getElementById('adminTabBtn');
    const addMatchBtn = document.getElementById('addMatchBtn');
    const generateBracketBtn = document.getElementById('generateBracketBtn');

    if (currentToken) {
        if (authBtnText) authBtnText.textContent = 'Sign Out';
        if (authIcon) authIcon.innerHTML = `<use href="#icon-logout"></use>`;
        if (adminTabBtn) adminTabBtn.style.display = 'inline-flex';
        if (addMatchBtn) addMatchBtn.style.display = 'inline-flex';
        if (generateBracketBtn) generateBracketBtn.style.display = 'inline-flex';
    } else {
        if (authBtnText) authBtnText.textContent = 'Admin Login';
        if (authIcon) authIcon.innerHTML = `<use href="#icon-login"></use>`;
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
            alert(data.error || 'Login failed - invalid credentials');
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
        renderSportFilterChips();
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

function getSportIconId(sportType) {
    if (!sportType) return 'icon-stopwatch';
    const type = sportType.toLowerCase();
    if (type.includes('cricket')) return 'icon-cricket';
    if (type.includes('football') || type.includes('soccer')) return 'icon-football';
    if (type.includes('basketball')) return 'icon-basketball';
    return 'icon-stopwatch';
}

function renderSportFilterChips() {
    const chipGroup = document.getElementById('sportChipGroup');
    if (!chipGroup) return;

    let html = `<button type="button" class="chip ${selectedSportId === '' ? 'active' : ''}" onclick="selectSportChip('')">ALL SPORTS</button>`;
    sportsData.forEach(s => {
        const iconId = getSportIconId(s.type);
        const isActive = selectedSportId === s.id ? 'active' : '';
        html += `
            <button type="button" class="chip ${isActive}" onclick="selectSportChip('${s.id}')">
                <svg class="icon" style="width:14px; height:14px; vertical-align: middle; margin-right:4px;"><use href="#${iconId}"></use></svg>
                ${s.name.toUpperCase()}
            </button>
        `;
    });
    chipGroup.innerHTML = html;
}

function selectSportChip(sportId) {
    selectedSportId = sportId;
    document.getElementById('filterSport').value = sportId;
    renderSportFilterChips();
    loadCurrentTabData();
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
    const sportId = selectedSportId;
    const level = document.getElementById('filterLevel').value;
    const container = document.getElementById('leaderboardContainer');

    container.innerHTML = `<p style="padding: 24px; color: var(--text-secondary);">Loading leaderboard...</p>`;

    try {
        const res = await fetch(`/api/leaderboard?sport_id=${sportId}&level=${level}`);
        const data = await res.json();

        if (!data || data.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <svg class="icon"><use href="#icon-empty"></use></svg>
                    <h3>No Tournament Results Yet</h3>
                    <p style="font-size: 14px;">There are no completed matches for the selected filters.</p>
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
            const rankClass = rank === 1 ? 'rank-1' : '';
            const iconId = getSportIconId(row.sport_type);

            html += `
                <tr>
                    <td><span class="rank-badge ${rankClass}">${rank}</span></td>
                    <td><strong>${row.team_name}</strong></td>
                    <td>
                        <span style="display: inline-flex; align-items: center; gap: 6px;">
                            <svg class="icon" style="width: 16px; height: 16px;"><use href="#${iconId}"></use></svg>
                            ${row.sport_name}
                        </span>
                    </td>
                    <td><span class="chip" style="font-size: 11px; padding: 2px 6px;">${row.level}</span></td>
                    <td>${row.played}</td>
                    <td>${row.wins}</td>
                    <td>${row.draws}</td>
                    <td>${row.losses}</td>
                    <td><strong>${row.points} PTS</strong></td>
                </tr>
            `;
        });

        html += `</tbody></table>`;
        container.innerHTML = html;
    } catch (err) {
        container.innerHTML = `<p style="padding: 16px; color: var(--error);">Failed to load leaderboard data.</p>`;
    }
}

// 2. MATCHES
async function loadMatches() {
    const sportId = selectedSportId;
    const level = document.getElementById('filterLevel').value;
    const container = document.getElementById('matchesContainer');

    try {
        const res = await fetch(`/api/matches?sport_id=${sportId}&level=${level}`);
        matchesData = await res.json();

        if (!matchesData || matchesData.length === 0) {
            container.innerHTML = `
                <div class="empty-state" style="grid-column: 1 / -1;">
                    <svg class="icon"><use href="#icon-calendar"></use></svg>
                    <h3>No Scheduled Matches</h3>
                    <p style="font-size: 14px;">No tournament matches match the selected criteria.</p>
                </div>
            `;
            return;
        }

        const teamsMap = {};
        teamsData.forEach(t => teamsMap[t.id] = t.name);

        let html = '';
        matchesData.forEach(m => {
            const isCompleted = m.status === 'completed';
            const statusPill = isCompleted
                ? `<span class="status-pill status-final">FINAL</span>`
                : (m.status === 'live' ? `<span class="status-pill status-live">LIVE</span>` : `<span class="status-pill status-final">SCHEDULED</span>`);

            const teamAName = teamsMap[m.team_a_id] || 'Team A';
            const teamBName = teamsMap[m.team_b_id] || 'Team B';

            html += `
                <div class="card">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                        <span class="chip" style="font-size: 11px; padding: 2px 6px;">${m.level}</span>
                        ${statusPill}
                    </div>
                    <h4 style="margin-bottom: 8px;">${m.round_info || 'Regular Match'}</h4>
                    <p class="body-text" style="font-weight: 600; margin-bottom: 12px;">
                        ${teamAName} vs ${teamBName}
                    </p>
                    ${m.score_summary ? `
                        <div style="background-color: var(--surface-container-low); padding: 8px 12px; border-radius: 4px; border: 1px solid var(--border-color); margin-bottom: 12px;">
                            <span class="mono-label" style="font-size: 12px; color: var(--text-secondary);">SCORE SUMMARY</span>
                            <div class="headline-md tabular-nums" style="color: var(--primary-container); margin-top: 2px;">${m.score_summary}</div>
                        </div>
                    ` : ''}

                    ${currentToken ? `
                        <button class="btn btn-primary" style="width: 100%; justify-content: center;" onclick="openScoringModal('${m.id}')">
                            ${isCompleted ? 'Edit Score' : 'Score Match'}
                        </button>
                    ` : ''}
                </div>
            `;
        });
        container.innerHTML = html;
    } catch (err) {
        container.innerHTML = `<p style="color: var(--error);">Failed to load matches.</p>`;
    }
}

// 3. TIE SHEET / BRACKETS
async function loadBrackets() {
    const sportId = selectedSportId;
    const level = document.getElementById('filterLevel').value;
    const container = document.getElementById('bracketsContainer');

    try {
        const res = await fetch(`/api/brackets?sport_id=${sportId}&level=${level}`);
        const brackets = await res.json();

        if (!brackets || brackets.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <svg class="icon"><use href="#icon-tree"></use></svg>
                    <h3>No Brackets Generated Yet</h3>
                    <p style="font-size: 14px;">Click "Generate Bracket" above as Admin to create tournament matchups.</p>
                </div>
            `;
            return;
        }

        let html = '<div style="display: flex; flex-direction: column; gap: 24px;">';
        brackets.forEach(b => {
            const struct = b.structure_json || {};
            const rounds = struct.rounds || [];

            html += `
                <div class="card">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
                        <h3>${b.type === 'single_elimination' ? 'Single Elimination Bracket' : 'Round Robin Tournament'}</h3>
                        <span class="chip" style="font-size: 11px; padding: 2px 6px;">${b.level}</span>
                    </div>
                    <div class="bracket-container">
            `;

            rounds.forEach(r => {
                html += `<div class="bracket-round"><span class="mono-label" style="margin-bottom: 8px;">${r.round_name || 'Round'}</span>`;
                if (r.pairs) {
                    r.pairs.forEach(p => {
                        html += `
                            <div class="bracket-match">
                                <div style="display: flex; justify-content: space-between; gap: 12px; margin-bottom: 4px;">
                                    <span>${p.team_a_name}</span>
                                </div>
                                <div style="display: flex; justify-content: space-between; gap: 12px; border-top: 1px solid var(--border-color); padding-top: 4px;">
                                    <span>${p.team_b_name}</span>
                                </div>
                            </div>
                        `;
                    });
                } else if (r.matches_count) {
                    html += `
                        <div class="bracket-match">
                            <span class="body-text">${r.matches_count} Round Robin Matchups</span>
                        </div>
                    `;
                }
                html += `</div>`;
            });

            html += `</div></div>`;
        });
        html += '</div>';
        container.innerHTML = html;
    } catch (err) {
        container.innerHTML = `<p style="color: var(--error);">Failed to load brackets.</p>`;
    }
}

// 4. ADMIN DASHBOARD & LISTS
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
            html += `<tr><td><strong>${t.name}</strong></td><td><span class="chip" style="font-size: 11px; padding: 2px 6px;">${t.level}</span></td></tr>`;
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
            html += `<tr><td><strong>${p.name}</strong></td><td>${p.grade || '-'}</td><td><span class="chip" style="font-size: 11px; padding: 2px 6px;">${p.level}</span></td></tr>`;
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
            alert('Sport registered successfully!');
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

// CSV IMPORT TOOL & DRAG-AND-DROP
function setupDragAndDrop() {
    const dropZone = document.getElementById('dropZone');
    if (!dropZone) return;

    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
        }, false);
    });

    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, () => {
            dropZone.style.borderColor = 'var(--primary-container)';
            dropZone.style.backgroundColor = 'var(--surface-container)';
        }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, () => {
            dropZone.style.borderColor = 'var(--border-color)';
            dropZone.style.backgroundColor = 'var(--surface-container-low)';
        }, false);
    });

    dropZone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files && files.length > 0) {
            const fileInput = document.getElementById('csvFileInput');
            fileInput.files = files;
            handleCsvPreview();
        }
    });
}

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

    header.textContent = `CSV Preview: ${data.valid_count} Valid Rows, ${data.error_count} Errors`;
    tbody.innerHTML = '';

    data.valid_rows.forEach(r => {
        tbody.innerHTML += `
            <tr>
                <td>${r.row_num}</td>
                <td><strong>${r.player_name}</strong></td>
                <td>${r.team_name}</td>
                <td>${r.sport_name}</td>
                <td>${r.grade}</td>
                <td><span class="chip" style="font-size: 11px; padding: 2px 6px;">${r.level}</span></td>
                <td><span style="color:var(--primary-container); font-weight:bold;">VALID</span></td>
            </tr>
        `;
    });

    data.errors.forEach(e => {
        tbody.innerHTML += `
            <tr style="background-color: var(--error-container);">
                <td>${e.row}</td>
                <td>${e.data.player_name || '-'}</td>
                <td>${e.data.team_name || '-'}</td>
                <td>${e.data.sport_name || '-'}</td>
                <td>${e.data.grade || '-'}</td>
                <td>${e.data.level}</td>
                <td><span style="color:var(--error); font-weight:bold;">${e.errors.join(', ')}</span></td>
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

// SPORT-SPECIFIC SCORING MODALS & LIVE PREVIEWS
function openScoringModal(matchId) {
    const match = matchesData.find(m => m.id === matchId);
    if (!match) return;

    const sport = sportsData.find(s => s.id === match.sport_id) || { type: 'generic' };
    const container = document.getElementById('scoringFormContainer');
    document.getElementById('scoringModalTitle').textContent = `Score Match (${sport.type.toUpperCase()})`;

    const teamAName = (teamsData.find(t => t.id === match.team_a_id) || {}).name || 'Team A';
    const teamBName = (teamsData.find(t => t.id === match.team_b_id) || {}).name || 'Team B';

    if (sport.type === 'cricket') {
        container.innerHTML = `
            <form onsubmit="submitCricketScore(event, '${matchId}')">
                <div style="background-color: var(--surface-container-low); padding: 16px; border-radius: 4px; border: 1px solid var(--border-color); text-align: center; margin-bottom: 16px;">
                    <span class="mono-label">LIVE SCOREBOARD PREVIEW</span>
                    <div id="cricketLivePreview" class="display-score" style="color: var(--primary-container); margin-top: 8px;">0/0 vs 0/0</div>
                </div>

                <div style="margin-bottom: 16px;">
                    <h4 class="mono-label">${teamAName} (Innings 1)</h4>
                    <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; margin-top: 8px;">
                        <div class="form-group"><label>Runs</label><input type="number" id="cricket_r1" value="0" oninput="updateCricketLivePreview()" required></div>
                        <div class="form-group"><label>Wickets</label><input type="number" id="cricket_w1" value="0" oninput="updateCricketLivePreview()" required></div>
                        <div class="form-group"><label>Overs</label><input type="number" step="0.1" id="cricket_o1" value="20.0" required></div>
                    </div>
                </div>

                <div style="margin-bottom: 24px;">
                    <h4 class="mono-label">${teamBName} (Innings 2)</h4>
                    <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; margin-top: 8px;">
                        <div class="form-group"><label>Runs</label><input type="number" id="cricket_r2" value="0" oninput="updateCricketLivePreview()" required></div>
                        <div class="form-group"><label>Wickets</label><input type="number" id="cricket_w2" value="0" oninput="updateCricketLivePreview()" required></div>
                        <div class="form-group"><label>Overs</label><input type="number" step="0.1" id="cricket_o2" value="20.0" required></div>
                    </div>
                </div>

                <div style="display: flex; justify-content: flex-end; gap: 8px;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('scoringModal')">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save Final Score</button>
                </div>
            </form>
        `;
        updateCricketLivePreview();
    } else if (sport.type === 'football') {
        container.innerHTML = `
            <form onsubmit="submitFootballScore(event, '${matchId}')">
                <div style="background-color: var(--surface-container-low); padding: 16px; border-radius: 4px; border: 1px solid var(--border-color); text-align: center; margin-bottom: 16px;">
                    <span class="mono-label">RUNNING SCOREBOARD</span>
                    <div id="fbLivePreview" class="display-score" style="color: var(--primary-container); margin-top: 8px;">0 - 0</div>
                </div>

                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px;">
                    <div class="form-group">
                        <label>${teamAName} Goals</label>
                        <input type="number" id="fb_goals_a" value="0" oninput="updateFootballLivePreview()" required>
                    </div>
                    <div class="form-group">
                        <label>${teamBName} Goals</label>
                        <input type="number" id="fb_goals_b" value="0" oninput="updateFootballLivePreview()" required>
                    </div>
                </div>

                <div style="margin-bottom: 16px;">
                    <span class="mono-label">CARD EVENTS (Square Markers)</span>
                    <div style="display: flex; gap: 12px; margin-top: 8px;">
                        <span style="display: inline-flex; align-items: center; gap: 6px; font-size: 14px;">
                            <span class="card-event-marker card-event-yellow"></span> Yellow Card
                        </span>
                        <span style="display: inline-flex; align-items: center; gap: 6px; font-size: 14px;">
                            <span class="card-event-marker card-event-red"></span> Red Card
                        </span>
                    </div>
                </div>

                <div style="display: flex; justify-content: flex-end; gap: 8px;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('scoringModal')">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save Score</button>
                </div>
            </form>
        `;
        updateFootballLivePreview();
    } else if (sport.type === 'basketball') {
        container.innerHTML = `
            <form onsubmit="submitBasketballScore(event, '${matchId}')">
                <div style="background-color: var(--surface-container-low); padding: 16px; border-radius: 4px; border: 1px solid var(--border-color); text-align: center; margin-bottom: 16px;">
                    <span class="mono-label">RUNNING TOTAL SCORE</span>
                    <div id="bkLivePreview" class="display-score" style="color: var(--primary-container); margin-top: 8px;">0 - 0</div>
                </div>

                <div style="margin-bottom: 16px;">
                    <span class="mono-label">QUARTER SCORES (${teamAName} vs ${teamBName})</span>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 8px;">
                        <input type="number" id="bk_q1_a" placeholder="Q1 ${teamAName}" value="0" oninput="updateBasketballLivePreview()">
                        <input type="number" id="bk_q1_b" placeholder="Q1 ${teamBName}" value="0" oninput="updateBasketballLivePreview()">
                        <input type="number" id="bk_q2_a" placeholder="Q2 ${teamAName}" value="0" oninput="updateBasketballLivePreview()">
                        <input type="number" id="bk_q2_b" placeholder="Q2 ${teamBName}" value="0" oninput="updateBasketballLivePreview()">
                        <input type="number" id="bk_q3_a" placeholder="Q3 ${teamAName}" value="0" oninput="updateBasketballLivePreview()">
                        <input type="number" id="bk_q3_b" placeholder="Q3 ${teamBName}" value="0" oninput="updateBasketballLivePreview()">
                        <input type="number" id="bk_q4_a" placeholder="Q4 ${teamAName}" value="0" oninput="updateBasketballLivePreview()">
                        <input type="number" id="bk_q4_b" placeholder="Q4 ${teamBName}" value="0" oninput="updateBasketballLivePreview()">
                    </div>
                </div>

                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px;">
                    <div class="form-group"><label class="mono-label">FOULS (${teamAName})</label><input type="number" value="0"></div>
                    <div class="form-group"><label class="mono-label">FOULS (${teamBName})</label><input type="number" value="0"></div>
                </div>

                <div style="display: flex; justify-content: flex-end; gap: 8px;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('scoringModal')">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save Score</button>
                </div>
            </form>
        `;
        updateBasketballLivePreview();
    } else {
        container.innerHTML = `
            <form onsubmit="submitGenericScore(event, '${matchId}')">
                <div class="form-group" style="margin-bottom: 16px;">
                    <label>${teamAName} Score / Time</label>
                    <input type="number" step="0.01" id="gen_score_a" placeholder="e.g. 10.52" required>
                </div>
                <div class="form-group" style="margin-bottom: 16px;">
                    <label>${teamBName} Score / Time</label>
                    <input type="number" step="0.01" id="gen_score_b" placeholder="e.g. 11.20" required>
                </div>
                <div class="form-group" style="margin-bottom: 24px;">
                    <label>Notes / Official Remarks</label>
                    <input type="text" id="gen_notes" placeholder="Optional notes">
                </div>
                <div style="display: flex; justify-content: flex-end; gap: 8px;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('scoringModal')">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save Score</button>
                </div>
            </form>
        `;
    }

    openModal('scoringModal');
}

function updateCricketLivePreview() {
    const r1 = document.getElementById('cricket_r1')?.value || 0;
    const w1 = document.getElementById('cricket_w1')?.value || 0;
    const r2 = document.getElementById('cricket_r2')?.value || 0;
    const w2 = document.getElementById('cricket_w2')?.value || 0;
    const target = document.getElementById('cricketLivePreview');
    if (target) target.textContent = `${r1}/${w1} vs ${r2}/${w2}`;
}

function updateFootballLivePreview() {
    const gA = document.getElementById('fb_goals_a')?.value || 0;
    const gB = document.getElementById('fb_goals_b')?.value || 0;
    const target = document.getElementById('fbLivePreview');
    if (target) target.textContent = `${gA} - ${gB}`;
}

function updateBasketballLivePreview() {
    const q1a = parseInt(document.getElementById('bk_q1_a')?.value || 0);
    const q1b = parseInt(document.getElementById('bk_q1_b')?.value || 0);
    const q2a = parseInt(document.getElementById('bk_q2_a')?.value || 0);
    const q2b = parseInt(document.getElementById('bk_q2_b')?.value || 0);
    const q3a = parseInt(document.getElementById('bk_q3_a')?.value || 0);
    const q3b = parseInt(document.getElementById('bk_q3_b')?.value || 0);
    const q4a = parseInt(document.getElementById('bk_q4_a')?.value || 0);
    const q4b = parseInt(document.getElementById('bk_q4_b')?.value || 0);

    const totA = q1a + q2a + q3a + q4a;
    const totB = q1b + q2b + q3b + q4b;
    const target = document.getElementById('bkLivePreview');
    if (target) target.textContent = `${totA} - ${totB}`;
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
            { team_id: match.team_a_id, score: parseFloat(document.getElementById('gen_score_a').value), notes: document.getElementById('gen_notes').value },
            { team_id: match.team_b_id, score: parseFloat(document.getElementById('gen_score_b').value), notes: document.getElementById('gen_notes').value }
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
