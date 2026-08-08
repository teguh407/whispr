"""
Whispr Discovery/Matching Router
─────────────────────────────────
Radius-based + interest + karma user discovery.

Inspired by:
  • Emerald — karma-tier matching (Newcomer/Regular/Trusted/VIP)
  • Hush fix — radius matching via Haversine (NOT exact distance), CLOSER→FARTHER

Endpoints:
  POST /api/discovery/match    — matched users based on filters
  GET  /api/discovery/interests — available interest tags catalog
  PUT  /api/me/interests        — set the caller's interest tags (max 5)
  PUT  /api/me/location         — set the caller's lat/lng
  GET  /api/me/nearby           — users within radius, sorted closer→farther

Auth: JWT Bearer (matches main.py's get_current_user pattern).
DB:   asyncpg pool from main.py (db_pool global).
"""
import math
import uuid
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field

# Import the shared DB pool + auth dependency + karma helper from main.py.
# main.py must be importable as a module (it is the FastAPI entrypoint).
from main import db_pool, get_current_user, karma_level

router = APIRouter()

# ─── Curated interest catalog ──────────────────────────────
# The canonical list of selectable interest tags. Stored on the users
# table as a TEXT[] array column (users.interests). Keeping a fixed
# catalog lets the client render a picker and lets us validate input.
INTEREST_CATALOG: List[dict] = [
    {"tag": "gaming", "label": "Gaming", "emoji": "🎮"},
    {"tag": "music", "label": "Music", "emoji": "🎵"},
    {"tag": "movies", "label": "Movies & TV", "emoji": "🎬"},
    {"tag": "anime", "label": "Anime", "emoji": "🍥"},
    {"tag": "art", "label": "Art & Design", "emoji": "🎨"},
    {"tag": "reading", "label": "Reading", "emoji": "📚"},
    {"tag": "tech", "label": "Technology", "emoji": "💻"},
    {"tag": "science", "label": "Science", "emoji": "🔬"},
    {"tag": "travel", "label": "Travel", "emoji": "✈️"},
    {"tag": "food", "label": "Food & Cooking", "emoji": "🍜"},
    {"tag": "fitness", "label": "Fitness", "emoji": "🏋️"},
    {"tag": "sports", "label": "Sports", "emoji": "⚽"},
    {"tag": "photography", "label": "Photography", "emoji": "📷"},
    {"tag": "nature", "label": "Nature & Outdoors", "emoji": "🌿"},
    {"tag": "dating", "label": "Dating & Crushes", "emoji": "💞"},
    {"tag": "vent", "label": "Venting Space", "emoji": "🌧️"},
    {"tag": "confessions", "label": "Confessions", "emoji": "🤫"},
    {"tag": "late_night", "label": "Late Night Talks", "emoji": "🌙"},
    {"tag": "memes", "label": "Memes & Humor", "emoji": "😂"},
    {"tag": "philosophy", "label": "Philosophy", "emoji": "🧠"},
    {"tag": "spirituality", "label": "Spirituality", "emoji": "🕉️"},
    {"tag": "lifestyle", "label": "Lifestyle", "emoji": "☕"},
    {"tag": "fashion", "label": "Fashion", "emoji": "👗"},
    {"tag": "pets", "label": "Pets", "emoji": "🐾"},
    {"tag": "cars", "label": "Cars", "emoji": "🚗"},
    {"tag": "diy", "label": "DIY & Crafts", "emoji": "🛠️"},
]
_VALID_TAGS = {t["tag"] for t in INTEREST_CATALOG}

# Karma tier order (lower index = lower tier). Used to find "similar" tiers.
_KARMA_TIERS = ["Newcomer", "Regular", "Trusted", "VIP"]

# ─── Request Models ─────────────────────────────────────────
class InterestsReq(BaseModel):
    interests: List[str] = Field(..., min_length=1, max_length=5)


class LocationReq(BaseModel):
    lat: float = Field(..., ge=-90, le=90)
    lng: float = Field(..., ge=-180, le=180)
    city: Optional[str] = None


# ─── Haversine ──────────────────────────────────────────────
EARTH_RADIUS_KM = 6371.0


def haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Great-circle distance in km between two lat/lng points (Haversine)."""
    rlat1, rlng1, rlat2, rlng2 = map(math.radians, (lat1, lng1, lat2, lng2))
    dlat = rlat2 - rlat1
    dlng = rlng2 - rlng1
    a = (
        math.sin(dlat / 2) ** 2
        + math.cos(rlat1) * math.cos(rlat2) * math.sin(dlng / 2) ** 2
    )
    return 2 * EARTH_RADIUS_KM * math.asin(math.sqrt(a))


def _shared_interests(user_interests: List[str], mine: List[str]) -> List[str]:
    """Return the intersection of two interest-tag lists (lowercased)."""
    mine_set = {t.lower() for t in mine}
    return [t for t in user_interests if t.lower() in mine_set]


def _karma_tier_for(karma: int) -> str:
    return karma_level(karma)


# ─── Endpoints ──────────────────────────────────────────────

@router.get("/api/discovery/interests")
async def list_interests():
    """Return the available interest-tag catalog for the picker UI."""
    return {"interests": INTEREST_CATALOG, "count": len(INTEREST_CATALOG)}


@router.put("/api/me/interests")
async def set_my_interests(req: InterestsReq, user=Depends(get_current_user)):
    """Set the caller's interest tags (1–5). Tags must exist in the catalog."""
    normalized = []
    for tag in req.interests:
        t = tag.lower().strip()
        if t not in _VALID_TAGS:
            raise HTTPException(400, f"Unknown interest tag: '{tag}'. See /api/discovery/interests.")
        if t not in normalized:
            normalized.append(t)
    if not normalized:
        raise HTTPException(400, "At least one interest tag is required.")
    if len(normalized) > 5:
        raise HTTPException(400, "Maximum 5 interest tags allowed.")
    async with db_pool.acquire() as conn:
        await conn.execute(
            "UPDATE users SET interests = $1 WHERE id = $2",
            normalized,
            user["id"],
        )
    return {"interests": normalized, "count": len(normalized)}


@router.put("/api/me/location")
async def set_my_location(req: LocationReq, user=Depends(get_current_user)):
    """Set the caller's lat/lng (and optional city) for radius matching."""
    async with db_pool.acquire() as conn:
        if req.city:
            await conn.execute(
                "UPDATE users SET lat = $1, lng = $2, city = $3 WHERE id = $4",
                req.lat,
                req.lng,
                req.city,
                user["id"],
            )
        else:
            await conn.execute(
                "UPDATE users SET lat = $1, lng = $2 WHERE id = $3",
                req.lat,
                req.lng,
                user["id"],
            )
    return {"lat": req.lat, "lng": req.lng, "city": req.city}


@router.get("/api/me/nearby")
async def list_nearby_users(
    radius_km: int = Query(10, ge=1, le=100),
    user=Depends(get_current_user),
):
    """List active users within radius_km of the caller, sorted CLOSER→FARTHER.

    Distance is computed with the Haversine formula (not exact/street distance).
    Excludes the caller and any user lacking lat/lng.
    """
    my_lat, my_lng = user.get("lat"), user.get("lng")
    if my_lat is None or my_lng is None:
        raise HTTPException(400, "Set your location first (PUT /api/me/location).")

    # Rough bounding-box pre-filter in SQL to cut the candidate set, then
    # precise Haversine in Python. ~1 deg ≈ 111 km; pad by a small margin.
    deg_pad = (radius_km / 111.0) + 0.05
    async with db_pool.acquire() as conn:
        rows = await conn.fetch(
            """
            SELECT id, username, display_name, avatar_url, karma, days_active,
                   interests, lat, lng
            FROM users
            WHERE is_active = TRUE
              AND id <> $1
              AND lat IS NOT NULL AND lng IS NOT NULL
              AND lat BETWEEN $2 AND $3
              AND lng BETWEEN $4 AND $5
            """,
            user["id"],
            my_lat - deg_pad,
            my_lat + deg_pad,
            my_lng - deg_pad,
            my_lng + deg_pad,
        )

    mine = user.get("interests") or []
    results = []
    for r in rows:
        dist = haversine_km(my_lat, my_lng, r["lat"], r["lng"])
        if dist > radius_km:
            continue  # precise filter after Haversine
        shared = _shared_interests(list(r["interests"] or []), list(mine))
        results.append({
            "id": str(r["id"]),
            "username": r["username"],
            "display_name": r["display_name"],
            "avatar_url": r["avatar_url"],
            "karma": r["karma"],
            "karma_level": _karma_tier_for(r["karma"]),
            "distance_km": round(dist, 1),
            "shared_interests": len(shared),
            "shared_interest_tags": shared,
        })

    # Sort: closest first, then most shared interests.
    results.sort(key=lambda u: (u["distance_km"], -u["shared_interests"]))
    return {"count": len(results), "radius_km": radius_km, "users": results}


