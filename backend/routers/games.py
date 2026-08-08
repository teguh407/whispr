"""
Whispr Q&A Game Modes Router
─────────────────────────────
Standalone interactive game engine for 1:1 chats — three ice-breaker /
party-game modes that run inside an existing Whispr chat between two users.

Game modes
----------
1. Never Have I Ever  — pre-made "Never have I ever…" prompts; each player
                         answers Yes/No; score = number of "Yes" (things they
                         HAVE done). Most "Yes" wins.
2. Confessions         — random confession prompts; players type a free-form
                         confession. Answers are shared anonymously (no user
                         identity surfaced with the confession text).
3. 3 Words             — a random topic is drawn; players respond with EXACTLY
                         three words. Other player can upvote the best
                         3-word answers. Most upvoted wins.

This router is SELF-CONTAINED:
  • Owns its own asyncpg pool reference (injected via init_games(pool)).
  • Has its own JWT auth helpers (identical semantics to main.py so tokens
    issued by main.py's /api/auth/* endpoints decode here too).
  • Does NOT import from main.py — avoids circular imports when main.py
    imports this router at startup.

DB
  PostgreSQL whispr_db (user whispr / pass whispr123 / localhost).
  Uses existing tables: users (id, username, display_name) and chats
  (id, user1_id, user2_id) — both created by main.py's init_db().

  New idempotent tables (all CREATE TABLE IF NOT EXISTS — safe to run
  alongside main.py's init_db()):
    game_sessions  — id UUID PK, game_type, chat_id, started_by, status,
                     current_turn, round, created_at, ended_at
    game_questions — id UUID PK, session_id, question_text, question_type,
                     round, asked_to, answered
    game_answers    — id UUID PK, session_id, question_id, user_id,
                     answer_text, score, created_at
    game_prompts    — id UUID PK, game_type, prompt_text  (seed: 20 per mode)

JWT auth
  PyJWT (import jwt), claim key "user_id" (NOT "sub"), 30-day expiry,
  SECRET from WHISPR_SECRET env var — identical to main.py.

Endpoints
---------
GET    /api/games                       — list available game modes
POST   /api/games/start                 — start a game session (game_type, chat_id)
GET    /api/games/{session_id}          — get game state (current question, scores, turn)
POST   /api/games/{session_id}/answer   — submit an answer (text)
POST   /api/games/{session_id}/next      — advance to next question/turn
DELETE /api/games/{session_id}          — end game session
GET    /api/games/{session_id}/results  — final results

Integration (in main.py)
-------------------------
    from routers.games import router as games_router, init_games
    app.include_router(games_router)
    # inside lifespan, AFTER init_db():
    await init_games(db_pool)
"""
from __future__ import annotations

import os
import random
import uuid
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Any

import asyncpg
import jwt  # PyJWT — matches main.py
from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api/games", tags=["games"])

# ─── Config (mirrors main.py) ──────────────────────────────
SECRET_KEY = os.getenv("WHISPR_SECRET", "whispr-dev-secret-change-me")
ALGORITHM = "HS256"

# ─── Shared DB pool (injected from main.py via init_games) ─
_pool: Optional[asyncpg.Pool] = None
security = HTTPBearer()

# ─── Game mode registry ─────────────────────────────────────
GAME_TYPES = ("never_have_i_ever", "confessions", "three_words")

GAME_MODES_INFO = [
    {
        "game_type": "never_have_i_ever",
        "name": "Never Have I Ever",
        "description": "Classic ice-breaker. Answer 'Never have I ever…' "
                       "prompts with Yes/No. Score = things you HAVE done.",
        "answer_hint": "yes|no",
        "max_rounds": 10,
    },
    {
        "game_type": "confessions",
        "name": "Confessions",
        "description": "Random confession prompts. Type your confession — "
                       "answers are shared anonymously.",
        "answer_hint": "free text",
        "max_rounds": 10,
    },
    {
        "game_type": "three_words",
        "name": "3 Words",
        "description": "A random topic is drawn. Respond with EXACTLY three "
                       "words. The other player upvotes the best answers.",
        "answer_hint": "exactly 3 words",
        "max_rounds": 10,
    },
]
_GAME_TYPE_BY_KEY = {m["game_type"]: m for m in GAME_MODES_INFO}

