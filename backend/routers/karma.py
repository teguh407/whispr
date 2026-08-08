"""
Whispr Karma System Router
──────────────────────────
Standalone karma earn/lose engine + audit trail + public/private API.

This router is SELF-CONTAINED:
  • Owns its own asyncpg pool reference (injected via init_karma(pool)).
  • Has its own JWT auth helpers (identical semantics to main.py so tokens
    issued by main.py's /api/auth/* endpoints decode here too).
  • Duplicates the karma_level() helper from main.py (thresholds matched
    exactly) — done deliberately to avoid a circular import when running
    `python main.py` (main.py imports this router, so this router must NOT
    import main.py).

Service layer (callable from any other router or from main.py):
  await award_karma (user_id, amount, reason, ref_id=None)
  await deduct_karma(user_id, amount, reason, ref_id=None)
  await get_karma_log(user_id, limit=50, offset=0)
  await get_user_karma_summary(user_id)

Rule-triggered convenience wrappers (use the KARMA_RULES amounts):
  await karma_on_post_100_views(user_id, post_id)
  await karma_on_post_upvoted (user_id, post_id)
  await karma_on_comment_viewed(user_id, comment_id)
  await karma_on_post_reported (user_id, post_id)
  await karma_on_post_removed  (user_id, post_id)

Karma earn/lose rules (from product spec, mirrored in KARMA_RULES):
  +5  post reaches 100+ views        (post_100_views)
  +3  post is upvoted                (post_upvoted)
  +1  comment is viewed              (comment_viewed)
  -5  post is reported               (post_reported)
  -10 post is removed/deleted by mod (post_removed)

Level thresholds (match main.py's karma_level()):
  Newcomer  0–29
  Regular   30–149
  Trusted   150–499
  VIP       500+

DB:
  PostgreSQL whispr_db (user whispr / pass whispr123 / localhost).
  Users table: id UUID, username, karma INT  (owned by main.py's init_db).
  This router adds an idempotent karma_log audit table:
    karma_log (id UUID PK DEFAULT gen_random_uuid(),
               user_id UUID,
               amount INT,        -- signed delta (+earn / -lose)
               reason TEXT,
               ref_id   UUID,     -- optional: post/comment that triggered it
               created_at TIMESTAMPTZ DEFAULT NOW())

  NOTE: main.py already has a `karma_events` table (delta, reason, ref_id)
  used by its inline award_karma(). This router writes to `karma_log`
  (the spec-required table). Both tables can coexist; if you migrate
  main.py's inline award_karma() to call this router's service functions,
  point readers at `karma_log` and eventually drop `karma_events`.

JWT auth:
  PyJWT (import jwt), claim key "user_id" (NOT "sub"), 30-day expiry,
  SECRET from WHISPR_SECRET env var — identical to main.py.

Endpoints
---------
GET /api/karma           — current user's karma + level + recent transactions (auth)
GET /api/karma/log       — current user's full karma transaction history, paginated (auth)
GET /api/karma/{user_id} — any user's public karma + level (auth, public data)

Integration (in main.py)
------------------------
    from routers.karma import router as karma_router, init_karma
    app.include_router(karma_router)
    # inside lifespan, AFTER init_db():
    await init_karma(db_pool)

  ⚠ main.py already defines an inline `@app.get("/api/karma")` (lines ~805-818).
  REMOVE or comment out that inline endpoint before including this router to
  avoid a duplicate-path conflict. The router's /api/karma is a strict
  superset (returns level + recent transactions + pagination support).
"""
from __future__ import annotations

import os
import uuid
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Any

import asyncpg
import jwt  # PyJWT — matches main.py
from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field

# ─── Config (mirrors main.py) ──────────────────────────────
SECRET_KEY = os.getenv("WHISPR_SECRET", "whispr-dev-secret-change-me")
ALGORITHM = "HS256"

# ─── Shared DB pool (injected from main.py via init_karma) ─
_pool: Optional[asyncpg.Pool] = None
security = HTTPBearer()

router = APIRouter(prefix="/api/karma", tags=["karma"])


def get_pool() -> asyncpg.Pool:
    """Return the shared asyncpg pool; raise 503 if not initialized yet."""
    if _pool is None:
        raise HTTPException(status_code=503, detail="Karma DB pool not initialized")
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
            "SELECT id, username, display_name, karma, days_active FROM users "
            "WHERE id = $1 AND is_active = TRUE",
            uuid.UUID(data["user_id"]),
        )
    if not user:
        raise HTTPException(401, "User not found")
    return dict(user)


# ─── Level thresholds (match main.py's karma_level exactly) ─
def karma_level(karma: int) -> str:
    """Map a karma integer to a tier label. Mirrors main.py's karma_level()."""
    if karma >= 500:
        return "VIP"
    if karma >= 150:
        return "Trusted"
    if karma >= 30:
        return "Regular"
    return "Newcomer"


