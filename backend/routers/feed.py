"""
Whispr Feed Router — Feed Tabs + Poll Posts + Story Posts
═══════════════════════════════════════════════════════════

Feed Tabs (GET /api/feed?tab=…)
  • hot         — trending posts from last 24h (score = upvotes×1.0 + replies×0.5)
  • global      — all posts, newest first (default)
  • local       — posts from users within caller's radius (Haversine, needs lat/lng)
  • confessions — posts where post_type='confession' OR mood='venting'

Trending (GET /api/feed/trending)
  • top-10 hashtags from the last 24h (post_tags table)

Poll Posts
  POST /api/posts/poll                  — create a poll post (question, options[], duration_hours)
  POST /api/posts/{post_id}/poll/vote  — vote on a poll option (by option_index)
  GET  /api/posts/{post_id}/poll       — get poll results (vote counts per option)

Stories (ephemeral, auto-expire after 24h via WHERE expires_at > NOW())
  POST   /api/stories           — create story (content/media, background)
  GET    /api/stories           — list active (non-expired) stories
  DELETE /api/stories/{story_id} — delete own story

Auth:  JWT Bearer (self-contained — same WHISPR_SECRET, user_id claim, 30-day expiry).
DB:    asyncpg pool injected from main.py via init_feed(pool).
       Does NOT import from main.py — avoids circular imports.

DB Migrations (idempotent, in init_feed):
  • polls table:        options JSONB, duration_hours INT (ALTER ADD COLUMN IF NOT EXISTS)
  • poll_votes table:   option_index INT, voted_at TIMESTAMPTZ (ALTER ADD COLUMN IF NOT EXISTS)
  • stories table:      bg_type VARCHAR, bg_value TEXT (ALTER ADD COLUMN IF NOT EXISTS)
  • posts table:        view_count INT DEFAULT 0 (ALTER ADD COLUMN IF NOT EXISTS)

Integration (in main.py)
─────────────────────────
    from routers.feed import router as feed_router, init_feed
    app.include_router(feed_router)
    # inside lifespan, AFTER init_db():
    await init_feed(db_pool)
"""
from __future__ import annotations

import json
import math
import os
import uuid
from datetime import datetime, timedelta
from typing import Optional, List

import asyncpg
import jwt  # PyJWT — matches main.py
from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field

router = APIRouter(tags=["feed"])

# ─── Config (mirrors main.py) ──────────────────────────────
SECRET_KEY = os.getenv("WHISPR_SECRET", "whispr-dev-secret-change-me")
ALGORITHM = "HS256"

# ─── Shared DB pool (injected from main.py via init_feed) ──
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


async def get_current_user(
    cred: HTTPAuthorizationCredentials = Depends(security),
) -> dict:
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


