"""
Whispr Auto-Destruct Timer Router
──────────────────────────────────
Adds ephemeral (self-destructing) chat messages to Whispr. A message can be
given a TTL of 5, 10, or 30 seconds; once the TTL elapses the message is
soft-deleted (is_deleted = true) by a background sweeper that runs every 5
seconds.

Endpoints
---------
PATCH  /api/messages/{message_id}/ttl  — set a TTL (5/10/30s) on a message
GET    /api/messages/expired           — (debug) list recently auto-deleted msgs

Background task
--------------
A coroutine (_auto_destruct_loop) is started in init_auto_destruct() and runs
forever, sleeping 5s between sweeps. It soft-deletes every message whose
expires_at < NOW() AND is_deleted = false. The task handle is stored on the
module-level `_cleanup_task` and cancelled on shutdown via shutdown_auto_destruct().

WebSocket helper
-----------------
process_message_ttl(msg_dict) is a pure helper meant to be called by main.py's
/ws/chat/{token} handler right before broadcasting a new message:

    from routers.auto_destruct import process_message_ttl
    payload = process_message_ttl(payload)

It does two things:
  1. If the inbound msg carries ttl_seconds (or destruct_seconds), compute and
     attach expires_at = now() + ttl.
  2. If the msg already has an expires_at in the past, mark it as deleted
     (is_deleted = true) so clients hide it.

JWT auth mirrors main.py: WHISPR_SECRET env var, user_id claim, 30-day exp.
DB pool is injected from main.py via init_auto_destruct(pool) — no import of
main to avoid circular deps.

Integration (in main.py)
------------------------
    from routers.auto_destruct import (
        router as auto_destruct_router,
        init_auto_destruct,
        shutdown_auto_destruct,
        process_message_ttl,
    )
    app.include_router(auto_destruct_router)

    # in lifespan, AFTER init_db():
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        await init_db()
        await init_auto_destruct(db_pool)          # ← starts background sweeper
        yield
        await shutdown_auto_destruct()            # ← cancels background sweeper
        if db_pool:
            await db_pool.close()
"""
from __future__ import annotations

import asyncio
import os
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

import asyncpg
import jwt  # PyJWT — matches main.py
from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api/messages", tags=["auto-destruct"])

# ─── Config (mirrors main.py) ──────────────────────────────
SECRET_KEY = os.getenv("WHISPR_SECRET", "whispr-dev-secret-change-me")
ALGORITHM = "HS256"
ALLOWED_TTL_SECONDS = (5, 10, 30)
SWEEP_INTERVAL_SECONDS = 5

# ─── Shared DB pool (injected from main.py via init_auto_destruct) ─
_pool: Optional[asyncpg.Pool] = None
security = HTTPBearer()

# Handle to the background sweeper task so we can cancel it on shutdown.
_cleanup_task: Optional[asyncio.Task] = None


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


# ─── Init: idempotent migration + start background sweeper ──
async def init_auto_destruct(pool: asyncpg.Pool) -> None:
    """
    Idempotent schema setup + launch the auto-destruct background task.

    Adds two columns to the messages table (both IF NOT EXISTS, so this is
    safe to run alongside main.py's init_db() which may have already added
    the legacy `destruct_seconds` / `expires_at` columns):

      ttl_seconds INT          — NULL means no auto-destruct
      expires_at  TIMESTAMPTZ  — NULL means no expiry

    The legacy `destruct_seconds` column (added by main.py) is tolerated but
    not required; this router standardizes on `ttl_seconds`.
    """
    global _pool, _cleanup_task
    _pool = pool

    async with pool.acquire() as conn:
        # ── Task-spec columns (idempotent) ──
        await conn.execute(
            "ALTER TABLE messages ADD COLUMN IF NOT EXISTS ttl_seconds INT;"
        )
        await conn.execute(
            "ALTER TABLE messages ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;"
        )
        # Backfill: if a legacy row has destruct_seconds but no ttl_seconds,
        # copy it over so the sweeper picks it up. (One-time, idempotent.)
        await conn.execute(
            """
            UPDATE messages
               SET ttl_seconds = destruct_seconds,
                   expires_at = created_at + (destruct_seconds || ' seconds')::INTERVAL
             WHERE ttl_seconds IS NULL
               AND destruct_seconds IS NOT NULL
               AND destruct_seconds > 0
               AND expires_at IS NULL;
            """
        )

    # Start the background sweeper (idempotent — never start two).
    if _cleanup_task is None or _cleanup_task.done():
        _cleanup_task = asyncio.create_task(_auto_destruct_loop())
        print("✅ Auto-destruct background sweeper started (5s interval)")


