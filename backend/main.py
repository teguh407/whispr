"""
Whispr — Anonymous Chat App Backend
FastAPI + WebSocket + PostgreSQL
"""
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Depends, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy import Column, Integer, String, Text, DateTime, Boolean, Float, ForeignKey
from sqlalchemy.orm import relationship
from jose import JWTError, jwt
import bcrypt
from datetime import datetime, timedelta
from typing import Optional, List
import enum
import json

# ── Config ──
DATABASE_URL = "postgresql+asyncpg://whispr:whispr_secret@localhost:5432/whispr"
SECRET_KEY = "whispr-secret-key-change-in-production"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24 * 7

# ── App ──
app = FastAPI(title="Whispr API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Database ──
engine = create_async_engine(DATABASE_URL, echo=False)
async_session = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

class Base(DeclarativeBase):
    pass

# ── Models ──
class User(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(30), unique=True, index=True, nullable=False)
    email = Column(String(255), unique=True, index=True, nullable=True)
    hashed_password = Column(String(255), nullable=False)
    avatar_seed = Column(String(50), nullable=False)
    karma = Column(Integer, default=0)
    karma_level = Column(String(20), default="newcomer")
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    posts = relationship("Post", back_populates="author")

class Post(Base):
    __tablename__ = "posts"
    id = Column(Integer, primary_key=True, index=True)
    author_id = Column(Integer, ForeignKey("users.id"))
    content = Column(Text, nullable=False)
    post_type = Column(String(20), default="post")
    upvotes = Column(Integer, default=0)
    downvotes = Column(Integer, default=0)
    comment_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)
    author = relationship("User", back_populates="posts")
    tags = relationship("PostTag", back_populates="post")

class PostTag(Base):
    __tablename__ = "post_tags"
    id = Column(Integer, primary_key=True, index=True)
    post_id = Column(Integer, ForeignKey("posts.id"))
    tag = Column(String(50), nullable=False)
    post = relationship("Post", back_populates="tags")

class Message(Base):
    __tablename__ = "messages"
    id = Column(Integer, primary_key=True, index=True)
    sender_id = Column(Integer, ForeignKey("users.id"))
    receiver_id = Column(Integer, ForeignKey("users.id"))
    content = Column(Text, nullable=True)
    message_type = Column(String(20), default="text")
    is_read = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)

# ── Schemas ──
from pydantic import BaseModel

class UserCreate(BaseModel):
    username: str
    email: Optional[str] = None
    password: str

class UserResponse(BaseModel):
    id: int
    username: str
    karma: int
    karma_level: str
    avatar_seed: str
    created_at: datetime
    class Config:
        from_attributes = True

class Token(BaseModel):
    access_token: str
    token_type: str

class PostCreate(BaseModel):
    content: str
    post_type: str = "post"
    tags: List[str] = []

class PostResponse(BaseModel):
    id: int
    content: str
    post_type: str
    upvotes: int
    downvotes: int
    comment_count: int
    author: UserResponse
    tags: List[str]
    created_at: datetime
    class Config:
        from_attributes = True

class MessageCreate(BaseModel):
    receiver_id: int
    content: Optional[str] = None
    message_type: str = "text"

# ── Auth Helpers ──
def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

def verify_password(password: str, hashed: str) -> bool:
    return bcrypt.checkpw(password.encode('utf-8'), hashed.encode('utf-8'))

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")

async def get_db():
    async with async_session() as session:
        yield session

def create_access_token(data: dict):
    to_encode = data.copy()
    if "sub" in to_encode and isinstance(to_encode["sub"], int):
        to_encode["sub"] = str(to_encode["sub"])
    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

async def get_current_user(token: str = Depends(oauth2_scheme), db: AsyncSession = Depends(get_db)):
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        user_id_str = payload.get("sub")
        if user_id_str is None:
            raise credentials_exception
        user_id = int(user_id_str)
    except (JWTError, ValueError):
        raise credentials_exception
    from sqlalchemy import select
    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if user is None:
        raise credentials_exception
    return user

# ── Routes ──
@app.get("/")
async def root():
    return {"message": "Whispr API", "version": "0.1.0"}