@router.post("/api/discovery/match")
async def match_users(
    radius_km: int = Query(10, ge=1, le=100),
    interests: Optional[List[str]] = Query(None),
    gender: Optional[str] = Query(None, max_length=20),
    age_min: Optional[int] = Query(None, ge=13, le=120),
    age_max: Optional[int] = Query(None, ge=13, le=120),
    karma_level_filter: Optional[str] = Query(None, alias="karma_level"),
    limit: int = Query(20, ge=1, le=100),
    user=Depends(get_current_user),
):
    """Find matched users based on filters.

    - radius_km: Haversine radius (1–100 km). Caller must have lat/lng set.
    - interests: list of tags; users with overlapping tags get priority.
    - gender: optional exact match on users.gender.
    - age_min / age_max: optional inclusive range on users.age.
    - karma_level: only return users in the same tier (Newcomer/Regular/
      Trusted/VIP) or — when the caller is near a tier boundary — the
      adjacent tier as well (Emerald-style "similar karma" matching).

    Returns matched users sorted: closest first, then most shared interests.
    """
    my_lat, my_lng = user.get("lat"), user.get("lng")
    if my_lat is None or my_lng is None:
        raise HTTPException(400, "Set your location first (PUT /api/me/location).")

    # Validate + normalize requested interest filters against the catalog.
    norm_interests: List[str] = []
    if interests:
        for tag in interests:
            t = tag.lower().strip()
            if t not in _VALID_TAGS:
                raise HTTPException(400, f"Unknown interest tag: '{tag}'. See /api/discovery/interests.")
            if t not in norm_interests:
                norm_interests.append(t)

    # Validate karma-level filter.
    if karma_level_filter and karma_level_filter not in _KARMA_TIERS:
        raise HTTPException(
            400,
            f"Invalid karma_level '{karma_level_filter}'. Choose from: {', '.join(_KARMA_TIERS)}",
        )

    # Resolve "similar karma" tiers. If a filter is given, use just that tier.
    # Otherwise use the caller's own tier plus the adjacent one (if any).
    my_karma = user.get("karma") or 0
    if karma_level_filter:
        target_tiers = {karma_level_filter}
    else:
        my_tier = _karma_tier_for(my_karma)
        idx = _KARMA_TIERS.index(my_tier)
        target_tiers = {my_tier}
        if idx + 1 < len(_KARMA_TIERS):
            target_tiers.add(_KARMA_TIERS[idx + 1])
        if idx - 1 >= 0:
            target_tiers.add(_KARMA_TIERS[idx - 1])

    # Build SQL with dynamic filters. Parameters are bound positionally.
    params: list = [user["id"]]
    param_idx = 2
    where = [
        "is_active = TRUE",
        "id <> $1",
        "lat IS NOT NULL AND lng IS NOT NULL",
    ]

    # Bounding box pre-filter for radius.
    deg_pad = (radius_km / 111.0) + 0.05
    where.append(f"lat BETWEEN ${param_idx} AND ${param_idx + 1}")
    params.extend([my_lat - deg_pad, my_lat + deg_pad]); param_idx += 2
    where.append(f"lng BETWEEN ${param_idx} AND ${param_idx + 1}")
    params.extend([my_lng - deg_pad, my_lng + deg_pad]); param_idx += 2

    if gender:
        where.append(f"gender = ${param_idx}")
        params.append(gender); param_idx += 1

    if age_min is not None and age_max is not None:
        if age_min > age_max:
            raise HTTPException(400, "age_min cannot be greater than age_max.")
        where.append(f"age BETWEEN ${param_idx} AND ${param_idx + 1}")
        params.extend([age_min, age_max]); param_idx += 2
    elif age_min is not None:
        where.append(f"age >= ${param_idx}")
        params.append(age_min); param_idx += 1
    elif age_max is not None:
        where.append(f"age <= ${param_idx}")
        params.append(age_max); param_idx += 1

    where_sql = " AND ".join(where)

    async with db_pool.acquire() as conn:
        rows = await conn.fetch(
            f"""
            SELECT id, username, display_name, avatar_url, karma, days_active,
                   interests, gender, age, lat, lng
            FROM users
            WHERE {where_sql}
            """,
            *params,
        )

    mine = user.get("interests") or []

    # Optional explicit interest filter: if provided, only keep users that
    # share at least one requested tag (still prioritized by overlap count).
    filter_tags = set(norm_interests) if norm_interests else None

    results = []
    for r in rows:
        dist = haversine_km(my_lat, my_lng, r["lat"], r["lng"])
        if dist > radius_km:
            continue  # precise Haversine filter
        their_interests = list(r["interests"] or [])
        shared = _shared_interests(their_interests, list(mine))

        # If the caller requested specific interests, require ≥1 overlap.
        if filter_tags is not None:
            if not any(t in filter_tags for t in their_interests):
                continue

        # Karma-tier filter (similar-karma matching).
        if karma_level_filter:
            # Exact tier requested — already covered above for candidates,
            # but we didn't SQL-filter karma (no tier column). Filter here.
            if _karma_tier_for(r["karma"]) not in target_tiers:
                continue
        else:
            # Similar-karma: only keep users in target_tiers.
            if _karma_tier_for(r["karma"]) not in target_tiers:
                continue

        results.append({
            "id": str(r["id"]),
            "username": r["username"],
            "display_name": r["display_name"],
            "avatar_url": r["avatar_url"],
            "karma": r["karma"],
            "karma_level": _karma_tier_for(r["karma"]),
            "distance_km": round(dist, 1),
            "shared_interests": len(shared),
            "shared_interest_tags": shared,
            "gender": r["gender"],
            "age": r["age"],
        })

    # Sort: closest first, then most shared interests (desc).
    results.sort(key=lambda u: (u["distance_km"], -u["shared_interests"]))

    return {
        "count": len(results),
        "filters": {
            "radius_km": radius_km,
            "interests": norm_interests,
            "gender": gender,
            "age_min": age_min,
            "age_max": age_max,
            "karma_level": karma_level_filter,
            "similar_karma_tiers": sorted(target_tiers),
        },
        "users": results[:limit],
    }
