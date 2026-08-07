#!/usr/bin/env python3
"""
Seed Whispr feed with LLM-authored anonymous posts so the feed isn't empty.
Idempotent-ish: skips seeding if seed users already exist.
Run: sudo -u postgres python3 seed_feed.py   (uses DATABASE_URL from env or default)
"""
import os, asyncio, random, uuid
from datetime import datetime, timedelta, timezone
import asyncpg, bcrypt

DB_DSN = os.getenv("DATABASE_URL", "postgresql://whispr@/whispr_db")

# ── Persona seed users (anonymous vibe) ──
PERSONAS = [
    ("nightowl_2am",   "Night Owl"),
    ("lostinjakarta",  "Lost in the City"),
    ("quietstorm",     "Quiet Storm"),
    ("cafedreamer",    "Cafe Dreamer"),
    ("bluewhisper",    "Blue Whisper"),
    ("wanderghost",    "Wander Ghost"),
    ("softspoken",     "Softspoken"),
    ("midnightmind",   "Midnight Mind"),
    ("paperplane_",    "Paper Plane"),
    ("velvetnoise",    "Velvet Noise"),
]

BG = ["none","none","none","violet","sunset","ocean","mint","candy","midnight","aurora","grape","peach"]

# ── LLM-authored posts (content, tags, bg_id) ──
POSTS = [
    ("Kadang gue kangen sama orang yang bahkan udah lupa gue pernah ada. Aneh ya.", ["curhat","malam"], "midnight"),
    ("If you could restart one year of your life, which one would you pick? Gue pilih 2019 tanpa mikir.", ["question"], "violet"),
    ("Baru sadar temen deket gue tinggal 2 km dari sini selama ini dan kita ga pernah ketemu langsung 😂", ["nearby","random"], "none"),
    ("Confession: gue pura-pura sibuk padahal cuma takut ngobrol duluan. Anyone else?", ["confession"], "sunset"),
    ("It's 2am and I'm wondering if the people I miss ever think about me too.", ["latenight","feelings"], "ocean"),
    ("Hari ini akhirnya berani resign. Takut banget tapi lega. Doain ya strangers.", ["milestone","brave"], "mint"),
    ("Anyone here also eats dinner alone and it's actually kinda peaceful?", ["random"], "none"),
    ("Gue ga pernah bilang ke siapa-siapa: gue masih simpan chat terakhir sama nyokap sebelum beliau pergi.", ["confession","family"], "grape"),
    ("Pertanyaan random: kalian percaya orang bisa berubah 100%? Atau cuma pinter nyembunyiin?", ["question","deep"], "none"),
    ("Kota ini rame banget tapi kok gue ngerasa paling sepi ya. Ada yang relate?", ["curhat","lonely"], "midnight"),
    ("Just moved to a new city and I don't know a single soul. Say hi if you're nearby 👋", ["nearby","newhere"], "aurora"),
    ("Gue jatuh cinta sama suara orang di sebelah gue di kereta tadi. Ga sempet kenalan. Regret.", ["confession","random"], "candy"),
    ("What's a small thing today that made you smile? Gue: kucing liar mau gue elus buat pertama kali.", ["question","wholesome"], "peach"),
    ("Sometimes I write long messages to people and delete them all. This app feels safer somehow.", ["feelings"], "none"),
    ("Confession: umur 27 dan masih ga tau mau jadi apa. Dan itu ternyata ga apa-apa.", ["confession","growth"], "violet"),
    ("Ada yang lagi begadang juga? Temenin dong lewat post random kalian.", ["latenight","nearby"], "none"),
    ("I think I'm finally healing. Bulan lalu gue ga bisa bangun dari kasur. Hari ini gue masak sendiri.", ["milestone","mentalhealth"], "mint"),
    ("Unpopular opinion: sendirian di bioskop itu healing, bukan menyedihkan.", ["random","hottake"], "none"),
    ("Kalau kalian bisa kirim satu pesan ke diri lu 5 tahun lalu, isinya apa?", ["question","deep"], "ocean"),
    ("Gue diam-diam ngefans sama barista di kafe deket kantor. Dia ga akan pernah tau lewat post ini kan 🙈", ["confession","nearby"], "sunset"),
    ("Malam ini hujan dan gue cuma pengen ada yang bilang 'semua bakal baik-baik aja' tanpa nanya kenapa.", ["curhat","feelings"], "midnight"),
    ("Anyone else feel like they're the side character in everyone else's story?", ["deep","lonely"], "none"),
    ("Baru putus kemarin. Bukan minta kasihan, cuma pengen nulis biar dadanya ga sesek.", ["curhat","heartbreak"], "grape"),
    ("What song are you playing on loop right now? Gue butuh rekomendasi buat nemenin insomnia.", ["question","music"], "none"),
    ("Gue orang yang selalu ngingetin temen buat makan tapi lupa makan sendiri. Relate ga sih.", ["random"], "peach"),
    ("Confession: gue baik ke semua orang bukan karena kuat, tapi karena takut ditinggalin.", ["confession","deep"], "aurora"),
    ("Ada yang tinggal di area Selatan? Pengen ada temen jalan pagi tanpa harus banyak ngomong.", ["nearby"], "none"),
    ("Today marks 90 days sober. Ga nyangka bisa sampai sini. Terima kasih ruang anonim ini.", ["milestone"], "mint"),
    ("Kadang gue mikir, mungkin orang yang paling ramah itu yang paling capek di dalam.", ["deep","feelings"], "none"),
    ("If nobody told you today: you're doing better than you think. Beneran.", ["wholesome"], "violet"),
]

