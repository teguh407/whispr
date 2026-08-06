package com.whispr.app.network

import com.whispr.app.data.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // Auth
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/me")
    suspend fun getMe(): Response<User>

    @PUT("api/me")
    suspend fun updateProfile(@Body profile: ProfileUpdate): Response<User>

    // Posts
    @GET("api/posts")
    suspend fun getPosts(@Query("tag") tag: String? = null): Response<List<Post>>

    @POST("api/posts")
    suspend fun createPost(@Body request: CreatePostRequest): Response<Post>

    @PUT("api/posts/{id}")
    suspend fun updatePost(@Path("id") id: Int, @Body body: RequestBody): Response<Post>

    @DELETE("api/posts/{id}")
    suspend fun deletePost(@Path("id") id: Int): Response<Unit>

    @POST("api/posts/{id}/upvote")
    suspend fun upvotePost(@Path("id") id: Int): Response<Unit>

    @GET("api/posts/{id}/view-once")
    suspend fun viewOncePost(@Path("id") id: Int): Response<Post>

    // Upload
    @Multipart
    @POST("api/upload/voice")
    suspend fun uploadVoice(@Part file: MultipartBody.Part): Response<Map<String, String>>

    @Multipart
    @POST("api/upload/photo")
    suspend fun uploadPhoto(@Part file: MultipartBody.Part): Response<Map<String, String>>

    // Chats
    @GET("api/chats")
    suspend fun getChats(): Response<List<Chat>>

    @POST("api/chats")
    suspend fun createChat(@Body body: Map<String, Int>): Response<Chat>

    @GET("api/chats/{id}/messages")
    suspend fun getMessages(@Path("id") chatId: Int): Response<List<ChatMessage>>

    // Links
    @POST("api/links")
    suspend fun createLink(@Body request: CreateLinkRequest): Response<ShareableLink>

    @GET("api/links")
    suspend fun getLinks(): Response<List<ShareableLink>>

    @POST("api/links/{code}/message")
    suspend fun sendLinkMessage(@Path("code") code: String, @Body msg: LinkMessage): Response<Unit>

    // Accounts
    @GET("api/accounts")
    suspend fun getAccounts(): Response<List<Account>>

    @POST("api/accounts")
    suspend fun createAccount(@Body request: CreateAccountRequest): Response<AuthResponse>

    // Block
    @POST("api/block/{id}")
    suspend fun blockUser(@Path("id") userId: Int): Response<Unit>

    @DELETE("api/block/{id}")
    suspend fun unblockUser(@Path("id") userId: Int): Response<Unit>

    @GET("api/blocks")
    suspend fun getBlocks(): Response<List<BlockedUser>>

    // GIF
    @GET("api/gif/search")
    suspend fun searchGifs(@Query("q") query: String): Response<List<GifResult>>

    // Call
    @POST("api/call/start")
    suspend fun startCall(@Body body: Map<String, Int>): Response<CallSession>

    @POST("api/call/{id}/answer")
    suspend fun answerCall(@Path("id") callId: Int): Response<Unit>

    @POST("api/call/{id}/end")
    suspend fun endCall(@Path("id") callId: Int): Response<Unit>
}