# ─── Karma earn/lose rules ─────────────────────────────────
# Signed amounts. Positive = earn, negative = lose.
# Mirrors main.py's KARMA_RULES (expanded with comment_viewed from the spec).
KARMA_RULES: Dict[str, int] = {
    # ── Earn ──
    "post_created": 1,      # +1  user creates a post
    "post_100_views": 5,    # +5  post reaches 100+ views
    "post_upvoted": 3,      # +3  post receives an upvote
    "comment_viewed": 1,    # +1  comment is viewed
    "message_sent": 1,      # +1  (bonus, kept for main.py parity)
    "call_completed": 2,    # +2  (bonus, kept for main.py parity)
    # ── Lose ──
    "post_reported": -5,    # -5  post is reported
    "post_removed": -10,    # -10 post is removed/deleted by mod
}


# ─── Init: idempotent schema setup ─────────────────────────
async def init_karma(pool: asyncpg.Pool) -> None:
    """
    Idempotent schema setup for the karma audit table.

    Creates `karma_log` if absent (spec-required table) and adds an
    optional `ref_id` column (not in the minimal spec schema but useful
    for linking karma events back to the post/comment that triggered them).
    Safe to run against a DB already migrated by main.py's init_db().
    """
    global _pool
    _pool = pool
    async with pool.acquire() as conn:
        # ── karma_log: audit trail of every earn/lose transaction ──
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS karma_log (
                id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id    UUID REFERENCES users(id) ON DELETE CASCADE,
                amount     INT NOT NULL,
                reason     TEXT NOT NULL,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)
        # ref_id: optional FK to the triggering post/comment (added idempotently)
        await conn.execute(
            "ALTER TABLE karma_log ADD COLUMN IF NOT EXISTS ref_id UUID;"
        )
        # Index for fast per-user history lookups
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_karma_log_user_created "
            "ON karma_log (user_id, created_at DESC);"
        )
    print("✅ Karma system ready (karma_log table)")


# ═══════════════════════════════════════════════════════════
#  SERVICE LAYER — callable from any router or from main.py
#  These functions acquire their own connection from the injected pool,
#  so callers don't need to manage connections.
#  For transactional use (caller already holds a conn), use the
#  *_with_conn variants exported at the bottom of this file.
# ═══════════════════════════════════════════════════════════

async def _apply_karma_with_conn(
    conn: asyncpg.Connection,
    user_id: uuid.UUID,
    amount: int,
    reason: str,
    ref_id: Optional[uuid.UUID] = None,
) -> int:
    """
    Core karma mutation (connection provided by caller).

    Updates users.karma (floored at 0 so it never goes negative) and
    inserts a row into karma_log. Returns the new karma value.
    """
    if amount == 0:
        # Nothing to do — don't log zero-delta events.
        return await conn.fetchval(
            "SELECT karma FROM users WHERE id = $1", user_id
        ) or 0

    # Atomically update + return the new value. GREATEST(...) floors at 0.
    new_karma = await conn.fetchval(
        "UPDATE users SET karma = GREATEST(karma + $1, 0) "
        "WHERE id = $2 RETURNING karma",
        amount,
        user_id,
    )
    if new_karma is None:
        # User doesn't exist — no-op, don't pollute the audit table.
        raise HTTPException(404, f"User {user_id} not found")

    await conn.execute(
        "INSERT INTO karma_log (user_id, amount, reason, ref_id) "
        "VALUES ($1, $2, $3, $4)",
        user_id,
        amount,
        reason,
        ref_id,
    )
    return new_karma


async def award_karma(
    user_id,
    amount: int,
    reason: str,
    ref_id=None,
) -> int:
    """
    Add `amount` karma to `user_id` and record the transaction.

    Args:
        user_id: UUID str or uuid.UUID
        amount:  positive int (the actual delta to add)
        reason:  short label, e.g. "post_upvoted"
        ref_id:  optional UUID of the post/comment that triggered this

    Returns the user's new karma total.
    Raises HTTPException(404) if the user doesn't exist.
    """
    uid = uuid.UUID(str(user_id))
    pool = get_pool()
    async with pool.acquire() as conn:
        return await _apply_karma_with_conn(conn, uid, abs(amount), reason, _to_uuid(ref_id))


async def deduct_karma(
    user_id,
    amount: int,
    reason: str,
    ref_id=None,
) -> int:
    """
    Remove `amount` karma from `user_id` (floored at 0) and record the transaction.

    Args:
        user_id: UUID str or uuid.UUID
        amount:  positive int (will be negated internally)
        reason:  short label, e.g. "post_reported"
        ref_id:  optional UUID of the post/comment that triggered this

    Returns the user's new karma total (never negative).
    Raises HTTPException(404) if the user doesn't exist.
    """
    uid = uuid.UUID(str(user_id))
    pool = get_pool()
    async with pool.acquire() as conn:
        # Negate so the audit log records the actual (negative) delta.
        return await _apply_karma_with_conn(conn, uid, -abs(amount), reason, _to_uuid(ref_id))


