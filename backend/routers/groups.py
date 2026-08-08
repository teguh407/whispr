"""
Whispr Groups & Interest Clubs Router
─────────────────────────────────────
Topic-based community groups + pre-made interest clubs (Gaming, Music,
Dating, Venting, Movies, Tech, Art, Fitness). Users create/join groups
and chat inside group rooms.

Inspired by Wakie's interest clubs + Reddit-style topic communities.

Endpoints
---------
POST   /api/groups                   — create a group (name, description, topic, is_private)
GET    /api/groups                   — list groups (filter by topic, full-text search)
GET    /api/groups/clubs             — list pre-made interest clubs
GET    /api/groups/{group_id}        — group details + member count
POST   /api/groups/{group_id}/join   — join a group
DELETE /api/groups/{group_id}/join   — leave a group
GET    /api/groups/{group_id}/chat   — list group chat messages
POST   /api/groups/{group_id}/chat   — send a message to the group chat

Auth: JWT Bearer (mirrors main.py's get_current_user — same env var +
same claim/expiry so tokens issued by main.py decode here too).
DB:   asyncpg pool injected from main.py via init_groups(db_pool).

Tables (all CREATE TABLE IF NOT EXISTS — safe alongside main.py's init_db):
  groups          — id, name, description, topic, owner_id, is_private,
                    is_club, members_count, created_at
  group_members   — group_id, user_id, joined_at, role (member/admin)
  group_messages  — id, group_id, sender_id, content, msg_type,
                    media_url, created_at, is_deleted

Integration (in main.py)
-------------------------
    from routers.groups import router as groups_router, init_groups
    app.include_router(groups_router)
    # inside lifespan, AFTER init_db():
    await init_groups(db_pool)
"""
from __future__ import annotations

import os
import uuid
from datetime import datetime, timedelta
from typing import Optional, List

import asyncpg
import jwt  # PyJWT — matches main.py
from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api/groups", tags=["groups"])

# ─── Config (mirrors main.py) ──────────────────────────────
SECRET_KEY = os.getenv("WHISPR_SECRET", "whispr-dev-secret-change-me")
ALGORITHM = "HS256"

# ─── Shared DB pool (injected from main.py via init_groups) ─
_pool: Optional[asyncpg.Pool] = None
security = HTTPBearer()


def get_pool() -> asyncpg.Pool:
    """Return the shared asyncpg pool; raise 503 if not initialized yet."""
    if _pool is None:
        raise HTTPException(status_code=503, detail="Database pool not initialized")
    return _pool


