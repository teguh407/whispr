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

    @POST("api/auth/google")
    suspend fun googleAuth(@Body request: GoogleAuthRequest): Response<AuthResponse>

    @GET("api/me")
    suspend fun getMe(): Response<User>

    @PUT("api/me")
    suspend fun updateProfile(@Body profile: ProfileUpdate): Response<User>

    // Posts
    @GET("api/posts")
    suspend fun getPosts(@Query("tag") tag: String? = null, @Query("tab") tab: String? = null): Response<List<Post>>

    @POST("api/posts")
    suspend fun createPost(@Body request: CreatePostRequest): Response<Post>

    @FormUrlEncoded
    @PUT("api/posts/{id}")
    suspend fun updatePost(@Path("id") id: String, @Field("content") content: String): Response<Unit>

    @DELETE("api/posts/{id}")
    suspend fun deletePost(@Path("id") id: String): Response<Unit>

    @POST("api/posts/{id}/upvote")
    suspend fun upvotePost(@Path("id") id: String): Response<Unit>

    @GET("api/posts/{id}/view-once")
    suspend fun viewOncePost(@Path("id") id: String): Response<Post>

    // Upload
    @Multipart
    @POST("api/upload/voice")
    suspend fun uploadVoice(@Part file: MultipartBody.Part): Response<UploadResponse>

    @Multipart
    @POST("api/upload/photo")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part("is_once_view") isOnceView: RequestBody
    ): Response<UploadResponse>

    @Multipart
    @POST("api/upload/document")
    suspend fun uploadDocument(@Part file: MultipartBody.Part): Response<UploadResponse>

    // Chats
    @GET("api/chats")
    suspend fun getChats(): Response<List<Chat>>

    @POST("api/chats")
    suspend fun createChat(@Body body: Map<String, String>): Response<Chat>

    @GET("api/chats/{id}/messages")
    suspend fun getMessages(@Path("id") chatId: String): Response<List<ChatMessage>>

    @PATCH("api/messages/{id}/ttl")
    suspend fun setMessageTtl(@Path("id") messageId: String, @Query("ttl") ttl: Int): Response<Unit>

    @GET("api/messages/expired")
    suspend fun getExpiredMessages(): Response<List<ChatMessage>>

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
    suspend fun blockUser(@Path("id") userId: String): Response<Unit>

    @DELETE("api/block/{id}")
    suspend fun unblockUser(@Path("id") userId: String): Response<Unit>

    @GET("api/blocks")
    suspend fun getBlocks(): Response<List<BlockedUser>>

    // GIF
    @GET("api/gif/search")
    suspend fun searchGifs(@Query("q") query: String): Response<List<GifResult>>

    // Call
    @POST("api/call/start")
    suspend fun startCall(@Body body: Map<String, String>): Response<CallSession>

    @POST("api/call/{id}/answer")
    suspend fun answerCall(@Path("id") callId: String): Response<Unit>

    @POST("api/call/{id}/end")
    suspend fun endCall(@Path("id") callId: String): Response<Unit>

    // Karma
    @GET("api/karma")
    suspend fun getKarma(): Response<KarmaResponse>

    @GET("api/karma/log")
    suspend fun getKarmaLog(): Response<List<KarmaLogEntry>>

    @GET("api/karma/{user_id}")
    suspend fun getUserKarma(@Path("user_id") userId: String): Response<KarmaResponse>

    // Discover
    @GET("api/discover")
    suspend fun getDiscoverUsers(
        @Query("radius_km") radiusKm: Int? = null,
        @Query("interests") interests: String? = null,
        @Query("min_karma") minKarma: Int? = null,
        @Query("gender") gender: String? = null,
        @Query("min_age") minAge: Int? = null,
        @Query("max_age") maxAge: Int? = null
    ): Response<List<DiscoverUser>>

    // Location
    @PUT("api/me/location")
    suspend fun updateLocation(@Body request: LocationUpdate): Response<Map<String, Boolean>>

    // Trending
    @GET("api/trending")
    suspend fun getTrending(): Response<List<TrendingTag>>

    // Report
    @POST("api/posts/{id}/report")
    suspend fun reportPost(@Path("id") postId: String, @Body request: ReportRequest): Response<Unit>

    // Account switching
    @POST("api/accounts/{id}/switch")
    suspend fun switchAccount(@Path("id") accountId: String): Response<AuthResponse>

    // Polls
    @POST("api/polls")
    suspend fun createPoll(@Body request: CreatePollRequest): Response<Map<String, String>>

    @GET("api/polls/{id}")
    suspend fun getPoll(@Path("id") pollId: String): Response<Poll>

    @POST("api/polls/{id}/vote")
    suspend fun votePoll(@Path("id") pollId: String, @Body request: PollVoteRequest): Response<Unit>

    // Stories
    @GET("api/stories")
    suspend fun getStories(): Response<List<Story>>

    @Multipart
    @POST("api/stories")
    suspend fun createStory(
        @Part file: MultipartBody.Part,
        @Part("caption") caption: RequestBody?
    ): Response<Story>

    @POST("api/stories/{id}/view")
    suspend fun viewStory(@Path("id") storyId: String): Response<Unit>

    // Groups
    @GET("api/groups")
    suspend fun getGroups(): Response<List<Group>>

    @POST("api/groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): Response<Group>

    @POST("api/groups/{id}/join")
    suspend fun joinGroup(@Path("id") groupId: String): Response<Unit>

    @POST("api/groups/{id}/leave")
    suspend fun leaveGroup(@Path("id") groupId: String): Response<Unit>

    @GET("api/groups/{id}/messages")
    suspend fun getGroupMessages(@Path("id") groupId: String): Response<List<GroupMessage>>

    // Games — match actual backend API
    @GET("api/games/modes")
    suspend fun getGameModes(): Response<List<GameMode>>

    @GET("api/games/{mode}/prompt")
    suspend fun getGamePrompt(@Path("mode") mode: String): Response<GamePrompt>

    @POST("api/games/answer")
    suspend fun submitGameAnswer(@Body request: SubmitAnswerRequest): Response<Unit>
}