# ─── Seed prompts (20 per game type) ───────────────────────
SEED_PROMPTS: Dict[str, List[str]] = {
    "never_have_i_ever": [
        "Never have I ever sent a text to the wrong person.",
        "Never have I ever pretended to be on the phone to avoid someone.",
        "Never have I ever fallen asleep in a meeting or class.",
        "Never have I ever eaten food off the floor.",
        "Never have I ever lied about my age.",
        "Never have I ever ghosted someone.",
        "Never have I ever cried during a Disney movie.",
        "Never have I ever sung karaoke.",
        "Never have I ever accidentally liked a very old photo.",
        "Never have I ever stayed up all night talking to a crush.",
        "Never have I ever pretended to like a gift I hated.",
        "Never have I ever broken something at a friend's house and said nothing.",
        "Never have I ever used someone else's Netflix account.",
        "Never have I ever danced when nobody was watching — then got caught.",
        "Never have I ever drunk-texted an ex.",
        "Never have I ever said 'I love you' first.",
        "Never have I ever made up an excuse to leave a party early.",
        "Never have I ever Googled myself.",
        "Never have I ever had a crush on a cartoon character.",
        "Never have I ever re-gifted a present.",
    ],
    "confessions": [
        "Confess something you've never told anyone.",
        "Confess the most embarrassing thing you've done this year.",
        "Confess a secret you've been keeping from your best friend.",
        "Confess the worst lie you've ever told.",
        "Confess something you're secretly proud of.",
        "Confess a weird habit you have when you're alone.",
        "Confess the last thing that made you cry.",
        "Confess something you wish you'd done differently.",
        "Confess a fear you haven't told anyone about.",
        "Confess the biggest risk you've ever taken.",
        "Confess something small that brings you joy.",
        "Confess a time you were completely wrong about someone.",
        "Confess the last time you felt truly happy.",
        "Confess something you're jealous of.",
        "Confess an unpopular opinion you hold.",
        "Confess a moment you wish you could relive.",
        "Confess something you want to say to someone but haven't.",
        "Confess the hardest thing you've ever done.",
        "Confess what you're really looking forward to right now.",
        "Confess a small act of kindness you've done recently.",
    ],
    "three_words": [
        "Describe your day.",
        "Describe your mood right now.",
        "Describe your best friend.",
        "Describe your crush.",
        "Describe your dream vacation.",
        "Describe your favourite food.",
        "Describe your childhood.",
        "Describe your biggest fear.",
        "Describe your pet.",
        "Describe the weather today.",
        "Describe your weekend plans.",
        "Describe your morning routine.",
        "Describe your hometown.",
        "Describe your boss or teacher.",
        "Describe your phone wallpaper.",
        "Describe your style.",
        "Describe a perfect date.",
        "Describe your last dream.",
        "Describe your superpower (if you had one).",
        "Describe your current love life.",
    ],
}


# ─── Helpers ───────────────────────────────────────────────
def get_pool() -> asyncpg.Pool:
    """Return the shared asyncpg pool; raise 503 if not initialized yet."""
    if _pool is None:
        raise HTTPException(status_code=503, detail="Games DB pool not initialized")
    return _pool


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
            "SELECT id, username, display_name FROM users WHERE id = $1 AND is_active = TRUE",
            uuid.UUID(data["user_id"]),
        )
    if not user:
        raise HTTPException(401, "User not found")
    return dict(user)


def _parse_uuid(value: str, label: str = "id") -> uuid.UUID:
    try:
        return uuid.UUID(value)
    except (ValueError, AttributeError):
        raise HTTPException(400, f"Invalid {label}")


def _fmt_user(row: Any) -> dict:
    """Safely build a user dict from a record (may be None)."""
    if row is None:
        return None
    if isinstance(row, dict):
        return {
            "id": str(row.get("id")),
            "username": row.get("username") or "Anonymous",
            "display_name": row.get("display_name") or "Anonymous",
        }
    return {
        "id": str(row["id"]),
        "username": row["username"] or "Anonymous",
        "display_name": row["display_name"] or "Anonymous",
    }


