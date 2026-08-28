// DSS Sports Inter-House Meet Web Application Logic
let currentToken = localStorage.getItem('sb_auth_token') || null;
let activeTab = 'houseLeaderboardTab';
let sportsData = [];
let housesData = [];
let squadsData = [];
let playersData = [];
let matchesData = [];
let seederLogsData = [];
let selectedSportId = '';
let pendingCsvPlayers = [];

// House Colors Map
const HOUSE_COLORS = {
    'karnali': '#10B981',
    'koshi': '#0EA5E9',
    'mahakali': '#8B5CF6',
    'mechi': '#F97316'
};

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    checkAuthUI();
    checkDbHealth();
});

async function checkDbHealth() {
    const badge = document.getElementById('dbModeBadge');
    if (!badge) return;
    try {
        const res = await fetch('/api/health');
        const data = await res.json();
        badge.textContent = 'Connected: Postgres DB [DEBUG]';
        badge.style.color = 'var(--house-karnali, #10B981)';
    } catch (e) {
        badge.textContent = 'Offline';
        badge.style.color = '#ef4444';
    }
}

function initTheme() {
    const savedTheme = localStorage.getItem('sb_theme') || 'dark';
    document.documentElement.setAttribute('data-theme', savedTheme);
    if (savedTheme === 'dark') {
        document.documentElement.classList.add('dark');
    } else {
        document.documentElement.classList.remove('dark');
    }
    updateThemeUI(savedTheme);
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    if (next === 'dark') {
        document.documentElement.classList.add('dark');
    } else {
        document.documentElement.classList.remove('dark');
    }
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
            window.location.href = '/admin';
        } else {
            alert(data.error || 'Login failed');
        }
    } catch (err) {
        alert('Network error during login');
    }
}

function openModal(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.remove('hidden');
    if (window.gsap) {
        gsap.fromTo(el.querySelector('.modal-content'),
            { scale: 0.9, opacity: 0, y: 20 },
            { scale: 1, opacity: 1, y: 0, duration: 0.25, ease: 'back.out(1.5)' }
        );
    }
}

function closeModal(id) {
    const el = document.getElementById(id);
    if (!el) return;
    if (window.gsap) {
        gsap.to(el.querySelector('.modal-content'), {
            scale: 0.9, opacity: 0, y: 20, duration: 0.15, onComplete: () => {
                el.classList.add('hidden');
            }
        });
    } else {
        el.classList.add('hidden');
    }
}

function switchTab(tabId) {
    activeTab = tabId;
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active', 'border-emerald-400', 'text-emerald-400');
        btn.classList.add('border-transparent', 'text-slate-400');
    });
    document.querySelectorAll('.tab-content').forEach(content => content.style.display = 'none');

    const selectedBtn = Array.from(document.querySelectorAll('.tab-btn')).find(b => {
        const onclickAttr = b.getAttribute('onclick');
        return onclickAttr && onclickAttr.includes(tabId);
    });
    if (selectedBtn) {
        selectedBtn.classList.add('active', 'border-emerald-400', 'text-emerald-400');
        selectedBtn.classList.remove('border-transparent', 'text-slate-400');
    }

    const contentEl = document.getElementById(tabId);
    if (contentEl) {
        contentEl.style.display = 'block';
        if (window.gsap) {
            gsap.fromTo(contentEl, { opacity: 0, y: 15 }, { opacity: 1, y: 0, duration: 0.3, ease: 'power2.out' });
        }
    }
}