@app.post("/auth/register", response_model=Token)
async def register(user: UserCreate, db: AsyncSession = Depends(get_db)):
    from sqlalchemy import select
    result = await db.execute(select(User).where(User.username == user.username))
    if result.scalar_one_or_none():
        raise HTTPException(status_code=400, detail="Username already taken")
    import random
    avatar_seed = f"{user.username}_{random.randint(1000, 9999)}"
    new_user = User(
        username=user.username,
        email=user.email,
        hashed_password=hash_password(user.password),
        avatar_seed=avatar_seed
    )
    db.add(new_user)
    await db.commit()
    await db.refresh(new_user)
    access_token = create_access_token(data={"sub": new_user.id})
    return {"access_token": access_token, "token_type": "bearer"}

@app.post("/token", response_model=Token)
async def login(form_data: OAuth2PasswordRequestForm = Depends(), db: AsyncSession = Depends(get_db)):
    from sqlalchemy import select
    result = await db.execute(select(User).where(User.username == form_data.username))
    user = result.scalar_one_or_none()
    if not user or not verify_password(form_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    access_token = create_access_token(data={"sub": user.id})
    return {"access_token": access_token, "token_type": "bearer"}

@app.get("/auth/me", response_model=UserResponse)
async def get_me(current_user: User = Depends(get_current_user)):
    return current_user

@app.post("/posts", response_model=PostResponse)
async def create_post(post: PostCreate, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    new_post = Post(author_id=current_user.id, content=post.content, post_type=post.post_type)
    db.add(new_post)
    await db.flush()
    for tag_name in post.tags:
        db.add(PostTag(post_id=new_post.id, tag=tag_name))
    await db.commit()
    await db.refresh(new_post)
    return PostResponse(
        id=new_post.id, content=new_post.content, post_type=new_post.post_type,
        upvotes=new_post.upvotes, downvotes=new_post.downvotes, comment_count=new_post.comment_count,
        author=UserResponse.model_validate(current_user), tags=post.tags, created_at=new_post.created_at
    )

@app.get("/posts", response_model=List[PostResponse])
async def get_posts(skip: int = 0, limit: int = 20, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    from sqlalchemy import select
    result = await db.execute(select(Post).offset(skip).limit(limit).order_by(Post.created_at.desc()))
    posts = result.scalars().all()
    response = []
    for post in posts:
        author_result = await db.execute(select(User).where(User.id == post.author_id))
        author = author_result.scalar_one()
        tags_result = await db.execute(select(PostTag).where(PostTag.post_id == post.id))
        tags = [t.tag for t in tags_result.scalars().all()]
        response.append(PostResponse(
            id=post.id, content=post.content, post_type=post.post_type,
            upvotes=post.upvotes, downvotes=post.downvotes, comment_count=post.comment_count,
            author=UserResponse.model_validate(author), tags=tags, created_at=post.created_at
        ))
    return response

@app.post("/posts/{post_id}/upvote")
async def upvote_post(post_id: int, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    from sqlalchemy import select
    result = await db.execute(select(Post).where(Post.id == post_id))
    post = result.scalar_one_or_none()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")
    post.upvotes += 1
    author_result = await db.execute(select(User).where(User.id == post.author_id))
    author = author_result.scalar_one()
    author.karma += 3
    await db.commit()
    return {"message": "Upvoted"}

# ── WebSocket ──
class ConnectionManager:
    def __init__(self):
        self.active_connections: dict[int, WebSocket] = {}
    async def connect(self, websocket: WebSocket, user_id: int):
        await websocket.accept()
        self.active_connections[user_id] = websocket
    def disconnect(self, user_id: int):
        if user_id in self.active_connections:
            del self.active_connections[user_id]
    async def send_personal_message(self, message: str, user_id: int):
        if user_id in self.active_connections:
            await self.active_connections[user_id].send_text(message)

manager = ConnectionManager()

@app.websocket("/ws/{user_id}")
async def websocket_endpoint(websocket: WebSocket, user_id: int):
    await manager.connect(websocket, user_id)
    try:
        while True:
            data = await websocket.receive_text()
            message_data = json.loads(data)
            target_id = message_data.get("receiver_id")
            if target_id:
                await manager.send_personal_message(json.dumps({
                    "sender_id": user_id,
                    "content": message_data.get("content"),
                    "message_type": message_data.get("message_type", "text")
                }), target_id)
    except WebSocketDisconnect:
        manager.disconnect(user_id)

# ── Init DB ──
@app.on_event("startup")
async def startup():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8004)