# ─── Init: idempotent migrations ───────────────────────────
async def init_feed(pool: asyncpg.Pool) -> None:
    """
    Idempotent schema setup for the feed feature.

    Uses CREATE TABLE IF NOT EXISTS + ALTER TABLE ADD COLUMN IF NOT EXISTS
    so it's safe to run against a database already migrated by main.py's
    init_db(). Adds the columns the task spec requires that main.py's
    original schema may omit (options JSONB, duration_hours, option_index,
    voted_at, bg_type, view_count).
    """
    global _pool
    _pool = pool
    async with pool.acquire() as conn:
        # ── posts.view_count ──
        await conn.execute(
            "ALTER TABLE posts ADD COLUMN IF NOT EXISTS view_count INT DEFAULT 0;"
        )

        # ── polls table (task schema: options JSONB, duration_hours) ──
        await conn.execute(
            """
            CREATE TABLE IF NOT EXISTS polls (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
                question TEXT NOT NULL,
                options JSONB DEFAULT '[]',
                duration_hours INT,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                expires_at TIMESTAMPTZ
            );
            """
        )
        # Add columns that main.py's original polls schema may not have
        await conn.execute(
            "ALTER TABLE polls ADD COLUMN IF NOT EXISTS options JSONB DEFAULT '[]';"
        )
        await conn.execute(
            "ALTER TABLE polls ADD COLUMN IF NOT EXISTS duration_hours INT;"
        )

        # ── poll_votes (task schema: option_index, voted_at) ──
        await conn.execute(
            """
            CREATE TABLE IF NOT EXISTS poll_votes (
                poll_id UUID REFERENCES polls(id) ON DELETE CASCADE,
                user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                option_index INT NOT NULL,
                voted_at TIMESTAMPTZ DEFAULT NOW(),
                PRIMARY KEY (poll_id, user_id)
            );
            """
        )
        # If table already exists from main.py (with option_id), add our columns
        await conn.execute(
            "ALTER TABLE poll_votes ADD COLUMN IF NOT EXISTS option_index INT;"
        )
        await conn.execute(
            "ALTER TABLE poll_votes ADD COLUMN IF NOT EXISTS voted_at TIMESTAMPTZ DEFAULT NOW();"
        )
        # Relax option_id NOT NULL so our INSERTs (without option_id) succeed
        await conn.execute(
            "ALTER TABLE poll_votes ALTER COLUMN option_id DROP NOT NULL;"
        )

        # ── stories table (task schema: bg_type, bg_value) ──
        await conn.execute(
            """
            CREATE TABLE IF NOT EXISTS stories (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                author_id UUID REFERENCES users(id) ON DELETE CASCADE,
                content TEXT,
                media_url TEXT,
                bg_type VARCHAR(20) DEFAULT 'none',
                bg_value TEXT,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '24 hours')
            );
            """
        )
        await conn.execute(
            "ALTER TABLE stories ADD COLUMN IF NOT EXISTS bg_type VARCHAR(20) DEFAULT 'none';"
        )
        await conn.execute(
            "ALTER TABLE stories ADD COLUMN IF NOT EXISTS bg_value TEXT;"
        )

        # ── Index for polls lookup by post_id ──
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_polls_post_id ON polls(post_id);"
        )

    print("✅ feed router ready (polls/poll_votes/stories + view_count)")


# ─── Helpers ───────────────────────────────────────────────
def _parse_uuid(value: str, label: str = "id") -> uuid.UUID:
    try:
        return uuid.UUID(value)
    except (ValueError, AttributeError):
        raise HTTPException(400, f"Invalid {label}")


EARTH_RADIUS_KM = 6371.0


