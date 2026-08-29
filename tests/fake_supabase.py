"""In-memory FakeSupabase for the ScoreBoard test suite.

Replicates just enough of the supabase-py client API that the Flask backend
uses, including row-level joins, views, auth, and the admin/session tables.
Views are computed with the same logic the old mock DB used, so the existing
standings fixture assertions keep passing unchanged.
"""
import uuid
from types import SimpleNamespace


def _split_top(s):
    parts, depth, cur = [], 0, ''
    for ch in s:
        if ch == '(':
            depth += 1
        if ch == ')':
            depth -= 1
        if ch == ',' and depth == 0:
            parts.append(cur)
            cur = ''
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return [p.strip() for p in parts]


class FakeAuthError(Exception):
    pass


class FakeAPIResponse:
    def __init__(self, data, count=None):
        self.data = data
        self.count = count


class FakeQuery:
    def __init__(self, db, table):
        self.db = db
        self.table = table
        self._select = None
        self._has_count = False
        self._filters = []
        self._orders = []
        self._limit = None
        self._range = None
        self._single_mode = False
        self._maybe_single_mode = False
        self._write = None  # ('insert'|'upsert'|'update'|'delete', arg)

    # ── filters / modifiers ──
    def select(self, cols, count=None):
        self._select = cols
        if count:
            self._has_count = True
        return self

    def eq(self, col, val):
        self._filters.append(('eq', col, val))
        return self

    def neq(self, col, val):
        self._filters.append(('neq', col, val))
        return self

    def ilike(self, col, pattern):
        self._filters.append(('ilike', col, pattern))
        return self

    def range(self, start, end):
        self._range = (start, end)
        return self

    def lt(self, col, val):
        self._filters.append(('lt', col, val))
        return self

    def lte(self, col, val):
        self._filters.append(('lte', col, val))
        return self

    def gt(self, col, val):
        self._filters.append(('gt', col, val))
        return self

    def gte(self, col, val):
        self._filters.append(('gte', col, val))
        return self

    def in_(self, col, vals):
        self._filters.append(('in', col, list(vals)))
        return self

    def order(self, col, desc=False):
        # Last call wins (the app retries with a different column on failure).
        self._orders = [(col, desc)]
        return self

    def limit(self, n):
        self._limit = n
        return self

    def single(self):
        self._single_mode = True
        return self

    def maybe_single(self):
        self._maybe_single_mode = True
        return self

    # ── write operations ──
    def insert(self, rows):
        self._write = ('insert', rows)
        return self

    def upsert(self, rows, on_conflict=None):
        self._write = ('upsert', (rows, on_conflict))
        return self

    def update(self, data):
        self._write = ('update', dict(data))
        return self

    def delete(self):
        self._write = ('delete', None)
        return self

    def _run_write(self):
        kind, arg = self._write
        self._write = None
        if kind == 'insert':
            rows = arg if isinstance(arg, list) else [arg]
            out = []
            for row in rows:
                item = dict(row)
                item.setdefault('id', str(uuid.uuid4()))
                self.db._insert(self.table, item)
                out.append(dict(item))
            return FakeAPIResponse(out)
        if kind == 'upsert':
            rows, on_conflict = arg
            if isinstance(rows, dict):
                rows = [rows]
            out = []
            for row in rows:
                item = dict(row)
                item.setdefault('id', str(uuid.uuid4()))
                out.append(dict(self.db._upsert(self.table, item, on_conflict)))
            return FakeAPIResponse(out)
        if kind == 'update':
            updated = self.db._update(self.table, arg, self._filters)
            return FakeAPIResponse([dict(r) for r in updated])
        # delete
        self.db._delete(self.table, self._filters)
        return FakeAPIResponse([])

    # ── read execution ──
    def execute(self):
        if self._write:
            return self._run_write()
        rows = self.db._select_rows(self.table, self._filters)
        rows = [dict(r) for r in rows]

        if self._select:
            rows = [self.db._apply_select(r, self.table, self._select) for r in rows]

        total = len(rows)
        if self._orders:
            for col, desc in reversed(self._orders):
                if rows and col not in rows[0]:
                    raise FakeAuthError(f"column {col} does not exist")
                rows.sort(key=lambda r: r.get(col) if col in r else 0, reverse=desc)

        if self._range is not None:
            start, end = self._range
            rows = rows[start:end + 1]

        if self._limit is not None:
            rows = rows[: self._limit]

        if self._single_mode:
            if not rows:
                raise FakeAuthError("no rows")
            return FakeAPIResponse(rows[0], total)
        if self._maybe_single_mode:
            return FakeAPIResponse(rows[0] if rows else None, total)
        return FakeAPIResponse(rows, total if self._has_count else None)


