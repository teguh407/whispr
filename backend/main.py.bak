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
    FastAPI, Depends, HTTPException, status,
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

# ─── Config ────────────────────────────────────────────────
SECRET_KEY = os.getenv("WHISPR_SECRET", "whispr-dev-secret-change-me")
ALGORITHM = "HS256"
DB_DSN = os.getenv("DATABASE_URL", "postgresql://postgres:postgres@localhost/whispr_db")
UPLOAD_DIR = os.path.join(os.path.dirname(__file__), "uploads")
GIPHY_API_KEY = os.getenv("GIPHY_API_KEY", "")  # optional
MAX_ACCOUNTS = 3
EDIT_WINDOW_MINUTES = 5

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
                is_active BOOLEAN DEFAULT TRUE
            );
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
        print("✅ Database tables ready")

@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield
    if db_pool:
        await db_pool.close()

app = FastAPI(title="Whispr API", version="1.0.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

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
    return dict(user)

async def check_blocked(conn, user_a, user_b) -> bool:
    row = await conn.fetchrow(
        "SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1)",
        user_a, user_b
    )
    return row is not None

# ─── Auth Routes ───────────────────────────────────────────
class RegisterReq(BaseModel):
    username: str = Field(..., min_length=3, max_length=30)
    password: str = Field(..., min_length=6)
    email: Optional[str] = None
    display_name: Optional[str] = None

class LoginReq(BaseModel):
    username: str
    password: str

@app.post("/api/auth/register")
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

@app.post("/api/auth/login")
async def login(req: LoginReq):
    async with db_pool.acquire() as conn:
        user = await conn.fetchrow(
            "SELECT * FROM users WHERE username = $1 AND is_active = TRUE",
            req.username.lower()
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
        "days_active": user["days_active"],
        "posts_count": posts_count
    }

class UpdateProfileReq(BaseModel):
    display_name: Optional[str] = None
    bio: Optional[str] = None
    avatar_url: Optional[str] = None

@app.put("/api/me")
async def update_profile(req: UpdateProfileReq, user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        await conn.execute(
            """UPDATE users SET 
               display_name = COALESCE($1, display_name),
               bio = COALESCE($2, bio),
               avatar_url = COALESCE($3, avatar_url)
               WHERE id = $4""",
            req.display_name, req.bio, req.avatar_url, user["id"]
        )
    return {"ok": True}

# ─── Posts (Feature 4: Edit/Delete) ────────────────────────
class CreatePostReq(BaseModel):
    content: str = Field(..., min_length=1, max_length=5000)
    tags: Optional[List[str]] = []
    is_once_view: bool = False

@app.post("/api/posts")
async def create_post(req: CreatePostReq, user=Depends(get_current_user)):
    post_id = uuid.uuid4()
    async with db_pool.acquire() as conn:
        await conn.execute(
            """INSERT INTO posts (id, author_id, content, is_once_view) 
               VALUES ($1, $2, $3, $4)""",
            post_id, user["id"], req.content, req.is_once_view
        )
        if req.tags:
            for tag in req.tags[:5]:
                await conn.execute(
                    "INSERT INTO post_tags (post_id, tag) VALUES ($1, $2)",
                    post_id, tag.lower()[:50]
                )
        # Update karma
        await conn.execute(
            "UPDATE users SET karma = karma + 1 WHERE id = $1", user["id"]
        )
    return {"id": str(post_id), "ok": True}

@app.get("/api/posts")
async def list_posts(
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=50),
    tag: Optional[str] = None,
    user=Depends(get_current_user)
):
    offset = (page - 1) * limit
    async with db_pool.acquire() as conn:
        if tag:
            rows = await conn.fetch(
                """SELECT p.*, u.username, u.display_name, u.avatar_url,
                   (SELECT COUNT(*) FROM upvotes WHERE post_id = p.id) as vote_count,
                   EXISTS(SELECT 1 FROM upvotes WHERE post_id = p.id AND user_id = $1) as user_upvoted
                   FROM posts p 
                   JOIN users u ON p.author_id = u.id
                   JOIN post_tags pt ON p.id = pt.post_id
                   WHERE pt.tag = $2 AND p.is_deleted = FALSE
                   ORDER BY p.created_at DESC LIMIT $3 OFFSET $4""",
                user["id"], tag.lower(), limit, offset
            )
        else:
            rows = await conn.fetch(
                """SELECT p.*, u.username, u.display_name, u.avatar_url,
                   (SELECT COUNT(*) FROM upvotes WHERE post_id = p.id) as vote_count,
                   EXISTS(SELECT 1 FROM upvotes WHERE post_id = p.id AND user_id = $1) as user_upvoted
                   FROM posts p 
                   JOIN users u ON p.author_id = u.id
                   WHERE p.is_deleted = FALSE
                   ORDER BY p.created_at DESC LIMIT $2 OFFSET $3""",
                user["id"], limit, offset
            )
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
        "author": {"username": r["username"], "display_name": r["display_name"], "avatar_url": r["avatar_url"]},
        "created_at": r["created_at"].isoformat()
    } for r in rows]

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
            return {"upvoted": True}

