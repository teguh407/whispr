#!/usr/bin/env python3
"""
Seed Whispr feed with a large batch of LLM-authored posts.
Adds post_type + mood variety. Idempotent via a sentinel tag ('seedv2').
Uses existing persona users; creates them if missing.
"""
import os, asyncio, random, uuid, secrets
from datetime import datetime, timedelta, timezone
import asyncpg, bcrypt

DB_DSN = os.getenv("DATABASE_URL", "postgresql://whispr@/whispr_db")
SENTINEL = "seedv2"

PERSONAS = [
    ("nightowl_2am", "Night Owl"), ("lostinjakarta", "Lost in the City"),
    ("quietstorm", "Quiet Storm"), ("cafedreamer", "Cafe Dreamer"),
    ("bluewhisper", "Blue Whisper"), ("wanderghost", "Wander Ghost"),
    ("softspoken", "Softspoken"), ("midnightmind", "Midnight Mind"),
    ("paperplane_", "Paper Plane"), ("velvetnoise", "Velvet Noise"),
    ("emberglow_", "Ember Glow"), ("driftwoodx", "Driftwood"),
    ("hushedecho", "Hushed Echo"), ("moonlitpath", "Moonlit Path"),
    ("silentbloom", "Silent Bloom"),
]

BG_GRADIENTS = ["violet", "sunset", "ocean", "mint", "candy", "midnight", "aurora", "grape", "peach"]