def _haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Great-circle distance in km between two lat/lng points (Haversine)."""
    rlat1, rlng1, rlat2, rlng2 = map(math.radians, (lat1, lng1, lat2, lng2))
    dlat = rlat2 - rlat1
    dlng = rlng2 - rlng1
    a = (
        math.sin(dlat / 2) ** 2
        + math.cos(rlat1) * math.cos(rlat2) * math.sin(dlng / 2) ** 2
    )
    return 2 * EARTH_RADIUS_KM * math.asin(math.sqrt(a))


_VALID_TABS = {"hot", "global", "local", "confessions"}
_MOODS = {"Happy", "Lonely", "Sad", "Angry", "Excited", "Anxious"}


def _fmt_post(r: asyncpg.Record, user_id) -> dict:
    """Format a post row into the standard API response dict.

    Matches the response format of main.py's GET /api/posts so the
    frontend can consume both endpoints interchangeably.
    """
    return {
        "id": str(r["id"]),
        "content": r["content"],
        "media_url": r.get("media_url"),
        "media_type": r.get("media_type") or "none",
        "is_once_view": r["is_once_view"],
        "upvotes": r["vote_count"],
        "replies_count": r["replies_count"],
        "is_edited": r["is_edited"],
        "user_upvoted": r["user_upvoted"],
        "view_count": r.get("view_count") or 0,
        "bg_type": r.get("bg_type") or "none",
        "bg_value": r.get("bg_value"),
        "post_type": r.get("post_type") or "anonymous",
        "mood": r.get("mood"),
        "is_mine": str(r["author_id"]) == str(user_id),
        "author": {
            "id": str(r["author_id"]),
            "username": r["username"],
            "display_name": r["display_name"],
            "avatar_url": r["avatar_url"],
            "city": r.get("city"),
        },
        "created_at": r["created_at"].isoformat(),
    }


# ═══════════════════════════════════════════════════════════
# FEED TABS
# ═══════════════════════════════════════════════════════════

@router.get("/api/feed")
async def get_feed(
    tab: str = Query("global", description="hot | global | local | confessions"),
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=50),
    tag: Optional[str] = Query(None, description="filter by hashtag"),
    radius_km: int = Query(50, ge=1, le=500, description="radius for local tab"),
    user: dict = Depends(get_current_user),
):
    """Feed tabs.

    • **hot**         — posts from last 24h, sorted by score
      (score = upvotes × 1.0 + replies_count × 0.5)
    • **global**      — all posts, newest first (default)
    • **local**       — posts from users within the caller's radius
      (Haversine; requires caller to have lat/lng set)
    • **confessions** — posts where post_type='confession' OR mood='venting'
    """
    tab = tab.lower().strip()
    if tab not in _VALID_TABS:
        raise HTTPException(
            400,
            f"Invalid tab '{tab}'. Choose from: {', '.join(sorted(_VALID_TABS))}",
        )

    offset = (page - 1) * limit
    pool = get_pool()

    # ── Local tab: need caller's lat/lng ──
    my_lat = user.get("lat")
    my_lng = user.get("lng")
    if tab == "local":
        if my_lat is None or my_lng is None:
            raise HTTPException(
                400,
                "Set your location first (PUT /api/me/location).",
            )

    # ── Build dynamic SQL ──
    where: List[str] = ["p.is_deleted = FALSE"]
    joins = ""
    params: list = [user["id"]]
    pidx = 2

    if tag:
        joins += " JOIN post_tags pt ON p.id = pt.post_id"
        where.append(f"pt.tag = ${pidx}")
        params.append(tag.lower())
        pidx += 1

    if tab == "hot":
        where.append("p.created_at > NOW() - INTERVAL '24 hours'")
    elif tab == "confessions":
        where.append("(p.post_type = 'confession' OR p.mood = 'venting')")
    elif tab == "local":
        # Bounding-box pre-filter (1 deg ≈ 111 km, pad by margin)
        deg_pad = (radius_km / 111.0) + 0.05
        where.append("u.lat IS NOT NULL AND u.lng IS NOT NULL")
        where.append(f"u.lat BETWEEN ${pidx} AND ${pidx + 1}")
        params.extend([my_lat - deg_pad, my_lat + deg_pad])
        pidx += 2
        where.append(f"u.lng BETWEEN ${pidx} AND ${pidx + 1}")
        params.extend([my_lng - deg_pad, my_lng + deg_pad])
        pidx += 2

    # ── Ordering ──
    if tab == "hot":
        # score = upvotes × 1.0 + replies_count × 0.5, then recency
        order = (
            "ORDER BY (vote_count * 1.0 + p.replies_count * 0.5) DESC, "
            "p.created_at DESC"
        )
    else:
        order = "ORDER BY p.created_at DESC"

    where_sql = " AND ".join(where)
    params.extend([limit, offset])

    sql = f"""
        SELECT p.*, u.username, u.display_name, u.avatar_url, u.city,
               u.lat, u.lng,
               (SELECT COUNT(*) FROM upvotes WHERE post_id = p.id) AS vote_count,
               EXISTS(SELECT 1 FROM upvotes
                      WHERE post_id = p.id AND user_id = $1) AS user_upvoted
        FROM posts p
        JOIN users u ON p.author_id = u.id{joins}
        WHERE {where_sql}
        {order}
        LIMIT ${pidx} OFFSET ${pidx + 1}
    """

    async with pool.acquire() as conn:
        rows = await conn.fetch(sql, *params)

    # ── Local: precise Haversine post-filter ──
    if tab == "local":
        filtered = []
        for r in rows:
            dist = _haversine_km(my_lat, my_lng, r["lat"], r["lng"])
            if dist <= radius_km:
                filtered.append(r)
        rows = filtered[:limit]  # trim over-fetch back to requested limit

    return [_fmt_post(r, user["id"]) for r in rows]


# ─── Trending hashtags bar ─────────────────────────────────
@router.get("/api/feed/trending")
async def trending_tags(
    limit: int = Query(10, ge=1, le=30),
    user: dict = Depends(get_current_user),
):
    """Top trending hashtags from the last 24h (top 10 by default).

    Extracts tags from the post_tags table (populated when posts are
    created with tags). Counts occurrences, returns sorted by count desc.
    """
    pool = get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT pt.tag, COUNT(*) AS cnt
            FROM post_tags pt
            JOIN posts p ON p.id = pt.post_id
            WHERE p.is_deleted = FALSE
              AND p.created_at > NOW() - INTERVAL '24 hours'
            GROUP BY pt.tag
            ORDER BY cnt DESC, pt.tag ASC
            LIMIT $1
            """,
            limit,
        )
    return [{"tag": r["tag"], "count": r["cnt"]} for r in rows]


