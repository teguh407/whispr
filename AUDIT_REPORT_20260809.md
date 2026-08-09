# Whispr App Audit Report
**Date:** 2026-08-09
**Auditor:** SUPERAGENT

---

## 🔴 CRITICAL BUGS

1. **`is_once_view` vs `once_view` field name mismatch** — Mobile sends `once_view` but backend expects `is_once_view` → once-view posts silently broken
2. **`setMessageTtl` uses `@Query` but backend expects `@Body` JSON** — Auto-destruct messages never work
3. **`viewOncePost` uses GET but backend expects POST** — Returns 405 Method Not Allowed
4. **`createPost` return type mismatch** — Mobile expects `Response<Post>`, backend returns `{id, ok}` → crash/null
5. **`updateProfile` return type mismatch** — Mobile expects `Response<User>`, backend returns `{ok: True}` → deserialization fail
6. **GroupChatScreen send button non-functional** — Only clears input, no message actually sent
7. **DiscoverScreen always uses hardcoded Jakarta coords** — `(-6.2088, 106.8456)` regardless of city input
8. **Password shown in plaintext** — AccountsScreen create dialog has no PasswordVisualTransformation

---

## 🟡 MEDIUM BUGS

9. FeedScreen hardcoded "2 km away" on every post
10. ChatScreen "Active now" always shown regardless of real status
11. ChatScreen share button navigates to post instead of sharing
12. ProfileScreen privacy toggles not persisted (local `remember` only)
13. ProfileScreen avatar ignores `avatarUrl` — always shows default icon
14. StoriesScreen video stories don't play (uses AsyncImage for video URLs)
15. CreatePostScreen Poll/Voice/Photo/Nearby types selectable but not implemented
16. `list_chats` response missing `unread_count` field — backend doesn't return it
17. `getMessages` `sender` field always null — backend only returns `sender_id`
18. Missing `"notifications"` navigation route — FeedScreen bell button does nothing
19. GifPicker doesn't pass selected GIF URL back to chat
20. `Chat.unreadCount` in mobile model but backend never returns it — dead field
21. Bottom nav duplicated across 4 screens instead of centralized in Navigation scaffold
22. 4 standalone routers (feed, groups, games, discovery) never registered via `include_router`

---

## 🟠 UX ISSUES

23. No pull-to-refresh ANYWHERE in the app
24. No loading indicator in ChatListScreen
25. No typing indicator in ChatScreen or GroupChatScreen
26. No WebSocket reconnection/disconnect state in ChatScreen
27. No email field in RegisterScreen — no password recovery possible
28. No "Forgot Password" link in LoginScreen
29. No email validation in LoginScreen
30. VoiceCallScreen mute/speaker toggles are cosmetic only — no actual audio routing
31. VoiceCallScreen no incoming call/ringing state
32. GamesScreen results/comparison feature missing (promised but not built)
33. LinksScreen no empty state, no loading indicator, no toast after copy
34. BlocksScreen no confirmation before unblock
35. AccountsScreen no validation on create dialog (empty fields allowed)
36. GifPickerScreen no loading/empty/error states
37. MatchScreen searching state can loop forever if server doesn't respond
38. No character limit indicator in CreatePostScreen
39. Daily question in FeedScreen is hardcoded, not from server
40. No notification badge count on bell icon

---

## 🔵 SYSTEMIC ISSUES

41. 200+ hardcoded strings — no i18n/string resources
42. `relativeTime` helper duplicated in 5+ files
43. `BasicSearchField`/`SearchField` duplicated between screens
44. Deprecated `Divider()` used in 6+ screens — should be `HorizontalDivider()`
45. `PersonaColors` static hardcoded — doesn't adapt to light/dark theme
46. In-memory rate limiter resets on server restart
47. CORS allows all origins `["*"]` — production risk
48. Hardcoded dev secret `"whispr-dev-secret-change-me"` in main.py
49. Hardcoded Tenor API key in main.py
50. Token in WebSocket URL path `/ws/chat/$token` — leaks in logs
51. `discovery.py` imports from main.py directly — circular import risk
52. SessionManager DataStore name still "sanstream_prefs" (legacy, if shared with Bluvia)

---

## 📊 PRIORITAS

**P1 — Fix immediately:** #1, #2, #3, #4, #5, #6, #7, #8
**P2 — Next release:** #9-#22 (medium bugs + API mismatches)
**P3 — Improvement backlog:** #23-#40 (UX) + #41-#52 (systemic)