async function loadInitialData() {
    await fetchHouses();
    await fetchSports();
    await fetchSquads();
    await fetchPlayers();
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

async function fetchSquads() {
    try {
        const res = await fetch('/api/teams');
        squadsData = await res.json();
    } catch (e) {
        console.error('Error fetching squads', e);
    }
}

async function fetchPlayers() {
    try {
        const res = await fetch('/api/players');
        playersData = await res.json();
    } catch (e) {
        console.error('Error fetching players', e);
    }
}

async function fetchMatches() {
    try {
        const res = await fetch('/api/matches');
        matchesData = await res.json();
    } catch (e) {
        console.error('Error fetching matches', e);
    }
}

function renderSportFilterChips() {
    const chipGroup = document.getElementById('sportChipGroup');
    if (!chipGroup) return;

    let html = `<button type="button" class="chip px-3 py-1.5 rounded-lg text-xs font-bold border transition ${selectedSportId === '' ? 'bg-emerald-600 border-emerald-500 text-white shadow-md' : 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'}" onclick="selectSportChip('', '')">ALL SPORTS</button>`;
    sportsData.forEach(s => {
        const isActive = selectedSportId === s.id;
        const slug = s.name.toLowerCase();
        html += `<button type="button" class="chip px-3 py-1.5 rounded-lg text-xs font-bold border transition ${isActive ? 'bg-emerald-600 border-emerald-500 text-white shadow-md' : 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'}" onclick="selectSportChip('${s.id}', '${slug}')">${s.name.toUpperCase()}</button>`;
    });
    chipGroup.innerHTML = html;
}

function selectSportChip(sportId, sportSlug = '') {
    selectedSportId = sportId;
    renderSportFilterChips();
    if (sportSlug) {
        window.history.pushState({}, '', `/standings/${sportSlug}`);
    }
    loadPerSportStandings();
}

function loadHouseOverallStandingsPage() {
    loadHouseOverallStandings();
}

function loadPerSportStandingsPage() {
    loadPerSportStandings();
}

function loadFixturesPage() {
    loadFixtures();
}

function loadAdminCenterPage() {
    loadAdminCenterData();
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
            const hColor = h.color_hex || HOUSE_COLORS[h.house_name.toLowerCase()] || '#10B981';
            heroHtml += `
                <div class="house-hero-card relative p-6 bg-slate-900/90 rounded-2xl border border-slate-800 shadow-xl overflow-hidden group hover:border-slate-700 transition" style="border-left: 6px solid ${hColor};">
                    <div class="absolute -right-6 -bottom-6 w-32 h-32 rounded-full blur-2xl opacity-20 pointer-events-none" style="background-color: ${hColor};"></div>
                    <div class="flex justify-between items-start mb-4">
                        <span class="inline-block px-2.5 py-1 text-xs font-mono font-bold rounded-full text-white" style="background-color: ${hColor};">${h.short_code || h.house_name.substring(0,3).toUpperCase()} HOUSE</span>
                        <span class="text-3xl font-black" style="color: ${hColor};">#${h.rank}</span>
                    </div>
                    <h2 class="text-2xl font-black text-white mb-6 tracking-tight">${h.house_name}</h2>
                    <div class="flex justify-between items-end border-t border-slate-800/80 pt-4">
                        <div>
                            <span class="block text-[10px] font-mono font-bold text-slate-400 uppercase tracking-widest">TOTAL POINTS</span>
                            <div class="text-4xl font-extrabold" style="color: ${hColor};">${h.total_points}</div>
                        </div>
                        <div class="text-right">
                            <span class="block text-[10px] font-mono font-bold text-slate-400 uppercase tracking-widest">W - D - L</span>
                            <div class="text-sm font-bold text-white">${h.total_wins} - ${h.total_draws} - ${h.total_losses}</div>
                        </div>
                    </div>
                </div>
            `;
        });
        heroContainer.innerHTML = heroHtml;

        if (window.gsap) {
            gsap.from('.house-hero-card', { opacity: 0, y: 20, duration: 0.4, stagger: 0.08, ease: 'power2.out' });
        }

        let tableHtml = `
            <table class="w-full text-left text-sm text-slate-200">
                <thead class="bg-slate-950 text-slate-400 text-xs font-bold uppercase tracking-wider border-b border-slate-800">
                    <tr>
                        <th class="p-4 w-16">Rank</th>
                        <th class="p-4">House Name</th>
                        <th class="p-4">Squads</th>
                        <th class="p-4">Played</th>
                        <th class="p-4">Wins</th>
                        <th class="p-4">Draws</th>
                        <th class="p-4">Losses</th>
                        <th class="p-4">Goal Diff</th>
                        <th class="p-4 text-right">Total Points</th>
                    </tr>
                </thead>
                <tbody class="divide-y divide-slate-800/60">
        `;

        standings.forEach(h => {
            const hColor = h.color_hex || HOUSE_COLORS[h.house_name.toLowerCase()] || '#10B981';
            tableHtml += `
                <tr class="hover:bg-slate-800/40 transition">
                    <td class="p-4 font-black text-base" style="color: ${hColor};">#${h.rank}</td>
                    <td class="p-4 font-bold text-white flex items-center gap-2.5">
                        <span class="w-3 h-3 rounded-sm inline-block" style="background-color: ${hColor};"></span>
                        ${h.house_name} House
                    </td>
                    <td class="p-4">${h.total_squads}</td>
                    <td class="p-4">${h.matches_played}</td>
                    <td class="p-4 text-emerald-400 font-semibold">${h.total_wins}</td>
                    <td class="p-4 text-slate-400">${h.total_draws}</td>
                    <td class="p-4 text-rose-400">${h.total_losses}</td>
                    <td class="p-4 font-mono">${h.total_score_difference > 0 ? '+' + h.total_score_difference : h.total_score_difference}</td>
                    <td class="p-4 text-right font-black text-lg" style="color: ${hColor};">${h.total_points} PTS</td>
                </tr>
            `;
        });
        tableHtml += `</tbody></table>`;
        tableContainer.innerHTML = tableHtml;

    } catch (e) {
        heroContainer.innerHTML = `<p class="text-rose-400">Error loading house standings.</p>`;
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
                <div class="p-12 text-center space-y-4">
                    <svg class="w-12 h-12 text-slate-600 mx-auto"><use href="#icon-empty"></use></svg>
                    <h3 class="text-base font-semibold text-slate-400">No Per-Sport Standings Available</h3>
                </div>
            `;
            return;
        }

        let html = `
            <table class="w-full text-left text-sm text-slate-200">
                <thead class="bg-slate-950 text-slate-400 text-xs font-bold uppercase tracking-wider border-b border-slate-800">
                    <tr>
                        <th class="p-4 w-16">Rank</th>
                        <th class="p-4">Squad Name</th>
                        <th class="p-4">House</th>
                        <th class="p-4">Sport</th>
                        <th class="p-4">Gender</th>
                        <th class="p-4">P</th>
                        <th class="p-4">W</th>
                        <th class="p-4">D</th>
                        <th class="p-4">L</th>
                        <th class="p-4">Diff</th>
                        <th class="p-4 text-right">Points</th>
                    </tr>
                </thead>
                <tbody class="divide-y divide-slate-800/60">
        `;

        standings.forEach(s => {
            const hColor = s.house_color || HOUSE_COLORS[(s.house_name || '').toLowerCase()] || '#10B981';
            html += `
                <tr class="hover:bg-slate-800/40 transition" style="border-left: 4px solid ${hColor};">
                    <td class="p-4 font-bold">#${s.rank}</td>
                    <td class="p-4 font-extrabold text-white">${s.team_name}</td>
                    <td class="p-4">
                        <span class="inline-flex items-center gap-1.5 font-bold" style="color: ${hColor};">
                            <span class="w-2.5 h-2.5 rounded-sm" style="background-color: ${hColor};"></span>
                            ${s.house_name || 'House'}
                        </span>
                    </td>
                    <td class="p-4 text-slate-300">${s.sport_name}</td>
                    <td class="p-4 text-slate-400">${s.gender}</td>
                    <td class="p-4 font-semibold">${s.played}</td>
                    <td class="p-4 text-emerald-400 font-semibold">${s.wins}</td>
                    <td class="p-4 text-slate-400">${s.draws}</td>
                    <td class="p-4 text-rose-400">${s.losses}</td>
                    <td class="p-4 font-mono">${s.score_difference > 0 ? '+' + s.score_difference : s.score_difference}</td>
                    <td class="p-4 text-right font-black text-base" style="color: ${hColor};">${s.points} PTS</td>
                </tr>
            `;
        });
        html += `</tbody></table>`;
        container.innerHTML = html;

    } catch (e) {
        container.innerHTML = `<p class="p-6 text-rose-400">Failed to load per-sport standings.</p>`;
    }
}

// 3. FIXTURES
async function loadFixtures() {
    const container = document.getElementById('fixturesContainer');
    try {
        const res = await fetch(`/api/matches?sport_id=${selectedSportId}`);
        matchesData = await res.json();

        if (!matchesData || matchesData.length === 0) {
            container.innerHTML = `
                <div class="col-span-full p-12 text-center bg-slate-900/60 rounded-xl border border-slate-800 space-y-4">
                    <svg class="w-12 h-12 text-slate-600 mx-auto"><use href="#icon-calendar"></use></svg>
                    <h3 class="text-base font-semibold text-slate-400">No Scheduled Fixtures</h3>
                </div>
            `;
            return;
        }

        let html = '';
        matchesData.forEach(m => {
            const isCompleted = m.status === 'completed';
            const teamAName = getTeamName(m.team_a_id);
            const teamBName = getTeamName(m.team_b_id);

            html += `
                <div class="card relative p-5 bg-slate-900/90 rounded-xl border border-slate-800 shadow-lg flex flex-col justify-between hover:border-slate-700 transition">
                    <div>
                        <div class="flex justify-between items-center mb-3">
                            <span class="text-[10px] font-mono font-bold uppercase tracking-wider text-slate-400">${m.gender || 'Boys'} • ${(m.stage || 'league').toUpperCase()}</span>
                            <div class="flex items-center gap-2">
                                <span class="px-2 py-0.5 text-[10px] font-bold rounded-full ${isCompleted ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' : 'bg-slate-800 text-slate-400 border border-slate-700'}">${m.status.toUpperCase()}</span>
                                ${currentToken ? `
                                    <button onclick="openMatchModal('${m.id}')" title="Edit Match Result" class="p-1 rounded bg-purple-950 hover:bg-purple-900 text-purple-300 border border-purple-800 transition">
                                        <svg class="w-3.5 h-3.5"><use href="#icon-edit"></use></svg>
                                    </button>
                                ` : ''}
                            </div>
                        </div>
                        <h4 class="font-bold text-white text-base mb-4">${m.round_info || 'League Game'}</h4>
                        <div class="text-sm font-semibold text-slate-300 mb-4 flex justify-between items-center bg-slate-950 p-3 rounded-lg border border-slate-800">
                            <span>${teamAName}</span>
                            <span class="text-xs text-slate-500 uppercase px-1.5 font-mono">VS</span>
                            <span>${teamBName}</span>
                        </div>
                    </div>
                    ${m.score_summary ? `
                        <div class="bg-emerald-950/40 p-3 rounded-lg border border-emerald-900/50 text-center">
                            <span class="block text-[10px] font-mono text-emerald-400 uppercase tracking-widest mb-1">FINAL SCORE</span>
                            <div class="text-2xl font-black text-white font-mono">${m.score_summary}</div>
                        </div>
                    ` : '<p class="text-xs text-slate-500 text-center py-2 italic">Match Unplayed</p>'}
                </div>
            `;
        });
        container.innerHTML = html;

        if (window.gsap) {
            gsap.from('#fixturesContainer .card', { opacity: 0, y: 15, duration: 0.3, stagger: 0.05, ease: 'power2.out' });
        }

    } catch (e) {
        container.innerHTML = `<p class="text-rose-400">Error loading fixtures.</p>`;
    }
}

// 4. ADMIN CENTER MANAGEMENT
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
    const badge = document.getElementById('squadCountBadge');
    if (badge) badge.textContent = `${squadsData.length} Squads`;

    if (!squadsData || squadsData.length === 0) {
        container.innerHTML = `
            <div class="p-8 text-center space-y-3">
                <svg class="w-10 h-10 text-slate-600 mx-auto"><use href="#icon-empty"></use></svg>
                <p class="text-sm text-slate-400 font-medium">No squads yet</p>
                <button onclick="openSquadModal()" class="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold rounded-lg shadow transition">
                    Add Squad
                </button>
            </div>
        `;
        return;
    }

    let html = `
        <table class="w-full text-left text-xs text-slate-200">
            <thead class="bg-slate-950 text-slate-400 font-semibold uppercase border-b border-slate-800">
                <tr>
                    <th class="p-2.5">Squad Name</th>
                    <th class="p-2.5">House</th>
                    <th class="p-2.5">Sport</th>
                    <th class="p-2.5">Gender</th>
                    <th class="p-2.5 text-right">Actions</th>
                </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/60">
    `;

    squadsData.forEach(s => {
        const hName = (s.houses || {}).name || getHouseName(s.house_id);
        const sName = (s.sports || {}).name || getSportName(s.sport_id);
        const hColor = HOUSE_COLORS[(hName || '').toLowerCase()] || '#10B981';

        html += `
            <tr class="hover:bg-slate-800/40 transition" style="border-left: 3px solid ${hColor};">
                <td class="p-2.5 font-bold text-white">${s.name}</td>
                <td class="p-2.5 font-semibold" style="color: ${hColor};">${hName}</td>
                <td class="p-2.5 text-slate-300">${sName}</td>
                <td class="p-2.5 text-slate-400">${s.gender} (${s.squad_label})</td>
                <td class="p-2.5 text-right space-x-1">
                    <button onclick="openSquadModal('${s.id}')" class="p-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition" title="Edit Squad">
                        <svg class="w-3.5 h-3.5"><use href="#icon-edit"></use></svg>
                    </button>
                    <button onclick="deleteSquad('${s.id}')" class="p-1 rounded bg-rose-950 hover:bg-rose-900 text-rose-300 transition" title="Delete Squad">
                        <svg class="w-3.5 h-3.5"><use href="#icon-trash"></use></svg>
                    </button>
                </td>
            </tr>
        `;
    });
    html += `</tbody></table>`;
    container.innerHTML = html;
}

// PLAYERS TABLE
function renderAdminPlayersTable() {
    const container = document.getElementById('adminPlayersList');
    const badge = document.getElementById('playerCountBadge');
    if (badge) badge.textContent = `${playersData.length} Players`;

    if (!playersData || playersData.length === 0) {
        container.innerHTML = `
            <div class="p-8 text-center space-y-3">
                <svg class="w-10 h-10 text-slate-600 mx-auto"><use href="#icon-empty"></use></svg>
                <p class="text-sm text-slate-400 font-medium">No players registered yet</p>
                <div class="flex justify-center gap-2">
                    <button onclick="openPlayerModal()" class="px-3 py-1.5 bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold rounded-lg shadow transition">
                        Add Player
                    </button>
                    <button onclick="openCsvModal()" class="px-3 py-1.5 bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold rounded-lg shadow transition">
                        Import CSV
                    </button>
                </div>
            </div>
        `;
        return;
    }

    let html = `
        <table class="w-full text-left text-xs text-slate-200">
            <thead class="bg-slate-950 text-slate-400 font-semibold uppercase border-b border-slate-800">
                <tr>
                    <th class="p-2.5 w-16">RN</th>
                    <th class="p-2.5">Player Name</th>
                    <th class="p-2.5">Squad</th>
                    <th class="p-2.5">Grade/Sec</th>
                    <th class="p-2.5 text-right">Actions</th>
                </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/60">
    `;

    playersData.forEach(p => {
        const teamName = getTeamName(p.team_id);
        html += `
            <tr class="hover:bg-slate-800/40 transition">
                <td class="p-2.5 font-mono text-slate-400">${p.roll_number || '-'}</td>
                <td class="p-2.5 font-bold text-white">${p.name}</td>
                <td class="p-2.5 text-emerald-400 font-medium">${teamName}</td>
                <td class="p-2.5 text-slate-400">${p.grade || '-'}${p.section ? ' (' + p.section + ')' : ''}</td>
                <td class="p-2.5 text-right space-x-1">
                    <button onclick="openPlayerModal('${p.id}')" class="p-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition" title="Edit Player">
                        <svg class="w-3.5 h-3.5"><use href="#icon-edit"></use></svg>
                    </button>
                    <button onclick="deletePlayer('${p.id}')" class="p-1 rounded bg-rose-950 hover:bg-rose-900 text-rose-300 transition" title="Delete Player">
                        <svg class="w-3.5 h-3.5"><use href="#icon-trash"></use></svg>
                    </button>
                </td>
            </tr>
        `;
    });
    html += `</tbody></table>`;
    container.innerHTML = html;
}

// SEEDER LOGS DISPLAY
async function fetchSeederLogs() {
    try {
        const res = await fetch('/api/admin/seeder-logs');
        seederLogsData = await res.json();
    } catch (e) {
        console.error('Error fetching seeder logs', e);
    }
}

function renderAdminSeederLogs() {
    const container = document.getElementById('adminSeederLogsContainer');
    if (!container) return;

    if (!seederLogsData || seederLogsData.length === 0) {
        container.innerHTML = `<p class="text-xs text-slate-400 py-4 italic">No seeder logs recorded yet. Click "Run Seeder Now" to generate data seeder stats.</p>`;
        return;
    }

    const latest = seederLogsData[0];
    let html = `
        <div class="bg-slate-950 p-4 rounded-xl border border-slate-800 mb-4 flex flex-wrap justify-between items-center gap-4">
            <div>
                <div class="flex items-center gap-2 mb-1">
                    <span class="px-2 py-0.5 text-[10px] font-bold rounded-full uppercase ${latest.status === 'success' ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' : 'bg-rose-950 text-rose-400 border border-rose-800'}">${latest.status}</span>
                    <span class="text-xs font-mono text-slate-400">Run: ${new Date(latest.created_at).toLocaleString()}</span>
                </div>
                <p class="text-xs font-semibold text-white">${latest.details || 'Seeder run completed'}</p>
            </div>
            <div class="flex gap-4 text-center">
                <div class="bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800">
                    <span class="block text-[10px] text-slate-400 font-mono">HOUSES</span>
                    <span class="text-sm font-bold text-emerald-400">${latest.houses_created || 0}</span>
                </div>
                <div class="bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800">
                    <span class="block text-[10px] text-slate-400 font-mono">SPORTS</span>
                    <span class="text-sm font-bold text-sky-400">${latest.sports_created || 0}</span>
                </div>
                <div class="bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800">
                    <span class="block text-[10px] text-slate-400 font-mono">SQUADS</span>
                    <span class="text-sm font-bold text-purple-400">${latest.squads_created || 0}</span>
                </div>
                <div class="bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800">
                    <span class="block text-[10px] text-slate-400 font-mono">PLAYERS</span>
                    <span class="text-sm font-bold text-amber-400">${latest.players_created || 0}</span>
                </div>
                <div class="bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800">
                    <span class="block text-[10px] text-slate-400 font-mono">FIXTURES</span>
                    <span class="text-sm font-bold text-emerald-400">${latest.fixtures_created || 0}</span>
                </div>
            </div>
        </div>
    `;

    if (seederLogsData.length > 1) {
        html += `
            <details class="text-xs">
                <summary class="cursor-pointer text-slate-400 hover:text-white font-semibold py-1">View Previous Log History (${seederLogsData.length - 1} runs)</summary>
                <div class="mt-2 space-y-2 max-h-48 overflow-y-auto">
        `;
        seederLogsData.slice(1).forEach(log => {
            html += `
                <div class="p-2.5 bg-slate-950/60 rounded-lg border border-slate-800/80 flex justify-between items-center text-slate-300">
                    <div>
                        <span class="font-mono text-[10px] text-slate-400">${new Date(log.created_at).toLocaleString()}</span>
                        <span class="ml-2 font-semibold">${log.details || 'Seeder run'}</span>
                    </div>
                    <span class="font-mono text-xs font-bold text-emerald-400">${log.players_created || 0} P / ${log.fixtures_created || 0} F</span>
                </div>
            `;
        });
        html += `</div></details>`;
    }

    container.innerHTML = html;
}

// TRIGGER SEEDER RUN
async function triggerSeederRun() {
    const btn = document.getElementById('runSeederBtn');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<svg class="w-4 h-4 animate-spin mr-1"><use href="#icon-refresh"></use></svg> Running Seeder...`;
    }

    try {
        const res = await fetch('/api/admin/run-seeder', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}`
            }
        });
        const logEntry = await res.json();
        await loadAdminCenterData();
        alert(`Seeder completed successfully! ${logEntry.players_created || 0} players & ${logEntry.fixtures_created || 0} fixtures processed.`);
    } catch (e) {
        alert(`Seeder run failed: ${e.message}`);
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = `<svg class="icon w-4 h-4 mr-1"><use href="#icon-play"></use></svg> Run Seeder Now`;
        }
    }
}

// ADMIN FIXTURES MANAGEMENT TABLE
function renderAdminFixturesTable() {
    const container = document.getElementById('adminFixturesContainer');
    if (!matchesData || matchesData.length === 0) {
        container.innerHTML = `<p class="p-4 text-xs text-slate-400">No matches found in database.</p>`;
        return;
    }

    let html = `
        <table class="w-full text-left text-xs text-slate-200">
            <thead class="bg-slate-950 text-slate-400 font-semibold uppercase border-b border-slate-800">
                <tr>
                    <th class="p-2.5">Fixture</th>
                    <th class="p-2.5">Sport/Gender</th>
                    <th class="p-2.5">Status</th>
                    <th class="p-2.5">Result</th>
                    <th class="p-2.5 text-right">Quick Score Action</th>
                </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/60">
    `;

    matchesData.forEach(m => {
        const teamAName = getTeamName(m.team_a_id);
        const teamBName = getTeamName(m.team_b_id);
        const sName = getSportName(m.sport_id);

        html += `
            <tr class="hover:bg-slate-800/40 transition">
                <td class="p-2.5 font-bold text-white">${teamAName} vs ${teamBName}</td>
                <td class="p-2.5 text-slate-400">${sName} (${m.gender})</td>
                <td class="p-2.5">
                    <span class="px-2 py-0.5 text-[10px] font-bold rounded-full ${m.status === 'completed' ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' : 'bg-slate-800 text-slate-400 border border-slate-700'}">${m.status.toUpperCase()}</span>
                </td>
                <td class="p-2.5 font-mono font-bold text-emerald-400">${m.score_summary || '-'}</td>
                <td class="p-2.5 text-right space-x-1">
                    <button onclick="openMatchModal('${m.id}')" class="px-2.5 py-1 bg-purple-600 hover:bg-purple-500 text-white text-[11px] font-semibold rounded transition shadow">
                        Record Score
                    </button>
                </td>
            </tr>
        `;
    });
    html += `</tbody></table>`;
    container.innerHTML = html;
}

// MODAL HANDLERS FOR SQUAD CRUD
function openSquadModal(squadId = null) {
    const houseSelect = document.getElementById('squadHouseId');
    const sportSelect = document.getElementById('squadSportId');

    houseSelect.innerHTML = housesData.map(h => `<option value="${h.id}">${h.name}</option>`).join('');
    sportSelect.innerHTML = sportsData.map(s => `<option value="${s.id}">${s.name}</option>`).join('');

    if (squadId) {
        const sq = squadsData.find(s => s.id === squadId);
        if (sq) {
            document.getElementById('squadId').value = sq.id;
            document.getElementById('squadHouseId').value = sq.house_id;
            document.getElementById('squadSportId').value = sq.sport_id;
            document.getElementById('squadGender').value = sq.gender || 'Boys';
            document.getElementById('squadLabel').value = sq.squad_label || 'A';
            document.getElementById('squadModalTitle').textContent = 'Edit House Squad';
        }
    } else {
        document.getElementById('squadId').value = '';
        document.getElementById('squadLabel').value = 'A';
        document.getElementById('squadModalTitle').textContent = 'Add House Squad';
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
        const res = await fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}`
            },
            body: JSON.stringify({ house_id, sport_id, gender, squad_label })
        });
        if (res.ok) {
            closeModal('squadModal');
            await loadAdminCenterData();
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to save squad');
        }
    } catch (err) {
        alert('Network error while saving squad');
    }
}