# ═══════════════════════════════════════════════════════════
# POLL POSTS
# ═══════════════════════════════════════════════════════════

class CreatePollPostReq(BaseModel):
    question: str = Field(..., min_length=1, max_length=500)
    options: List[str] = Field(..., min_items=2, max_items=6)
    duration_hours: int = Field(24, ge=1, le=168)
    tags: Optional[List[str]] = []
    bg_type: str = "none"
    bg_value: Optional[str] = None
    mood: Optional[str] = None


class PollVoteReq(BaseModel):
    option_index: int = Field(..., ge=0)


@router.post("/api/posts/poll")
async def create_poll_post(
    req: CreatePollPostReq,
    user: dict = Depends(get_current_user),
):
    """Create a poll post.

    Inserts a post (post_type='poll', content=question) and a polls row
    with options stored as a JSONB array. The poll auto-expires after
    duration_hours (default 24h, max 168h = 7 days).
    """
    # ── Validate + clean options ──
    cleaned: List[str] = []
    for opt in req.options:
        opt = opt.strip()
        if not opt:
            raise HTTPException(400, "Poll options cannot be empty")
        if len(opt) > 120:
            raise HTTPException(400, "Poll option too long (max 120 chars)")
        cleaned.append(opt)
    if len(cleaned) < 2:
        raise HTTPException(400, "At least 2 poll options required")

    # ── Validate mood / bg ──
    mood = req.mood if req.mood in _MOODS else None
    bg_type = req.bg_type if req.bg_type in ("none", "gradient", "color") else "none"
    bg_value = req.bg_value if bg_type != "none" else None

    post_id = uuid.uuid4()
    poll_id = uuid.uuid4()
    expires_at = datetime.utcnow() + timedelta(hours=req.duration_hours)

    pool = get_pool()
    async with pool.acquire() as conn:
        # Create the post (post_type='poll')
        await conn.execute(
            """
            INSERT INTO posts (id, author_id, content, post_type, mood,
                               bg_type, bg_value)
            VALUES ($1, $2, $3, 'poll', $4, $5, $6)
            """,
            post_id, user["id"], req.question, mood, bg_type, bg_value,
        )
        # Create the poll (options as JSONB)
        await conn.execute(
            """
            INSERT INTO polls (id, post_id, question, options,
                               duration_hours, expires_at)
            VALUES ($1, $2, $3, $4, $5, $6)
            """,
            poll_id, post_id, req.question, cleaned,
            req.duration_hours, expires_at,
        )
        # Tags (max 5, stored in post_tags)
        if req.tags:
            for tag in req.tags[:5]:
                t = tag.lower().strip()[:50]
                if t:
                    await conn.execute(
                        "INSERT INTO post_tags (post_id, tag) "
                        "VALUES ($1, $2) ON CONFLICT DO NOTHING",
                        post_id, t,
                    )

    return {
        "post_id": str(post_id),
        "poll_id": str(poll_id),
        "options": [
            {"index": i, "label": label} for i, label in enumerate(cleaned)
        ],
        "duration_hours": req.duration_hours,
        "expires_at": expires_at.isoformat(),
        "ok": True,
    }


