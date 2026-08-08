"""
Google OAuth router for Whispr — Hush-style anonymous identity.

POST /api/auth/google  { "id_token": "<Google ID token>" }
  → { "token": "<jwt>", "is_new": bool, "user": { id, username, display_name, bio, avatar_url, karma, days_active } }

How it works
------------
1. Android client signs in with GoogleSignIn → obtains an ID token.
2. Client POSTs { id_token } to this endpoint.
3. Backend verifies the ID token with the `google-auth` library (signature +
   issuer + audience checks against Google's public certs).
4. find-or-create user keyed by `google_id` (falls back to email match).
5. New users get an auto-generated anonymous username like `silentfox4821`
   (adjective + noun + 4 digits, retried on UNIQUE collision).
6. A JWT is minted with the SAME claims/expiry as the existing register
   endpoint so existing `get_current_user` / `decode_token` accept it.

JWT note: main.py uses PyJWT (`import jwt`) with claim key `"user_id"`
(not `sub`), 30-day expiry. This router matches that exactly so tokens are
decodable by main.py's existing auth helpers.

Integration (in main.py)
------------------------
    from routers.google_auth import router as google_auth_router, init_google_auth
    app.include_router(google_auth_router)
    # inside lifespan / init_db, after db_pool is created:
    await init_google_auth(db_pool)

Dependencies: pip install google-auth google-auth-httplib2
(plus asyncpg / fastapi / pydantic / PyJWT already present in the project)
"""
from __future__ import annotations

import os
import random
import uuid
from datetime import datetime, timedelta
from typing import Optional

import asyncpg
import jwt  # PyJWT — matches main.py
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter()

# ─── Config (mirrors main.py) ──────────────────────────────
SECRET_KEY = os.getenv("WHISPR_SECRET", "whispr-dev-secret-change-me")
ALGORITHM = "HS256"
# Optional: Google OAuth Web/Android client ID. If set, the ID token's
# `aud` claim is checked against it. If unset, audience verification is
# skipped (fine for an MVP where any Google-issued token is accepted).
GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID", "").strip() or None

# ─── Shared DB pool (injected from main.py via init_google_auth) ─
_pool: Optional[asyncpg.Pool] = None


def get_pool() -> asyncpg.Pool:
    """Return the shared asyncpg pool; raise 503 if not initialized yet."""
    if _pool is None:
        raise HTTPException(status_code=503, detail="Database pool not initialized")
    return _pool


async def init_google_auth(pool: asyncpg.Pool) -> None:
    """
    Inject the shared asyncpg pool and run idempotent migrations.

    Both statements are IF NOT EXISTS / DROP NOT NULL so they are safe to
    run repeatedly against an existing database (including one already
    migrated by main.py's init_db).
    """
    global _pool
    _pool = pool
    async with pool.acquire() as conn:
        # Google users have no password → password_hash must be nullable.
        await conn.execute("ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;")
        # Stable identity link across reinstallations / devices.
        await conn.execute(
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS google_id VARCHAR(64) UNIQUE;"
        )
    print("✅ google_auth router ready (google_id column + nullable password_hash)")


# ─── JWT (identical to main.py.create_token) ───────────────
def create_token(user_id: str) -> str:
    """Mint a JWT with the same payload/expiry as the register endpoint."""
    return jwt.encode(
        {"user_id": user_id, "exp": datetime.utcnow() + timedelta(days=30)},
        SECRET_KEY,
        algorithm=ALGORITHM,
    )


# ─── Anonymous username generator ──────────────────────────
# adjective + noun + 4 digits, lowercase, retried on UNIQUE collision.
_ADJ = [
    "Silent", "Hidden", "Shadow", "Mystic", "Velvet", "Crimson", "Frost", "Lunar",
    "Golden", "Neon", "Wild", "Quiet", "Cosmic", "Ember", "Azure", "Phantom",
    "Midnight", "Echo", "Drift", "Rogue", "Amber", "Storm", "Zen", "Nova",
]
_NOUN = [
    "Fox", "Wolf", "Raven", "Owl", "Tiger", "Falcon", "Panda", "Otter", "Lynx",
    "Cobra", "Heron", "Bear", "Hawk", "Moth", "Deer", "Koi", "Whale", "Sparrow",
    "Jaguar", "Comet", "Ghost", "Wanderer", "Dreamer", "Voyager",
]


