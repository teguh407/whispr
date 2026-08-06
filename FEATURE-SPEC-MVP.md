# WHISPR — Feature Spec MVP v1.0

> Anonymous chat app yang lebih clean dari Hush. Global market.

---

## 🎯 Vision

> *"Anonymous conversations, reimagined. Privacy-first, feature-rich, beautifully designed."*

---

## 📱 CORE FEATURES (MVP v1.0)

### 1. 🔐 IDENTITY & ONBOARDING

#### Registration
- ✅ **No phone/email required** — langsung bikin anonymous ID
- ✅ **Auto-generate avatar** — AI/random 3D style (ga perlu upload foto)
- ✅ **Nickname system** — user pilih username unik
- ✅ **Guest mode** — chat tanpa daftar (limit: 10 chat/day)
- ✅ **Register benefits** — unlimited chat + history + customization

#### Multiple Accounts
- ✅ **Easy switch** — tap profile icon, switch dalam 1 detik
- ✅ **Max 3 accounts** per device
- ✅ **Per-account settings** — tema, notification, privacy berbeda
- ✅ **No cross-account visibility** — akun A ga bisa liat akun B

---

### 2. 💬 CHAT & MESSAGING

#### Text Chat
- ✅ **Real-time messaging** — WebSocket connection
- ✅ **Read receipts** — optional (user bisa matiin)
- ✅ **Typing indicator** — optional
- ✅ **Edit message** — within 5 minutes + "(edited)" tag
- ✅ **Delete message** — for self only
- ✅ **Reply to specific message** — quote reply
- ✅ **Emoji reactions** — tap untuk react

#### Media
- ✅ **Voice note** — record & send, waveform visual
- ✅ **GIF integration** — GIPHY/Tenor search langsung di chat
- ✅ **Sticker packs** — 3 starter packs free
- ✅ **Send foto** — langsung kirim, **TANPA PERMISSION**
- ✅ **Once-view foto** — tutup = hilang permanen

#### Privacy Features
- ✅ **Anti-screenshot** — `FLAG_SECURE` (Android) + detect & notify (iOS)
- ✅ **Invisible watermark** — trace bocor ke siapa
- ✅ **Auto-destruct timer** — 5s / 10s / 30s untuk foto & text
- ✅ **Block final** — 1x block = permanent sampai unblock
- ✅ **Report user** — 1-tap report dengan AI auto-flag

---

### 3. 📞 VOICE CALL (MVP)

#### Anonymous Voice Call
- ✅ **1-on-1 voice call** — end-to-end encrypted
- ✅ **Voice changer filter** — 5 filters:
  - 🤖 Robot
  - 🎵 Chipmunk
  - 🎤 Deep
  - 🗣️ Normal
  - 🎭 Random
- ✅ **Call duration timer** — visible to both
- ✅ **Auto-disconnect** — optional: 5/10/15 min
- ✅ **No call history** — sekali call selesai, ga ada record
- ✅ **Reject/accept UI** — full screen incoming call

---

### 4. 🏠 FEED & POST

#### Create Content
- ✅ **Create Post** — text + image + poll
- ✅ **Create Story** — long-form anonymous story
- ✅ **Create Poll** — text poll + image poll + timer
- ✅ **Edit post/comment** — within 1 hour
- ✅ **Delete post/comment** — for self only

#### Feed Tabs
- ✅ **Hot 🔥** — trending posts
- ✅ **Global 🌐** — all posts worldwide
- ✅ **Local 📍** — posts near user (radius-based)
- ✅ **More ⏷** — categories & filters

#### Interactions
- ✅ **Upvote/Downvote** — anonymous voting
- ✅ **Comments** — threaded, editable
- ✅ **Share** — generate link ke external apps
- ✅ **Bookmark** — save posts for later

---

### 5. 🔍 DISCOVERY & MATCHING

#### Location
- ✅ **Radius-based matching** — user set jarak (1km - 100km)
- ✅ **CLOSER ↔ FARTHER slider** — smooth UX
- ✅ **No exact distance shown** — hanya radius
- ✅ **Fake GPS friendly** — ga block, tapi ga support built-in

#### Matching
- ✅ **Interest tags** — user pilih minat (1-5 tags)
- ✅ **Gender filter** — optional
- ✅ **Age range filter** — optional
- ✅ **Karma filter** — match dengan karma setara

#### Karma System
- ✅ **Karma score** — naik dari good interactions
- ✅ **How to earn karma:**
  - +5 post dilihat 100+ orang
  - +3 post di-upvote
  - +1 comment dilihat
  - -5 post di-report
  - -10 post di-remove moderator
- ✅ **Karma visible** — badge level (Newcomer → Regular → Trusted → VIP)

#### Q&A Game Modes
- ✅ **"Never Have I Ever"** — anonymous party game
- ✅ **"Confessions"** — anonymous confessions feed
- ✅ **"3 Words"** — describe someone in 3 words
- ✅ **Shareable results** — post ke feed atau share external

---

