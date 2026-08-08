"""
Whispr — Anonymous Chat MVP Backend
Features: Chat, Voice Note, Once-View Photo, Edit/Delete, 
          Shareable Links, Multiple Accounts, Block, GIF, Voice Call
"""
import os
import uuid
import hashlib
import secrets
import asyncio
from datetime import datetime, timedelta
from typing import Optional, List, Dict
from contextlib import asynccontextmanager

from fastapi import (
    FastAPI, Depends, HTTPException, status, Request,
    WebSocket, WebSocketDisconnect, UploadFile, File,
    Query, Form
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
import jwt
import bcrypt
import asyncpg
from PIL import Image
import io

# ─── Feature Routers ───────────────────────────────────────
# Only include routers for features NOT already implemented inline in main.py.
# Discovery, polls, stories, games, groups are already inline (tested & working).
from routers.google_auth import router as google_auth_router, init_google_auth
from routers.karma import router as karma_router, init_karma
from routers.auto_destruct import router as auto_destruct_router, init_auto_destruct, shutdown_auto_destruct

# ─── Config ────────────────────────────────────────────────
SECRET_KEY = os.getenv("WHISPR_SECRET", "whispr-dev-secret-change-me")
ALGORITHM = "HS256"
DB_DSN = os.getenv("DATABASE_URL", "postgresql://postgres:postgres@localhost/whispr_db")
UPLOAD_DIR = os.path.join(os.path.dirname(__file__), "uploads")
GIPHY_API_KEY = os.getenv("GIPHY_API_KEY", "")  # optional
MAX_ACCOUNTS = 3
EDIT_WINDOW_MINUTES = 1440  # 24h — generous edit window
MAX_UPLOAD_BYTES = 15 * 1024 * 1024  # 15 MB upload cap

# ─── Simple in-memory rate limiter (per-IP, per-bucket) ─────
# key: (bucket, ip) → list[timestamps]. Purged lazily.
_rate_buckets: Dict[tuple, list] = {}

def rate_limit(bucket: str, limit: int, window_sec: int):
    """Dependency factory: raise 429 if caller exceeds limit within window."""
    async def _check(request: Request):
        ip = request.client.host if request.client else "unknown"
        key = (bucket, ip)
        now = datetime.utcnow().timestamp()
        ts = _rate_buckets.setdefault(key, [])
        # drop expired
        _rate_buckets[key] = [t for t in ts if now - t < window_sec]
        if len(_rate_buckets[key]) >= limit:
            raise HTTPException(429, f"Rate limit: max {limit} per {window_sec}s")
        _rate_buckets[key].append(now)
    return _check

os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(os.path.join(UPLOAD_DIR, "voice"), exist_ok=True)
os.makedirs(os.path.join(UPLOAD_DIR, "photos"), exist_ok=True)
os.makedirs(os.path.join(UPLOAD_DIR, "avatars"), exist_ok=True)

# ─── DB Pool ───────────────────────────────────────────────
db_pool: Optional[asyncpg.Pool] = None

async def init_db():
    global db_pool
    db_pool = await asyncpg.create_pool(DB_DSN, min_size=2, max_size=10)
    async with db_pool.acquire() as conn:
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                username VARCHAR(30) UNIQUE NOT NULL,
                email VARCHAR(255),
                password_hash TEXT NOT NULL,
                display_name VARCHAR(50),
                avatar_url TEXT,
                bio TEXT DEFAULT '',
                karma INT DEFAULT 0,
                days_active INT DEFAULT 1,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                is_active BOOLEAN DEFAULT TRUE,
                active_account_id UUID NULL
            );
        """)
        # Add active_account_id column if upgrading from older schema
        await conn.execute("""
            ALTER TABLE users ADD COLUMN IF NOT EXISTS active_account_id UUID;
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS accounts (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                owner_id UUID REFERENCES users(id) ON DELETE CASCADE,
                account_name VARCHAR(30) NOT NULL,
                username VARCHAR(30) UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                avatar_url TEXT,
                is_active BOOLEAN DEFAULT TRUE,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS posts (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                author_id UUID REFERENCES users(id) ON DELETE SET NULL,
                content TEXT NOT NULL,
                media_url TEXT,
                media_type VARCHAR(20) DEFAULT 'none',
                is_once_view BOOLEAN DEFAULT FALSE,
                once_view_max INT DEFAULT 1,
                once_view_count INT DEFAULT 0,
                upvotes INT DEFAULT 0,
                replies_count INT DEFAULT 0,
                is_edited BOOLEAN DEFAULT FALSE,
                is_deleted BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                edited_at TIMESTAMPTZ
            );
        """)
        # Tier-1 background support (idempotent for existing DBs)
        await conn.execute("""
            ALTER TABLE posts ADD COLUMN IF NOT EXISTS bg_type VARCHAR(20) DEFAULT 'none';
            ALTER TABLE posts ADD COLUMN IF NOT EXISTS bg_value TEXT;
        """)
        # Post type + mood (idempotent for existing DBs)
        await conn.execute("""
            ALTER TABLE posts ADD COLUMN IF NOT EXISTS post_type VARCHAR(20) DEFAULT 'anonymous';
            ALTER TABLE posts ADD COLUMN IF NOT EXISTS mood VARCHAR(20);
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS post_tags (
                post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
                tag VARCHAR(50) NOT NULL,
                PRIMARY KEY (post_id, tag)
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS upvotes (
                user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                PRIMARY KEY (user_id, post_id)
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS chats (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user1_id UUID REFERENCES users(id) ON DELETE CASCADE,
                user2_id UUID REFERENCES users(id) ON DELETE SET NULL,
                link_code VARCHAR(20) UNIQUE,
                is_link_chat BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                last_message_at TIMESTAMPTZ
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                chat_id UUID REFERENCES chats(id) ON DELETE CASCADE,
                sender_id UUID REFERENCES users(id) ON DELETE SET NULL,
                content TEXT,
                msg_type VARCHAR(20) DEFAULT 'text',
                media_url TEXT,
                is_once_view BOOLEAN DEFAULT FALSE,
                is_viewed BOOLEAN DEFAULT FALSE,
                is_deleted BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS shareable_links (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                owner_id UUID REFERENCES users(id) ON DELETE CASCADE,
                code VARCHAR(20) UNIQUE NOT NULL,
                title VARCHAR(100) DEFAULT 'Anonymous',
                is_active BOOLEAN DEFAULT TRUE,
                messages_count INT DEFAULT 0,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS blocks (
                blocker_id UUID REFERENCES users(id) ON DELETE CASCADE,
                blocked_id UUID REFERENCES users(id) ON DELETE CASCADE,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                PRIMARY KEY (blocker_id, blocked_id)
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS voice_calls (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                caller_id UUID REFERENCES users(id) ON DELETE SET NULL,
                callee_id UUID REFERENCES users(id) ON DELETE SET NULL,
                chat_id UUID REFERENCES chats(id) ON DELETE SET NULL,
                status VARCHAR(20) DEFAULT 'ringing',
                started_at TIMESTAMPTZ,
                ended_at TIMESTAMPTZ,
                duration_seconds INT DEFAULT 0,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS once_view_photos (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
                viewer_id UUID REFERENCES users(id) ON DELETE SET NULL,
                viewed_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)
        # ── Google OAuth + anonymous identity (idempotent) ──
        await conn.execute("""
            ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
            ALTER TABLE users ADD COLUMN IF NOT EXISTS google_id VARCHAR(64) UNIQUE;
            ALTER TABLE users ADD COLUMN IF NOT EXISTS interests TEXT[] DEFAULT '{}';
            ALTER TABLE users ADD COLUMN IF NOT EXISTS lat DOUBLE PRECISION;
            ALTER TABLE users ADD COLUMN IF NOT EXISTS lng DOUBLE PRECISION;
            ALTER TABLE users ADD COLUMN IF NOT EXISTS city VARCHAR(80);
            ALTER TABLE users ADD COLUMN IF NOT EXISTS gender VARCHAR(20);
            ALTER TABLE users ADD COLUMN IF NOT EXISTS age INT;
            ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen TIMESTAMPTZ DEFAULT NOW();
        """)
        # ── Auto-destruct timer for chat messages ──
        await conn.execute("""
            ALTER TABLE messages ADD COLUMN IF NOT EXISTS destruct_seconds INT DEFAULT 0;
            ALTER TABLE messages ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
        """)
        # ── Link analytics: view counter ──
        await conn.execute("""
            ALTER TABLE shareable_links ADD COLUMN IF NOT EXISTS views_count INT DEFAULT 0;
        """)
        # ── Karma ledger (audit trail of earn/lose) ──
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS karma_events (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                delta INT NOT NULL,
                reason VARCHAR(50) NOT NULL,
                ref_id UUID,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)
        # ── Reports (drives karma loss) ──
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS reports (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                reporter_id UUID REFERENCES users(id) ON DELETE CASCADE,
                post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
                reason VARCHAR(200),
                created_at TIMESTAMPTZ DEFAULT NOW(),
                UNIQUE (reporter_id, post_id)
            );
        """)
        # ── Polls (Feed) ──
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS polls (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
                question TEXT NOT NULL,
                image_url TEXT,
                expires_at TIMESTAMPTZ,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
            CREATE TABLE IF NOT EXISTS poll_options (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                poll_id UUID REFERENCES polls(id) ON DELETE CASCADE,
                label TEXT NOT NULL,
                position INT DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS poll_votes (
                poll_id UUID REFERENCES polls(id) ON DELETE CASCADE,
                option_id UUID REFERENCES poll_options(id) ON DELETE CASCADE,
                user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                PRIMARY KEY (poll_id, user_id)
            );
        """)
        # ── Stories (24h ephemeral) ──
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS stories (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                author_id UUID REFERENCES users(id) ON DELETE CASCADE,
                content TEXT,
                media_url TEXT,
                media_type VARCHAR(20) DEFAULT 'text',
                bg_value VARCHAR(40),
                views_count INT DEFAULT 0,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '24 hours')
            );
            CREATE TABLE IF NOT EXISTS story_views (
                story_id UUID REFERENCES stories(id) ON DELETE CASCADE,
                viewer_id UUID REFERENCES users(id) ON DELETE CASCADE,
                viewed_at TIMESTAMPTZ DEFAULT NOW(),
                PRIMARY KEY (story_id, viewer_id)
            );
        """)
        # ── Groups & interest clubs ──
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
            CREATE TABLE IF NOT EXISTS group_members (
                group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
                user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                joined_at TIMESTAMPTZ DEFAULT NOW(),
                PRIMARY KEY (group_id, user_id)
            );
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
        # ── Seed pre-made interest clubs (once) ──
        for club, topic in [
            ("Gaming Lounge", "gaming"), ("Music Room", "music"),
            ("Dating & Crushes", "dating"), ("Venting Space", "venting"),
            ("Late Night Talks", "lifestyle"), ("Confessions Circle", "confessions"),
        ]:
            await conn.execute("""
                INSERT INTO groups (name, topic, is_club, description)
                SELECT $1::varchar, $2, TRUE, $3
                WHERE NOT EXISTS (SELECT 1 FROM groups WHERE name=$1::varchar AND is_club=TRUE)
            """, club, topic, f"Anonymous {topic} club — hang out and chat.")
        print("✅ Database tables ready")

@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    await init_google_auth(db_pool)
    await init_karma(db_pool)
    await init_auto_destruct(db_pool)
    yield
    await shutdown_auto_destruct()
    if db_pool:
        await db_pool.close()

app = FastAPI(title="Whispr API", version="1.0.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(google_auth_router)
app.include_router(karma_router)
app.include_router(auto_destruct_router)

security = HTTPBearer()

# ─── Auth Helpers ──────────────────────────────────────────
def create_token(user_id: str) -> str:
    return jwt.encode(
        {"user_id": user_id, "exp": datetime.utcnow() + timedelta(days=30)},
        SECRET_KEY, algorithm=ALGORITHM
    )

def decode_token(token: str) -> dict:
    try:
        return jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
    except jwt.ExpiredSignatureError:
        raise HTTPException(401, "Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(401, "Invalid token")

async def get_current_user(cred: HTTPAuthorizationCredentials = Depends(security)):
    data = decode_token(cred.credentials)
    async with db_pool.acquire() as conn:
        user = await conn.fetchrow(
            "SELECT * FROM users WHERE id = $1 AND is_active = TRUE", 
            uuid.UUID(data["user_id"])
        )
        if not user:
            raise HTTPException(401, "User not found")
        user = dict(user)
        # If a sub-account is active, overlay its username/display_name/avatar
        if user.get("active_account_id"):
            acc = await conn.fetchrow(
                "SELECT * FROM accounts WHERE id = $1 AND owner_id = $2",
                user["active_account_id"], user["id"]
            )
            if acc:
                user["username"] = acc["username"]
                user["display_name"] = acc["account_name"]
                if acc.get("avatar_url"):
                    user["avatar_url"] = acc["avatar_url"]
    return user

async def check_blocked(conn, user_a, user_b) -> bool:
    row = await conn.fetchrow(
        "SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1)",
        user_a, user_b
    )
    return row is not None

# ─── Karma System ──────────────────────────────────────────
# Earn: +5 post hits 100 views, +3 post upvoted, +1 comment/msg viewed, +1 post created
# Lose: -5 post reported, -10 post removed
KARMA_RULES = {
    "post_created": 1,
    "post_upvoted": 3,
    "post_100_views": 5,
    "message_sent": 1,
    "call_completed": 2,
    "post_reported": -5,
    "post_removed": -10,
}

def karma_level(karma: int) -> str:
    if karma >= 500:
        return "VIP"
    if karma >= 150:
        return "Trusted"
    if karma >= 30:
        return "Regular"
    return "Newcomer"

async def award_karma(conn, user_id, reason: str, ref_id=None):
    """Apply a karma delta from KARMA_RULES and log it to karma_events."""
    delta = KARMA_RULES.get(reason, 0)
    if delta == 0:
        return
    await conn.execute("UPDATE users SET karma = GREATEST(karma + $1, 0) WHERE id = $2", delta, user_id)
    await conn.execute(
        "INSERT INTO karma_events (user_id, delta, reason, ref_id) VALUES ($1,$2,$3,$4)",
        user_id, delta, reason, ref_id
    )

# ─── Anonymous username generator ──────────────────────────
_ADJ = ["Silent", "Hidden", "Shadow", "Mystic", "Velvet", "Crimson", "Frost", "Lunar",
        "Golden", "Neon", "Wild", "Quiet", "Cosmic", "Ember", "Azure", "Phantom",
        "Midnight", "Echo", "Drift", "Rogue", "Amber", "Storm", "Zen", "Nova"]
_NOUN = ["Fox", "Wolf", "Raven", "Owl", "Tiger", "Falcon", "Panda", "Otter", "Lynx",
         "Cobra", "Heron", "Bear", "Hawk", "Moth", "Deer", "Koi", "Whale", "Sparrow",
         "Jaguar", "Comet", "Ghost", "Wanderer", "Dreamer", "Voyager"]

async def generate_username(conn) -> str:
    import random
    for _ in range(20):
        candidate = f"{random.choice(_ADJ)}{random.choice(_NOUN)}{random.randint(1000,9999)}".lower()
        exists = await conn.fetchval("SELECT 1 FROM users WHERE username = $1", candidate)
        if not exists:
            return candidate
    # Fallback: guaranteed unique
    return f"user{uuid.uuid4().hex[:12]}"

# ─── Auth Routes ───────────────────────────────────────────
class RegisterReq(BaseModel):
    username: str = Field(..., min_length=3, max_length=30)
    password: str = Field(..., min_length=6)
    email: Optional[str] = None
    display_name: Optional[str] = None

class LoginReq(BaseModel):
    username: Optional[str] = None
    email: Optional[str] = None
    password: str

@app.post("/api/auth/register", dependencies=[Depends(rate_limit("register", 5, 3600))])
async def register(req: RegisterReq):
    async with db_pool.acquire() as conn:
        exists = await conn.fetchval(
            "SELECT 1 FROM users WHERE username = $1", req.username.lower()
        )
        if exists:
            raise HTTPException(409, "Username taken")
        pw_hash = bcrypt.hashpw(req.password.encode(), bcrypt.gensalt()).decode()
        user = await conn.fetchrow(
            """INSERT INTO users (username, email, password_hash, display_name) 
               VALUES ($1, $2, $3, $4) RETURNING id, username, display_name, karma, days_active""",
            req.username.lower(), req.email, pw_hash,
            req.display_name or req.username
        )
    token = create_token(str(user["id"]))
    return {
        "token": token,
        "user": {
            "id": str(user["id"]),
            "username": user["username"],
            "display_name": user["display_name"],
            "karma": user["karma"],
            "days_active": user["days_active"]
        }
    }

@app.post("/api/auth/login", dependencies=[Depends(rate_limit("login", 10, 300))])
async def login(req: LoginReq):
    async with db_pool.acquire() as conn:
        # Accept either username or email in whichever field the client sends.
        # APK sends `email` field which may actually contain a username.
        ident = (req.username or req.email or "").strip().lower()
        if not ident:
            raise HTTPException(422, "Provide username or email")
        user = await conn.fetchrow(
            "SELECT * FROM users WHERE (email = $1 OR username = $1) AND is_active = TRUE",
            ident
        )
        if not user or not bcrypt.checkpw(req.password.encode(), user["password_hash"].encode()):
            raise HTTPException(401, "Invalid credentials")
    token = create_token(str(user["id"]))
    return {
        "token": token,
        "user": {
            "id": str(user["id"]),
            "username": user["username"],
            "display_name": user["display_name"],
            "bio": user["bio"],
            "avatar_url": user["avatar_url"],
            "karma": user["karma"],
            "days_active": user["days_active"]
        }
    }

# ─── Profile ───────────────────────────────────────────────
@app.get("/api/me")
async def get_me(user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        posts_count = await conn.fetchval(
            "SELECT COUNT(*) FROM posts WHERE author_id = $1 AND is_deleted = FALSE", user["id"]
        )
    return {
        "id": str(user["id"]),
        "username": user["username"],
        "display_name": user["display_name"],
        "bio": user["bio"],
        "avatar_url": user["avatar_url"],
        "karma": user["karma"],
        "karma_level": karma_level(user["karma"]),
        "days_active": user["days_active"],
        "posts_count": posts_count,
        "interests": list(user["interests"]) if user.get("interests") else [],
        "city": user.get("city"),
        "gender": user.get("gender"),
        "age": user.get("age"),
        "has_google": bool(user.get("google_id")),
    }


# ─── Account Deletion (Google Play requirement) ─────────────
class DeleteAccountReq(BaseModel):
    password: Optional[str] = None  # required for username/password accounts

@app.delete("/api/me")
async def delete_account(req: DeleteAccountReq, user=Depends(get_current_user)):
    """Permanently delete the caller's account and all associated data.

    Google Play User Data policy: apps with account creation must offer
    in-app account deletion. All FK references use ON DELETE CASCADE or
    SET NULL, so deleting the users row is sufficient.
    Password required if account has password_hash (Google-only may skip).
    """
    uid = user["id"]
    if user.get("password_hash"):
        if not req.password:
            raise HTTPException(400, "Password required to delete account")
        if not bcrypt.checkpw(req.password.encode(), user["password_hash"].encode()):
            raise HTTPException(403, "Incorrect password")

    async with db_pool.acquire() as conn:
        # All FKs cascade or SET NULL — deleting user row is enough
        await conn.execute("DELETE FROM users WHERE id = $1", uid)
    return {"deleted": True}


class UpdateProfileReq(BaseModel):
    display_name: Optional[str] = None
    bio: Optional[str] = None
    avatar_url: Optional[str] = None
    username: Optional[str] = Field(None, min_length=3, max_length=30)
    interests: Optional[List[str]] = None
    city: Optional[str] = None
    gender: Optional[str] = None
    age: Optional[int] = None

@app.put("/api/me")
async def update_profile(req: UpdateProfileReq, user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        # Username change: enforce uniqueness
        if req.username and req.username.lower() != user["username"]:
            uname = req.username.lower()
            taken = await conn.fetchval(
                "SELECT 1 FROM users WHERE username = $1 AND id <> $2", uname, user["id"]
            )
            if taken:
                raise HTTPException(409, "Username taken")
            await conn.execute("UPDATE users SET username = $1 WHERE id = $2", uname, user["id"])
        interests = req.interests[:5] if req.interests is not None else None
        await conn.execute(
            """UPDATE users SET
               display_name = COALESCE($1, display_name),
               bio = COALESCE($2, bio),
               avatar_url = COALESCE($3, avatar_url),
               interests = COALESCE($4, interests),
               city = COALESCE($5, city),
               gender = COALESCE($6, gender),
               age = COALESCE($7, age)
               WHERE id = $8""",
            req.display_name, req.bio, req.avatar_url,
            interests, req.city, req.gender, req.age, user["id"]
        )
    return {"ok": True}

# ─── Posts (Feature 4: Edit/Delete) ────────────────────────
class CreatePostReq(BaseModel):
    content: str = Field(..., min_length=1, max_length=5000)
    tags: Optional[List[str]] = []
    is_once_view: bool = False
    bg_type: str = "none"          # none | gradient
    bg_value: Optional[str] = None # gradient preset id, e.g. "sunset"
    post_type: str = "anonymous"   # anonymous|question|confession|poll|voice|photo|nearby
    mood: Optional[str] = None     # Happy|Lonely|Sad|Angry|Excited|Anxious

_POST_TYPES = {"anonymous", "question", "confession", "poll", "voice", "photo", "nearby"}
_MOODS = {"Happy", "Lonely", "Sad", "Angry", "Excited", "Anxious"}

@app.post("/api/posts", dependencies=[Depends(rate_limit("posts", 30, 3600))])
async def create_post(req: CreatePostReq, user=Depends(get_current_user)):
    post_id = uuid.uuid4()
    bg_type = req.bg_type if req.bg_type in ("none", "gradient") else "none"
    bg_value = req.bg_value if bg_type == "gradient" else None
    post_type = req.post_type if req.post_type in _POST_TYPES else "anonymous"
    mood = req.mood if req.mood in _MOODS else None
    async with db_pool.acquire() as conn:
        await conn.execute(
            """INSERT INTO posts (id, author_id, content, is_once_view, bg_type, bg_value, post_type, mood) 
               VALUES ($1, $2, $3, $4, $5, $6, $7, $8)""",
            post_id, user["id"], req.content, req.is_once_view, bg_type, bg_value, post_type, mood
        )
        if req.tags:
            for tag in req.tags[:5]:
                await conn.execute(
                    "INSERT INTO post_tags (post_id, tag) VALUES ($1, $2)",
                    post_id, tag.lower()[:50]
                )
        # Update karma (+1 post created, logged)
        await award_karma(conn, user["id"], "post_created", post_id)
    return {"id": str(post_id), "ok": True}

@app.get("/api/posts")
async def list_posts(
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=50),
    tag: Optional[str] = None,
    tab: str = Query("global"),  # hot | global | local | confessions
    user=Depends(get_current_user)
):
    offset = (page - 1) * limit
    tab = tab.lower()
    async with db_pool.acquire() as conn:
        # Base filters + ordering per tab
        where = ["p.is_deleted = FALSE"]
        joins = ""
        params = [user["id"]]
        pidx = 2
        if tag:
            joins += " JOIN post_tags pt ON p.id = pt.post_id"
            where.append(f"pt.tag = ${pidx}")
            params.append(tag.lower())
            pidx += 1
        if tab == "confessions":
            where.append("p.post_type = 'confession'")
        elif tab == "local" and user.get("city"):
            # local: same city as viewer
            where.append(f"u.city = ${pidx}")
            params.append(user["city"])
            pidx += 1
        # Ordering
        if tab == "hot":
            # score = upvotes*2 + replies, then recency — can't use alias in ORDER BY
            order = "ORDER BY ((SELECT COUNT(*) FROM upvotes WHERE post_id = p.id) * 2 + p.replies_count) DESC, p.created_at DESC"
        else:
            order = "ORDER BY p.created_at DESC"
        sql = f"""
            SELECT p.*, u.username, u.display_name, u.avatar_url, u.city,
               (SELECT COUNT(*) FROM upvotes WHERE post_id = p.id) as vote_count,
               EXISTS(SELECT 1 FROM upvotes WHERE post_id = p.id AND user_id = $1) as user_upvoted
            FROM posts p
            JOIN users u ON p.author_id = u.id{joins}
            WHERE {' AND '.join(where)}
            {order} LIMIT ${pidx} OFFSET ${pidx+1}
        """
        params.extend([limit, offset])
        rows = await conn.fetch(sql, *params)
    return [{
        "id": str(r["id"]),
        "content": r["content"],
        "media_url": r["media_url"],
        "media_type": r["media_type"],
        "is_once_view": r["is_once_view"],
        "upvotes": r["vote_count"],
        "replies_count": r["replies_count"],
        "is_edited": r["is_edited"],
        "user_upvoted": r["user_upvoted"],
        "bg_type": r["bg_type"] if "bg_type" in r else "none",
        "bg_value": r["bg_value"] if "bg_value" in r else None,
        "post_type": r["post_type"] if "post_type" in r else "anonymous",
        "mood": r["mood"] if "mood" in r else None,
        "is_mine": str(r["author_id"]) == str(user["id"]),
        "author": {"id": str(r["author_id"]), "username": r["username"], "display_name": r["display_name"], "avatar_url": r["avatar_url"], "city": r["city"]},
        "created_at": r["created_at"].isoformat()
    } for r in rows]

# ─── Trending hashtags bar ─────────────────────────────────
@app.get("/api/trending")
async def trending_tags(limit: int = Query(10, ge=1, le=30), user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        rows = await conn.fetch(
            """SELECT pt.tag, COUNT(*) as cnt
               FROM post_tags pt JOIN posts p ON p.id = pt.post_id
               WHERE p.is_deleted = FALSE AND p.created_at > NOW() - INTERVAL '7 days'
               GROUP BY pt.tag ORDER BY cnt DESC, pt.tag LIMIT $1""",
            limit
        )
    return [{"tag": r["tag"], "count": r["cnt"]} for r in rows]

# ─── Report a post (drives karma loss) ─────────────────────
class ReportReq(BaseModel):
    reason: Optional[str] = None

@app.post("/api/posts/{post_id}/report")
async def report_post(post_id: str, req: ReportReq, user=Depends(get_current_user)):
    pid = uuid.UUID(post_id)
    async with db_pool.acquire() as conn:
        post = await conn.fetchrow("SELECT author_id FROM posts WHERE id=$1 AND is_deleted=FALSE", pid)
        if not post:
            raise HTTPException(404, "Post not found")
        if str(post["author_id"]) == str(user["id"]):
            raise HTTPException(400, "Can't report your own post")
        dup = await conn.fetchval(
            "SELECT 1 FROM reports WHERE reporter_id=$1 AND post_id=$2", user["id"], pid
        )
        if dup:
            raise HTTPException(409, "Already reported")
        await conn.execute(
            "INSERT INTO reports (reporter_id, post_id, reason) VALUES ($1,$2,$3)",
            user["id"], pid, (req.reason or "")[:200]
        )
        # Author loses karma per report
        await award_karma(conn, post["author_id"], "post_reported", pid)
        # Auto-remove threshold: 5 reports → soft delete + extra penalty
        report_count = await conn.fetchval("SELECT COUNT(*) FROM reports WHERE post_id=$1", pid)
        if report_count >= 5:
            await conn.execute("UPDATE posts SET is_deleted = TRUE WHERE id = $1", pid)
            await award_karma(conn, post["author_id"], "post_removed", pid)
    return {"ok": True, "reports": report_count}

@app.put("/api/posts/{post_id}")
async def edit_post(post_id: str, content: str = Form(...), user=Depends(get_current_user)):
    pid = uuid.UUID(post_id)
    async with db_pool.acquire() as conn:
        post = await conn.fetchrow(
            "SELECT * FROM posts WHERE id = $1 AND author_id = $2 AND is_deleted = FALSE",
            pid, user["id"]
        )
        if not post:
            raise HTTPException(404, "Post not found or not yours")
        age = (datetime.utcnow() - post["created_at"].replace(tzinfo=None)).total_seconds() / 60
        if age > EDIT_WINDOW_MINUTES:
            raise HTTPException(400, f"Edit window expired ({EDIT_WINDOW_MINUTES} min)")
        await conn.execute(
            "UPDATE posts SET content = $1, is_edited = TRUE, edited_at = NOW() WHERE id = $2",
            content, pid
        )
    return {"ok": True}

@app.delete("/api/posts/{post_id}")
async def delete_post(post_id: str, user=Depends(get_current_user)):
    pid = uuid.UUID(post_id)
    async with db_pool.acquire() as conn:
        post = await conn.fetchrow(
            "SELECT * FROM posts WHERE id = $1 AND author_id = $2",
            pid, user["id"]
        )
        if not post:
            raise HTTPException(404, "Post not found or not yours")
        await conn.execute("UPDATE posts SET is_deleted = TRUE WHERE id = $1", pid)
    return {"ok": True}

# ─── Upvotes ───────────────────────────────────────────────
@app.post("/api/posts/{post_id}/upvote")
async def toggle_upvote(post_id: str, user=Depends(get_current_user)):
    pid = uuid.UUID(post_id)
    async with db_pool.acquire() as conn:
        author_id = await conn.fetchval("SELECT author_id FROM posts WHERE id = $1", pid)
        existing = await conn.fetchrow(
            "SELECT 1 FROM upvotes WHERE user_id = $1 AND post_id = $2",
            user["id"], pid
        )
        if existing:
            await conn.execute(
                "DELETE FROM upvotes WHERE user_id = $1 AND post_id = $2",
                user["id"], pid
            )
            await conn.execute("UPDATE posts SET upvotes = upvotes - 1 WHERE id = $1", pid)
            return {"upvoted": False}
        else:
            await conn.execute(
                "INSERT INTO upvotes (user_id, post_id) VALUES ($1, $2)",
                user["id"], pid
            )
            await conn.execute("UPDATE posts SET upvotes = upvotes + 1 WHERE id = $1", pid)
            # Award karma to author (not self-upvote)
            if author_id and str(author_id) != str(user["id"]):
                await award_karma(conn, author_id, "post_upvoted", pid)
            return {"upvoted": True}
# ─── Upload (Voice Note + Once-View Photo) ─────────────────
@app.post("/api/upload/voice", dependencies=[Depends(rate_limit("upload", 30, 3600))])
async def upload_voice(
    file: UploadFile = File(...),
    user=Depends(get_current_user)
):
    ext = file.filename.split(".")[-1] if "." in file.filename else "ogg"
    fname = f"{uuid.uuid4().hex}.{ext}"
    data = await file.read()
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, "File too large (max 15 MB)")
    fpath = os.path.join(UPLOAD_DIR, "voice", fname)
    with open(fpath, "wb") as f:
        f.write(data)
    return {"url": f"/uploads/voice/{fname}", "type": "voice"}

@app.post("/api/upload/photo", dependencies=[Depends(rate_limit("upload", 30, 3600))])
async def upload_photo(
    file: UploadFile = File(...),
    is_once_view: bool = Form(False),
    user=Depends(get_current_user)
):
    ext = file.filename.split(".")[-1] if "." in file.filename else "jpg"
    fname = f"{uuid.uuid4().hex}.{ext}"
    fpath = os.path.join(UPLOAD_DIR, "photos", fname)
    content = await file.read()
    if len(content) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, "File too large (max 15 MB)")
    with open(fpath, "wb") as f:
        f.write(content)
    
    # Generate invisible watermark hash
    watermark = hashlib.md5(f"{user['id']}{fname}".encode()).hexdigest()[:16]
    
    return {
        "url": f"/uploads/photos/{fname}",
        "type": "photo",
        "watermark_id": watermark,
        "is_once_view": is_once_view
    }

@app.post("/api/upload/document", dependencies=[Depends(rate_limit("upload", 30, 3600))])
async def upload_document(
    file: UploadFile = File(...),
    user=Depends(get_current_user)
):
    ext = file.filename.split(".")[-1] if "." in file.filename else "bin"
    fname = f"{uuid.uuid4().hex}.{ext}"
    fpath = os.path.join(UPLOAD_DIR, "documents", fname)
    os.makedirs(os.path.dirname(fpath), exist_ok=True)
    content = await file.read()
    if len(content) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, "File too large (max 15 MB)")
    with open(fpath, "wb") as f:
        f.write(content)
    return {
        "url": f"/uploads/documents/{fname}",
        "type": "document",
        "filename": file.filename,
        "size": len(content)
    }

# ─── Once-View Photo View Tracking ────────────────────────
@app.post("/api/posts/{post_id}/view-once")
async def view_once_photo(post_id: str, user=Depends(get_current_user)):
    pid = uuid.UUID(post_id)
    async with db_pool.acquire() as conn:
        post = await conn.fetchrow(
            "SELECT * FROM posts WHERE id = $1 AND is_once_view = TRUE AND is_deleted = FALSE",
            pid
        )
        if not post:
            raise HTTPException(404, "Post not found or not once-view")
        if post["author_id"] == user["id"]:
            raise HTTPException(400, "Can't view your own once-view")
        if post["once_view_count"] >= post["once_view_max"]:
            raise HTTPException(410, "Already viewed")
        already = await conn.fetchrow(
            "SELECT 1 FROM once_view_photos WHERE post_id = $1 AND viewer_id = $2",
            pid, user["id"]
        )
        if already:
            raise HTTPException(410, "Already viewed by you")
        
        await conn.execute(
            "INSERT INTO once_view_photos (post_id, viewer_id) VALUES ($1, $2)",
            pid, user["id"]
        )
        await conn.execute(
            "UPDATE posts SET once_view_count = once_view_count + 1 WHERE id = $1", pid
        )
        # Auto-delete after max views
        if post["once_view_count"] + 1 >= post["once_view_max"]:
            await conn.execute(
                "UPDATE posts SET is_deleted = TRUE WHERE id = $1", pid
            )
    
    return {"media_url": post["media_url"], "media_type": post["media_type"]}

# ─── Chat System (Feature 1: WebSocket) ───────────────────
class ConnectionManager:
    def __init__(self):
        self.active: Dict[str, WebSocket] = {}  # user_id -> ws
    
    async def connect(self, user_id: str, ws: WebSocket):
        await ws.accept()
        self.active[user_id] = ws
    
    def disconnect(self, user_id: str):
        self.active.pop(user_id, None)
    
    async def send_to(self, user_id: str, data: dict):
        ws = self.active.get(user_id)
        if ws:
            await ws.send_json(data)

manager = ConnectionManager()

class GroupManager:
    def __init__(self):
        self.rooms: Dict[str, Dict[str, WebSocket]] = {}  # room_key -> {user_id: ws}

    def add(self, key: str, user_id: str, ws: WebSocket):
        self.rooms.setdefault(key, {})[user_id] = ws

    def remove(self, key: str, user_id: str):
        if key in self.rooms:
            self.rooms[key].pop(user_id, None)
            if not self.rooms[key]:
                self.rooms.pop(key, None)

    async def broadcast(self, key: str, data: dict):
        for uid, ws in list(self.rooms.get(key, {}).items()):
            try:
                await ws.send_json(data)
            except Exception:
                self.rooms.get(key, {}).pop(uid, None)

group_manager = GroupManager()

@app.websocket("/ws/chat/{token}")
async def websocket_chat(ws: WebSocket, token: str):
    try:
        data = decode_token(token)
        user_id = data["user_id"]
    except:
        await ws.close(code=4001)
        return
    
    await manager.connect(user_id, ws)
    try:
        while True:
            msg = await ws.receive_json()
            chat_id = uuid.UUID(msg.get("chat_id", ""))
            content = msg.get("content", "")
            msg_type = msg.get("type", "text")
            media_url = msg.get("media_url")
            is_once_view = msg.get("is_once_view", False)
            
            async with db_pool.acquire() as conn:
                chat = await conn.fetchrow(
                    "SELECT * FROM chats WHERE id = $1 AND (user1_id = $2 OR user2_id = $2)",
                    chat_id, uuid.UUID(user_id)
                )
                if not chat:
                    await ws.send_json({"error": "Chat not found"})
                    continue
                
                # Determine recipient
                recipient_id = str(chat["user2_id"]) if str(chat["user1_id"]) == user_id else str(chat["user1_id"])
                
                # Check block
                if recipient_id and await check_blocked(conn, uuid.UUID(user_id), uuid.UUID(recipient_id)):
                    await ws.send_json({"error": "User blocked"})
                    continue
                
                msg_id = uuid.uuid4()
                destruct_seconds = msg.get("destruct_seconds")
                expires_at = None
                if destruct_seconds and int(destruct_seconds) > 0:
                    expires_at = datetime.utcnow() + timedelta(seconds=int(destruct_seconds))
                await conn.execute(
                    """INSERT INTO messages (id, chat_id, sender_id, content, msg_type, media_url, is_once_view, destruct_seconds, expires_at)
                       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)""",
                    msg_id, chat_id, uuid.UUID(user_id), content, msg_type, media_url, is_once_view,
                    int(destruct_seconds) if destruct_seconds else None, expires_at
                )
                await conn.execute(
                    "UPDATE chats SET last_message_at = NOW() WHERE id = $1", chat_id
                )
                await award_karma(conn, uuid.UUID(user_id), "message_sent", chat_id)
            
            payload = {
                "id": str(msg_id),
                "chat_id": str(chat_id),
                "sender_id": user_id,
                "content": content,
                "type": msg_type,
                "media_url": media_url,
                "is_once_view": is_once_view,
                "destruct_seconds": int(destruct_seconds) if destruct_seconds else None,
                "expires_at": expires_at.isoformat() if expires_at else None,
                "created_at": datetime.utcnow().isoformat()
            }
            
            # Send to recipient
            if recipient_id:
                await manager.send_to(recipient_id, payload)
            # Echo back to sender
            await ws.send_json(payload)
    
    except WebSocketDisconnect:
        manager.disconnect(user_id)

@app.get("/api/chats")
async def list_chats(user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        rows = await conn.fetch(
            """SELECT c.*, 
                   CASE WHEN c.user1_id = $1 THEN c.user2_id ELSE c.user1_id END as other_user_id,
                   m.content as last_message, m.msg_type as last_msg_type, m.created_at as last_msg_at
               FROM chats c
               LEFT JOIN messages m ON m.chat_id = c.id AND m.created_at = c.last_message_at
               WHERE c.user1_id = $1 OR c.user2_id = $1
               ORDER BY c.last_message_at DESC NULLS LAST""",
            user["id"]
        )
    results = []
    for r in rows:
        other = None
        if r["other_user_id"]:
            async with db_pool.acquire() as conn:
                other = await conn.fetchrow(
                    "SELECT id, username, display_name, avatar_url FROM users WHERE id = $1",
                    r["other_user_id"]
                )
        # Generate a unique anonymous alias per chat so the list isn't all "Anonymous"
        chat_suffix = str(r["id"])[-6:]
        results.append({
            "id": str(r["id"]),
            "other_user": {
                "id": str(other["id"]) if other else None,
                "username": other["username"] if other else f"stranger_{chat_suffix}",
                "display_name": other["display_name"] if other else f"Stranger #{chat_suffix}",
                "avatar_url": other["avatar_url"] if other else None
            } if other else {
                "id": None,
                "username": f"stranger_{chat_suffix}",
                "display_name": f"Stranger #{chat_suffix}",
                "avatar_url": None
            },
            "last_message": r["last_message"],
            "last_msg_type": r["last_msg_type"],
            "last_msg_at": r["last_msg_at"].isoformat() if r["last_msg_at"] else None,
            "is_link_chat": r["is_link_chat"],
            "created_at": r["created_at"].isoformat()
        })
    return results

@app.post("/api/chats")
async def create_chat(user=Depends(get_current_user)):
    chat_id = uuid.uuid4()
    async with db_pool.acquire() as conn:
        await conn.execute(
            "INSERT INTO chats (id, user1_id) VALUES ($1, $2)",
            chat_id, user["id"]
        )
    return {"chat_id": str(chat_id)}

@app.get("/api/chats/{chat_id}/messages")
async def get_messages(
    chat_id: str, 
    before: Optional[str] = None,
    limit: int = Query(50, ge=1, le=100),
    user=Depends(get_current_user)
):
    cid = uuid.UUID(chat_id)
    async with db_pool.acquire() as conn:
        chat = await conn.fetchrow(
            "SELECT * FROM chats WHERE id = $1 AND (user1_id = $2 OR user2_id = $2)",
            cid, user["id"]
        )
        if not chat:
            raise HTTPException(404, "Chat not found")
        
        if before:
            rows = await conn.fetch(
                """SELECT * FROM messages WHERE chat_id = $1 AND created_at < $2
                   ORDER BY created_at DESC LIMIT $3""",
                cid, datetime.fromisoformat(before), limit
            )
        else:
            rows = await conn.fetch(
                """SELECT * FROM messages WHERE chat_id = $1
                   ORDER BY created_at DESC LIMIT $2""",
                cid, limit
            )
    
    return [{
        "id": str(r["id"]),
        "sender_id": str(r["sender_id"]) if r["sender_id"] else None,
        "content": r["content"],
        "type": r["msg_type"],
        "media_url": r["media_url"],
        "is_once_view": r["is_once_view"],
        "is_viewed": r["is_viewed"],
        "is_deleted": r["is_deleted"],
        "created_at": r["created_at"].isoformat()
    } for r in reversed(rows)]

# ─── Shareable Link (Feature 5) ───────────────────────────
@app.post("/api/links")
async def create_shareable_link(title: str = "Anonymous", user=Depends(get_current_user)):
    code = secrets.token_urlsafe(8)
    link_id = uuid.uuid4()
    async with db_pool.acquire() as conn:
        await conn.execute(
            "INSERT INTO shareable_links (id, owner_id, code, title) VALUES ($1, $2, $3, $4)",
            link_id, user["id"], code, title
        )
    return {"code": code, "url": f"https://whispr.app/link/{code}", "title": title}

@app.get("/api/links")
async def list_my_links(user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT * FROM shareable_links WHERE owner_id = $1 AND is_active = TRUE ORDER BY created_at DESC",
            user["id"]
        )
    return [{"code": r["code"], "title": r["title"], "messages_count": r["messages_count"], 
             "created_at": r["created_at"].isoformat()} for r in rows]

@app.post("/api/links/{code}/message")
async def send_via_link(code: str, content: str = Form(...)):
    async with db_pool.acquire() as conn:
        link = await conn.fetchrow(
            "SELECT * FROM shareable_links WHERE code = $1 AND is_active = TRUE", code
        )
        if not link:
            raise HTTPException(404, "Link not found or expired")
        
        # Create or find chat for this link
        chat = await conn.fetchrow(
            "SELECT * FROM chats WHERE link_code = $1", code
        )
        if not chat:
            chat_id = uuid.uuid4()
            await conn.execute(
                "INSERT INTO chats (id, user1_id, link_code, is_link_chat) VALUES ($1, $2, $3, TRUE)",
                chat_id, link["owner_id"], code
            )
        else:
            chat_id = chat["id"]
        
        msg_id = uuid.uuid4()
        await conn.execute(
            """INSERT INTO messages (id, chat_id, content, msg_type) 
               VALUES ($1, $2, $3, 'text')""",
            msg_id, chat_id, content
        )
        await conn.execute(
            "UPDATE shareable_links SET messages_count = messages_count + 1 WHERE id = $1",
            link["id"]
        )
        await conn.execute(
            "UPDATE chats SET last_message_at = NOW() WHERE id = $1", chat_id
        )
        
        # Notify owner via WebSocket
        await manager.send_to(str(link["owner_id"]), {
            "type": "new_link_message",
            "link_code": code,
            "chat_id": str(chat_id),
            "content": content
        })
    
    return {"ok": True, "message": "Sent anonymously!"}

# ─── Multiple Accounts (Feature 6) ────────────────────────
@app.get("/api/accounts")
async def list_accounts(cred: HTTPAuthorizationCredentials = Depends(security)):
    """List main + sub-accounts. Uses raw token decode (not get_current_user)
    to avoid overlaying the active sub-account identity."""
    data = decode_token(cred.credentials)
    uid = uuid.UUID(data["user_id"])
    async with db_pool.acquire() as conn:
        # Read raw user (not overlaid by active sub-account)
        raw_user = await conn.fetchrow(
            "SELECT * FROM users WHERE id = $1 AND is_active = TRUE", uid
        )
        if not raw_user:
            raise HTTPException(401, "User not found")
        rows = await conn.fetch(
            "SELECT * FROM accounts WHERE owner_id = $1 ORDER BY created_at",
            uid
        )
        active_acc_id = raw_user.get("active_account_id")
        
        result = []
        # Main account (always show real username, not overlaid)
        result.append({
            "id": str(uid),
            "username": raw_user["username"],
            "display_name": raw_user["display_name"],
            "is_active": active_acc_id is None
        })
        # Sub-accounts
        for r in rows:
            result.append({
                "id": str(r["id"]),
                "username": r["username"],
                "display_name": r["account_name"],
                "is_active": bool(active_acc_id) and str(r["id"]) == str(active_acc_id)
            })
    return result

class CreateAccountReq(BaseModel):
    username: str = Field(..., min_length=3, max_length=30)
    password: str = Field(..., min_length=6)
    display_name: Optional[str] = None

@app.post("/api/accounts")
async def create_account(req: CreateAccountReq, cred: HTTPAuthorizationCredentials = Depends(security)):
    data = decode_token(cred.credentials)
    uid = uuid.UUID(data["user_id"])
    async with db_pool.acquire() as conn:
        raw_user = await conn.fetchrow(
            "SELECT * FROM users WHERE id = $1 AND is_active = TRUE", uid
        )
        if not raw_user:
            raise HTTPException(401, "User not found")
        raw_user = dict(raw_user)
        
        count = await conn.fetchval(
            "SELECT COUNT(*) FROM accounts WHERE owner_id = $1", uid
        )
        if count >= MAX_ACCOUNTS:
            raise HTTPException(400, f"Max {MAX_ACCOUNTS} accounts")
        exists = await conn.fetchval(
            "SELECT 1 FROM users WHERE username = $1", req.username.lower()
        )
        if exists:
            raise HTTPException(409, "Username taken")
        
        pw_hash = bcrypt.hashpw(req.password.encode(), bcrypt.gensalt()).decode()
        acc_id = uuid.uuid4()
        await conn.execute(
            """INSERT INTO accounts (id, owner_id, account_name, username, password_hash)
               VALUES ($1, $2, $3, $4, $5)""",
            acc_id, uid, req.display_name or req.username,
            req.username.lower(), pw_hash
        )
        # Set as active
        await conn.execute(
            "UPDATE users SET active_account_id = $1 WHERE id = $2",
            acc_id, uid
        )
        posts_count = await conn.fetchval(
            "SELECT COUNT(*) FROM posts WHERE author_id = $1 AND is_deleted = FALSE", uid
        )
    token = create_token(str(uid))
    return {
        "token": token,
        "user": {
            "id": str(uid),
            "username": req.username.lower(),
            "display_name": req.display_name or req.username,
            "avatar_url": None,
            "karma": raw_user["karma"],
            "posts_count": posts_count or 0
        }
    }

@app.post("/api/accounts/{acc_id}/switch")
async def switch_account(acc_id: str, cred: HTTPAuthorizationCredentials = Depends(security)):
    """Switch active account. Uses raw token decode to get un-overlaid user."""
    data = decode_token(cred.credentials)
    uid = uuid.UUID(data["user_id"])
    
    async with db_pool.acquire() as conn:
        # Read raw user (not overlaid)
        raw_user = await conn.fetchrow(
            "SELECT * FROM users WHERE id = $1 AND is_active = TRUE", uid
        )
        if not raw_user:
            raise HTTPException(401, "User not found")
        raw_user = dict(raw_user)
        posts_count = await conn.fetchval(
            "SELECT COUNT(*) FROM posts WHERE author_id = $1 AND is_deleted = FALSE", uid
        )
        
        # Switch to main account (acc_id == user_id)
        if acc_id == str(uid):
            await conn.execute(
                "UPDATE users SET active_account_id = NULL WHERE id = $1", uid
            )
            token = create_token(str(uid))
            return {
                "token": token,
                "user": {
                    "id": str(uid),
                    "username": raw_user["username"],
                    "display_name": raw_user["display_name"],
                    "avatar_url": raw_user.get("avatar_url"),
                    "karma": raw_user["karma"],
                    "posts_count": posts_count or 0
                }
            }
        
        # Switch to sub-account
        aid = uuid.UUID(acc_id)
        acc = await conn.fetchrow(
            "SELECT * FROM accounts WHERE id = $1 AND owner_id = $2",
            aid, uid
        )
        if not acc:
            raise HTTPException(404, "Account not found")
        # Set active account
        await conn.execute(
            "UPDATE users SET active_account_id = $1 WHERE id = $2", aid, uid
        )
    token = create_token(str(uid))
    return {
        "token": token,
        "user": {
            "id": str(uid),
            "username": acc["username"],
            "display_name": acc["account_name"],
            "avatar_url": acc.get("avatar_url"),
            "karma": raw_user["karma"],
            "posts_count": posts_count or 0
        }
    }

# ─── Block System (Feature 7) ─────────────────────────────
@app.post("/api/block/{target_user_id}")
async def block_user(target_user_id: str, user=Depends(get_current_user)):
    tid = uuid.UUID(target_user_id)
    if tid == user["id"]:
        raise HTTPException(400, "Can't block yourself")
    async with db_pool.acquire() as conn:
        exists = await conn.fetchval(
            "SELECT 1 FROM blocks WHERE blocker_id = $1 AND blocked_id = $2",
            user["id"], tid
        )
        if exists:
            raise HTTPException(409, "Already blocked")
        await conn.execute(
            "INSERT INTO blocks (blocker_id, blocked_id) VALUES ($1, $2)",
            user["id"], tid
        )
    return {"ok": True, "message": "User blocked"}

@app.delete("/api/block/{target_user_id}")
async def unblock_user(target_user_id: str, user=Depends(get_current_user)):
    tid = uuid.UUID(target_user_id)
    async with db_pool.acquire() as conn:
        await conn.execute(
            "DELETE FROM blocks WHERE blocker_id = $1 AND blocked_id = $2",
            user["id"], tid
        )
    return {"ok": True}

@app.get("/api/blocks")
async def list_blocks(user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        rows = await conn.fetch(
            """SELECT u.id, u.username, u.display_name, u.avatar_url 
               FROM blocks b JOIN users u ON b.blocked_id = u.id
               WHERE b.blocker_id = $1""",
            user["id"]
        )
    return [{"id": str(r["id"]), "username": r["username"], 
             "display_name": r["display_name"], "avatar_url": r["avatar_url"]} for r in rows]

# ─── GIF Integration (Feature 8) ──────────────────────────
@app.get("/api/gif/search")
async def search_gifs(q: str = Query(..., min_length=1)):
    if GIPHY_API_KEY:
        import httpx
        async with httpx.AsyncClient() as client:
            resp = await client.get(
                f"https://api.giphy.com/v1/gifs/search",
                params={"api_key": GIPHY_API_KEY, "q": q, "limit": 20, "rating": "g"}
            )
            data = resp.json()
            return [{"url": g["images"]["fixed_height"]["url"],
                     "thumbnail": g["images"]["fixed_height_small"]["url"],
                     "title": g["title"]} for g in data.get("data", [])]
    else:
        # Fallback: use Tenor (free, no key)
        import httpx
        async with httpx.AsyncClient() as client:
            resp = await client.get(
                "https://tenor.googleapis.com/v2/search",
                params={"q": q, "limit": 20, "key": "AIzaSyAyimkuYQYF_FXVALexPuGQctUWRURdCYQ", "media_filter": "tinygif"}
            )
            data = resp.json()
            return [{"url": g["media_formats"]["gif"]["url"],
                     "thumbnail": g["media_formats"]["tinygif"]["url"],
                     "title": g.get("title", "")} for g in data.get("results", [])]

# ─── Voice Call Signaling (Feature 9, no voice changer) ───
@app.post("/api/call/start")
async def start_call(target_user_id: str, user=Depends(get_current_user)):
    tid = uuid.UUID(target_user_id)
    if tid == user["id"]:
        raise HTTPException(400, "Can't call yourself")
    async with db_pool.acquire() as conn:
        blocked = await check_blocked(conn, user["id"], tid)
        if blocked:
            raise HTTPException(403, "User blocked")
        
        call_id = uuid.uuid4()
        await conn.execute(
            """INSERT INTO voice_calls (id, caller_id, callee_id, status) 
               VALUES ($1, $2, $3, 'ringing')""",
            call_id, user["id"], tid
        )
    
    # Notify callee
    await manager.send_to(target_user_id, {
        "type": "incoming_call",
        "call_id": str(call_id),
        "caller": {"id": str(user["id"]), "username": user["username"]}
    })
    
    return {"call_id": str(call_id), "status": "ringing"}

@app.post("/api/call/{call_id}/answer")
async def answer_call(call_id: str, user=Depends(get_current_user)):
    cid = uuid.UUID(call_id)
    async with db_pool.acquire() as conn:
        call = await conn.fetchrow(
            "SELECT * FROM voice_calls WHERE id = $1 AND callee_id = $2 AND status = 'ringing'",
            cid, user["id"]
        )
        if not call:
            raise HTTPException(404, "Call not found or already answered")
        await conn.execute(
            "UPDATE voice_calls SET status = 'active', started_at = NOW() WHERE id = $1", cid
        )
    
    # Notify caller
    await manager.send_to(str(call["caller_id"]), {
        "type": "call_answered",
        "call_id": str(call_id)
    })
    return {"status": "active"}

@app.post("/api/call/{call_id}/end")
async def end_call(call_id: str, user=Depends(get_current_user)):
    cid = uuid.UUID(call_id)
    async with db_pool.acquire() as conn:
        call = await conn.fetchrow(
            "SELECT * FROM voice_calls WHERE id = $1", cid
        )
        if not call:
            raise HTTPException(404, "Call not found")
        await conn.execute(
            """UPDATE voice_calls SET status = 'ended', ended_at = NOW(),
               duration_seconds = EXTRACT(EPOCH FROM (NOW() - started_at))::INT
               WHERE id = $1""", cid
        )
        # Karma for call duration
        duration = call.get("duration_seconds") or 0
    
    other_id = str(call["callee_id"]) if str(call["caller_id"]) == user["id"] else str(call["caller_id"])
    await manager.send_to(other_id, {
        "type": "call_ended",
        "call_id": str(call_id),
        "duration": duration
    })
    return {"ok": True}

# ─── WebSocket for real-time call signaling ────────────────
@app.websocket("/ws/call/{token}")
async def websocket_call(ws: WebSocket, token: str):
    try:
        data = decode_token(token)
        user_id = data["user_id"]
    except:
        await ws.close(code=4001)
        return
    
    await manager.connect(f"call_{user_id}", ws)
    try:
        while True:
            msg = await ws.receive_json()
            # WebRTC signaling: forward to target user
            target = msg.get("target_user_id")
            if target:
                await manager.send_to(f"call_{target}", {
                    **msg,
                    "from_user_id": user_id
                })
    except WebSocketDisconnect:
        manager.disconnect(f"call_{user_id}")

# ─── Serve uploads ─────────────────────────────────────────
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")

# ═══════════════════════════════════════════════════════════
# DISCOVERY / MATCHING (Feature: radius, interests, karma, gender/age)
# ═══════════════════════════════════════════════════════════
import math

def _haversine_km(lat1, lng1, lat2, lng2):
    R = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lng2 - lng1)
    a = math.sin(dphi/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dlmb/2)**2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))

class LocationReq(BaseModel):
    lat: float
    lng: float
    city: Optional[str] = None

@app.put("/api/me/location")
async def update_location(req: LocationReq, user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        await conn.execute(
            "UPDATE users SET lat=$1, lng=$2, city=COALESCE($3, city), last_seen=NOW() WHERE id=$4",
            req.lat, req.lng, req.city, user["id"]
        )
    return {"ok": True}

@app.get("/api/discover")
async def discover(
    radius_km: Optional[float] = Query(None, ge=1, le=20000),
    interests: Optional[str] = Query(None, description="comma-separated tags"),
    min_karma: int = Query(0, ge=0),
    gender: Optional[str] = None,
    min_age: Optional[int] = Query(None, ge=13),
    max_age: Optional[int] = Query(None, le=120),
    limit: int = Query(30, ge=1, le=100),
    user=Depends(get_current_user)
):
    """Find people to connect with. Filters: geo radius, shared interests,
    minimum karma, gender, age range. Blocked users excluded."""
    want_tags = [t.strip().lower() for t in interests.split(",")] if interests else []
    async with db_pool.acquire() as conn:
        where = ["u.id <> $1", "u.is_active = TRUE"]
        params = [user["id"]]
        pidx = 2
        if min_karma > 0:
            where.append(f"u.karma >= ${pidx}"); params.append(min_karma); pidx += 1
        if gender:
            where.append(f"u.gender = ${pidx}"); params.append(gender.lower()); pidx += 1
        if min_age is not None:
            where.append(f"u.age >= ${pidx}"); params.append(min_age); pidx += 1
        if max_age is not None:
            where.append(f"u.age <= ${pidx}"); params.append(max_age); pidx += 1
        if want_tags:
            where.append(f"u.interests && ${pidx}::text[]"); params.append(want_tags); pidx += 1
        # Exclude blocked (either direction)
        where.append(f"""NOT EXISTS (SELECT 1 FROM blocks b
            WHERE (b.blocker_id=$1 AND b.blocked_id=u.id)
               OR (b.blocker_id=u.id AND b.blocked_id=$1))""")
        sql = f"""
            SELECT u.id, u.username, u.display_name, u.avatar_url, u.bio,
                   u.karma, u.interests, u.city, u.gender, u.age, u.lat, u.lng, u.last_seen
            FROM users u
            WHERE {' AND '.join(where)}
            ORDER BY u.last_seen DESC NULLS LAST, u.karma DESC
            LIMIT ${pidx}
        """
        params.append(limit * 3)  # over-fetch for geo post-filter
        rows = await conn.fetch(sql, *params)

    me_lat, me_lng = user.get("lat"), user.get("lng")
    results = []
    for r in rows:
        dist = None
        if me_lat is not None and me_lng is not None and r["lat"] is not None and r["lng"] is not None:
            dist = round(_haversine_km(me_lat, me_lng, r["lat"], r["lng"]), 1)
            if radius_km is not None and dist > radius_km:
                continue
        elif radius_km is not None and me_lat is not None:
            # viewer has location but target doesn't → skip under strict radius
            continue
        shared = list(set(want_tags) & set(r["interests"] or [])) if want_tags else []
        results.append({
            "id": str(r["id"]),
            "username": r["username"],
            "display_name": r["display_name"],
            "avatar_url": r["avatar_url"],
            "bio": r["bio"],
            "karma": r["karma"],
            "karma_level": karma_level(r["karma"]),
            "interests": list(r["interests"] or []),
            "shared_interests": shared,
            "city": r["city"],
            "gender": r["gender"],
            "age": r["age"],
            "distance_km": dist,
        })
        if len(results) >= limit:
            break
    return results

# ═══════════════════════════════════════════════════════════
# POLLS (Feed)
# ═══════════════════════════════════════════════════════════
class CreatePollReq(BaseModel):
    question: str = Field(..., min_length=1, max_length=300)
    options: List[str] = Field(..., min_items=2, max_items=6)
    image_url: Optional[str] = None
    expires_hours: Optional[int] = Field(None, ge=1, le=168)
    tags: Optional[List[str]] = []

@app.post("/api/polls")
async def create_poll(req: CreatePollReq, user=Depends(get_current_user)):
    post_id = uuid.uuid4()
    poll_id = uuid.uuid4()
    expires = (datetime.utcnow() + timedelta(hours=req.expires_hours)) if req.expires_hours else None
    async with db_pool.acquire() as conn:
        await conn.execute(
            """INSERT INTO posts (id, author_id, content, post_type)
               VALUES ($1,$2,$3,'poll')""",
            post_id, user["id"], req.question
        )
        await conn.execute(
            "INSERT INTO polls (id, post_id, question, image_url, expires_at) VALUES ($1,$2,$3,$4,$5)",
            poll_id, post_id, req.question, req.image_url, expires
        )
        for i, label in enumerate(req.options):
            await conn.execute(
                "INSERT INTO poll_options (poll_id, label, position) VALUES ($1,$2,$3)",
                poll_id, label[:120], i
            )
        for tag in (req.tags or [])[:5]:
            await conn.execute("INSERT INTO post_tags (post_id, tag) VALUES ($1,$2) ON CONFLICT DO NOTHING",
                               post_id, tag.lower()[:50])
        await award_karma(conn, user["id"], "post_created", post_id)
    return {"poll_id": str(poll_id), "post_id": str(post_id), "ok": True}

async def _poll_payload(conn, poll_id, user_id):
    poll = await conn.fetchrow("SELECT * FROM polls WHERE id=$1", poll_id)
    if not poll:
        return None
    opts = await conn.fetch(
        """SELECT o.id, o.label, o.position,
           (SELECT COUNT(*) FROM poll_votes v WHERE v.option_id=o.id) as votes
           FROM poll_options o WHERE o.poll_id=$1 ORDER BY o.position""", poll_id
    )
    my_vote = await conn.fetchval(
        "SELECT option_id FROM poll_votes WHERE poll_id=$1 AND user_id=$2", poll_id, user_id
    )
    total = sum(o["votes"] for o in opts) or 0
    return {
        "poll_id": str(poll_id),
        "question": poll["question"],
        "image_url": poll["image_url"],
        "expires_at": poll["expires_at"].isoformat() if poll["expires_at"] else None,
        "total_votes": total,
        "my_vote": str(my_vote) if my_vote else None,
        "options": [{
            "id": str(o["id"]), "label": o["label"], "votes": o["votes"],
            "pct": round(o["votes"] * 100 / total, 1) if total else 0.0
        } for o in opts]
    }

@app.get("/api/polls/{poll_id}")
async def get_poll(poll_id: str, user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        payload = await _poll_payload(conn, uuid.UUID(poll_id), user["id"])
    if not payload:
        raise HTTPException(404, "Poll not found")
    return payload

@app.post("/api/polls/{poll_id}/vote")
async def vote_poll(poll_id: str, option_id: str = Form(...), user=Depends(get_current_user)):
    pid = uuid.UUID(poll_id)
    oid = uuid.UUID(option_id)
    async with db_pool.acquire() as conn:
        poll = await conn.fetchrow("SELECT * FROM polls WHERE id=$1", pid)
        if not poll:
            raise HTTPException(404, "Poll not found")
        if poll["expires_at"] and poll["expires_at"].replace(tzinfo=None) < datetime.utcnow():
            raise HTTPException(410, "Poll expired")
        valid = await conn.fetchval("SELECT 1 FROM poll_options WHERE id=$1 AND poll_id=$2", oid, pid)
        if not valid:
            raise HTTPException(400, "Invalid option")
        await conn.execute(
            """INSERT INTO poll_votes (poll_id, option_id, user_id) VALUES ($1,$2,$3)
               ON CONFLICT (poll_id, user_id) DO UPDATE SET option_id=$2, created_at=NOW()""",
            pid, oid, user["id"]
        )
        payload = await _poll_payload(conn, pid, user["id"])
    return payload

# ═══════════════════════════════════════════════════════════
# STORIES (24h ephemeral)
# ═══════════════════════════════════════════════════════════
class CreateStoryReq(BaseModel):
    content: Optional[str] = None
    media_url: Optional[str] = None
    media_type: str = "text"   # text|photo|voice
    bg_value: Optional[str] = None

@app.post("/api/stories")
async def create_story(req: CreateStoryReq, user=Depends(get_current_user)):
    if not req.content and not req.media_url:
        raise HTTPException(400, "Story needs content or media")
    sid = uuid.uuid4()
    async with db_pool.acquire() as conn:
        await conn.execute(
            """INSERT INTO stories (id, author_id, content, media_url, media_type, bg_value)
               VALUES ($1,$2,$3,$4,$5,$6)""",
            sid, user["id"], req.content, req.media_url, req.media_type, req.bg_value
        )
    return {"id": str(sid), "ok": True}

@app.get("/api/stories")
async def list_stories(user=Depends(get_current_user)):
    """Active (non-expired) stories, grouped by author, blocked excluded."""
    async with db_pool.acquire() as conn:
        rows = await conn.fetch(
            """SELECT s.*, u.username, u.display_name, u.avatar_url,
                EXISTS(SELECT 1 FROM story_views v WHERE v.story_id=s.id AND v.viewer_id=$1) as seen
               FROM stories s JOIN users u ON s.author_id = u.id
               WHERE s.expires_at > NOW()
                 AND NOT EXISTS (SELECT 1 FROM blocks b
                    WHERE (b.blocker_id=$1 AND b.blocked_id=s.author_id)
                       OR (b.blocker_id=s.author_id AND b.blocked_id=$1))
               ORDER BY s.created_at DESC""",
            user["id"]
        )
    grouped: Dict[str, dict] = {}
    for r in rows:
        aid = str(r["author_id"])
        grouped.setdefault(aid, {
            "author": {"id": aid, "username": r["username"],
                       "display_name": r["display_name"], "avatar_url": r["avatar_url"]},
            "all_seen": True, "stories": []
        })
        if not r["seen"]:
            grouped[aid]["all_seen"] = False
        grouped[aid]["stories"].append({
            "id": str(r["id"]), "content": r["content"], "media_url": r["media_url"],
            "media_type": r["media_type"], "bg_value": r["bg_value"],
            "views_count": r["views_count"], "seen": r["seen"],
            "created_at": r["created_at"].isoformat(),
            "expires_at": r["expires_at"].isoformat(),
        })
    return list(grouped.values())

@app.post("/api/stories/{story_id}/view")
async def view_story(story_id: str, user=Depends(get_current_user)):
    sid = uuid.UUID(story_id)
    async with db_pool.acquire() as conn:
        st = await conn.fetchrow("SELECT author_id FROM stories WHERE id=$1 AND expires_at > NOW()", sid)
        if not st:
            raise HTTPException(404, "Story not found or expired")
        if str(st["author_id"]) != str(user["id"]):
            inserted = await conn.fetchval(
                """INSERT INTO story_views (story_id, viewer_id) VALUES ($1,$2)
                   ON CONFLICT DO NOTHING RETURNING 1""", sid, user["id"])
            if inserted:
                await conn.execute("UPDATE stories SET views_count = views_count + 1 WHERE id=$1", sid)
    return {"ok": True}

# ═══════════════════════════════════════════════════════════
# Q&A GAME MODES (Never Have I Ever, 3 Words, Would You Rather)
# ═══════════════════════════════════════════════════════════
_GAME_PROMPTS = {
    "never_have_i_ever": [
        "Never have I ever ghosted someone.",
        "Never have I ever stalked an ex on social media.",
        "Never have I ever lied about my age online.",
        "Never have I ever sent a text to the wrong person.",
        "Never have I ever pretended to be busy to avoid someone.",
        "Never have I ever had a crush on a friend's partner.",
        "Never have I ever faked being sick to skip work.",
        "Never have I ever cried in a public place.",
    ],
    "three_words": [
        "Describe your ex in 3 words.",
        "Describe yourself in 3 words.",
        "Your ideal weekend in 3 words.",
        "Describe your crush in 3 words.",
        "Your last relationship in 3 words.",
        "Describe your mood tonight in 3 words.",
    ],
    "would_you_rather": [
        "Would you rather be invisible or read minds?",
        "Would you rather never feel lonely or never feel embarrassed?",
        "Would you rather text your ex or your boss right now?",
        "Would you rather know when you'll die or how you'll die?",
        "Would you rather always tell the truth or always lie?",
    ],
}

@app.get("/api/games/modes")
async def game_modes(user=Depends(get_current_user)):
    return [
        {"key": "never_have_i_ever", "title": "Never Have I Ever", "emoji": "🙈"},
        {"key": "three_words", "title": "3 Words", "emoji": "✏️"},
        {"key": "would_you_rather", "title": "Would You Rather", "emoji": "🤔"},
    ]

@app.get("/api/games/{mode}/prompt")
async def game_prompt(mode: str, user=Depends(get_current_user)):
    import random
    prompts = _GAME_PROMPTS.get(mode)
    if not prompts:
        raise HTTPException(404, "Unknown game mode")
    return {"mode": mode, "prompt": random.choice(prompts)}

class GamePostReq(BaseModel):
    mode: str
    prompt: str
    answer: str = Field(..., min_length=1, max_length=500)
    tags: Optional[List[str]] = []

@app.post("/api/games/answer")
async def post_game_answer(req: GamePostReq, user=Depends(get_current_user)):
    """Post a game answer as a special post (shows up in feed as a game card)."""
    if req.mode not in _GAME_PROMPTS:
        raise HTTPException(404, "Unknown game mode")
    post_id = uuid.uuid4()
    content = f"[{req.mode}] {req.prompt}\n\n{req.answer}"
    async with db_pool.acquire() as conn:
        await conn.execute(
            """INSERT INTO posts (id, author_id, content, post_type)
               VALUES ($1,$2,$3,'question')""",
            post_id, user["id"], content
        )
        await conn.execute("INSERT INTO post_tags (post_id, tag) VALUES ($1,$2) ON CONFLICT DO NOTHING",
                           post_id, req.mode)
        await award_karma(conn, user["id"], "post_created", post_id)
    return {"id": str(post_id), "ok": True}

# ═══════════════════════════════════════════════════════════
# GROUPS & INTEREST CLUBS
# ═══════════════════════════════════════════════════════════
class CreateGroupReq(BaseModel):
    name: str = Field(..., min_length=2, max_length=60)
    topic: str = Field(..., max_length=40)
    description: Optional[str] = ""

@app.get("/api/groups")
async def list_groups(
    topic: Optional[str] = None,
    clubs_only: bool = False,
    mine: bool = False,
    user=Depends(get_current_user)
):
    async with db_pool.acquire() as conn:
        where = ["1=1"]; params = []; pidx = 1
        if topic:
            where.append(f"g.topic = ${pidx}"); params.append(topic.lower()); pidx += 1
        if clubs_only:
            where.append("g.is_club = TRUE")
        if mine:
            where.append(f"EXISTS(SELECT 1 FROM group_members m WHERE m.group_id=g.id AND m.user_id=${pidx})")
            params.append(user["id"]); pidx += 1
        params.append(user["id"])  # for is_member flag
        rows = await conn.fetch(f"""
            SELECT g.*,
               EXISTS(SELECT 1 FROM group_members m WHERE m.group_id=g.id AND m.user_id=${pidx}) as is_member
            FROM groups g WHERE {' AND '.join(where)}
            ORDER BY g.is_club DESC, g.members_count DESC, g.created_at DESC
        """, *params)
    return [{
        "id": str(r["id"]), "name": r["name"], "topic": r["topic"],
        "description": r["description"], "is_club": r["is_club"],
        "members_count": r["members_count"], "is_member": r["is_member"],
        "created_at": r["created_at"].isoformat()
    } for r in rows]

@app.post("/api/groups")
async def create_group(req: CreateGroupReq, user=Depends(get_current_user)):
    gid = uuid.uuid4()
    async with db_pool.acquire() as conn:
        await conn.execute(
            """INSERT INTO groups (id, name, topic, description, is_club, owner_id, members_count)
               VALUES ($1,$2,$3,$4,FALSE,$5,1)""",
            gid, req.name, req.topic.lower(), req.description or "", user["id"]
        )
        await conn.execute("INSERT INTO group_members (group_id, user_id) VALUES ($1,$2)", gid, user["id"])
    return {"id": str(gid), "ok": True}

@app.post("/api/groups/{group_id}/join")
async def join_group(group_id: str, user=Depends(get_current_user)):
    gid = uuid.UUID(group_id)
    async with db_pool.acquire() as conn:
        g = await conn.fetchval("SELECT 1 FROM groups WHERE id=$1", gid)
        if not g:
            raise HTTPException(404, "Group not found")
        inserted = await conn.fetchval(
            """INSERT INTO group_members (group_id, user_id) VALUES ($1,$2)
               ON CONFLICT DO NOTHING RETURNING 1""", gid, user["id"])
        if inserted:
            await conn.execute("UPDATE groups SET members_count = members_count + 1 WHERE id=$1", gid)
    return {"ok": True}

@app.post("/api/groups/{group_id}/leave")
async def leave_group(group_id: str, user=Depends(get_current_user)):
    gid = uuid.UUID(group_id)
    async with db_pool.acquire() as conn:
        deleted = await conn.fetchval(
            "DELETE FROM group_members WHERE group_id=$1 AND user_id=$2 RETURNING 1", gid, user["id"])
        if deleted:
            await conn.execute("UPDATE groups SET members_count = GREATEST(members_count - 1, 0) WHERE id=$1", gid)
    return {"ok": True}

@app.get("/api/groups/{group_id}/messages")
async def group_messages(group_id: str, limit: int = Query(50, ge=1, le=100), user=Depends(get_current_user)):
    gid = uuid.UUID(group_id)
    async with db_pool.acquire() as conn:
        member = await conn.fetchval("SELECT 1 FROM group_members WHERE group_id=$1 AND user_id=$2", gid, user["id"])
        if not member:
            raise HTTPException(403, "Join the group first")
        rows = await conn.fetch(
            """SELECT gm.*, u.username, u.display_name, u.avatar_url
               FROM group_messages gm LEFT JOIN users u ON gm.sender_id = u.id
               WHERE gm.group_id=$1 ORDER BY gm.created_at DESC LIMIT $2""", gid, limit
        )
    return [{
        "id": str(r["id"]), "content": r["content"], "msg_type": r["msg_type"],
        "media_url": r["media_url"],
        "sender": {"id": str(r["sender_id"]) if r["sender_id"] else None,
                   "username": r["username"] or "Anonymous",
                   "display_name": r["display_name"] or "Anonymous",
                   "avatar_url": r["avatar_url"]},
        "created_at": r["created_at"].isoformat()
    } for r in reversed(rows)]

@app.websocket("/ws/group/{group_id}/{token}")
async def websocket_group(ws: WebSocket, group_id: str, token: str):
    try:
        data = decode_token(token)
        user_id = data["user_id"]
        gid = uuid.UUID(group_id)
    except Exception:
        await ws.close(code=4001)
        return
    async with db_pool.acquire() as conn:
        member = await conn.fetchval("SELECT 1 FROM group_members WHERE group_id=$1 AND user_id=$2", gid, uuid.UUID(user_id))
    if not member:
        await ws.close(code=4003)
        return
    key = f"group_{group_id}"
    group_manager.add(key, user_id, ws)
    await ws.accept()
    try:
        while True:
            msg = await ws.receive_json()
            content = msg.get("content", "")
            msg_type = msg.get("type", "text")
            media_url = msg.get("media_url")
            mid = uuid.uuid4()
            async with db_pool.acquire() as conn:
                urow = await conn.fetchrow("SELECT username, display_name, avatar_url FROM users WHERE id=$1", uuid.UUID(user_id))
                await conn.execute(
                    """INSERT INTO group_messages (id, group_id, sender_id, content, msg_type, media_url)
                       VALUES ($1,$2,$3,$4,$5,$6)""",
                    mid, gid, uuid.UUID(user_id), content, msg_type, media_url
                )
            payload = {
                "id": str(mid), "content": content, "msg_type": msg_type, "media_url": media_url,
                "sender": {"id": user_id, "username": urow["username"],
                           "display_name": urow["display_name"], "avatar_url": urow["avatar_url"]},
                "created_at": datetime.utcnow().isoformat()
            }
            await group_manager.broadcast(key, payload)
    except WebSocketDisconnect:
        group_manager.remove(key, user_id)

# ─── Health ────────────────────────────────────────────────
@app.get("/api/health")
async def health():
    return {"status": "ok", "version": "1.0.0", "features": [
        "chat", "voice_note", "once_view_photo", "edit_delete",
        "shareable_links", "multiple_accounts", "block",
        "gif", "voice_call"
    ]}

# ═══════════════════════════════════════════════════════════
# MATCH WITH STRANGER — 1-on-1 anonymous game matching
# ═══════════════════════════════════════════════════════════
import time as _time
import random as _random
import string as _string

# In-memory match queue: list of {"user_id": uuid, "mode": str, "joined_at": float}
_match_queue: list = []

# Active matches keyed by match_id (uuid str):
#   {"user1": uuid, "user2": uuid, "mode": str, "prompt": str,
#    "answer1": str|None, "answer2": str|None,
#    "react1": str|None, "react2": str|None,
#    "anon1": str, "anon2": str,
#    "started_at": float, "phase": "answering"|"reveal"|"done"}
_active_matches: dict = {}

_MATCH_ANSWER_WINDOW = 30   # seconds to answer before auto-reveal
_MATCH_TOTAL_LIFETIME = 60  # seconds before match is cleaned up
_VALID_REACT_EMOJIS = {"🔥", "😂", "👍", "😍"}


def _gen_anon_id() -> str:
    """Generate an anonymous stranger id like 'Stranger_abc123'."""
    suffix = "".join(_random.choices(_string.ascii_lowercase + _string.digits, k=6))
    return f"Stranger_{suffix}"


def _pick_random_mode() -> str:
    return _random.choice(list(_GAME_PROMPTS.keys()))


def _refresh_match_phase(m: dict) -> str:
    """Advance match phase based on elapsed time. Returns current phase."""
    now = _time.time()
    elapsed = now - m["started_at"]
    phase = m["phase"]
    if phase == "done":
        return "done"
    if elapsed >= _MATCH_TOTAL_LIFETIME:
        m["phase"] = "done"
        return "done"
    if phase == "answering":
        # Auto-reveal if the answer window elapsed OR both answered
        both_answered = bool(m["answer1"]) and bool(m["answer2"])
        if both_answered or elapsed >= _MATCH_ANSWER_WINDOW:
            m["phase"] = "reveal"
            return "reveal"
    return m["phase"]


class MatchJoinReq(BaseModel):
    mode: Optional[str] = None  # defaults to random


@app.post("/api/games/match/join")
async def game_match_join(req: MatchJoinReq, user=Depends(get_current_user)):
    """Join the matchmaking queue. Returns immediately with 'waiting' or 'matched'."""
    uid = user["id"]
    # Validate / pick mode
    mode = req.mode or _pick_random_mode()
    if mode not in _GAME_PROMPTS:
        raise HTTPException(400, f"Unknown game mode: {mode}")

    # Remove any prior queue entries for this user (no double-queueing)
    _match_queue[:] = [e for e in _match_queue if e["user_id"] != uid]

    # Remove expired queue entries (>120s)
    now = _time.time()
    _match_queue[:] = [
        e for e in _match_queue
        if (now - e["joined_at"]) < 120 and e["user_id"] != uid
    ]

    # Look for an opponent already waiting (any mode — prefer same mode)
    opponent = None
    # First try same-mode match
    for i, e in enumerate(_match_queue):
        if e["mode"] == mode:
            opponent = _match_queue.pop(i)
            break
    # Otherwise match with the first available (fallback to opponent's mode)
    if not opponent and _match_queue:
        opponent = _match_queue.pop(0)
        mode = opponent["mode"]  # use the waiting user's chosen mode

    if opponent:
        match_id = str(uuid.uuid4())
        prompt = _random.choice(_GAME_PROMPTS[mode])
        _active_matches[match_id] = {
            "user1": opponent["user_id"],
            "user2": uid,
            "mode": mode,
            "prompt": prompt,
            "answer1": None,
            "answer2": None,
            "react1": None,
            "react2": None,
            "anon1": _gen_anon_id(),
            "anon2": _gen_anon_id(),
            "started_at": now,
            "phase": "answering",
        }
        # Return matched response for the current (2nd) user
        return {
            "status": "matched",
            "match_id": match_id,
            "opponent_id": _active_matches[match_id]["anon1"],
            "prompt": prompt,
            "mode": mode,
        }

    # No opponent — add to queue and tell user to wait
    _match_queue.append({"user_id": uid, "mode": mode, "joined_at": now})
    return {"status": "waiting", "match_id": None, "mode": mode}


def _user_side_in_match(m: dict, uid) -> int:
    """Return 1 or 2 depending on which side uid is in match m, or 0 if not present."""
    if str(m["user1"]) == str(uid):
        return 1
    if str(m["user2"]) == str(uid):
        return 2
    return 0


@app.get("/api/games/match/{match_id}/status")
async def game_match_status(match_id: str, user=Depends(get_current_user)):
    """Get the current state of a match from the requesting user's perspective."""
    m = _active_matches.get(match_id)
    if not m:
        return {"phase": "done", "my_answer": None, "opponent_answer": None, "time_left": 0}

    uid = user["id"]
    side = _user_side_in_match(m, uid)
    if side == 0:
        raise HTTPException(403, "You are not part of this match")

    # If this user has been polling and an opponent just joined, still reflect
    # the match's phase (this endpoint is called once matched).
    phase = _refresh_match_phase(m)

    if side == 1:
        my_answer = m["answer1"]
        opp_answer = m["answer2"]
    else:
        my_answer = m["answer2"]
        opp_answer = m["answer1"]

    # Only reveal opponent's answer during reveal/done
    if phase in ("answering", "waiting"):
        opp_answer = None

    now = _time.time()
    elapsed = now - m["started_at"]
    if phase == "answering":
        time_left = max(0, int(_MATCH_ANSWER_WINDOW - elapsed))
    elif phase == "reveal":
        time_left = max(0, int(_MATCH_TOTAL_LIFETIME - elapsed))
    else:
        time_left = 0

    return {
        "phase": phase,
        "my_answer": my_answer,
        "opponent_answer": opp_answer,
        "time_left": time_left,
        "prompt": m["prompt"],
        "mode": m["mode"],
    }


class MatchAnswerReq(BaseModel):
    answer: str = Field(..., min_length=1, max_length=500)


@app.post("/api/games/match/{match_id}/answer")
async def game_match_answer(match_id: str, req: MatchAnswerReq, user=Depends(get_current_user)):
    """Submit an answer for the current match."""
    m = _active_matches.get(match_id)
    if not m:
        raise HTTPException(404, "Match not found or expired")
    phase = _refresh_match_phase(m)
    if phase == "done":
        raise HTTPException(410, "Match has ended")

    uid = user["id"]
    side = _user_side_in_match(m, uid)
    if side == 0:
        raise HTTPException(403, "You are not part of this match")

    # Store the answer if not already submitted
    if side == 1 and not m["answer1"]:
        m["answer1"] = req.answer
    elif side == 2 and not m["answer2"]:
        m["answer2"] = req.answer

    # Re-evaluate phase (both answered → reveal)
    phase = _refresh_match_phase(m)
    return {"submitted": True, "phase": phase}


class MatchReactReq(BaseModel):
    emoji: str  # 🔥|😂|👍|😍


@app.post("/api/games/match/{match_id}/react")
async def game_match_react(match_id: str, req: MatchReactReq, user=Depends(get_current_user)):
    """React to the opponent's answer. Awards +1 karma to the opponent."""
    if req.emoji not in _VALID_REACT_EMOJIS:
        raise HTTPException(400, "Invalid emoji. Allowed: 🔥 😂 👍 😍")

    m = _active_matches.get(match_id)
    if not m:
        raise HTTPException(404, "Match not found or expired")
    _refresh_match_phase(m)
    if m["phase"] not in ("reveal", "done"):
        raise HTTPException(400, "Can only react after answers are revealed")

    uid = user["id"]
    side = _user_side_in_match(m, uid)
    if side == 0:
        raise HTTPException(403, "You are not part of this match")

    # Record reaction (only first reaction counts)
    if side == 1 and not m["react1"]:
        m["react1"] = req.emoji
        opponent_uid = m["user2"]
    elif side == 2 and not m["react2"]:
        m["react2"] = req.emoji
        opponent_uid = m["user1"]
    else:
        # Already reacted
        return {"ok": True}

    # Award karma to the opponent (+1 per reaction)
    try:
        async with db_pool.acquire() as conn:
            await conn.execute(
                "UPDATE users SET karma = GREATEST(karma + 1, 0) WHERE id = $1",
                opponent_uid,
            )
    except Exception:
        # Non-fatal: karma award failure shouldn't break the reaction flow
        pass
    return {"ok": True}


@app.post("/api/games/match/{match_id}/leave")
async def game_match_leave(match_id: str, user=Depends(get_current_user)):
    """Clean up a match and remove the user from the queue."""
    uid = user["id"]
    # Remove from queue
    _match_queue[:] = [e for e in _match_queue if e["user_id"] != uid]
    # Remove the match if it exists and user is part of it
    m = _active_matches.get(match_id)
    if m and _user_side_in_match(m, uid) != 0:
        m["phase"] = "done"
        # Delete match immediately on explicit leave
        _active_matches.pop(match_id, None)
    return {"ok": True}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8004, reload=True)