# (content, post_type, mood, tags)  — mood None allowed. bg chosen randomly.
POSTS = [
    # anonymous / curhat
    ("Kadang gue kangen sama orang yang bahkan udah lupa gue pernah ada.", "anonymous", "Lonely", ["curhat"]),
    ("Sometimes I write long messages to people and delete them all. This app feels safer.", "anonymous", "Anxious", ["curhat"]),
    ("Malam ini hujan dan gue cuma pengen ada yang bilang 'semua bakal baik-baik aja'.", "anonymous", "Sad", ["curhat", "latenight"]),
    ("Baru sadar gue lebih tenang pas ngomong ke stranger daripada ke temen sendiri.", "anonymous", "Lonely", ["random"]),
    ("Hari ini gue senyum ke orang asing di halte dan dia senyum balik. Kecil, tapi bikin hari gue.", "anonymous", "Happy", ["wholesome"]),
    ("There's a specific kind of tired that sleep doesn't fix.", "anonymous", "Sad", ["latenight"]),
    ("Gue capek jadi orang yang selalu ngertiin duluan.", "anonymous", "Angry", ["curhat"]),
    ("Ada yang lagi begadang juga? Rasanya jam 3 pagi tuh dunia lebih jujur.", "anonymous", None, ["latenight"]),
    ("I miss who I was before I started overthinking everything.", "anonymous", "Anxious", ["curhat"]),
    ("Pengen pindah kota, ganti nama, mulai dari nol. Tapi takut kehilangan yang sekarang.", "anonymous", "Anxious", ["random"]),
    # question
    ("If you could restart one year of your life, which one would you pick?", "question", None, ["question"]),
    ("Kalian kalau lagi sedih dengerin lagu apa? Butuh rekomendasi malam ini.", "question", "Sad", ["question", "music"]),
    ("What's a small thing that instantly makes your day better?", "question", "Happy", ["question"]),
    ("Menurut kalian, first love itu beneran ga pernah bener-bener hilang?", "question", None, ["question"]),
    ("Kalau bisa ngomong satu kalimat ke diri lo 5 tahun lalu, apa yang lo bilang?", "question", None, ["question"]),
    ("Is it normal to feel lonely even when you're surrounded by people?", "question", "Lonely", ["question"]),
    ("Cara kalian move on dari sesuatu yang belum sempet dimulai gimana?", "question", "Sad", ["question"]),
    ("What's something you're proud of but never get to talk about?", "question", "Happy", ["question"]),
    # confession
    ("Confession: gue pura-pura sibuk padahal cuma takut ngobrol duluan.", "confession", "Anxious", ["confession"]),
    ("I still check their profile even though I said I moved on. I didn't.", "confession", "Sad", ["confession"]),
    ("Gue pernah bohong soal 'gapapa' padahal hancur banget waktu itu.", "confession", "Sad", ["confession"]),
    ("Sometimes I pretend my phone is ringing so I don't look alone in public.", "confession", "Lonely", ["confession"]),
    ("Gue diam-diam iri sama temen yang keliatannya punya hidup lebih rapi.", "confession", "Anxious", ["confession"]),
    ("Aku belum bilang ke siapa-siapa kalau aku takut banget sama masa depan.", "confession", "Anxious", ["confession"]),
    ("I gave the best advice to a friend that I've never been able to follow myself.", "confession", None, ["confession"]),
    # milestone
    ("Hari ini akhirnya berani resign. Takut tapi lega. Doain ya strangers.", "anonymous", "Excited", ["milestone"]),
    ("30 days sober today. Nobody around me knows. But you do now.", "anonymous", "Excited", ["milestone"]),
    ("Baru selesai bayar utang terakhir. Napas gue kerasa beda hari ini.", "anonymous", "Happy", ["milestone"]),
    ("First day at a new job tomorrow. Terrified and hopeful at the same time.", "anonymous", "Anxious", ["milestone"]),
    ("Akhirnya berani ngomong 'tidak' tanpa minta maaf. Progress.", "anonymous", "Excited", ["milestone"]),
    # nearby
    ("Just moved to a new city and I don't know a single soul. Say hi.", "nearby", "Lonely", ["nearby"]),
    ("Ada anak Jaksel yang suka nongkrong sendirian di coffee shop juga ga?", "nearby", None, ["nearby"]),
    ("Anyone near downtown wanna be accountability buddies for morning runs?", "nearby", "Excited", ["nearby"]),
    ("Cari temen ngobrol yang sekota, yang ngerti rasanya jauh dari keluarga.", "nearby", "Lonely", ["nearby"]),
    # wholesome
    ("If nobody told you today: you're doing better than you think.", "anonymous", "Happy", ["wholesome"]),
    ("Reminder buat kamu yang baca ini: istirahat itu bukan tanda lemah.", "anonymous", "Happy", ["wholesome"]),
    ("You survived every bad day so far. That's a 100% success rate.", "anonymous", "Excited", ["wholesome"]),
    ("Buat yang lagi berjuang diam-diam: aku bangga sama kamu, beneran.", "anonymous", "Happy", ["wholesome"]),
    # poll-style (text)
    ("Tim rebahan atau tim produktif akhir pekan? Gue tim rebahan garis keras.", "poll", "Happy", ["poll", "random"]),
    ("Kopi atau teh buat nemenin overthinking jam 1 pagi?", "poll", None, ["poll"]),
    ("Hujan bikin tenang atau bikin sedih? Jujur aja.", "poll", "Sad", ["poll"]),
    # more anonymous variety
    ("Gue baru sadar ternyata sendirian ga selalu berarti kesepian.", "anonymous", "Happy", ["random"]),
    ("The scariest part of healing is realizing how long you tolerated the pain.", "anonymous", "Sad", ["curhat"]),
    ("Kadang pengen dipeluk aja tanpa ditanya kenapa. Gitu doang.", "anonymous", "Lonely", ["curhat"]),
    ("I talk to myself a lot. Turns out I'm decent company.", "anonymous", "Happy", ["random"]),
    ("Rindu itu aneh ya, dateng pas lagi ga siap-siapnya.", "anonymous", "Sad", ["curhat"]),
    ("Berhenti ngejar orang yang cuma inget gue pas butuh. Cukup.", "anonymous", "Angry", ["curhat"]),
    ("Some nights I just want to disappear for a while, not forever. Just a pause.", "anonymous", "Sad", ["latenight"]),
    ("Akhirnya beli bunga buat diri sendiri. Kenapa nunggu orang lain sih.", "anonymous", "Happy", ["wholesome"]),
    ("Gue mulai nulis jurnal lagi. Ternyata pikiran gue berisik banget.", "anonymous", None, ["random"]),
    ("It's okay to outgrow people who only knew the old you.", "anonymous", "Excited", ["wholesome"]),
    ("Pengen bilang 'aku ga baik-baik aja' tapi takut ngerepotin. Jadi di sini aja.", "confession", "Sad", ["confession"]),
    ("Ternyata yang paling susah itu maafin diri sendiri, bukan orang lain.", "anonymous", "Anxious", ["curhat"]),
    ("What if the person you're waiting for is also waiting for you to reach out first?", "question", None, ["question"]),
    ("Gue kangen masa kecil, pas capek cuma soal main bukan soal hidup.", "anonymous", "Sad", ["curhat"]),
    ("Today I chose myself for the first time in a long time. Feels weird. Feels right.", "anonymous", "Excited", ["milestone"]),
    ("Ada yang ngerasa lebih ekspresif lewat tulisan daripada ngomong langsung?", "question", None, ["question"]),
    ("Lagi belajar buat ga jelasin diri gue ke orang yang emang niatnya salah paham.", "anonymous", "Angry", ["random"]),
    ("Malam-malam gini kepikiran, apa kabar orang yang dulu janji ga bakal pergi ya.", "anonymous", "Sad", ["latenight"]),
    ("You don't need a reason to rest. Being human is reason enough.", "anonymous", "Happy", ["wholesome"]),
    ("Gue akhirnya block dia. Bukan karena benci, tapi karena sayang sama diri sendiri.", "confession", "Excited", ["confession"]),
    ("Kalau capek, boleh kok berhenti sebentar. Ga semua hal harus dikejar hari ini.", "anonymous", "Happy", ["wholesome"]),
    ("What's a memory you'd relive just one more time?", "question", "Sad", ["question"]),
    ("Ternyata nyaman sama kesendirian itu skill yang mahal ya.", "anonymous", None, ["random"]),
    ("I'm proud of how quietly I've been carrying everything lately.", "confession", "Anxious", ["confession"]),
    ("Ada yang lagi struggle sama tidur juga? Yuk temenin, ceritain harimu.", "nearby", "Lonely", ["nearby", "latenight"]),
    ("Baru nangis di kamar mandi kantor terus lanjut meeting kayak ga terjadi apa-apa.", "confession", "Sad", ["confession"]),
    ("Semoga yang baca ini besok dapet kabar baik yang udah lama ditunggu.", "anonymous", "Happy", ["wholesome"]),
]