async def shutdown_auto_destruct() -> None:
    """Cancel the background sweeper on app shutdown (graceful)."""
    global _cleanup_task
    if _cleanup_task is not None and not _cleanup_task.done():
        _cleanup_task.cancel()
        try:
            await _cleanup_task
        except asyncio.CancelledError:
            pass
        print("🛑 Auto-destruct background sweeper stopped")
    _cleanup_task = None


# ─── Background sweeper ────────────────────────────────────
async def _auto_destruct_loop() -> None:
    """
    Periodic cleanup loop. Every SWEEP_INTERVAL_SECONDS (5s), soft-delete
    (is_deleted = true) every message whose expires_at < NOW() and that is
    not already deleted. Designed to run forever via asyncio.create_task();
    cancelled on shutdown by shutdown_auto_destruct().
    """
    # Guard: never start work if the pool wasn't injected.
    if _pool is None:
        print("⚠️  Auto-destruct sweeper: pool not initialized, aborting loop")
        return
    while True:
        try:
            async with _pool.acquire() as conn:
                result = await conn.execute(
                    """
                    UPDATE messages
                       SET is_deleted = TRUE
                     WHERE expires_at IS NOT NULL
                       AND expires_at < NOW()
                       AND is_deleted = FALSE
                    """
                )
                # asyncpg returns 'UPDATE N' — extract count for optional logging.
                count = int(result.split()[-1]) if result else 0
                if count:
                    print(f"🗑  Auto-destruct: soft-deleted {count} expired message(s)")
        except asyncio.CancelledError:
            # Cooperative shutdown — re-raise so the task ends cleanly.
            raise
        except Exception as e:
            # Never let the sweeper die — just log and keep going.
            print(f"⚠️  Auto-destruct sweeper error: {e}")
        await asyncio.sleep(SWEEP_INTERVAL_SECONDS)


# ─── WebSocket helper (pure, no DB needed) ─────────────────
def process_message_ttl(msg_dict: Dict[str, Any]) -> Dict[str, Any]:
    """
    Pure helper for main.py's /ws/chat/{token} handler. Call it on the
    outbound message payload right before broadcasting:

        from routers.auto_destruct import process_message_ttl
        payload = process_message_ttl(payload)

    Behavior:
      1. If msg carries `ttl_seconds` (preferred) or legacy `destruct_seconds`
         and is > 0, compute expires_at = now(UTC) + ttl and attach it.
      2. If msg already has an `expires_at` that is in the past, set
         is_deleted = True (and clear content) so clients hide it.
      3. Always normalizes the ttl field name to `ttl_seconds`.

    This is intentionally side-effect free (no DB writes) — the background
    sweeper handles persistence of the soft-delete; this just makes the
    real-time WS payload consistent.
    """
    if not isinstance(msg_dict, dict):
        return msg_dict

    now = datetime.now(timezone.utc)

    # Normalize TTL field name (accept legacy destruct_seconds too).
    ttl = msg_dict.get("ttl_seconds")
    if ttl is None:
        ttl = msg_dict.get("destruct_seconds")
    try:
        ttl_int = int(ttl) if ttl is not None else 0
    except (TypeError, ValueError):
        ttl_int = 0

    if ttl_int and ttl_int > 0:
        msg_dict["ttl_seconds"] = ttl_int
        expires_at = now + timedelta(seconds=ttl_int)
        # Keep ISO-format string for JSON serialization over WS.
        msg_dict["expires_at"] = expires_at.isoformat()
    else:
        msg_dict.setdefault("ttl_seconds", None)
        msg_dict.setdefault("expires_at", None)

    # If the message already has an expires_at and it's past, mark deleted.
    raw_expires = msg_dict.get("expires_at")
    if raw_expires:
        try:
            # Handle both datetime objects and ISO strings; ignore naive tz
            # by assuming UTC if no tzinfo present.
            if isinstance(raw_expires, datetime):
                expires_dt = raw_expires
            else:
                expires_dt = datetime.fromisoformat(str(raw_expires))
            if expires_dt.tzinfo is None:
                expires_dt = expires_dt.replace(tzinfo=timezone.utc)
            if expires_dt < now:
                msg_dict["is_deleted"] = True
                msg_dict["content"] = None
        except (TypeError, ValueError):
            # Malformed expires_at — leave as-is, sweeper will handle it.
            pass

    return msg_dict