async def main():
    pool = await asyncpg.create_pool(DB_DSN, min_size=1, max_size=4)
    async with pool.acquire() as conn:
        # skip if already seeded
        already = await conn.fetchval("SELECT 1 FROM users WHERE username = $1", PERSONAS[0][0])
        if already:
            print("Seed users already exist — skipping. (delete them first to re-seed)")
            await pool.close(); return

        # create persona users (unusable password — seed only)
        uids = {}
        dead_hash = bcrypt.hashpw(secrets_token().encode(), bcrypt.gensalt()).decode()
        for uname, dname in PERSONAS:
            uid = await conn.fetchval(
                """INSERT INTO users (username, display_name, password_hash, karma, days_active)
                   VALUES ($1,$2,$3,$4,$5) RETURNING id""",
                uname, dname, dead_hash, random.randint(3, 240), random.randint(1, 120)
            )
            uids[uname] = uid
        print(f"Created {len(uids)} persona users")

        now = datetime.now(timezone.utc)
        unames = list(uids.keys())
        n = 0
        for i, (content, tags, bg) in enumerate(POSTS):
            author = uids[random.choice(unames)]
            # spread timestamps across the last ~5 days, newest first-ish
            created = now - timedelta(minutes=random.randint(5, 60*24*5))
            bg_type = "gradient" if bg != "none" else "none"
            bg_value = bg if bg != "none" else None
            pid = uuid.uuid4()
            await conn.execute(
                """INSERT INTO posts (id, author_id, content, is_once_view, bg_type, bg_value,
                                      upvotes, replies_count, created_at)
                   VALUES ($1,$2,$3,FALSE,$4,$5,$6,$7,$8)""",
                pid, author, content, bg_type, bg_value,
                random.randint(0, 84), random.randint(0, 23), created
            )
            for t in tags[:5]:
                await conn.execute(
                    "INSERT INTO post_tags (post_id, tag) VALUES ($1,$2) ON CONFLICT DO NOTHING",
                    pid, t.lower()[:50]
                )
            n += 1
        print(f"Seeded {n} posts")
    await pool.close()

def secrets_token():
    import secrets
    return secrets.token_hex(16)

if __name__ == "__main__":
    asyncio.run(main())