async function deleteSquad(squadId) {
    if (!confirm('Are you sure you want to delete this squad?')) return;
    try {
        const res = await fetch(`/api/teams/${squadId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${currentToken}` }
        });
        if (res.ok) {
            await loadAdminCenterData();
        } else {
            alert('Failed to delete squad');
        }
    } catch (err) {
        alert('Network error');
    }
}

// MODAL HANDLERS FOR PLAYER CRUD
function openPlayerModal(playerId = null) {
    const teamSelect = document.getElementById('playerTeamId');
    teamSelect.innerHTML = squadsData.map(s => `<option value="${s.id}">${s.name}</option>`).join('');

    if (playerId) {
        const p = playersData.find(item => item.id === playerId);
        if (p) {
            document.getElementById('playerId').value = p.id;
            document.getElementById('playerName').value = p.name;
            document.getElementById('playerRoll').value = p.roll_number || '';
            document.getElementById('playerTeamId').value = p.team_id;
            document.getElementById('playerGrade').value = p.grade || '';
            document.getElementById('playerSection').value = p.section || '';
            document.getElementById('playerGender').value = p.gender || 'Boys';
            document.getElementById('playerModalTitle').textContent = 'Edit Player';
        }
    } else {
        document.getElementById('playerId').value = '';
        document.getElementById('playerName').value = '';
        document.getElementById('playerRoll').value = '';
        document.getElementById('playerGrade').value = '';
        document.getElementById('playerSection').value = '';
        document.getElementById('playerModalTitle').textContent = 'Add Player';
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
        const res = await fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}`
            },
            body: JSON.stringify({ name, roll_number, team_id, grade, section, gender })
        });
        if (res.ok) {
            closeModal('playerModal');
            await loadAdminCenterData();
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to save player');
        }
    } catch (err) {
        alert('Network error while saving player');
    }
}

async function deletePlayer(playerId) {
    if (!confirm('Are you sure you want to delete this player?')) return;
    try {
        const res = await fetch(`/api/players/${playerId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${currentToken}` }
        });
        if (res.ok) {
            await loadAdminCenterData();
        } else {
            alert('Failed to delete player');
        }
    } catch (err) {
        alert('Network error');
    }
}

// MODAL HANDLERS FOR CREATING FIXTURES & MATCH SCORES
async function openCreateMatchModal() {
    const sportSelect = document.getElementById('newMatchSportId');
    if (!sportSelect) return;

    if (!sportsData || sportsData.length === 0) {
        await fetchSports();
    }
    if (!squadsData || squadsData.length === 0) {
        await fetchSquads();
    }

    sportSelect.innerHTML = sportsData.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
    onNewMatchSportOrGenderChange();
    openModal('createMatchModal');
}

function onNewMatchSportOrGenderChange() {
    const sportId = document.getElementById('newMatchSportId').value;
    const gender = document.getElementById('newMatchGender').value;
    const teamASel = document.getElementById('newMatchTeamA');
    const teamBSel = document.getElementById('newMatchTeamB');

    // Filter squads by matching sport_id and gender
    const matchingSquads = squadsData.filter(s => s.sport_id === sportId && s.gender === gender);

    if (matchingSquads.length === 0) {
        teamASel.innerHTML = `<option value="">No squads available</option>`;
        teamBSel.innerHTML = `<option value="">No squads available</option>`;
        return;
    }

    teamASel.innerHTML = matchingSquads.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
    teamBSel.innerHTML = matchingSquads.map((s, idx) => `<option value="${s.id}" ${idx === 1 ? 'selected' : ''}>${s.name}</option>`).join('');
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
        alert('Please select two distinct teams for the fixture.');
        return;
    }

    try {
        const res = await fetch('/api/matches', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}`
            },
            body: JSON.stringify({ sport_id, gender, team_a_id, team_b_id, stage, round_info })
        });

        if (res.ok) {
            closeModal('createMatchModal');
            await loadAdminCenterData();
            alert('Fixture created successfully!');
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to create fixture');
        }
    } catch (err) {
        alert('Network error while creating fixture');
    }
}