### 6. 🔗 VIRAL GROWTH

#### Shareable Links
- ✅ **Generate anonymous link** — user create link
- ✅ **Share to IG/WhatsApp/TikTok** — 1-tap share
- ✅ **Receive anonymous messages** — via link
- ✅ **Link analytics** — views & messages count
- ✅ **Custom link text** — "Send me anonymous messages"

#### Social Prompts
- ✅ **Template questions** — siap pakai
  - "Send me your confessions"
  - "Describe me in 3 words"
  - "What's your honest opinion about me?"
- ✅ **Custom prompt** — user tulis sendiri

---

### 7. 👥 GROUPS & COMMUNITY

#### Group Features
- ✅ **Create group** — topic-based
- ✅ **Join group** — browse & join
- ✅ **Group chat** — real-time
- ✅ **Group moderation** — admin tools

#### Interest Clubs
- ✅ **Pre-made clubs** — Gaming, Music, Dating, Venting, etc.
- ✅ **Club chat rooms** — topic-specific
- ✅ **Club karma** — group reputation

---

### 8. 🎨 UI/UX DESIGN

#### Visual Style
- ✅ **Dark mode default** — #0e0e14 background
- ✅ **Gradient accent** — purple (#5B4CFF) to pink (#FF6B9D)
- ✅ **Clean typography** — Plus Jakarta Sans
- ✅ **Rounded corners** — 12-16px throughout
- ✅ **Smooth animations** — Lottie/Spring

#### Navigation
- ✅ **Bottom nav** — 5 tabs: Home, Search, Create, Heart, Messages
- ✅ **Gesture-based** — swipe actions
- ✅ **Pull to refresh** — smooth
- ✅ **Skeleton loading** — better UX

#### Customization
- ✅ **Theme selector** — 5 free themes
- ✅ **Avatar customization** — outfits & accessories
- ✅ **Profile backgrounds** — patterns & colors

---

### 9. 🛡️ SAFETY & MODERATION

#### User Protection
- ✅ **1x block = final** — no multiple block needed
- ✅ **Report system** — categorize + AI auto-flag
- ✅ **Shadowban** — user ga tau dia dibanned
- ✅ **Age verification** — self-declaration + content lock

#### Content Moderation
- ✅ **AI toxic filter** — auto-flag inappropriate content
- ✅ **NSFW opt-in** — user set sendiri mau liat NSFW atau tidak
- ✅ **User word filter** — custom blocklist (10 kata)
- ✅ **Community reports** — human review queue

---

### 10. 💰 MONETIZATION (v2+)

#### Premium Features
- 👑 **Premium avatar** — exclusive animated avatars
- 👑 **Extra voice filters** — unlock 5 more filters
- 👑 **Ghost mode pro** — hide dari feed + search
- 👑 **Custom themes** — premium color schemes
- 👑 **Remove ads** — ad-free experience
- 👑 **Double karma** — earn karma 2x faster

#### IAP
- 🎁 **Gift system** — kirim gifts ke orang
- 🎁 **Premium stickers** — exclusive packs
- 🎁 **Boost post** — push post ke trending

---

## 📋 MVP SCOPE

### v1.0 — Launch (8 weeks)
- [ ] Registration + Multiple accounts
- [ ] Chat (text, voice note, GIF, once-view foto)
- [ ] Anti-screenshot + watermark
- [ ] Block final
- [ ] Feed (post, edit, comment, poll)
- [ ] Voice call + voice changer (5 filters)
- [ ] Location feed (radius-based)
- [ ] Interest matching
- [ ] Karma system (basic)
- [ ] Shareable links
- [ ] Dark mode UI

### v2.0 — Growth (4 weeks)
- [ ] Q&A game modes
- [ ] Guest mode
- [ ] Interest clubs
- [ ] Gift system (basic)
- [ ] Theme customization

### v3.0 — Monetization (4 weeks)
- [ ] Premium subscription
- [ ] Premium avatars/stickers
- [ ] Ghost mode pro
- [ ] Boost post

---

## 🛠️ TECH STACK

### Backend
- **FastAPI** + WebSocket (realtime)
- **PostgreSQL** (database)
- **Redis** (presence, caching)
- **MinIO/S3** (media storage)

### Frontend (Android)
- **Kotlin** + Jetpack Compose
- **Material 3** + custom theming
- **Coil** (image loading)
- **Retrofit** (API)

### Voice Call
- **WebRTC** (P2P voice)
- **TURN/STUN servers** (relay)
- **Web Audio API** (voice filters)

### Moderation
- **LLM API** (toxic detection)
- **Human review queue**

---

## 📊 SUCCESS METRICS

### Growth
- DAU/MAU ratio > 30%
- Shareable link conversion > 5%
- Organic growth rate > 20% monthly

### Engagement
- Avg session > 10 min
- Messages per user > 20/day
- Call completion rate > 70%

### Retention
- D1 retention > 40%
- D7 retention > 20%
- D30 retention > 10%

---

*Last updated: August 6, 2026*
