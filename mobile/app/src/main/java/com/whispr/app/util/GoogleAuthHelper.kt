package com.whispr.app.util

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Google Sign-In via Credential Manager (replaces legacy GoogleSignInClient).
 *
 * Setup required in Google Cloud Console (one-time):
 *  1. Web application OAuth client → used as WEB_CLIENT_ID below + backend GOOGLE_CLIENT_ID
 *  2. Android OAuth client (package com.whispr.app) with BOTH SHA-1s:
 *     - Upload keystore: 75:B4:AE:5D:DF:F8:25:73:D8:A6:3C:F6:C3:75:44:9A:E7:AC:BB:77
 *     - Play App Signing key: Play Console → Setup → App Integrity (after first AAB upload)
 */
object GoogleAuthHelper {

    // TODO: replace with your Web application OAuth client ID
    // (Google Cloud Console → APIs & Services → Credentials → OAuth 2.0 Client IDs → Web application)
    const val WEB_CLIENT_ID = "REPLACE_WITH_WEB_CLIENT_ID.apps.googleusercontent.com"

    val isConfigured: Boolean
        get() = !WEB_CLIENT_ID.startsWith("REPLACE_")

    sealed class Result {
        data class Success(val idToken: String, val displayName: String?) : Result()
        data class Error(val message: String) : Result()
        object Cancelled : Result()
    }

    /**
     * Shows the Google account picker and returns a Google ID token on success.
     * Must be called from an Activity context (Compose: LocalContext.current).
     */
    suspend fun signIn(context: Context): Result {
        if (!isConfigured) return Result.Error("Google Sign-In not configured yet")

        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // show ALL accounts, not just previously used
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(context, request)
            handleResponse(response)
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: androidx.credentials.exceptions.NoCredentialException) {
            Result.Error("No Google account found on this device")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Google Sign-In failed")
        }
    }

    private fun handleResponse(response: GetCredentialResponse): Result {
        val credential = response.credential
        return if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            Result.Success(googleCredential.idToken, googleCredential.displayName)
        } else {
            Result.Error("Unexpected credential type")
        }
    }
}