# ─── JWT auth (identical semantics to main.py) ─────────────
def _decode_token(token: str) -> dict:
    try:
        return jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
    except jwt.ExpiredSignatureError:
        raise HTTPException(401, "Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(401, "Invalid token")


async def get_current_user(cred: HTTPAuthorizationCredentials = Depends(security)) -> dict:
    """Resolve the Bearer token to an active user dict (mirrors main.py)."""
    data = _decode_token(cred.credentials)
    pool = get_pool()
    async with pool.acquire() as conn:
        user = await conn.fetchrow(
            "SELECT * FROM users WHERE id = $1 AND is_active = TRUE",
            uuid.UUID(data["user_id"]),
        )
    if not user:
        raise HTTPException(401, "User not found")
    return dict(user)


# ─── Init: tables + columns + seed clubs ───────────────────
async def init_groups(pool: asyncpg.Pool) -> None:
    """
    Idempotent schema setup + seed for the groups feature.

    CREATE TABLE IF NOT EXISTS is used everywhere so this is safe to run
    against a database already migrated by main.py's init_db() — it will
    simply add the columns the task spec requires (is_private, role,
    is_deleted) that main.py's original schema omits.
    """
    global _pool
    _pool = pool
    async with pool.acquire() as conn:
        # ── Core tables (match main.py; no-op if already present) ──
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS groups (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                name VARCHAR(60) NOT NULL,
                topic VARCHAR(40) NOT NULL,
                description TEXT DEFAULT '',
                is_club BOOLEAN DEFAULT FALSE,
                owner_id UUID REFERENCES users(id) ON DELETE SET NULL,
                members_count INT DEFAULT 0,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS group_members (
                group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
                user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                joined_at TIMESTAMPTZ DEFAULT NOW(),
                PRIMARY KEY (group_id, user_id)
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS group_messages (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
                sender_id UUID REFERENCES users(id) ON DELETE SET NULL,
                content TEXT,
                msg_type VARCHAR(20) DEFAULT 'text',
                media_url TEXT,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)

        # ── Task-spec columns not in main.py's original schema ──
        # groups.is_private, group_members.role, group_messages.is_deleted
        await conn.execute(
            "ALTER TABLE groups ADD COLUMN IF NOT EXISTS is_private BOOLEAN DEFAULT FALSE;"
        )
        await conn.execute(
            "ALTER TABLE group_members ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'member';"
        )
        await conn.execute(
            "ALTER TABLE group_messages ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;"
        )

        # ── Seed the 8 pre-made interest clubs (once, by topic) ──
        # Existence is checked by topic+is_club so re-running is a no-op and
        # we never duplicate a club topic even if main.py seeded some already.
        clubs: List[tuple] = [
            ("Gaming", "gaming",
             "Discuss games, find teammates, and share your epic wins."),
            ("Music", "music",
             "Share tracks, discover artists, and vibe with fellow music lovers."),
            ("Dating", "dating",
             "Flirt, confess crushes, and meet new people anonymously."),
            ("Venting", "venting",
             "Let it all out. A safe space to vent and be heard."),
            ("Movies", "movies",
             "Talk films, reviews, trailers, and what to watch next."),
            ("Tech", "tech",
             "Gadgets, coding, AI, and all things tech."),
            ("Art", "art",
             "Showcase creations, get feedback, and discuss all forms of art."),
            ("Fitness", "fitness",
             "Workouts, motivation, nutrition tips, and progress sharing."),
        ]
        for name, topic, description in clubs:
            await conn.execute(
                """
                INSERT INTO groups (name, topic, description, is_club, is_private)
                SELECT $1, $2, $3, TRUE, FALSE
                WHERE NOT EXISTS (
                    SELECT 1 FROM groups WHERE topic = $2 AND is_club = TRUE
                )
                """,
                name, topic, description,
            )
    print("✅ groups router ready (groups/group_members/group_messages + 8 clubs)")


# ─── Helpers ───────────────────────────────────────────────
def _parse_uuid(value: str, label: str = "id") -> uuid.UUID:
    try:
        return uuid.UUID(value)
    except (ValueError, AttributeError):
        raise HTTPException(400, f"Invalid {label}")


def _fmt_user(row: asyncpg.Record) -> dict:
    return {
        "id": str(row["sender_id"]) if row.get("sender_id") else None,
        "username": row.get("username") or "Anonymous",
        "display_name": row.get("display_name") or "Anonymous",
        "avatar_url": row.get("avatar_url"),
    }


# ─── Request models ─────────────────────────────────────────
class CreateGroupReq(BaseModel):
    name: str = Field(..., min_length=2, max_length=60)
    description: Optional[str] = Field("", max_length=500)
    topic: str = Field(..., min_length=2, max_length=40)
    is_private: bool = False


class GroupMessageReq(BaseModel):
    content: Optional[str] = Field(None, max_length=4000)
    msg_type: str = Field("text", pattern="^(text|image|gif|voice|system)$")
    media_url: Optional[str] = None


# ─── 8. GET /api/groups/clubs  (defined BEFORE /{group_id}) ─
@router.get("/clubs")
async def list_clubs(user: dict = Depends(get_current_user)):
    """List the pre-made interest clubs."""
    pool = get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT g.*,
                   EXISTS(SELECT 1 FROM group_members m
                          WHERE m.group_id = g.id AND m.user_id = $1) AS is_member
            FROM groups g
            WHERE g.is_club = TRUE
            ORDER BY g.name ASC
            """,
            user["id"],
        )
    return [{
        "id": str(r["id"]),
        "name": r["name"],
        "topic": r["topic"],
        "description": r["description"],
        "is_club": r["is_club"],
        "is_private": r["is_private"],
        "members_count": r["members_count"],
        "is_member": r["is_member"],
        "created_at": r["created_at"].isoformat(),
    } for r in rows]


# ─── 1. POST /api/groups — create a group ──────────────────
@router.post("")
async def create_group(req: CreateGroupReq, user: dict = Depends(get_current_user)):
    pool = get_pool()
    gid = uuid.uuid4()
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO groups (id, name, topic, description, is_club,
                                 is_private, owner_id, members_count)
            VALUES ($1, $2, $3, $4, FALSE, $5, $6, 1)
            """,
            gid, req.name, req.topic.lower(), req.description or "",
            req.is_private, user["id"],
        )
        # Owner joins as admin
        await conn.execute(
            "INSERT INTO group_members (group_id, user_id, role) VALUES ($1, $2, 'admin')",
            gid, user["id"],
        )
    return {"id": str(gid), "ok": True}


# ─── 2. GET /api/groups — list groups (filter + search) ─────
@router.get("")
async def list_groups(
    topic: Optional[str] = Query(None, max_length=40),
    search: Optional[str] = Query(None, max_length=60),
    mine: bool = Query(False),
    clubs_only: bool = Query(False),
    limit: int = Query(50, ge=1, le=100),
    user: dict = Depends(get_current_user),
):
    pool = get_pool()
    where: List[str] = ["1=1"]
    params: list = []
    pidx = 1
    if topic:
        where.append(f"g.topic = ${pidx}")
        params.append(topic.lower())
        pidx += 1
    if clubs_only:
        where.append("g.is_club = TRUE")
    if mine:
        where.append(
            f"EXISTS(SELECT 1 FROM group_members m "
            f"WHERE m.group_id = g.id AND m.user_id = ${pidx})"
        )
        params.append(user["id"])
        pidx += 1
    if search:
        where.append(f"(g.name ILIKE ${pidx} OR g.description ILIKE ${pidx})")
        params.append(f"%{search}%")
        pidx += 1
    # is_member flag for the caller
    params.append(user["id"])
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            f"""
            SELECT g.*,
                   EXISTS(SELECT 1 FROM group_members m
                          WHERE m.group_id = g.id AND m.user_id = ${pidx}) AS is_member
            FROM groups g
            WHERE {' AND '.join(where)}
            ORDER BY g.is_club DESC, g.members_count DESC, g.created_at DESC
            LIMIT {limit}
            """,
            *params,
        )
    return [{
        "id": str(r["id"]),
        "name": r["name"],
        "topic": r["topic"],
        "description": r["description"],
        "is_club": r["is_club"],
        "is_private": r["is_private"],
        "members_count": r["members_count"],
        "is_member": r["is_member"],
        "created_at": r["created_at"].isoformat(),
    } for r in rows]


# ─── 3. GET /api/groups/{group_id} — details + member count ─
@router.get("/{group_id}")
async def group_details(group_id: str, user: dict = Depends(get_current_user)):
    gid = _parse_uuid(group_id, "group_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            SELECT g.*,
                   (SELECT COUNT(*) FROM group_members m
                    WHERE m.group_id = g.id) AS member_count,
                   EXISTS(SELECT 1 FROM group_members m
                          WHERE m.group_id = g.id AND m.user_id = $2) AS is_member
            FROM groups g
            WHERE g.id = $1
            """,
            gid, user["id"],
        )
        if not row:
            raise HTTPException(404, "Group not found")
    return {
        "id": str(row["id"]),
        "name": row["name"],
        "topic": row["topic"],
        "description": row["description"],
        "is_club": row["is_club"],
        "is_private": row["is_private"],
        "owner_id": str(row["owner_id"]) if row.get("owner_id") else None,
        "members_count": row["members_count"],
        "member_count": row["member_count"],
        "is_member": row["is_member"],
        "created_at": row["created_at"].isoformat(),
    }


# ─── 4. POST /api/groups/{group_id}/join — join a group ────
@router.post("/{group_id}/join")
async def join_group(group_id: str, user: dict = Depends(get_current_user)):
    gid = _parse_uuid(group_id, "group_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        exists = await conn.fetchval("SELECT 1 FROM groups WHERE id = $1", gid)
        if not exists:
            raise HTTPException(404, "Group not found")
        inserted = await conn.fetchval(
            """
            INSERT INTO group_members (group_id, user_id, role)
            VALUES ($1, $2, 'member')
            ON CONFLICT (group_id, user_id) DO NOTHING RETURNING 1
            """,
            gid, user["id"],
        )
        if inserted:
            await conn.execute(
                "UPDATE groups SET members_count = members_count + 1 WHERE id = $1",
                gid,
            )
            return {"ok": True, "joined": True}
    return {"ok": True, "joined": False, "detail": "Already a member"}


# ─── 5. DELETE /api/groups/{group_id}/join — leave a group ─
@router.delete("/{group_id}/join")
async def leave_group(group_id: str, user: dict = Depends(get_current_user)):
    gid = _parse_uuid(group_id, "group_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        deleted = await conn.fetchval(
            "DELETE FROM group_members WHERE group_id = $1 AND user_id = $2 RETURNING 1",
            gid, user["id"],
        )
        if deleted:
            await conn.execute(
                "UPDATE groups SET members_count = GREATEST(members_count - 1, 0) "
                "WHERE id = $1",
                gid,
            )
            return {"ok": True, "left": True}
    return {"ok": True, "left": False, "detail": "Not a member"}


# ─── 6. GET /api/groups/{group_id}/chat — list messages ─────
@router.get("/{group_id}/chat")
async def list_group_messages(
    group_id: str,
    limit: int = Query(50, ge=1, le=200),
    before: Optional[str] = Query(None, description="ISO timestamp for pagination"),
    user: dict = Depends(get_current_user),
):
    gid = _parse_uuid(group_id, "group_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        member = await conn.fetchval(
            "SELECT 1 FROM group_members WHERE group_id = $1 AND user_id = $2",
            gid, user["id"],
        )
        if not member:
            raise HTTPException(403, "Join the group first")

        params: list = [gid]
        where_extra = ""
        if before:
            try:
                before_ts = datetime.fromisoformat(before)
            except ValueError:
                raise HTTPException(400, "Invalid 'before' timestamp")
            params.append(before_ts)
            where_extra = f" AND gm.created_at < ${len(params)}"

        rows = await conn.fetch(
            f"""
            SELECT gm.id, gm.group_id, gm.sender_id, gm.content, gm.msg_type,
                   gm.media_url, gm.created_at, gm.is_deleted,
                   u.username, u.display_name, u.avatar_url
            FROM group_messages gm
            LEFT JOIN users u ON gm.sender_id = u.id
            WHERE gm.group_id = $1{where_extra}
            ORDER BY gm.created_at DESC
            LIMIT {limit}
            """,
            *params,
        )
    # newest-first from DB → reverse for chronological display
    return [{
        "id": str(r["id"]),
        "group_id": str(r["group_id"]),
        "content": r["content"] if not r["is_deleted"] else None,
        "msg_type": r["msg_type"],
        "media_url": r["media_url"] if not r["is_deleted"] else None,
        "is_deleted": r["is_deleted"],
        "sender": _fmt_user(r),
        "created_at": r["created_at"].isoformat(),
    } for r in reversed(rows)]


# ─── 7. POST /api/groups/{group_id}/chat — send message ─────
@router.post("/{group_id}/chat")
async def send_group_message(
    group_id: str,
    req: GroupMessageReq,
    user: dict = Depends(get_current_user),
):
    gid = _parse_uuid(group_id, "group_id")
    if not req.content and not req.media_url:
        raise HTTPException(422, "Message must have content or media_url")

    pool = get_pool()
    mid = uuid.uuid4()
    async with pool.acquire() as conn:
        member = await conn.fetchval(
            "SELECT 1 FROM group_members WHERE group_id = $1 AND user_id = $2",
            gid, user["id"],
        )
        if not member:
            raise HTTPException(403, "Join the group first")

        row = await conn.fetchrow(
            """
            WITH ins AS (
                INSERT INTO group_messages (id, group_id, sender_id, content,
                                             msg_type, media_url)
                VALUES ($1, $2, $3, $4, $5, $6)
                RETURNING id, group_id, sender_id, content, msg_type, media_url,
                          created_at, is_deleted
            )
            SELECT ins.*, u.username, u.display_name, u.avatar_url
            FROM ins
            LEFT JOIN users u ON ins.sender_id = u.id
            """,
            mid, gid, user["id"], req.content, req.msg_type, req.media_url,
        )

        # Karma: +1 per message (mirrors main.py KARMA_RULES["message_sent"])
        await conn.execute(
            "UPDATE users SET karma = GREATEST(karma + 1, 0) WHERE id = $1",
            user["id"],
        )
        await conn.execute(
            "INSERT INTO karma_events (user_id, delta, reason, ref_id) "
            "VALUES ($1, 1, 'message_sent', $2)",
            user["id"], mid,
        )
    return {
        "id": str(row["id"]),
        "group_id": str(row["group_id"]),
        "content": row["content"],
        "msg_type": row["msg_type"],
        "media_url": row["media_url"],
        "is_deleted": row["is_deleted"],
        "sender": _fmt_user(row),
        "created_at": row["created_at"].isoformat(),
    }
