package com.whispr.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.whispr.app.data.*
import com.whispr.app.network.ApiClient
import com.whispr.app.network.TokenStore
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

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
        }
    }

    private fun err(e: Exception) {
        _error.value = e.message ?: "Unknown error"
    }

    fun clearError() { _error.value = null }

    // Auth
    fun login(username: String, password: String) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = ApiClient.api.login(LoginRequest(username, password))
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

    fun logout() = viewModelScope.launch {
        TokenStore.clearToken(ctx())
        ApiClient.setToken(null)
        _currentUser.value = null
        _isLoggedIn.value = false
    }

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
    fun loadPosts(tag: String? = null) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = ApiClient.api.getPosts(tag)
            if (resp.isSuccessful) _posts.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
        _loading.value = false
    }

    fun createPost(content: String, tags: List<String>, onceView: Boolean) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.createPost(CreatePostRequest(content, tags, onceView))
            if (resp.isSuccessful) loadPosts()
        } catch (e: Exception) { err(e) }
    }

    fun upvotePost(postId: Int) = viewModelScope.launch {
        try {
            ApiClient.api.upvotePost(postId)
            loadPosts()
        } catch (e: Exception) { err(e) }
    }

    fun deletePost(postId: Int) = viewModelScope.launch {
        try {
            ApiClient.api.deletePost(postId)
            loadPosts()
        } catch (e: Exception) { err(e) }
    }

    // Chats
    fun loadChats() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getChats()
            if (resp.isSuccessful) _chats.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun createChat(userId: Int) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.createChat(mapOf("user_id" to userId))
            if (resp.isSuccessful) loadChats()
        } catch (e: Exception) { err(e) }
    }

    fun loadMessages(chatId: Int) = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getMessages(chatId)
            if (resp.isSuccessful) _messages.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    // Upload
    fun uploadVoice(file: File, onResult: (String) -> Unit) = viewModelScope.launch {
        try {
            val requestFile = file.asRequestBody("audio/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val resp = ApiClient.api.uploadVoice(part)
            if (resp.isSuccessful) {
                resp.body()?.get("url")?.let { onResult(it) }
            }
        } catch (e: Exception) { err(e) }
    }

    fun uploadPhoto(file: File, onResult: (String) -> Unit) = viewModelScope.launch {
        try {
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val resp = ApiClient.api.uploadPhoto(part)
            if (resp.isSuccessful) {
                resp.body()?.get("url")?.let { onResult(it) }
            }
        } catch (e: Exception) { err(e) }
    }

    // Links
    fun loadLinks() = viewModelScope.launch {
        try {
            val resp = ApiClient.api.getLinks()
            if (resp.isSuccessful) _links.value = resp.body() ?: emptyList()
        } catch (e: Exception) { err(e) }
    }

    fun createLink(url: String) = viewModelScope.launch {
        try {
            ApiClient.api.createLink(CreateLinkRequest(url))
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

    fun blockUser(userId: Int) = viewModelScope.launch {
        try {
            ApiClient.api.blockUser(userId)
            loadBlocks()
        } catch (e: Exception) { err(e) }
    }

    fun unblockUser(userId: Int) = viewModelScope.launch {
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
    fun startCall(userId: Int) = viewModelScope.launch {
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
}