# ─── Upload (Voice Note + Once-View Photo) ─────────────────
@app.post("/api/upload/voice")
async def upload_voice(
    file: UploadFile = File(...),
    user=Depends(get_current_user)
):
    ext = file.filename.split(".")[-1] if "." in file.filename else "ogg"
    fname = f"{uuid.uuid4().hex}.{ext}"
    fpath = os.path.join(UPLOAD_DIR, "voice", fname)
    content = await file.read()
    with open(fpath, "wb") as f:
        f.write(content)
    return {"url": f"/uploads/voice/{fname}", "type": "voice"}

@app.post("/api/upload/photo")
async def upload_photo(
    file: UploadFile = File(...),
    is_once_view: bool = Form(False),
    user=Depends(get_current_user)
):
    ext = file.filename.split(".")[-1] if "." in file.filename else "jpg"
    fname = f"{uuid.uuid4().hex}.{ext}"
    fpath = os.path.join(UPLOAD_DIR, "photos", fname)
    content = await file.read()
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
                await conn.execute(
                    """INSERT INTO messages (id, chat_id, sender_id, content, msg_type, media_url, is_once_view)
                       VALUES ($1, $2, $3, $4, $5, $6, $7)""",
                    msg_id, chat_id, uuid.UUID(user_id), content, msg_type, media_url, is_once_view
                )
                await conn.execute(
                    "UPDATE chats SET last_message_at = NOW() WHERE id = $1", chat_id
                )
                await conn.execute(
                    "UPDATE users SET karma = karma + 1 WHERE id = $1", uuid.UUID(user_id)
                )
            
            payload = {
                "id": str(msg_id),
                "chat_id": str(chat_id),
                "sender_id": user_id,
                "content": content,
                "type": msg_type,
                "media_url": media_url,
                "is_once_view": is_once_view,
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
        results.append({
            "id": str(r["id"]),
            "other_user": {
                "id": str(other["id"]) if other else None,
                "username": other["username"] if other else "Anonymous",
                "display_name": other["display_name"] if other else "Anonymous",
                "avatar_url": other["avatar_url"] if other else None
            } if other else None,
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
async def list_accounts(user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        rows = await conn.fetch(
            "SELECT * FROM accounts WHERE owner_id = $1 ORDER BY created_at",
            user["id"]
        )
        # Include main account
        main = {
            "id": str(user["id"]),
            "username": user["username"],
            "display_name": user["display_name"],
            "avatar_url": user["avatar_url"],
            "is_main": True
        }
    accounts = [{"id": str(r["id"]), "username": r["username"], 
                 "display_name": r["account_name"], "avatar_url": r["avatar_url"],
                 "is_main": False} for r in rows]
    return {"main": main, "accounts": accounts}

class CreateAccountReq(BaseModel):
    username: str = Field(..., min_length=3, max_length=30)
    password: str = Field(..., min_length=6)
    display_name: Optional[str] = None

@app.post("/api/accounts")
async def create_account(req: CreateAccountReq, user=Depends(get_current_user)):
    async with db_pool.acquire() as conn:
        count = await conn.fetchval(
            "SELECT COUNT(*) FROM accounts WHERE owner_id = $1", user["id"]
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
            acc_id, user["id"], req.display_name or req.username,
            req.username.lower(), pw_hash
        )
    return {"id": str(acc_id), "username": req.username.lower(), "ok": True}

@app.post("/api/accounts/{acc_id}/switch")
async def switch_account(acc_id: str, user=Depends(get_current_user)):
    aid = uuid.UUID(acc_id)
    async with db_pool.acquire() as conn:
        acc = await conn.fetchrow(
            "SELECT * FROM accounts WHERE id = $1 AND owner_id = $2",
            aid, user["id"]
        )
        if not acc:
            raise HTTPException(404, "Account not found")
    token = create_token(str(user["id"]))  # same owner, different display
    return {"token": token, "account": {"username": acc["username"], "display_name": acc["account_name"]}}

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

# ─── Health ────────────────────────────────────────────────
@app.get("/api/health")
async def health():
    return {"status": "ok", "version": "1.0.0", "features": [
        "chat", "voice_note", "once_view_photo", "edit_delete",
        "shareable_links", "multiple_accounts", "block",
        "gif", "voice_call"
    ]}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8004, reload=True)