def _validate_answer(game_type: str, text: str) -> str:
    """Validate/normalize an answer for the game type. Raises 400 on failure."""
    text = (text or "").strip()
    if not text:
        raise HTTPException(400, "Answer text is required")

    if game_type == "never_have_i_ever":
        normalized = text.lower().strip()
        if normalized not in ("yes", "no"):
            raise HTTPException(400, "Answer must be 'yes' or 'no'")
        return normalized

    if game_type == "three_words":
        # exactly three words (allow internal hyphens/apostrophes within a word)
        words = [w for w in text.replace("-", " ").split() if w]
        if len(words) != 3:
            raise HTTPException(400, "Answer must be exactly 3 words")
        return " ".join(words)

    # confessions: free text, capped
    return text[:1000]


def _score_for_answer(game_type: str, text: str) -> int:
    """Compute the score delta for an answer at submit time."""
    if game_type == "never_have_i_ever":
        return 1 if text.strip().lower() == "yes" else 0
    # confessions and three_words: score starts at 0; upvotes increment it later
    return 0


def _is_anonymous(game_type: str) -> bool:
    return game_type == "confessions"


# ─── Request models ─────────────────────────────────────────
class StartGameReq(BaseModel):
    game_type: str = Field(..., description="never_have_i_ever | confessions | three_words")
    chat_id: str = Field(..., description="UUID of the chat to start the game in")


class AnswerReq(BaseModel):
    text: str = Field(..., min_length=1, max_length=1000)