function openMatchModal(matchId) {
    const match = matchesData.find(m => m.id === matchId);
    if (!match) return;

    document.getElementById('matchId').value = match.id;
    const teamAName = getTeamName(match.team_a_id);
    const teamBName = getTeamName(match.team_b_id);

    document.getElementById('matchModalSummary').textContent = `${teamAName} vs ${teamBName}`;
    document.getElementById('teamALabel').textContent = `${teamAName} Score`;
    document.getElementById('teamBLabel').textContent = `${teamBName} Score`;
    document.getElementById('matchScoreA').value = match.score_team_a || 0;
    document.getElementById('matchScoreB').value = match.score_team_b || 0;

    openModal('matchModal');
}

async function handleMatchSubmit(e) {
    e.preventDefault();
    const matchId = document.getElementById('matchId').value;
    const score_team_a = document.getElementById('matchScoreA').value;
    const score_team_b = document.getElementById('matchScoreB').value;

    try {
        const res = await fetch(`/api/matches/${matchId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}`
            },
            body: JSON.stringify({ score_team_a, score_team_b })
        });

        if (res.ok) {
            closeModal('matchModal');
            loadCurrentTabData();
        } else {
            const err = await res.json();
            alert(err.error || 'Failed to update score');
        }
    } catch (err) {
        alert('Network error while updating match score');
    }
}

