package com.whispr.id.util

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Google Sign-In via classic GoogleSignInClient.
 *
 * More reliable than Credential Manager across all Android versions.
 * Uses Web application OAuth client ID for ID token.
 *
 * Setup required in Google Cloud Console:
 *  1. Web application OAuth client → WEB_CLIENT_ID below + backend GOOGLE_CLIENT_ID
 *  2. Android OAuth client (package com.whispr.id) with SHA-1:
 *     75:B4:AE:5D:DF:F8:25:73:D8:A6:3C:F6:C3:75:44:9A:E7:AC:BB:77
 */
object GoogleAuthHelper {

    const val WEB_CLIENT_ID = "148481030059-pff9u5ch19j60hr4dilpfvnhdk0rcjg3.apps.googleusercontent.com"

    val isConfigured: Boolean
        get() = !WEB_CLIENT_ID.startsWith("REPLACE_")

    // ── Cool username generator ──
    private val adjectives = listOf(
        "driftwood", "silent", "midnight", "blue", "crystal", "shadow", "golden",
        "silver", "iron", "frost", "ember", "storm", "wild", "dark", "neon",
        "cosmic", "velvet", "raven", "ghost", "phantom", "mystic", "ancient",
        "electric", "lunar", "solar", "ocean", "forest", "desert", "arctic",
        "zenith", "nova", "echo", "pulse", "vibe", "haze", "glow", "spark",
        "blaze", "frost", "dusk", "dawn", "twilight", "eclipse", "comet"
    )
    private val nouns = listOf(
        "fox", "wolf", "hawk", "crow", "owl", "lynx", "bison", "tiger",
        "panther", "raven", "serpent", "dragon", "phoenix", "stallion",
        "whisper", "bloom", "dreamer", "wanderer", "seeker", "nomad",
        "rebel", "sage", "oracle", "cipher", "flux", "wave", "storm",
        "breeze", "frost", "ember", "spark", "blaze", "shard", "drift",
        "echo", "pulse", "vibe", "haze", "glow", "mind", "soul",
        "shadow", "ghost", "phantom", "spirit"
    )

    fun generateUsername(): String {
        val adj = adjectives.random()
        val noun = nouns.random()
        val num = (100..999).random()
        return "$adj$noun$num"
    }

    sealed class Result {
        data class Success(val idToken: String, val displayName: String?) : Result()
        data class Error(val message: String) : Result()
        object Cancelled : Result()
    }

    /**
     * Returns the Google sign-in intent to launch via ActivityResultLauncher.
     */
    fun getSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    /**
     * Handle the ActivityResult returned by the sign-in intent.
     */
    fun handleResult(result: ActivityResult): Result {
        if (result.data == null) return Result.Error("No data returned from sign-in intent")

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        return try {
            val account = task.getResult(ApiException::class.java)
            if (account.idToken != null) {
                Result.Success(account.idToken!!, account.displayName)
            } else {
                Result.Error("ID token is null — verify OAuth consent screen is published")
            }
        } catch (e: ApiException) {
            val msg = when (e.statusCode) {
                12501 -> return Result.Cancelled
                10 -> "DEVELOPER_ERROR (code 10) — SHA-1 fingerprint or package name mismatch in Google Cloud Console.\n\nExpected SHA-1: 75:B4:AE:5D:DF:F8:25:73:D8:A6:3C:F6:C3:75:44:9A:E7:AC:BB:77\nExpected package: com.whispr.id"
                7 -> "NETWORK_ERROR (code 7) — no internet connection"
                4 -> "SIGN_IN_REQUIRED (code 4) — Google account needs re-auth on device"
                8 -> "INTERNAL_ERROR (code 8) — try again"
                12500 -> "SIGN_IN_FAILED (code 12500) — update Google Play Services"
                12502 -> "SIGN_IN_ALREADY_IN_PROGRESS (code 12502)"
                12503 -> "SIGN_IN_DEPRECATED (code 12503)"
                else -> "Google Sign-In error: code ${e.statusCode} — ${e.message}"
            }
            Result.Error(msg)
        } catch (e: Exception) {
            Result.Error("${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