# ─── Init: tables + seed prompts ───────────────────────────
async def init_games(pool: asyncpg.Pool) -> None:
    """
    Idempotent schema setup + seed for the games feature.

    CREATE TABLE IF NOT EXISTS is used everywhere so this is safe to run
    against a database already migrated by main.py's init_db().
    """
    global _pool
    _pool = pool
    async with pool.acquire() as conn:
        # ── Core tables ──
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS game_sessions (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                game_type VARCHAR(30) NOT NULL,
                chat_id UUID REFERENCES chats(id) ON DELETE CASCADE,
                started_by UUID REFERENCES users(id) ON DELETE SET NULL,
                status VARCHAR(20) DEFAULT 'active',
                current_turn UUID,
                round INT DEFAULT 1,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                ended_at TIMESTAMPTZ
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS game_questions (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                session_id UUID REFERENCES game_sessions(id) ON DELETE CASCADE,
                question_text TEXT NOT NULL,
                question_type VARCHAR(20) DEFAULT 'prompt',
                round INT DEFAULT 1,
                asked_to UUID,
                answered BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS game_answers (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                session_id UUID REFERENCES game_sessions(id) ON DELETE CASCADE,
                question_id UUID REFERENCES game_questions(id) ON DELETE CASCADE,
                user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                answer_text TEXT NOT NULL,
                score INT DEFAULT 0,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS game_prompts (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                game_type VARCHAR(30) NOT NULL,
                prompt_text TEXT NOT NULL,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        """)

        # ── Seed prompts (20 per game type). Idempotent by prompt_text. ──
        for game_type, prompts in SEED_PROMPTS.items():
            for p in prompts:
                await conn.execute(
                    """
                    INSERT INTO game_prompts (game_type, prompt_text)
                    SELECT $1, $2
                    WHERE NOT EXISTS (
                        SELECT 1 FROM game_prompts
                        WHERE game_type = $1 AND prompt_text = $2
                    )
                    """,
                    game_type, p,
                )

        # ── Defensive indexes (speed up the hot read paths) ──
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_game_sessions_chat "
            "ON game_sessions(chat_id);"
        )
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_game_questions_session "
            "ON game_questions(session_id);"
        )
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_game_answers_session "
            "ON game_answers(session_id);"
        )
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_game_prompts_type "
            "ON game_prompts(game_type);"
        )

    print("✅ games router ready "
          "(game_sessions/game_questions/game_answers/game_prompts + 60 prompts)")


# ─── Internal: draw a random prompt ───────────────────────
async def _draw_prompt(conn, game_type: str) -> str:
    """Pick a random prompt of the given game type. Falls back gracefully."""
    row = await conn.fetchrow(
        "SELECT prompt_text FROM game_prompts "
        "WHERE game_type = $1 ORDER BY RANDOM() LIMIT 1",
        game_type,
    )
    if row:
        return row["prompt_text"]
    # Should never happen (we seed 20 per type) — but keep it safe.
    return "Tell us something interesting about yourself."


async def _chat_participants(conn, chat_id: uuid.UUID, user_id: uuid.UUID) -> List[uuid.UUID]:
    """Return the two participants of a chat, caller first."""
    row = await conn.fetchrow(
        "SELECT user1_id, user2_id FROM chats WHERE id = $1", chat_id
    )
    if not row:
        raise HTTPException(404, "Chat not found")
    u1, u2 = row["user1_id"], row["user2_id"]
    if u2 is None:
        # link chat / one-sided — only the caller plays
        return [u1] if u1 == user_id else [user_id]
    if u1 == user_id:
        return [u1, u2]
    if u2 == user_id:
        return [u2, u1]
    raise HTTPException(403, "You are not a participant in this chat")


async def _fetch_session(conn, session_id: uuid.UUID) -> dict:
    row = await conn.fetchrow(
        """SELECT s.*, u1.username AS starter_username,
                  u1.display_name AS starter_display_name
           FROM game_sessions s
           LEFT JOIN users u1 ON s.started_by = u1.id
           WHERE s.id = $1""",
        session_id,
    )
    if not row:
        raise HTTPException(404, "Game session not found")
    return dict(row)


async def _session_state(conn, session: dict) -> dict:
    """Build the full game-state payload (current question, scores, turn)."""
    session_id = session["id"]
    players = await conn.fetch(
        """SELECT u.id, u.username, u.display_name,
                  COALESCE(SUM(ga.score), 0) AS score
           FROM users u
           JOIN chats c ON (c.id = $1 AND (c.user1_id = u.id OR c.user2_id = u.id))
           LEFT JOIN game_answers ga ON ga.user_id = u.id AND ga.session_id = $2
           GROUP BY u.id, u.username, u.display_name
           ORDER BY u.id""",
        session["chat_id"], session_id,
    )
    scores = [
        {
            "user_id": str(p["id"]),
            "username": p["username"] or "Anonymous",
            "display_name": p["display_name"] or "Anonymous",
            "score": p["score"],
        }
        for p in players
    ]

    # Current (unanswered) question, if any — newest first
    current = await conn.fetchrow(
        """SELECT q.id, q.question_text, q.question_type, q.round,
                  q.asked_to, q.answered, q.created_at,
                  u.username AS asked_to_username,
                  u.display_name AS asked_to_display_name
           FROM game_questions q
           LEFT JOIN users u ON q.asked_to = u.id
           WHERE q.session_id = $1
           ORDER BY q.created_at DESC LIMIT 1""",
        session_id,
    )
    current_question = None
    if current:
        anonymous = _is_anonymous(session["game_type"])
        # Answers for the current question (anonymized for confessions)
        ans_rows = await conn.fetch(
            """SELECT a.id, a.user_id, a.answer_text, a.score, a.created_at,
                      u.username, u.display_name
               FROM game_answers a
               LEFT JOIN users u ON a.user_id = u.id
               WHERE a.question_id = $1
               ORDER BY a.created_at ASC""",
            current["id"],
        )
        answers = []
        for a in ans_rows:
            if anonymous:
                answers.append({
                    "id": str(a["id"]),
                    "answer_text": a["answer_text"],
                    "score": a["score"],
                    "user_id": None,         # anonymous
                    "username": "Anonymous",
                    "display_name": "Anonymous",
                    "created_at": a["created_at"].isoformat() if a["created_at"] else None,
                })
            else:
                answers.append({
                    "id": str(a["id"]),
                    "answer_text": a["answer_text"],
                    "score": a["score"],
                    "user_id": str(a["user_id"]) if a["user_id"] else None,
                    "username": a["username"] or "Anonymous",
                    "display_name": a["display_name"] or "Anonymous",
                    "created_at": a["created_at"].isoformat() if a["created_at"] else None,
                })
        current_question = {
            "id": str(current["id"]),
            "text": current["question_text"],
            "question_type": current["question_type"],
            "round": current["round"],
            "asked_to": str(current["asked_to"]) if current["asked_to"] else None,
            "asked_to_username": current["asked_to_username"] or "Anonymous",
            "asked_to_display_name": current["asked_to_display_name"] or "Anonymous",
            "answered": current["answered"],
            "answers": answers,
            "created_at": current["created_at"].isoformat() if current["created_at"] else None,
        }

    return {
        "session_id": str(session_id),
        "game_type": session["game_type"],
        "game_name": _GAME_TYPE_BY_KEY.get(session["game_type"], {}).get("name"),
        "chat_id": str(session["chat_id"]) if session["chat_id"] else None,
        "status": session["status"],
        "round": session["round"],
        "current_turn": str(session["current_turn"]) if session["current_turn"] else None,
        "started_by": str(session["started_by"]) if session["started_by"] else None,
        "started_by_username": session.get("starter_username") or "Anonymous",
        "started_by_display_name": session.get("starter_display_name") or "Anonymous",
        "created_at": session["created_at"].isoformat() if session.get("created_at") else None,
        "ended_at": session["ended_at"].isoformat() if session.get("ended_at") else None,
        "scores": scores,
        "current_question": current_question,
    }


# ─── 1. GET /api/games — list available game modes ─────────
@router.get("")
async def list_game_modes(user: dict = Depends(get_current_user)):
    """List the three available Q&A game modes."""
    return [
        {
            "game_type": m["game_type"],
            "name": m["name"],
            "description": m["description"],
            "answer_hint": m["answer_hint"],
            "max_rounds": m["max_rounds"],
        }
        for m in GAME_MODES_INFO
    ]


# ─── 2. POST /api/games/start — start a game session ───────
@router.post("/start")
async def start_game(req: StartGameReq, user: dict = Depends(get_current_user)):
    """Start a new game session in a chat. Caller becomes the starter."""
    if req.game_type not in GAME_TYPES:
        raise HTTPException(
            400,
            f"Invalid game_type. Must be one of: {', '.join(GAME_TYPES)}",
        )
    chat_id = _parse_uuid(req.chat_id, "chat_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        participants = await _chat_participants(conn, chat_id, user["id"])
        if not participants:
            raise HTTPException(400, "Chat has no participants")

        # Enforce one active game per chat at a time
        active = await conn.fetchval(
            "SELECT id FROM game_sessions WHERE chat_id = $1 AND status = 'active'",
            chat_id,
        )
        if active:
            raise HTTPException(409, "An active game already exists for this chat")

        session_id = uuid.uuid4()
        starter = user["id"]
        first_player = participants[0]  # caller goes first
        await conn.execute(
            """INSERT INTO game_sessions
                   (id, game_type, chat_id, started_by, status, current_turn, round)
               VALUES ($1, $2, $3, $4, 'active', $5, 1)""",
            session_id, req.game_type, chat_id, starter, first_player,
        )

        # Draw the first prompt and create the first question row
        prompt = await _draw_prompt(conn, req.game_type)
        qid = uuid.uuid4()
        await conn.execute(
            """INSERT INTO game_questions
                   (id, session_id, question_text, question_type, round, asked_to, answered)
               VALUES ($1, $2, $3, 'prompt', 1, $4, FALSE)""",
            qid, session_id, prompt, first_player,
        )

        session = await _fetch_session(conn, session_id)
        state = await _session_state(conn, session)
    return state


# ─── 3. GET /api/games/{session_id} — game state ───────────
@router.get("/{session_id}")
async def get_game_state(session_id: str, user: dict = Depends(get_current_user)):
    sid = _parse_uuid(session_id, "session_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        session = await _fetch_session(conn, sid)
        # Membership check: caller must be a participant of the chat
        participants = await _chat_participants(conn, session["chat_id"], user["id"])
        state = await _session_state(conn, session)
    return state


# ─── 4. POST /api/games/{session_id}/answer — submit answer ─
@router.post("/{session_id}/answer")
async def submit_answer(
    session_id: str,
    req: AnswerReq,
    user: dict = Depends(get_current_user),
):
    sid = _parse_uuid(session_id, "session_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        session = await _fetch_session(conn, sid)
        if session["status"] != "active":
            raise HTTPException(400, "Game is not active")
        # Caller must be a participant
        participants = await _chat_participants(conn, session["chat_id"], user["id"])
        if user["id"] not in participants:
            raise HTTPException(403, "You are not a participant in this game")

        # Fetch the current (latest, unanswered) question
        current = await conn.fetchrow(
            """SELECT * FROM game_questions
               WHERE session_id = $1
               ORDER BY created_at DESC LIMIT 1""",
            sid,
        )
        if not current:
            raise HTTPException(400, "No active question to answer")
        if current["answered"]:
            raise HTTPException(400, "Current question already answered")

        # For "asked_to" games, the current_turn player must answer
        if current["asked_to"] and current["asked_to"] != user["id"]:
            raise HTTPException(403, "It's not your turn to answer")

        # Prevent duplicate answer for the same question by the same user
        already = await conn.fetchval(
            "SELECT 1 FROM game_answers WHERE question_id = $1 AND user_id = $2",
            current["id"], user["id"],
        )
        if already:
            raise HTTPException(409, "You already answered this question")

        normalized = _validate_answer(session["game_type"], req.text)
        score = _score_for_answer(session["game_type"], normalized)
        aid = uuid.uuid4()
        await conn.execute(
            """INSERT INTO game_answers
                   (id, session_id, question_id, user_id, answer_text, score)
               VALUES ($1, $2, $3, $4, $5, $6)""",
            aid, sid, current["id"], user["id"], normalized, score,
        )

        # Mark the question answered as soon as the asked_to player answers.
        # The game is turn-based: each question is posed to ONE player
        # (current_turn / asked_to), so a single valid answer completes it.
        # If asked_to is NULL (no turn-owner), the question is open and we
        # accept answers from everyone — mark answered once all participants
        # have submitted.
        if current["asked_to"]:
            await conn.execute(
                "UPDATE game_questions SET answered = TRUE WHERE id = $1",
                current["id"],
            )
        else:
            expected = len(participants)
            answered_count = await conn.fetchval(
                "SELECT COUNT(*) FROM game_answers WHERE question_id = $1",
                current["id"],
            )
            if answered_count >= expected:
                await conn.execute(
                    "UPDATE game_questions SET answered = TRUE WHERE id = $1",
                    current["id"],
                )

        session = await _fetch_session(conn, sid)
        state = await _session_state(conn, session)
    return state


# ─── 5. POST /api/games/{session_id}/next — advance round ───
@router.post("/{session_id}/next")
async def next_question(session_id: str, user: dict = Depends(get_current_user)):
    sid = _parse_uuid(session_id, "session_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        session = await _fetch_session(conn, sid)
        if session["status"] != "active":
            raise HTTPException(400, "Game is not active")
        participants = await _chat_participants(conn, session["chat_id"], user["id"])
        if user["id"] not in participants:
            raise HTTPException(403, "You are not a participant in this game")

        # Enforce: current question must be answered before advancing
        current = await conn.fetchrow(
            """SELECT * FROM game_questions
               WHERE session_id = $1
               ORDER BY created_at DESC LIMIT 1""",
            sid,
        )
        if current and not current["answered"]:
            raise HTTPException(400, "Current question hasn't been answered by all players yet")

        # Advance round; swap the current_turn
        next_round = (session["round"] or 1) + 1
        # Rotate turn: if more than one participant, pick the OTHER one
        if len(participants) > 1:
            prev_turn = session["current_turn"] or participants[0]
            next_player = participants[1] if prev_turn == participants[0] else participants[0]
        else:
            next_player = participants[0]

        await conn.execute(
            "UPDATE game_sessions SET round = $1, current_turn = $2 WHERE id = $3",
            next_round, next_player, sid,
        )

        # Draw a new prompt and create a new question row
        prompt = await _draw_prompt(conn, session["game_type"])
        qid = uuid.uuid4()
        await conn.execute(
            """INSERT INTO game_questions
                   (id, session_id, question_text, question_type, round, asked_to, answered)
               VALUES ($1, $2, $3, 'prompt', $4, $5, FALSE)""",
            qid, sid, prompt, next_round, next_player,
        )

        session = await _fetch_session(conn, sid)
        state = await _session_state(conn, session)
    return state


# ─── 6. DELETE /api/games/{session_id} — end session ────────
@router.delete("/{session_id}")
async def end_game(session_id: str, user: dict = Depends(get_current_user)):
    sid = _parse_uuid(session_id, "session_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        session = await _fetch_session(conn, sid)
        # Only the starter or a participant may end the game
        participants = await _chat_participants(conn, session["chat_id"], user["id"])
        if user["id"] != session["started_by"] and user["id"] not in participants:
            raise HTTPException(403, "Only a participant can end this game")
        await conn.execute(
            "UPDATE game_sessions SET status = 'ended', ended_at = NOW() WHERE id = $1",
            sid,
        )
    return {"ok": True, "session_id": str(sid), "status": "ended"}


# ─── 7. GET /api/games/{session_id}/results — final results ─
@router.get("/{session_id}/results")
async def game_results(session_id: str, user: dict = Depends(get_current_user)):
    sid = _parse_uuid(session_id, "session_id")
    pool = get_pool()
    async with pool.acquire() as conn:
        session = await _fetch_session(conn, sid)
        participants = await _chat_participants(conn, session["chat_id"], user["id"])
        if user["id"] not in participants and user["id"] != session["started_by"]:
            raise HTTPException(403, "Only a participant can view results")

        scores = await conn.fetch(
            """SELECT u.id, u.username, u.display_name,
                      COALESCE(SUM(ga.score), 0) AS score,
                      COUNT(ga.id) AS answers_count
               FROM users u
               JOIN chats c ON (c.id = $1 AND (c.user1_id = u.id OR c.user2_id = u.id))
               LEFT JOIN game_answers ga ON ga.user_id = u.id AND ga.session_id = $2
               GROUP BY u.id, u.username, u.display_name
               ORDER BY score DESC, u.id""",
            session["chat_id"], sid,
        )
        total_questions = await conn.fetchval(
            "SELECT COUNT(*) FROM game_questions WHERE session_id = $1", sid
        )
        total_answers = await conn.fetchval(
            "SELECT COUNT(*) FROM game_answers WHERE session_id = $1", sid
        )

        # Determine winner (highest score; tie → "tie")
        score_list = [
            {
                "user_id": str(s["id"]),
                "username": s["username"] or "Anonymous",
                "display_name": s["display_name"] or "Anonymous",
                "score": s["score"],
                "answers_count": s["answers_count"],
            }
            for s in scores
        ]
        winner = None
        if score_list:
            top = score_list[0]
            tied = [s for s in score_list if s["score"] == top["score"]]
            if len(tied) > 1:
                winner = {"type": "tie", "users": tied}
            else:
                winner = {"type": "winner", "user": top}

    return {
        "session_id": str(sid),
        "game_type": session["game_type"],
        "game_name": _GAME_TYPE_BY_KEY.get(session["game_type"], {}).get("name"),
        "status": session["status"],
        "round": session["round"],
        "started_by": str(session["started_by"]) if session["started_by"] else None,
        "created_at": session["created_at"].isoformat() if session.get("created_at") else None,
        "ended_at": session["ended_at"].isoformat() if session.get("ended_at") else None,
        "total_questions": total_questions,
        "total_answers": total_answers,
        "scores": score_list,
        "winner": winner,
    }