@router.post("/api/posts/{post_id}/poll/vote")
async def vote_poll_post(
    post_id: str,
    req: PollVoteReq,
    user: dict = Depends(get_current_user),
):
    """Vote on a poll option by option_index.

    One vote per user per poll (PK: poll_id, user_id). Voting again
    changes the previous vote (ON CONFLICT DO UPDATE). Polls past
    their expires_at return 410 Gone.
    """
    pid = _parse_uuid(post_id, "post_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        poll = await conn.fetchrow(
            "SELECT * FROM polls WHERE post_id = $1", pid
        )
        if not poll:
            raise HTTPException(404, "Poll not found for this post")

        # ── Check expiration ──
        if poll["expires_at"]:
            exp = poll["expires_at"]
            if exp.tzinfo is not None:
                exp = exp.replace(tzinfo=None)
            if exp < datetime.utcnow():
                raise HTTPException(410, "Poll expired")

        # ── Validate option_index against options array ──
        options = poll.get("options")
        if isinstance(options, str):
            options = json.loads(options)
        if not isinstance(options, list):
            options = []
        if not options:
            raise HTTPException(400, "Poll has no options configured")
        if req.option_index < 0 or req.option_index >= len(options):
            raise HTTPException(
                400,
                f"Invalid option_index. Must be 0–{len(options) - 1}",
            )

        # ── Insert or update vote ──
        await conn.execute(
            """
            INSERT INTO poll_votes (poll_id, user_id, option_index, voted_at)
            VALUES ($1, $2, $3, NOW())
            ON CONFLICT (poll_id, user_id)
            DO UPDATE SET option_index = $3, voted_at = NOW()
            """,
            poll["id"], user["id"], req.option_index,
        )

    return {"ok": True, "option_index": req.option_index}


@router.get("/api/posts/{post_id}/poll")
async def get_poll_results(
    post_id: str,
    user: dict = Depends(get_current_user),
):
    """Get poll results: vote counts per option, total votes, and the
    caller's vote (if any). Also returns whether the poll has expired.
    """
    pid = _parse_uuid(post_id, "post_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        poll = await conn.fetchrow(
            "SELECT * FROM polls WHERE post_id = $1", pid
        )
        if not poll:
            raise HTTPException(404, "Poll not found for this post")

        # ── Get options (JSONB or fallback to poll_options table) ──
        options = poll.get("options")
        if isinstance(options, str):
            options = json.loads(options)
        if not isinstance(options, list) or not options:
            # Fallback: polls created via main.py's /api/polls endpoint
            # use a separate poll_options table
            opts = await conn.fetch(
                "SELECT label, position FROM poll_options "
                "WHERE poll_id = $1 ORDER BY position",
                poll["id"],
            )
            options = [o["label"] for o in opts]

        # ── Vote counts by option_index (our schema) ──
        vote_rows = await conn.fetch(
            """
            SELECT option_index, COUNT(*) AS cnt
            FROM poll_votes
            WHERE poll_id = $1 AND option_index IS NOT NULL
            GROUP BY option_index
            """,
            poll["id"],
        )
        vote_map = {r["option_index"]: r["cnt"] for r in vote_rows}

        # ── Fallback: option_id-based votes (main.py's schema) ──
        if not vote_map:
            opt_vote_rows = await conn.fetch(
                """
                SELECT o.position, COUNT(v.id) AS cnt
                FROM poll_options o
                LEFT JOIN poll_votes v ON v.option_id = o.id
                WHERE o.poll_id = $1
                GROUP BY o.position
                """,
                poll["id"],
            )
            vote_map = {r["position"]: r["cnt"] for r in opt_vote_rows}

        # ── Caller's vote ──
        my_vote = await conn.fetchval(
            "SELECT option_index FROM poll_votes "
            "WHERE poll_id = $1 AND user_id = $2",
            poll["id"], user["id"],
        )

    total = sum(vote_map.values()) or 0

    # ── Determine if expired ──
    is_expired = False
    if poll["expires_at"]:
        exp = poll["expires_at"]
        if exp.tzinfo is not None:
            exp = exp.replace(tzinfo=None)
        is_expired = exp < datetime.utcnow()

    return {
        "poll_id": str(poll["id"]),
        "post_id": str(poll["post_id"]),
        "question": poll["question"],
        "duration_hours": poll.get("duration_hours"),
        "expires_at": poll["expires_at"].isoformat() if poll.get("expires_at") else None,
        "is_expired": is_expired,
        "total_votes": total,
        "my_vote": my_vote if my_vote is not None else None,
        "options": [
            {
                "index": i,
                "label": label,
                "votes": vote_map.get(i, 0),
                "pct": round(vote_map.get(i, 0) * 100.0 / total, 1) if total else 0.0,
            }
            for i, label in enumerate(options)
        ],
    }