async def get_karma_log(
    user_id,
    limit: int = 50,
    offset: int = 0,
) -> List[Dict[str, Any]]:
    """
    Return the karma transaction history for `user_id` (newest first).

    Each entry: {id, amount, reason, ref_id, created_at}
    """
    uid = uuid.UUID(str(user_id))
    pool = get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """SELECT id, amount, reason, ref_id, created_at
               FROM karma_log
               WHERE user_id = $1
               ORDER BY created_at DESC
               LIMIT $2 OFFSET $3""",
            uid,
            limit,
            offset,
        )
    return [
        {
            "id": str(r["id"]),
            "amount": r["amount"],
            "reason": r["reason"],
            "ref_id": str(r["ref_id"]) if r["ref_id"] else None,
            "created_at": r["created_at"].isoformat() if r["created_at"] else None,
        }
        for r in rows
    ]


async def get_user_karma_summary(user_id) -> Dict[str, Any]:
    """
    Return {user_id, username, karma, level} for any user (public profile data).
    Returns None if the user doesn't exist.
    """
    uid = uuid.UUID(str(user_id))
    pool = get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT id, username, display_name, karma FROM users WHERE id = $1",
            uid,
        )
    if not row:
        return None
    return {
        "user_id": str(row["id"]),
        "username": row["username"],
        "display_name": row["display_name"],
        "karma": row["karma"],
        "level": karma_level(row["karma"]),
    }


# ─── Rule-triggered convenience wrappers ───────────────────
# These use KARMA_RULES so amounts are defined in one place.
# Other routers call these instead of hardcoding amounts.

async def karma_on_post_100_views(user_id, post_id=None):
    """+5 when a post reaches 100+ views."""
    return await award_karma(user_id, KARMA_RULES["post_100_views"], "post_100_views", post_id)

async def karma_on_post_upvoted(user_id, post_id=None):
    """+3 when a post is upvoted."""
    return await award_karma(user_id, KARMA_RULES["post_upvoted"], "post_upvoted", post_id)

async def karma_on_comment_viewed(user_id, comment_id=None):
    """+1 when a comment is viewed."""
    return await award_karma(user_id, KARMA_RULES["comment_viewed"], "comment_viewed", comment_id)

async def karma_on_post_reported(user_id, post_id=None):
    """-5 when a post is reported."""
    return await deduct_karma(user_id, abs(KARMA_RULES["post_reported"]), "post_reported", post_id)

async def karma_on_post_removed(user_id, post_id=None):
    """-10 when a post is removed/deleted by a moderator."""
    return await deduct_karma(user_id, abs(KARMA_RULES["post_removed"]), "post_removed", post_id)


# ─── Small utility ──────────────────────────────────────────
def _to_uuid(val) -> Optional[uuid.UUID]:
    """Coerce str/UUID/None → uuid.UUID or None."""
    if val is None:
        return None
    if isinstance(val, uuid.UUID):
        return val
    try:
        return uuid.UUID(str(val))
    except (ValueError, AttributeError):
        return None


# ═══════════════════════════════════════════════════════════
#  API ENDPOINTS
# ═══════════════════════════════════════════════════════════

# IMPORTANT: /log must be registered BEFORE /{user_id} so the literal
# path "/log" is matched before the parameterised path.

@router.get("")
async def my_karma(user: dict = Depends(get_current_user)):
    """
    GET /api/karma
    Current user's karma, level, and recent transactions (last 10).
    """
    recent = await get_karma_log(user["id"], limit=10, offset=0)
    return {
        "user_id": str(user["id"]),
        "username": user["username"],
        "karma": user["karma"],
        "level": karma_level(user["karma"]),
        "recent_transactions": recent,
    }


@router.get("/log")
async def my_karma_log(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    user: dict = Depends(get_current_user),
):
    """
    GET /api/karma/log
    Paginated karma transaction history for the current user (newest first).
    """
    items = await get_karma_log(user["id"], limit=limit, offset=offset)
    return {
        "user_id": str(user["id"]),
        "karma": user["karma"],
        "level": karma_level(user["karma"]),
        "limit": limit,
        "offset": offset,
        "transactions": items,
    }


@router.get("/{user_id}")
async def public_karma(
    user_id: str,
    user: dict = Depends(get_current_user),
):
    """
    GET /api/karma/{user_id}
    Public karma + level for any user (no transaction history exposed).
    Requires auth (the caller must be logged in) but returns public data
    for the requested user_id.
    """
    summary = await get_user_karma_summary(user_id)
    if summary is None:
        raise HTTPException(404, "User not found")
    return summary


# ─── Conn-based variants for transactional callers ──────────
# Callers that already hold an asyncpg connection (e.g. main.py's
# award_karma replacement) can use these to keep the karma update
# inside the caller's transaction. The public functions above are
# thin wrappers that acquire their own connection.

async def award_karma_with_conn(conn, user_id, amount: int, reason: str, ref_id=None) -> int:
    """award_karma but using a caller-provided connection (transactional)."""
    return await _apply_karma_with_conn(conn, uuid.UUID(str(user_id)), abs(amount), reason, _to_uuid(ref_id))

async def deduct_karma_with_conn(conn, user_id, amount: int, reason: str, ref_id=None) -> int:
    """deduct_karma but using a caller-provided connection (transactional)."""
    return await _apply_karma_with_conn(conn, uuid.UUID(str(user_id)), -abs(amount), reason, _to_uuid(ref_id))
