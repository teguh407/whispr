package com.whispr.app.data

import com.google.gson.annotations.SerializedName

// Auth
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(
    val username: String,
    val password: String,
    @SerializedName("display_name") val displayName: String
)
data class AuthResponse(
    val token: String,
    val user: User? = null
)

// Google Auth
data class GoogleAuthRequest(@SerializedName("id_token") val idToken: String)
data class KarmaResponse(
    val karma: Int = 0,
    val level: String = "Newcomer",
    @SerializedName("next_level_at") val nextLevelAt: Int? = null
)
data class KarmaLogEntry(
    val id: String = "",
    val amount: Int = 0,
    val reason: String = "",
    @SerializedName("created_at") val createdAt: String? = null
)

// User
data class User(
    val id: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val karma: Int = 0,
    @SerializedName("days_active") val daysActive: Int = 0,
    @SerializedName("posts_count") val postsCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val interests: List<String> = emptyList()
)

data class ProfileUpdate(
    val bio: String? = null,
    @SerializedName("display_name") val displayName: String? = null
)

// Posts — match actual backend response
data class Post(
    val id: String = "",
    val content: String = "",
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("is_once_view") val hasOnceView: Boolean = false,
    val upvotes: Int = 0,
    @SerializedName("replies_count") val repliesCount: Int = 0,
    @SerializedName("is_edited") val isEdited: Boolean = false,
    @SerializedName("user_upvoted") val isUpvoted: Boolean = false,
    @SerializedName("bg_type") val bgType: String = "none",
    @SerializedName("bg_value") val bgValue: String? = null,
    @SerializedName("post_type") val postType: String = "anonymous",
    val mood: String? = null,
    @SerializedName("is_mine") val isMine: Boolean = false,
    val author: User? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    // Legacy compat fields (may be absent)
    @SerializedName("author_id") val authorId: String = "",
    @SerializedName("upvote_count") val upvoteCount: Int = 0,
    @SerializedName("has_once_view") val legacyHasOnceView: Boolean = false,
    val tags: List<String> = emptyList()
)

data class CreatePostRequest(
    val content: String,
    val tags: List<String> = emptyList(),
    @SerializedName("once_view") val onceView: Boolean = false,
    @SerializedName("bg_type") val bgType: String = "none",
    @SerializedName("bg_value") val bgValue: String? = null,
    @SerializedName("post_type") val postType: String = "anonymous",
    val mood: String? = null
)

// Poll
data class PollOption(
    val id: String = "",
    val text: String = "",
    val votes: Int = 0
)
data class Poll(
    val id: String = "",
    val question: String = "",
    val options: List<PollOption> = emptyList(),
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("user_voted") val userVoted: String? = null
)
data class CreatePollRequest(
    val question: String,
    val options: List<String>
)
data class PollVoteRequest(
    @SerializedName("option_id") val optionId: String
)

// Story
data class Story(
    val id: String = "",
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("media_type") val mediaType: String = "image",
    val caption: String? = null,
    val author: User? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("viewed") val viewed: Boolean = false,
    @SerializedName("view_count") val viewCount: Int = 0
)

// Chat
data class Chat(
    val id: String = "",
    val user: User? = null,
    @SerializedName("last_message") val lastMessage: String? = null,
    @SerializedName("last_message_at") val lastMessageAt: String? = null,
    @SerializedName("unread_count") val unreadCount: Int = 0
)

data class ChatMessage(
    val id: String? = null,
    val sender: User? = null,
    @SerializedName("sender_id") val senderId: String = "",
    val content: String = "",
    val type: String = "text",
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("ttl_seconds") val ttlSeconds: Int? = null,
    @SerializedName("expires_at") val expiresAt: String? = null
)

data class WsMessage(
    val type: String,
    val content: String? = null,
    val senderId: String? = null,
    @SerializedName("media_url") val mediaUrl: String? = null,
    val timestamp: String? = null
)

// Links
data class ShareableLink(
    val id: String = "",
    val code: String = "",
    val url: String = "",
    @SerializedName("message_count") val messageCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CreateLinkRequest(val title: String)
data class LinkMessage(val message: String)

// Accounts
data class Account(
    val id: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("is_active") val isActive: Boolean = false
)

data class CreateAccountRequest(
    val username: String,
    val password: String,
    @SerializedName("display_name") val displayName: String
)

// Block
data class BlockedUser(
    val id: String = "",
    val user: User? = null
)

// GIF
data class GifSearchResponse(val results: List<GifResult> = emptyList())
data class GifResult(
    val url: String = "",
    val thumbnail: String? = null,
    val title: String? = null
)

// Call
data class CallSession(
    val id: String = "",
    val token: String = "",
    val status: String = "ringing"
)

data class CallSignal(
    val type: String,
    val data: Any? = null,
    @SerializedName("call_id") val callId: String? = null
)

// Discovery
data class DiscoverUser(
    val id: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val karma: Int = 0,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    val interests: List<String> = emptyList(),
    val age: Int? = null,
    val gender: String? = null
)

// Groups
data class Group(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    @SerializedName("member_count") val memberCount: Int = 0,
    val topic: String? = null,
    @SerializedName("is_member") val isMember: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val topic: String? = null
)
data class GroupMessage(
    val id: String? = null,
    @SerializedName("sender_id") val senderId: String = "",
    @SerializedName("sender_name") val senderName: String = "",
    val content: String = "",
    @SerializedName("created_at") val createdAt: String? = null
)

// Games — match actual backend response
data class GameMode(
    val key: String = "",
    val title: String = "",
    val emoji: String = ""
)
data class GamePrompt(
    val mode: String = "",
    val prompt: String = ""
)
data class SubmitAnswerRequest(
    val mode: String,
    val prompt: String,
    val answer: String
)

// Location
data class LocationUpdate(
    val lat: Double,
    val lng: Double,
    val city: String? = null
)

// Trending
data class TrendingTag(
    val tag: String = "",
    val count: Int = 0
)

// Report
data class ReportRequest(
    val reason: String
)