class FakeAuthAdmin:
    def __init__(self, db):
        self.db = db

    def create_user(self, payload):
        email = (payload.get('email') or '').lower()
        if email in self.db._users:
            raise FakeAuthError("User already registered")
        user = {
            'id': payload.get('id') or str(uuid.uuid4()),
            'email': email,
            'password': payload.get('password') or '',
            'email_confirm': payload.get('email_confirm', True),
        }
        self.db._users[email] = user
        return SimpleNamespace(**user)

    def list_users(self):
        return [SimpleNamespace(**u) for u in self.db._users.values()]

    def delete_user(self, user_id):
        for email, u in list(self.db._users.items()):
            if u['id'] == user_id:
                del self.db._users[email]
                return SimpleNamespace(**u)
        raise FakeAuthError("user not found")

    def update_user_by_id(self, user_id, updates):
        for u in self.db._users.values():
            if u['id'] == user_id:
                u.update(updates)
                return SimpleNamespace(**u)
        raise FakeAuthError("user not found")


class FakeAuth:
    def __init__(self, db):
        self.db = db
        self.admin = FakeAuthAdmin(db)

    def sign_in_with_password(self, payload):
        email = (payload.get('email') or '').lower()
        password = payload.get('password') or ''
        user = self.db._users.get(email)
        if not user or user['password'] != password:
            raise FakeAuthError("InvalidCredentials")
        return SimpleNamespace(user=SimpleNamespace(email=user['email'], id=user['id']))