# ─── Schemas ───────────────────────────────────────────────
class TTLRequest(BaseModel):
    ttl_seconds: int = Field(..., description="Auto-destruct TTL in seconds (allowed: 5, 10, 30)")


class TTLResponse(BaseModel):
    id: str
    chat_id: Optional[str] = None
    sender_id: Optional[str] = None
    content: Optional[str] = None
    msg_type: Optional[str] = None
    media_url: Optional[str] = None
    is_once_view: Optional[bool] = None
    is_viewed: Optional[bool] = None
    is_deleted: bool
    ttl_seconds: Optional[int] = None
    expires_at: Optional[str] = None
    created_at: Optional[str] = None


def _row_to_response(row: asyncpg.Record) -> dict:
    """Format an asyncpg messages row into a JSON-serializable dict."""
    return {
        "id": str(row["id"]),
        "chat_id": str(row["chat_id"]) if row.get("chat_id") else None,
        "sender_id": str(row["sender_id"]) if row.get("sender_id") else None,
        "content": row["content"],
        "msg_type": row["msg_type"],
        "media_url": row["media_url"],
        "is_once_view": row["is_once_view"],
        "is_viewed": row["is_viewed"],
        "is_deleted": row["is_deleted"],
        "ttl_seconds": row["ttl_seconds"] if "ttl_seconds" in row.keys() else None,
        "expires_at": row["expires_at"].isoformat() if row.get("expires_at") else None,
        "created_at": row["created_at"].isoformat() if row.get("created_at") else None,
    }


# ─── Endpoints ─────────────────────────────────────────────
@router.patch("/{message_id}/ttl", response_model=TTLResponse)
async def set_message_ttl(
    message_id: str,
    req: TTLRequest,
    user: dict = Depends(get_current_user),
):
    """
    Set an auto-destruct TTL on an existing chat message.

    Allowed TTL values: 5, 10, or 30 seconds (enforced). The endpoint writes
    `ttl_seconds` and computes `expires_at = NOW() + ttl_seconds` atomically
    and returns the updated message. Setting ttl_seconds=0 via the DB is the
    way to clear a timer, but this endpoint only accepts the three allowed
    positive values.

    Only the message's sender may set its TTL.
    """
    if req.ttl_seconds not in ALLOWED_TTL_SECONDS:
        raise HTTPException(
            status_code=422,
            detail=f"ttl_seconds must be one of {list(ALLOWED_TTL_SECONDS)}",
        )

    try:
        mid = uuid.UUID(message_id)
    except ValueError:
        raise HTTPException(400, "Invalid message_id (must be UUID)")

    pool = get_pool()
    async with pool.acquire() as conn:
        # Fetch the message first to authorize (sender only) and read chat_id.
        row = await conn.fetchrow(
            "SELECT * FROM messages WHERE id = $1",
            mid,
        )
        if not row:
            raise HTTPException(404, "Message not found")

        if str(row["sender_id"]) != str(user["id"]):
            raise HTTPException(403, "Only the sender can set a TTL on this message")

        # Compute expires_at server-side to avoid client clock skew.
        expires_at = datetime.now(timezone.utc) + timedelta(seconds=req.ttl_seconds)

        await conn.execute(
            """
            UPDATE messages
               SET ttl_seconds = $1,
                   expires_at  = $2
             WHERE id = $3
            """,
            req.ttl_seconds,
            expires_at,
            mid,
        )

        # Re-fetch the updated row to return a consistent snapshot.
        updated = await conn.fetchrow("SELECT * FROM messages WHERE id = $1", mid)

    return _row_to_response(updated)


@router.get("/expired", response_model=list)
async def list_recently_expired(
    limit: int = 20,
    user: dict = Depends(get_current_user),
):
    """
    Debug/admin endpoint: list messages the background sweeper has soft-deleted
    (is_deleted = true) because their TTL elapsed. Useful for verifying the
    auto-destruct loop is working. Ordered by expires_at desc.
    """
    pool = get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT * FROM messages
             WHERE is_deleted = TRUE
               AND expires_at IS NOT NULL
               AND sender_id = $1
             ORDER BY expires_at DESC
             LIMIT $2
            """,
            user["id"],
            limit,
        )
    return [_row_to_response(r) for r in rows]