// CSV BULK IMPORT FLOW
function openCsvModal() {
    pendingCsvPlayers = [];
    document.getElementById('csvFileInput').value = '';
    document.getElementById('csvPreviewContainer').classList.add('hidden');
    document.getElementById('commitCsvBtn').disabled = true;
    openModal('csvModal');
}

function handleCsvFileSelected(e) {
    const file = e.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = function(evt) {
        const text = evt.target.result;
        parseAndPreviewCsv(text);
    };
    reader.readAsText(file);
}

function parseAndPreviewCsv(csvText) {
    const lines = csvText.split(/\r\n|\n/).filter(line => line.trim() !== '');
    if (lines.length < 2) {
        alert('CSV file is empty or missing headers');
        return;
    }

    const headers = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, '').toLowerCase());
    const rows = [];

    for (let i = 1; i < lines.length; i++) {
        const cols = lines[i].split(',').map(c => c.trim().replace(/^"|"$/g, ''));
        if (cols.length === 0 || !cols[0]) continue;

        let roll = '', name = '', grade = '', section = '', gender = 'Boys', squadStr = 'A';
        headers.forEach((h, idx) => {
            const val = cols[idx] || '';
            if (h.includes('roll') || h.includes('rn')) roll = val;
            else if (h.includes('name')) name = val;
            else if (h.includes('grade')) grade = val;
            else if (h.includes('sec')) section = val;
            else if (h.includes('gen') || h.includes('sex')) gender = val;
            else if (h.includes('squad') || h.includes('team')) squadStr = val;
        });

        if (!name) continue;

        // Resolve squad team_id
        let team_id = squadsData[0] ? squadsData[0].id : '';
        const foundSquad = squadsData.find(s => s.name.toLowerCase().includes(squadStr.toLowerCase()) || s.squad_label.toLowerCase() === squadStr.toLowerCase());
        if (foundSquad) team_id = foundSquad.id;

        const isUpdate = roll ? playersData.some(p => String(p.roll_number) === String(roll)) : false;

        rows.push({
            roll_number: roll,
            name: name,
            grade: grade,
            section: section,
            gender: gender.toLowerCase().includes('girl') ? 'Girls' : 'Boys',
            team_id: team_id,
            isUpdate: isUpdate
        });
    }

    pendingCsvPlayers = rows;
    renderCsvPreviewTable(rows);
}