class FakeSupabase:
    """In-memory stand-in for the Supabase client."""

    VIEWS = {'leaderboard_view', 'house_overall_standings', 'final_qualifiers_view'}

    def __init__(self, seed):
        self._users = {}
        self._tables = {
            name: [dict(r) for r in rows]
            for name, rows in seed.items()
            if name not in ('brackets', 'generic_results', 'football_events', 'basketball_quarters')
        }
        self._tables.setdefault('players', [])
        self._tables.setdefault('admins', [])
        self._tables.setdefault('admin_sessions', [])
        self._tables.setdefault('admin_audit_log', [])
        self.auth = FakeAuth(self)

    def table(self, name):
        return FakeQuery(self, name)

    # ── storage ──
    def _insert(self, table, item):
        self._tables.setdefault(table, []).append(item)

    def _upsert(self, table, item, on_conflict):
        rows = self._tables.setdefault(table, [])
        if on_conflict:
            key = item.get(on_conflict)
            for row in rows:
                if row.get(on_conflict) == key:
                    keep_id = row.get('id')
                    row.clear()
                    row.update(item)
                    if keep_id and 'id' not in row:
                        row['id'] = keep_id
                    return row
        rows.append(item)
        return item

    def _update(self, table, data, filters):
        rows = list(self._select_rows(table, filters))
        for row in rows:
            row.update(data)
        return rows

    def _delete(self, table, filters):
        rows = self._select_rows(table, filters)
        store = self._tables.get(table, [])
        ids = {id(r) for r in rows}
        self._tables[table] = [r for r in store if id(r) not in ids]

    def _select_rows(self, table, filters):
        if table in self.VIEWS:
            rows = self._compute_view(table)
        else:
            rows = self._tables.get(table, [])

        matched = []
        for row in rows:
            keep = True
            for op, col, val in filters:
                got = row.get(col)
                if op == 'eq':
                    if got != val:
                        keep = False
                        break
                elif op == 'neq':
                    if got == val:
                        keep = False
                        break
                elif op == 'lt':
                    if not (got is not None and got < val):
                        keep = False
                        break
                elif op == 'lte':
                    if not (got is not None and got <= val):
                        keep = False
                        break
                elif op == 'gt':
                    if not (got is not None and got > val):
                        keep = False
                        break
                elif op == 'gte':
                    if not (got is not None and got >= val):
                        keep = False
                        break
                elif op == 'in':
                    if got not in val:
                        keep = False
                        break
                elif op == 'ilike':
                    hay = str(got) if got is not None else ''
                    needle = str(val)
                    if needle.startswith('%') and needle.endswith('%') and len(needle) >= 2:
                        needle = needle[1:-1]
                    if needle.lower() not in hay.lower():
                        keep = False
                        break
            if keep:
                matched.append(row)
        return matched

    # ── select expansion (joins) ──
    def _parse_select(self, sel):
        plain, nested = [], []
        for chunk in _split_top(sel):
            if not chunk:
                continue
            alias = None
            if ':' in chunk:
                alias, _, chunk = chunk.partition(':')
            if '(' in chunk:
                table, _, inner = chunk.partition('(')
                table = table.strip()
                inner = inner.rsplit(')', 1)[0]
                fk = None
                if '!' in table:
                    table, _, fk_hint = table.partition('!')
                    fk = self._fk_from_hint(table, fk_hint)
                nested.append((alias or table, table, fk, inner))
            else:
                plain.append(chunk)
        return plain, nested

    @staticmethod
    def _fk_from_hint(table, hint):
        if hint == 'matches_team_a_id_fkey':
            return 'team_a_id'
        if hint == 'matches_team_b_id_fkey':
            return 'team_b_id'
        if hint and hint.startswith(table + '_'):
            return hint[len(table) + 1:]
        return None

    @staticmethod
    def _link_column(main_table, child_table):
        links = {
            ('teams', 'houses'): 'house_id',
            ('teams', 'sports'): 'sport_id',
            ('players', 'teams'): 'team_id',
            ('matches', 'sports'): 'sport_id',
        }
        return links.get((main_table, child_table))

    def _apply_select(self, row, main_table, sel):
        plain, nested = self._parse_select(sel)
        result = dict(row)
        if plain and plain != ['*']:
            keys = [k for k in list(result.keys()) if k in plain]
            result = {k: result[k] for k in keys if k in result}
        for alias, child_table, fk, inner in nested:
            child = self._resolve_nested(main_table, child_table, row, fk, inner)
            result[alias] = child
        return result

    def _resolve_nested(self, main_table, child_table, row, fk, inner):
        col = fk
        if col is None:
            col = self._link_column(main_table, child_table)
        child_id = row.get(col) if col else None
        if child_id is None:
            return None
        for child_row in self._tables.get(child_table, []):
            if child_row.get('id') == child_id:
                return self._apply_select(dict(child_row), child_table, inner)
        return None

    # ── views ──
    def _compute_view(self, name):
        if name == 'leaderboard_view':
            return self._leaderboard_rows()
        if name == 'house_overall_standings':
            return self._overall_rows()
        if name == 'final_qualifiers_view':
            return [r for r in self._leaderboard_rows() if r['rank'] <= 2]
        return []

    def _leaderboard_rows(self):
        teams = self._tables.get('teams', [])
        sports = {s['id']: s for s in self._tables.get('sports', [])}
        houses = {h['id']: h for h in self._tables.get('houses', [])}
        matches = self._tables.get('matches', [])

        results = []
        for t in teams:
            sport = sports.get(t['sport_id'], {'name': 'Futsal', 'type': 'football'})
            house = houses.get(t['house_id'], {'name': 'Karnali', 'color_hex': '#10B981', 'short_code': 'KAR'})
            played = wins = draws = losses = pts = diff = 0
            for m in matches:
                if m.get('status') != 'completed':
                    continue
                if m.get('team_a_id') == t['id']:
                    played += 1
                    if m.get('is_draw'):
                        draws += 1
                    elif m.get('winner_team_id') == t['id']:
                        wins += 1
                        pts += 3
                    else:
                        losses += 1
                    diff += (m.get('score_team_a', 0) - m.get('score_team_b', 0))
                elif m.get('team_b_id') == t['id']:
                    played += 1
                    if m.get('is_draw'):
                        draws += 1
                    elif m.get('winner_team_id') == t['id']:
                        wins += 1
                        pts += 3
                    else:
                        losses += 1
                    diff += (m.get('score_team_b', 0) - m.get('score_team_a', 0))

            results.append({
                'team_id': t['id'],
                'team_name': t['name'],
                'house_id': t['house_id'],
                'house_name': house['name'],
                'house_color': house['color_hex'],
                'house_short_code': house['short_code'],
                'gender': t.get('gender', 'Boys'),
                'squad_label': t.get('squad_label', 'A'),
                'pool': t.get('pool'),
                'sport_id': t['sport_id'],
                'sport_name': sport['name'],
                'sport_type': sport['type'],
                'level': t.get('level', 'HS'),
                'played': played,
                'wins': wins,
                'draws': draws,
                'losses': losses,
                'score_difference': diff,
                'points': pts,
                'rank': 1,
            })

        results.sort(key=lambda x: (x['points'], x['score_difference']), reverse=True)
        for idx, r in enumerate(results, start=1):
            r['rank'] = idx
        return results

    def _overall_rows(self):
        houses = self._tables.get('houses', [])
        teams = self._tables.get('teams', [])
        matches = self._tables.get('matches', [])

        squad_counts = {}
        for t in teams:
            squad_counts[t['house_id']] = squad_counts.get(t['house_id'], 0) + 1

        team_stats = {}
        for m in matches:
            if m.get('status') != 'completed' or m.get('stage') != 'league':
                continue
            ta, tb = m.get('team_a_id'), m.get('team_b_id')
            sa, sb = m.get('score_team_a', 0), m.get('score_team_b', 0)
            if ta:
                s = team_stats.setdefault(ta, {'played': 0, 'wins': 0, 'draws': 0, 'losses': 0, 'diff': 0, 'pts': 0})
                s['played'] += 1
                s['diff'] += (sa - sb)
                if m.get('is_draw'):
                    s['draws'] += 1
                    s['pts'] += 1
                elif m.get('winner_team_id') == ta:
                    s['wins'] += 1
                    s['pts'] += 3
                else:
                    s['losses'] += 1
            if tb:
                s = team_stats.setdefault(tb, {'played': 0, 'wins': 0, 'draws': 0, 'losses': 0, 'diff': 0, 'pts': 0})
                s['played'] += 1
                s['diff'] += (sb - sa)
                if m.get('is_draw'):
                    s['draws'] += 1
                    s['pts'] += 1
                elif m.get('winner_team_id') == tb:
                    s['wins'] += 1
                    s['pts'] += 3
                else:
                    s['losses'] += 1

        house_stats = {}
        for t in teams:
            h_id = t['house_id']
            st = team_stats.get(t['id'], {'played': 0, 'wins': 0, 'draws': 0, 'losses': 0, 'diff': 0, 'pts': 0})
            hs = house_stats.setdefault(h_id, {'played': 0, 'wins': 0, 'draws': 0, 'losses': 0, 'diff': 0, 'pts': 0})
            hs['played'] += st['played']
            hs['wins'] += st['wins']
            hs['draws'] += st['draws']
            hs['losses'] += st['losses']
            hs['diff'] += st['diff']
            hs['pts'] += st['pts']

        standings = []
        for h in houses:
            h_id = h['id']
            hs = house_stats.get(h_id, {'played': 0, 'wins': 0, 'draws': 0, 'losses': 0, 'diff': 0, 'pts': 0})
            standings.append({
                'house_id': h_id,
                'house_name': h['name'],
                'color_hex': h['color_hex'],
                'short_code': h['short_code'],
                'total_squads': squad_counts.get(h_id, 0),
                'matches_played': hs['played'],
                'total_wins': hs['wins'],
                'total_draws': hs['draws'],
                'total_losses': hs['losses'],
                'total_score_difference': hs['diff'],
                'total_points': hs['pts'],
                'rank': 1,
            })

        standings.sort(key=lambda x: (x['total_points'], x['total_score_difference'], x['total_wins']), reverse=True)
        for idx, r in enumerate(standings, start=1):
            r['rank'] = idx
        return standings