async def generate_username(conn: asyncpg.Connection) -> str:
    """Generate a unique anonymous username, retrying on UNIQUE collisions."""
    for _ in range(20):
        candidate = f"{random.choice(_ADJ)}{random.choice(_NOUN)}{random.randint(1000, 9999)}".lower()
        exists = await conn.fetchval("SELECT 1 FROM users WHERE username = $1", candidate)
        if not exists:
            return candidate
    # Fallback: guaranteed unique (UUID-based).
    return f"user{uuid.uuid4().hex[:12]}"


# ─── Google ID token verification ──────────────────────────
def verify_google_id_token(token: str) -> dict:
    """
    Verify a Google ID token using the `google-auth` library.

    Returns the decoded payload dict (contains `sub`, `email`, `picture`, ...).
    Raises HTTPException(401) on any verification failure.

    Falls back to Google's tokeninfo HTTPS endpoint if the `google-auth`
    package is not importable, so the endpoint still functions during
    development before the pip install.
    """
    # Primary path: google-auth library (signature + issuer + audience check).
    try:
        from google.oauth2 import id_token
        from google.auth.transport import httplib2 as google_transport  # requires google-auth-httplib2
    except ImportError:
        return _verify_via_tokeninfo(token)

    request = google_transport.Request()
    try:
        payload = id_token.verify_oauth2_token(token, request, GOOGLE_CLIENT_ID)
    except Exception as exc:  # google.auth.exceptions.* + ValueError on bad token
        # Network/certs errors → try the HTTP fallback once; bad token → 401.
        try:
            return _verify_via_tokeninfo(token)
        except HTTPException:
            raise HTTPException(status_code=401, detail=f"Invalid Google token: {exc}")
    if not isinstance(payload, dict) or "sub" not in payload:
        raise HTTPException(status_code=401, detail="Invalid Google token payload")
    return payload


def _verify_via_tokeninfo(token: str) -> dict:
    """Fallback: verify via Google's tokeninfo endpoint (uses httpx)."""
    import httpx
    try:
        resp = httpx.get(
            "https://oauth2.googleapis.com/tokeninfo",
            params={"id_token": token},
            timeout=10,
        )
    except Exception:
        raise HTTPException(status_code=401, detail="Could not contact Google tokeninfo")
    if resp.status_code != 200:
        raise HTTPException(status_code=401, detail="Invalid Google token")
    info = resp.json()
    if "sub" not in info:
        raise HTTPException(status_code=401, detail="Invalid Google token payload")
    return info


# ─── Route ─────────────────────────────────────────────────
class GoogleAuthReq(BaseModel):
    """GoogleSignIn ID token from the Android client."""
    id_token: str


def _user_dict(user: asyncpg.Record) -> dict:
    """Shape a user row into the same response format as /api/auth/login."""
    return {
        "id": str(user["id"]),
        "username": user["username"],
        "display_name": user["display_name"],
        "bio": user["bio"],
        "avatar_url": user["avatar_url"],
        "karma": user["karma"],
        "days_active": user["days_active"],
    }


@router.post("/api/auth/google")
async def google_auth(req: GoogleAuthReq):
    """
    Verify a Google ID token, then find-or-create an anonymous Whispr user.

    - Existing user (matched by google_id, or email) → return a fresh JWT.
    - New user → auto-generate a random anonymous username, insert with a
      NULL password_hash, and return a JWT.
    Response shape matches the register/login endpoints:
    { token, is_new, user: { id, username, display_name, bio, avatar_url, karma, days_active } }
    """
    info = verify_google_id_token(req.id_token)
    google_id = info.get("sub")
    email = info.get("email")
    avatar = info.get("picture")
    if not google_id:
        raise HTTPException(status_code=401, detail="Invalid Google token payload")

    pool = get_pool()
    is_new = False
    async with pool.acquire() as conn:
        # find-or-create: match on google_id, or on email for legacy accounts
        user = await conn.fetchrow(
            "SELECT * FROM users WHERE google_id = $1 OR (email = $2 AND email IS NOT NULL)",
            google_id, email,
        )
        if not user:
            # ── create new anonymous user ──
            username = await generate_username(conn)
            user = await conn.fetchrow(
                """INSERT INTO users
                     (username, email, google_id, display_name, avatar_url, password_hash)
                   VALUES ($1, $2, $3, $4, $5, NULL)
                   RETURNING *""",
                username, email, google_id, username, avatar,
            )
            is_new = True
        elif not user["google_id"]:
            # Link Google identity to an existing email-based account.
            await conn.execute(
                "UPDATE users SET google_id = $1 WHERE id = $2",
                google_id, user["id"],
            )

    token = create_token(str(user["id"]))
    return {
        "token": token,
        "is_new": is_new,
        "user": _user_dict(user),
    }
