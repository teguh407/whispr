package com.whispr.id.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.whispr.id.data.*
import com.whispr.id.network.ApiClient
import com.whispr.id.network.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class WhisprViewModel(application: Application) : AndroidViewModel(application) {
    private fun ctx() = getApplication<Application>()

    // Auth state
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _accountSwitchSuccess = MutableStateFlow(false)
    val accountSwitchSuccess: StateFlow<Boolean> = _accountSwitchSuccess

    private val _isAuthReady = MutableStateFlow(false)
    val isAuthReady: StateFlow<Boolean> = _isAuthReady

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // GIF picked in GifPickerScreen, consumed by ChatScreen
    private val _pendingGifUrl = MutableStateFlow<String?>(null)
    val pendingGifUrl: StateFlow<String?> = _pendingGifUrl
    fun setPendingGif(url: String?) { _pendingGifUrl.value = url }
    fun consumePendingGif(): String? {
        val v = _pendingGifUrl.value
        _pendingGifUrl.value = null
        return v
    }

    // Posts
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    // Chats
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    // Links
    private val _links = MutableStateFlow<List<ShareableLink>>(emptyList())
    val links: StateFlow<List<ShareableLink>> = _links

    // Accounts
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts

    // Blocks
    private val _blocks = MutableStateFlow<List<BlockedUser>>(emptyList())
    val blocks: StateFlow<List<BlockedUser>> = _blocks

    // GIFs
    private val _gifs = MutableStateFlow<List<GifResult>>(emptyList())
    val gifs: StateFlow<List<GifResult>> = _gifs

    // Loading
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    // Call
    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall

    // Karma
    private val _karma = MutableStateFlow<KarmaResponse?>(null)
    val karma: StateFlow<KarmaResponse?> = _karma

    private val _karmaLog = MutableStateFlow<List<KarmaLogEntry>>(emptyList())
    val karmaLog: StateFlow<List<KarmaLogEntry>> = _karmaLog

    // Discover
    private val _discoverUsers = MutableStateFlow<List<DiscoverUser>>(emptyList())
    val discoverUsers: StateFlow<List<DiscoverUser>> = _discoverUsers

    // Polls
    private val _polls = MutableStateFlow<List<Poll>>(emptyList())
    val polls: StateFlow<List<Poll>> = _polls

    // Stories
    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories

    // Groups
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups

    private val _groupMessages = MutableStateFlow<List<GroupMessage>>(emptyList())
    val groupMessages: StateFlow<List<GroupMessage>> = _groupMessages

    // Games
    private val _gameModes = MutableStateFlow<List<GameMode>>(emptyList())
    val gameModes: StateFlow<List<GameMode>> = _gameModes

    private val _currentPrompt = MutableStateFlow<GamePrompt?>(null)
    val currentPrompt: StateFlow<GamePrompt?> = _currentPrompt

    // Trending
    private val _trending = MutableStateFlow<List<TrendingTag>>(emptyList())
    val trending: StateFlow<List<TrendingTag>> = _trending

    init {
        viewModelScope.launch {
            val savedUrl = TokenStore.getBaseUrl(ctx())
            if (savedUrl != null) ApiClient.setBaseUrl(savedUrl)
            val token = TokenStore.getToken(ctx())
            if (token != null) {
                ApiClient.setToken(token)
                try {
                    _currentUser.value = ApiClient.api.getMe().body()
                    _isLoggedIn.value = true
                } catch (e: Exception) {
                    TokenStore.clearToken(ctx())
                }
            }
            _isAuthReady.value = true
        }
    }

    private fun err(e: Exception) {
        _error.value = e.message ?: "Unknown error"
    }

    fun clearError() { _error.value = null }

    // Auth
    fun login(email: String, password: String) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = ApiClient.api.login(LoginRequest(email, password))
            if (resp.isSuccessful) {
                resp.body()?.let {
                    TokenStore.saveToken(ctx(), it.token)
                    ApiClient.setToken(it.token)
                    _currentUser.value = it.user ?: ApiClient.api.getMe().body()
                    _isLoggedIn.value = true
                }
            } else {
                _error.value = "Login failed: ${resp.code()}"
            }
        } catch (e: Exception) { err(e) }
        _loading.value = false
    }

    fun register(username: String, password: String, displayName: String) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = ApiClient.api.register(RegisterRequest(username, password, displayName))
            if (resp.isSuccessful) {
                resp.body()?.let {
                    TokenStore.saveToken(ctx(), it.token)
                    ApiClient.setToken(it.token)
                    _currentUser.value = it.user ?: ApiClient.api.getMe().body()
                    _isLoggedIn.value = true
                }
            } else {
                _error.value = "Register failed: ${resp.code()}"
            }
        } catch (e: Exception) { err(e) }
        _loading.value = false
    }

    fun googleAuth(idToken: String) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = ApiClient.api.googleAuth(GoogleAuthRequest(idToken))
            if (resp.isSuccessful) {
                resp.body()?.let {
                    TokenStore.saveToken(ctx(), it.token)
                    ApiClient.setToken(it.token)
                    _currentUser.value = it.user ?: ApiClient.api.getMe().body()
                    _isLoggedIn.value = true
                }
            } else {
                _error.value = "Google auth failed: ${resp.code()}"
            }
        } catch (e: Exception) { err(e) }
        _loading.value = false
    }

    fun logout() = viewModelScope.launch {
        // Sign out of Google so account picker shows on next login
        try {
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).requestEmail().build()
            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(ctx(), gso)
            client.signOut()
        } catch (_: Exception) { /* Google not available */ }

        TokenStore.clearToken(ctx())
        ApiClient.setToken(null)
        _currentUser.value = null
        _isLoggedIn.value = false
        _karma.value = null
        _karmaLog.value = emptyList()
        _discoverUsers.value = emptyList()
        _groups.value = emptyList()
        _stories.value = emptyList()
        _currentPrompt.value = null
    }

    // Permanently delete account (Google Play requirement)
    private val _deleteAccountResult = MutableStateFlow<String?>(null) // null=idle, "ok", or error msg
    val deleteAccountResult: StateFlow<String?> = _deleteAccountResult

    fun deleteAccount(password: String?) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.deleteAccount(DeleteAccountRequest(password))
            if (resp.isSuccessful) {
                _deleteAccountResult.value = "ok"
                logout()
            } else {
                _deleteAccountResult.value = when (resp.code()) {
                    400 -> "Password required"
                    403 -> "Incorrect password"
                    else -> "Delete failed (${resp.code()})"
                }
            }
        } catch (e: Exception) {
            _deleteAccountResult.value = e.message ?: "Network error"
        }
    }

    fun resetDeleteAccountResult() { _deleteAccountResult.value = null }

    fun setBaseUrl(url: String) = viewModelScope.launch {
        ApiClient.setBaseUrl(url)
        TokenStore.saveBaseUrl(ctx(), url)
    }

    // Profile
    fun updateProfile(displayName: String?, bio: String?) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.updateProfile(ProfileUpdate(bio, displayName))
            if (resp.isSuccessful) _currentUser.value = resp.body()
        } catch (e: Exception) { err(e) }
    }

    // Posts
    fun loadPosts(tag: String? = null, tab: String? = null) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = ApiClient.api.getPosts(tag, tab)
            if (resp.isSuccessful) _posts.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
        _loading.value = false
    }

    fun createPost(content: String, tags: List<String>, onceView: Boolean,
                   bgType: String = "none", bgValue: String? = null,
                   postType: String = "anonymous", mood: String? = null) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.createPost(CreatePostRequest(content, tags, onceView, bgType, bgValue, postType, mood))
            if (resp.isSuccessful) loadPosts()
        } catch (e: Exception) { err(e) }
    }

    fun upvotePost(postId: String) = viewModelScope.launch {
        try {
            ApiClient.api.upvotePost(postId)
            loadPosts()
        } catch (e: Exception) { err(e) }
    }

    fun deletePost(postId: String) = viewModelScope.launch {
        try {
            ApiClient.api.deletePost(postId)
            loadPosts()
        } catch (e: Exception) { err(e) }
    }

    fun editPost(postId: String, content: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.updatePost(postId, content)
            if (resp.isSuccessful) loadPosts()
            else _error.value = "Edit failed: ${resp.code()}"
        } catch (e: Exception) { err(e) }
    }

    // Post detail + replies
    private val _postDetail = MutableStateFlow<Post?>(null)
    val postDetail: StateFlow<Post?> = _postDetail

    private val _replies = MutableStateFlow<List<Reply>>(emptyList())
    val replies: StateFlow<List<Reply>> = _replies

    fun loadPostDetail(postId: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getPostDetail(postId)
            if (resp.isSuccessful) _postDetail.value = resp.body()
        } catch (e: Exception) { err(e) }
    }

    fun loadReplies(postId: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getPostReplies(postId)
            if (resp.isSuccessful) _replies.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun createReply(postId: String, content: String, parentId: String? = null, onDone: () -> Unit = {}) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.createReply(postId, CreateReplyRequest(content, parentId))
            if (resp.isSuccessful) {
                loadReplies(postId)
                loadPostDetail(postId)  // refresh replies_count
                onDone()
            } else {
                _error.value = "Reply failed: ${resp.code()}"
            }
        } catch (e: Exception) { err(e) }
    }

    fun clearPostDetail() {
        _postDetail.value = null
        _replies.value = emptyList()
    }

    // Public profile
    private val _publicProfile = MutableStateFlow<PublicProfile?>(null)
    val publicProfile: StateFlow<PublicProfile?> = _publicProfile

    fun loadPublicProfile(userId: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getPublicProfile(userId)
            if (resp.isSuccessful) _publicProfile.value = resp.body()
        } catch (e: Exception) { err(e) }
    }

    fun clearPublicProfile() { _publicProfile.value = null }

    // Chats
    fun loadChats() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getChats()
            if (resp.isSuccessful) _chats.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun createChat(userId: String, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.createChat(mapOf("user_id" to userId))
            if (resp.isSuccessful) {
                val chatId = resp.body()?.get("chat_id") ?: ""
                if (chatId.isNotBlank()) onCreated(chatId)
                loadChats()
            }
        } catch (e: Exception) { err(e) }
    }

    fun loadMessages(chatId: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getMessages(chatId)
            if (resp.isSuccessful) _messages.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    fun setMessageTtl(messageId: String, ttlSeconds: Int) = viewModelScope.launch {
        try {
            ApiClient.api.setMessageTtl(messageId, mapOf("ttl_seconds" to ttlSeconds))
            _error.value = "Auto-destruct set: ${ttlSeconds}s"
        } catch (e: Exception) { err(e) }
    }

    /** Mark a once-view message as viewed (persists server-side). */
    fun markMessageViewed(messageId: String) = viewModelScope.launch {
        try {
            ApiClient.api.viewOnceMessage(messageId)
            // Update local list so it collapses to expired immediately
            _messages.value = _messages.value.map {
                if (it.id == messageId) it.copy(isViewed = true) else it
            }
        } catch (e: Exception) { /* non-fatal */ }
    }

    // Upload
    fun uploadVoice(file: File, onResult: (UploadResponse?) -> Unit) = viewModelScope.launch {
        try {
            val requestFile = file.asRequestBody("audio/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val resp = ApiClient.api.uploadVoice(part)
            if (resp.isSuccessful) {
                onResult(resp.body())
            } else {
                onResult(null)
            }
        } catch (e: Exception) { err(e); onResult(null) }
    }

    fun uploadPhoto(file: File, isOnceView: Boolean, onResult: (UploadResponse?) -> Unit) = viewModelScope.launch {
        try {
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val onceViewBody = if (isOnceView) "true".toRequestBody("text/plain".toMediaTypeOrNull()) else "false".toRequestBody("text/plain".toMediaTypeOrNull())
            val resp = ApiClient.api.uploadPhoto(part, onceViewBody)
            if (resp.isSuccessful) {
                onResult(resp.body())
            } else {
                onResult(null)
            }
        } catch (e: Exception) { err(e); onResult(null) }
    }

    fun uploadDocument(file: File, onResult: (UploadResponse?) -> Unit) = viewModelScope.launch {
        try {
            val requestFile = file.asRequestBody("*/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val resp = ApiClient.api.uploadDocument(part)
            if (resp.isSuccessful) {
                onResult(resp.body())
            } else {
                onResult(null)
            }
        } catch (e: Exception) { err(e); onResult(null) }
    }

    // Links
    fun loadLinks() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getLinks()
            if (resp.isSuccessful) _links.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun createLink(title: String) = viewModelScope.launch {
        try {
            ApiClient.api.createLink(CreateLinkRequest(title))
            loadLinks()
        } catch (e: Exception) { err(e) }
    }

    // Accounts
    fun loadAccounts() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getAccounts()
            if (resp.isSuccessful) _accounts.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun createAccount(username: String, password: String, displayName: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.createAccount(CreateAccountRequest(username, password, displayName))
            if (resp.isSuccessful) {
                resp.body()?.let {
                    TokenStore.saveToken(ctx(), it.token)
                    ApiClient.setToken(it.token)
                    _currentUser.value = it.user
                }
                loadAccounts()
            }
        } catch (e: Exception) { err(e) }
    }

    // Block
    fun loadBlocks() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getBlocks()
            if (resp.isSuccessful) _blocks.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun blockUser(userId: String) = viewModelScope.launch {
        try {
            ApiClient.api.blockUser(userId)
            loadBlocks()
        } catch (e: Exception) { err(e) }
    }

    fun unblockUser(userId: String) = viewModelScope.launch {
        try {
            ApiClient.api.unblockUser(userId)
            loadBlocks()
        } catch (e: Exception) { err(e) }
    }

    // GIF
    fun searchGifs(query: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.searchGifs(query)
            if (resp.isSuccessful) _gifs.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    // Call
    fun startCall(userId: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.startCall(mapOf("user_id" to userId))
            if (resp.isSuccessful) _activeCall.value = resp.body()
        } catch (e: Exception) { err(e) }
    }

    fun endCall() = viewModelScope.launch {
        _activeCall.value?.let { call ->
            try { ApiClient.api.endCall(call.id) } catch (_: Exception) {}
        }
        _activeCall.value = null
    }

    // Karma
    fun loadKarma() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getKarma()
            if (resp.isSuccessful) _karma.value = resp.body()
        } catch (e: Exception) { err(e) }
    }

    fun loadKarmaLog() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getKarmaLog()
            if (resp.isSuccessful) _karmaLog.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    // Discover
    fun loadDiscoverUsers(
        radiusKm: Int? = null,
        interests: String? = null,
        minKarma: Int? = null,
        gender: String? = null,
        minAge: Int? = null,
        maxAge: Int? = null
    ) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = ApiClient.api.getDiscoverUsers(radiusKm, interests, minKarma, gender, minAge, maxAge)
            if (resp.isSuccessful) _discoverUsers.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
        _loading.value = false
    }

    // Polls
    fun createPoll(question: String, options: List<String>) = viewModelScope.launch {
        try {
            ApiClient.api.createPoll(CreatePollRequest(question, options))
            _error.value = "Poll created"
        } catch (e: Exception) { err(e) }
    }

    fun votePoll(pollId: String, optionId: String) = viewModelScope.launch {
        try {
            ApiClient.api.votePoll(pollId, PollVoteRequest(optionId))
            _error.value = "Vote submitted"
        } catch (e: Exception) { err(e) }
    }

    // Stories
    fun loadStories() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getStories()
            if (resp.isSuccessful) _stories.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun viewStory(storyId: String) = viewModelScope.launch {
        try {
            ApiClient.api.viewStory(storyId)
        } catch (e: Exception) { err(e) }
    }

    // Groups
    fun loadGroups() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getGroups()
            if (resp.isSuccessful) _groups.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun createGroup(name: String, description: String?, topic: String?) = viewModelScope.launch {
        try {
            ApiClient.api.createGroup(CreateGroupRequest(name, description, topic))
            loadGroups()
        } catch (e: Exception) { err(e) }
    }

    fun joinGroup(groupId: String) = viewModelScope.launch {
        try {
            ApiClient.api.joinGroup(groupId)
            loadGroups()
        } catch (e: Exception) { err(e) }
    }

    fun leaveGroup(groupId: String) = viewModelScope.launch {
        try {
            ApiClient.api.leaveGroup(groupId)
            loadGroups()
        } catch (e: Exception) { err(e) }
    }

    fun loadGroupMessages(groupId: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getGroupMessages(groupId)
            if (resp.isSuccessful) _groupMessages.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun addGroupMessage(message: GroupMessage) {
        _groupMessages.value = _groupMessages.value + message
    }

    // Games — match actual backend API (modes → prompt → answer)
    fun loadGameModes() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getGameModes()
            if (resp.isSuccessful) _gameModes.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun loadGamePrompt(mode: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getGamePrompt(mode)
            if (resp.isSuccessful) _currentPrompt.value = resp.body()
        } catch (e: Exception) { err(e) }
    }

    fun submitGameAnswer(mode: String, prompt: String, answer: String) = viewModelScope.launch {
        try {
            ApiClient.api.submitGameAnswer(SubmitAnswerRequest(mode, prompt, answer))
            _error.value = "Answer submitted!"
            _currentPrompt.value = null
        } catch (e: Exception) { err(e) }
    }

    // Match with Stranger
    private val _matchJoin = MutableStateFlow<MatchJoinResponse?>(null)
    val matchJoin: StateFlow<MatchJoinResponse?> = _matchJoin

    private val _matchStatus = MutableStateFlow<MatchStatus?>(null)
    val matchStatus: StateFlow<MatchStatus?> = _matchStatus

    fun matchJoin(mode: String? = null) = viewModelScope.launch {
        try {
            val body = if (mode != null) mapOf("mode" to mode) else emptyMap()
            val resp = ApiClient.api.matchJoin(body)
            if (resp.isSuccessful) _matchJoin.value = resp.body()
        } catch (e: Exception) { err(e) }
    }

    fun matchStatus(matchId: String) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.matchStatus(matchId)
            if (resp.isSuccessful) _matchStatus.value = resp.body()
        } catch (e: Exception) { err(e) }
    }

    fun matchAnswer(matchId: String, answer: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        try {
            ApiClient.api.matchAnswer(matchId, MatchAnswerRequest(answer))
            onDone()
        } catch (e: Exception) { err(e) }
    }

    fun matchReact(matchId: String, emoji: String) = viewModelScope.launch {
        try {
            ApiClient.api.matchReact(matchId, MatchReactRequest(emoji))
        } catch (e: Exception) { err(e) }
    }

    fun matchLeave(matchId: String) = viewModelScope.launch {
        try {
            ApiClient.api.matchLeave(matchId)
            _matchJoin.value = null
            _matchStatus.value = null
        } catch (e: Exception) { err(e) }
    }

    // Trending
    fun loadTrending() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getTrending()
            if (resp.isSuccessful) _trending.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    // Report
    fun reportPost(postId: String, reason: String) = viewModelScope.launch {
        try {
            ApiClient.api.reportPost(postId, ReportRequest(reason))
            _error.value = "Post reported"
        } catch (e: Exception) { err(e) }
    }

    // Location
    suspend fun updateLocationSync(lat: Double, lng: Double, city: String?): Boolean {
        return try {
            val resp = ApiClient.api.updateLocation(LocationUpdate(lat, lng, city))
            resp.isSuccessful
        } catch (e: Exception) { false }
    }

    fun updateLocation(lat: Double, lng: Double, city: String?) = viewModelScope.launch {
        try {
            ApiClient.api.updateLocation(LocationUpdate(lat, lng, city))
        } catch (e: Exception) { err(e) }
    }

    // Account switching
    fun switchAccount(accountId: String) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = ApiClient.api.switchAccount(accountId)
            if (resp.isSuccessful) {
                resp.body()?.let {
                    TokenStore.saveToken(ctx(), it.token)
                    ApiClient.setToken(it.token)
                    // Fetch full user data after token switch
                    val meResp = ApiClient.api.getMe()
                    if (meResp.isSuccessful) {
                        _currentUser.value = meResp.body()
                    } else {
                        _currentUser.value = it.user
                    }
                    _isLoggedIn.value = true
                    _accountSwitchSuccess.value = true
                }
            } else {
                _error.value = "Switch failed: ${resp.code()}"
            }
        } catch (e: Exception) { err(e) }
        _loading.value = false
    }

    fun clearAccountSwitchSuccess() {
        _accountSwitchSuccess.value = false
    }
}
