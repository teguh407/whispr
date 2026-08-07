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

// User
data class User(
    val id: Int,
    val username: String,
    @SerializedName("display_name") val displayName: String?,
    val bio: String? = null,
    val avatar: String? = null,
    val karma: Int = 0,
    @SerializedName("days_active") val daysActive: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class ProfileUpdate(
    val bio: String? = null,
    @SerializedName("display_name") val displayName: String? = null
)

// Posts
data class Post(
    val id: Int,
    val content: String,
    val author: User? = null,
    @SerializedName("author_id") val authorId: Int = 0,
    @SerializedName("upvote_count") val upvoteCount: Int = 0,
    @SerializedName("is_upvoted") val isUpvoted: Boolean = false,
    val tags: List<String> = emptyList(),
    @SerializedName("has_once_view") val hasOnceView: Boolean = false,
    @SerializedName("view_once_media") val viewOnceMedia: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CreatePostRequest(
    val content: String,
    val tags: List<String> = emptyList(),
    @SerializedName("once_view") val onceView: Boolean = false
)

// Chat
data class Chat(
    val id: Int,
    val user: User? = null,
    @SerializedName("last_message") val lastMessage: String? = null,
    @SerializedName("last_message_at") val lastMessageAt: String? = null,
    @SerializedName("unread_count") val unreadCount: Int = 0
)

data class ChatMessage(
    val id: Int? = null,
    val sender: User? = null,
    @SerializedName("sender_id") val senderId: Int = 0,
    val content: String = "",
    val type: String = "text",
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class WsMessage(
    val type: String,
    val content: String? = null,
    val senderId: Int? = null,
    @SerializedName("media_url") val mediaUrl: String? = null,
    val timestamp: String? = null
)

// Links
data class ShareableLink(
    val id: Int,
    val code: String,
    val url: String,
    @SerializedName("message_count") val messageCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CreateLinkRequest(val url: String)
data class LinkMessage(val message: String)

// Accounts
data class Account(
    val id: Int,
    val username: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("is_active") val isActive: Boolean = false
)

data class CreateAccountRequest(
    val username: String,
    val password: String,
    @SerializedName("display_name") val displayName: String
)

// Block
data class BlockedUser(
    val id: Int,
    val user: User? = null
)

// GIF
data class GifSearchResponse(val results: List<GifResult> = emptyList())
data class GifResult(
    val url: String,
    val thumbnail: String? = null,
    val title: String? = null
)

// Call
data class CallSession(
    val id: Int,
    val token: String,
    val status: String = "ringing"
)

data class CallSignal(
    val type: String,
    val data: Any? = null,
    @SerializedName("call_id") val callId: Int? = null
)
