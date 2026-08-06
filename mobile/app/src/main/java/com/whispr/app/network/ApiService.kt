package com.whispr.app.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// Data classes
data class UserCreate(
    val username: String,
    val password: String,
    val email: String? = null
)

data class TokenResponse(
    val access_token: String,
    val token_type: String
)

data class UserResponse(
    val id: Int,
    val username: String,
    val karma: Int,
    val karma_level: String,
    val avatar_seed: String,
    val created_at: String
)

data class PostCreate(
    val content: String,
    val post_type: String = "post",
    val tags: List<String> = emptyList()
)

data class PostResponse(
    val id: Int,
    val content: String,
    val post_type: String,
    val upvotes: Int,
    val downvotes: Int,
    val comment_count: Int,
    val author: UserResponse,
    val tags: List<String>,
    val created_at: String
)

// API Interface
interface WhisprApi {
    @POST("auth/register")
    suspend fun register(@Body user: UserCreate): TokenResponse

    @FormUrlEncoded
    @POST("token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): TokenResponse

    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") token: String): UserResponse

    @POST("posts")
    suspend fun createPost(
        @Header("Authorization") token: String,
        @Body post: PostCreate
    ): PostResponse

    @GET("posts")
    suspend fun getPosts(
        @Header("Authorization") token: String,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 20
    ): List<PostResponse>

    @POST("posts/{id}/upvote")
    suspend fun upvotePost(
        @Header("Authorization") token: String,
        @Path("id") postId: Int
    ): Map<String, String>
}

// API Client
object ApiClient {
    // Change this to your server URL
    const val BASE_URL = "http://43.153.207.36:8080/"

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: WhisprApi = retrofit.create(WhisprApi::class.java)
}
