# Groups & Interest Clubs — Integration & Test Reference

## Router file
`/root/projects/whispr/backend/routers/groups.py`

## Integration snippet for main.py

Add these lines to `/root/projects/whispr/backend/main.py`:

### 1. Import + include the router

Place this AFTER the `app = FastAPI(...)` line (around line 321) and AFTER the
`app.add_middleware(...)` block:

```python
# ─── Groups & Interest Clubs router ────────────────────────
from routers.groups import router as groups_router, init_groups
app.include_router(groups_router)
```

### 2. Call init_groups in the lifespan

Find the existing lifespan (around line 314-319) and add the `init_groups` call
AFTER `init_db()`:

```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    await init_groups(db_pool)      # <-- ADD THIS LINE
    yield
    if db_pool:
        await db_pool.close()
```

### 3. (Optional) Remove the now-conflicting inline group routes

main.py already has inline group routes at lines ~1731-1866 that use DIFFERENT
paths from this router (e.g. `POST /api/groups/{id}/leave` vs this router's
`DELETE /api/groups/{id}/join`, and `GET /api/groups/{id}/messages` vs
`GET /api/groups/{id}/chat`). Because FastAPI matches routes in registration
order and the inline routes are registered first, they would SHADOW this router
unless removed.

To let this router own all `/api/groups/*` traffic, delete the inline block
from `# ════...GROUPS & INTEREST CLUBS════...` (line ~1731) through the end of
the `websocket_group` handler (line ~1866). The WebSocket group chat can stay
if you want realtime — it does not conflict with the REST routes. If you keep
it, just delete the REST handlers (`list_groups`, `create_group`, `join_group`,
`leave_group`, `group_messages`) in that span.

If you prefer to keep the inline routes, do NOT include this router (they
overlap). Pick one implementation.

---

## What the router does on init (idempotent)
- `CREATE TABLE IF NOT EXISTS` for `groups`, `group_members`, `group_messages`
  (no-op if main.py already created them).
- `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`:
  - `groups.is_private BOOLEAN DEFAULT FALSE`
  - `group_members.role VARCHAR(20) DEFAULT 'member'`
  - `group_messages.is_deleted BOOLEAN DEFAULT FALSE`
- Seeds 8 pre-made interest clubs (by-topic uniqueness, so no duplicates even
  if main.py already seeded some): Gaming, Music, Dating, Venting, Movies, Tech,
  Art, Fitness.

---

## Test curl commands

```bash
# Config
BASE=http://localhost:8004
# Get a token first (register or login):
TOKEN=$(curl -s -X POST $BASE/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"testgrp","password":"secret123"}' | python -c 'import sys,json;print(json.load(sys.stdin)["token"])')
AUTH="Authorization: Bearer $TOKEN"

# 8. List pre-made interest clubs
curl -s "$BASE/api/groups/clubs" -H "$AUTH" | python -m json.tool

# 2. List groups (all)
curl -s "$BASE/api/groups" -H "$AUTH" | python -m json.tool

# 2b. List groups — filter by topic
curl -s "$BASE/api/groups?topic=gaming" -H "$AUTH" | python -m json.tool

# 2c. List groups — search by name/description
curl -s "$BASE/api/groups?search=tech" -H "$AUTH" | python -m json.tool

# 2d. List only clubs I haven't joined (clubs only)
curl -s "$BASE/api/groups?clubs_only=true" -H "$AUTH" | python -m json.tool

# 1. Create a group
GROUP=$(curl -s -X POST "$BASE/api/groups" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Late Night Coders","description":"Code & chill","topic":"tech","is_private":false}')
echo "$GROUP"
GID=$(echo "$GROUP" | python -c 'import sys,json;print(json.load(sys.stdin)["id"])')

# 3. Group details + member count
curl -s "$BASE/api/groups/$GID" -H "$AUTH" | python -m json.tool

# 4. Join a group (use a club id, or $GID)
CLUB_GID=$(curl -s "$BASE/api/groups/clubs" -H "$AUTH" | python -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')
curl -s -X POST "$BASE/api/groups/$CLUB_GID/join" -H "$AUTH" | python -m json.tool

# 5. Leave a group (DELETE /join)
curl -s -X DELETE "$BASE/api/groups/$CLUB_GID/join" -H "$AUTH" | python -m json.tool

# Re-join so we can chat
curl -s -X POST "$BASE/api/groups/$CLUB_GID/join" -H "$AUTH" | python -m json.tool

# 7. Send a message to group chat
curl -s -X POST "$BASE/api/groups/$CLUB_GID/chat" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"hey everyone 👋","msg_type":"text"}' | python -m json.tool

# 6. List group chat messages
curl -s "$BASE/api/groups/$CLUB_GID/chat?limit=20" -H "$AUTH" | python -m json.tool

# 6b. Pagination — messages before a timestamp
curl -s "$BASE/api/groups/$CLUB_GID/chat?limit=10&before=2026-08-09T00:00:00" -H "$AUTH" | python -m json.tool
```

## Expected responses (shape)
- POST create → `{"id":"<uuid>","ok":true}`
- GET list → `[{"id","name","topic","description","is_club","is_private","members_count","is_member","created_at"}, ...]`
- GET details → `{...,"members_count","member_count","is_member",...}`
- POST join → `{"ok":true,"joined":true}` or `{"ok":true,"joined":false,"detail":"Already a member"}`
- DELETE join → `{"ok":true,"left":true}` or `{"ok":true,"left":false,"detail":"Not a member"}`
- POST chat → full message object with `sender:{id,username,display_name,avatar_url}`
- GET chat → chronological array of message objects (newest appended last)