# ═══════════════════════════════════════════════════════════
# STORIES (ephemeral, auto-expire after 24h)
# ═══════════════════════════════════════════════════════════

class CreateStoryReq(BaseModel):
    content: Optional[str] = Field(None, max_length=5000)
    media_url: Optional[str] = None
    bg_type: str = "none"  # none | gradient | color
    bg_value: Optional[str] = None


@router.post("/api/stories")
async def create_story(
    req: CreateStoryReq,
    user: dict = Depends(get_current_user),
):
    """Create a story. Auto-expires after 24h — the stories table has
    DEFAULT expires_at = NOW() + INTERVAL '24 hours', and all read
    queries filter WHERE expires_at > NOW(). No background task needed.
    """
    if not req.content and not req.media_url:
        raise HTTPException(400, "Story needs content or media")

    bg_type = req.bg_type if req.bg_type in ("none", "gradient", "color") else "none"
    bg_value = req.bg_value if bg_type != "none" else None

    sid = uuid.uuid4()
    pool = get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO stories (id, author_id, content, media_url,
                                 bg_type, bg_value)
            VALUES ($1, $2, $3, $4, $5, $6)
            """,
            sid, user["id"], req.content, req.media_url, bg_type, bg_value,
        )
    return {"id": str(sid), "ok": True}


@router.get("/api/stories")
async def list_stories(user: dict = Depends(get_current_user)):
    """List active (non-expired) stories.

    Auto-expiry is handled by the SQL filter WHERE expires_at > NOW() —
    no background cleanup task needed. Blocked users (either direction)
    are excluded.
    """
    pool = get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT s.*, u.username, u.display_name, u.avatar_url
            FROM stories s
            JOIN users u ON s.author_id = u.id
            WHERE s.expires_at > NOW()
              AND NOT EXISTS (
                SELECT 1 FROM blocks b
                WHERE (b.blocker_id = $1 AND b.blocked_id = s.author_id)
                   OR (b.blocker_id = s.author_id AND b.blocked_id = $1)
              )
            ORDER BY s.created_at DESC
            """,
            user["id"],
        )
    return [
        {
            "id": str(r["id"]),
            "content": r["content"],
            "media_url": r["media_url"],
            "bg_type": r.get("bg_type") or "none",
            "bg_value": r.get("bg_value"),
            "is_mine": str(r["author_id"]) == str(user["id"]),
            "author": {
                "id": str(r["author_id"]),
                "username": r["username"],
                "display_name": r["display_name"],
                "avatar_url": r["avatar_url"],
            },
            "created_at": r["created_at"].isoformat(),
            "expires_at": r["expires_at"].isoformat(),
        }
        for r in rows
    ]


@router.delete("/api/stories/{story_id}")
async def delete_story(
    story_id: str,
    user: dict = Depends(get_current_user),
):
    """Delete your own story (hard delete). Returns 404 if the story
    doesn't exist or belongs to another user."""
    sid = _parse_uuid(story_id, "story_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        deleted = await conn.fetchval(
            "DELETE FROM stories WHERE id = $1 AND author_id = $2 RETURNING 1",
            sid,
            user["id"],
        )
        if not deleted:
            raise HTTPException(404, "Story not found or not yours")
    return {"ok": True}