async def main():
    pool = await asyncpg.create_pool(DB_DSN, min_size=1, max_size=4)
    async with pool.acquire() as conn:
        already = await conn.fetchval(
            "SELECT 1 FROM post_tags WHERE tag = $1 LIMIT 1", SENTINEL)
        if already:
            print("seedv2 already present — skipping.")
            await pool.close(); return

        dead_hash = bcrypt.hashpw(secrets.token_hex(16).encode(), bcrypt.gensalt()).decode()
        uids = {}
        for uname, dname in PERSONAS:
            existing = await conn.fetchval("SELECT id FROM users WHERE username=$1", uname)
            if existing:
                uids[uname] = existing
            else:
                uids[uname] = await conn.fetchval(
                    """INSERT INTO users (username, display_name, password_hash, karma, days_active)
                       VALUES ($1,$2,$3,$4,$5) RETURNING id""",
                    uname, dname, dead_hash, random.randint(3, 240), random.randint(1, 120))
        print(f"Personas ready: {len(uids)}")

        now, unames, n = datetime.now(timezone.utc), list(uids), 0
        for content, ptype, mood, tags in POSTS:
            created = now - timedelta(minutes=random.randint(5, 60 * 24 * 6))  # ~6 days spread
            use_bg = random.random() < 0.35
            bg_type = "gradient" if use_bg else "none"
            bg_value = random.choice(BG_GRADIENTS) if use_bg else None
            pid = uuid.uuid4()
            await conn.execute(
                """INSERT INTO posts (id, author_id, content, is_once_view, bg_type, bg_value,
                                      post_type, mood, upvotes, replies_count, created_at)
                   VALUES ($1,$2,$3,FALSE,$4,$5,$6,$7,$8,$9,$10)""",
                pid, uids[random.choice(unames)], content, bg_type, bg_value,
                ptype, mood, random.randint(0, 96), random.randint(0, 27), created)
            for t in (tags + [SENTINEL])[:6]:
                await conn.execute(
                    "INSERT INTO post_tags (post_id, tag) VALUES ($1,$2) ON CONFLICT DO NOTHING",
                    pid, t.lower()[:50])
            n += 1
        print(f"Seeded {n} posts with post_type + mood")
    await pool.close()


if __name__ == "__main__":
    asyncio.run(main())