function renderCsvPreviewTable(rows) {
    const container = document.getElementById('csvPreviewContainer');
    const tbody = document.getElementById('csvPreviewTableBody');
    const countBadge = document.getElementById('csvPreviewCountBadge');
    const warningsDiv = document.getElementById('csvValidationWarnings');
    const commitBtn = document.getElementById('commitCsvBtn');

    countBadge.textContent = `${rows.length} records parsed`;

    // Compute squad counts for range warnings
    const squadCounts = {};
    rows.forEach(r => {
        squadCounts[r.team_id] = (squadCounts[r.team_id] || 0) + 1;
    });

    let warningHtml = '';
    // Squad size rules: Cricksal 7-11, Futsal 11-14, Basketball 7-10
    Object.keys(squadCounts).forEach(tid => {
        const count = squadCounts[tid];
        const squad = squadsData.find(s => s.id === tid);
        if (squad) {
            const sportName = getSportName(squad.sport_id).toLowerCase();
            let min = 7, max = 11;
            if (sportName.includes('futsal')) { min = 11; max = 14; }
            else if (sportName.includes('basketball')) { min = 7; max = 10; }

            if (count < min || count > max) {
                warningHtml += `
                    <div class="p-2 bg-amber-950/60 border border-amber-800 text-amber-300 rounded text-xs">
                        ⚠️ Warning: Squad <strong>${squad.name}</strong> has ${count} players (typical recommended range: ${min}–${max}).
                    </div>
                `;
            }
        }
    });
    warningsDiv.innerHTML = warningHtml;

    let tbodyHtml = '';
    rows.forEach(r => {
        const teamName = getTeamName(r.team_id);
        tbodyHtml += `
            <tr class="border-b border-slate-900">
                <td class="p-2 font-mono">${r.roll_number || '-'}</td>
                <td class="p-2 font-bold text-white">${r.name}</td>
                <td class="p-2">${r.grade || '-'}${r.section ? ' (' + r.section + ')' : ''}</td>
                <td class="p-2">${r.gender}</td>
                <td class="p-2 text-emerald-400 font-medium">${teamName}</td>
                <td class="p-2">
                    ${r.isUpdate ?
                        `<span class="px-2 py-0.5 text-[10px] font-bold bg-amber-950 text-amber-400 border border-amber-800 rounded-full">WILL UPDATE EXISTING</span>` :
                        `<span class="px-2 py-0.5 text-[10px] font-bold bg-emerald-950 text-emerald-400 border border-emerald-800 rounded-full">NEW PLAYER</span>`}
                </td>
            </tr>
        `;
    });
    tbody.innerHTML = tbodyHtml;
    container.classList.remove('hidden');
    commitBtn.disabled = rows.length === 0;
}

async function commitCsvImport() {
    if (!pendingCsvPlayers || pendingCsvPlayers.length === 0) return;

    const commitBtn = document.getElementById('commitCsvBtn');
    commitBtn.disabled = true;
    commitBtn.textContent = 'Importing...';

    try {
        const res = await fetch('/api/players/bulk', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}`
            },
            body: JSON.stringify(pendingCsvPlayers)
        });

        if (res.ok) {
            const data = await res.json();
            alert(data.message || 'Import successful!');
            closeModal('csvModal');
            await loadAdminCenterData();
        } else {
            const err = await res.json();
            alert(err.error || 'Import failed');
        }
    } catch (err) {
        alert('Network error during CSV import');
    } finally {
        commitBtn.disabled = false;
        commitBtn.textContent = 'Commit Import';
    }
}

// HELPER RESOLVERS
function getTeamName(teamId) {
    const t = squadsData.find(item => item.id === teamId);
    return t ? t.name : 'Squad';
}

function getHouseName(houseId) {
    const h = housesData.find(item => item.id === houseId);
    return h ? h.name : 'House';
}

function getSportName(sportId) {
    const s = sportsData.find(item => item.id === sportId);
    return s ? s.name : 'Sport';
}
