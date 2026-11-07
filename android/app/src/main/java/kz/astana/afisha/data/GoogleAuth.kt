package kz.astana.afisha.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kz.astana.afisha.BuildConfig

data class GoogleAccount(
    val googleId: String,
    val idToken: String,
    val email: String?,
    val displayName: String?,
)

sealed class GoogleSignInError(message: String) : Exception(message) {
    class NotConfigured : GoogleSignInError(
        "Сначала вставь GOOGLE_WEB_CLIENT_ID в app/build.gradle.kts",
    )
    class Cancelled : GoogleSignInError("Вход отменён")
    class NoAccount : GoogleSignInError("На устройстве нет Google-аккаунта")
    class Failed(cause: Throwable) : GoogleSignInError(
        cause.message ?: "Не удалось войти через Google",
    )
}

object GoogleAuth {
    fun isConfigured(): Boolean {
        val id = BuildConfig.GOOGLE_WEB_CLIENT_ID
        return id.isNotBlank() && !id.startsWith("REPLACE_ME")
    }

    /**
     * Credential Manager: сначала One Tap (GetGoogleIdOption),
     * если аккаунтов нет — кнопочный Sign in with Google.
     */
    suspend fun signIn(context: Context): GoogleAccount {
        if (!isConfigured()) throw GoogleSignInError.NotConfigured()

        val manager = CredentialManager.create(context)
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

        return try {
            requestGoogleId(manager, context, serverClientId, filterAuthorized = true)
        } catch (_: NoCredentialException) {
            try {
                requestGoogleId(manager, context, serverClientId, filterAuthorized = false)
            } catch (_: NoCredentialException) {
                requestSignInButton(manager, context, serverClientId)
            }
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInError.Cancelled()
        } catch (e: GetCredentialException) {
            if (e is NoCredentialException) {
                try {
                    requestSignInButton(manager, context, serverClientId)
                } catch (e2: GetCredentialCancellationException) {
                    throw GoogleSignInError.Cancelled()
                } catch (_: NoCredentialException) {
                    throw GoogleSignInError.NoAccount()
                } catch (e2: GetCredentialException) {
                    throw GoogleSignInError.Failed(e2)
                }
            } else {
                throw GoogleSignInError.Failed(e)
            }
        }
    }

    private suspend fun requestGoogleId(
        manager: CredentialManager,
        context: Context,
        serverClientId: String,
        filterAuthorized: Boolean,
    ): GoogleAccount {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterAuthorized)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = manager.getCredential(context, request)
        return parse(result.credential)
    }

    private suspend fun requestSignInButton(
        manager: CredentialManager,
        context: Context,
        serverClientId: String,
    ): GoogleAccount {
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = manager.getCredential(context, request)
        return parse(result.credential)
    }

    private fun parse(credential: androidx.credentials.Credential): GoogleAccount {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return try {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleAccount(
                    googleId = google.id,
                    idToken = google.idToken,
                    email = google.id,
                    displayName = google.displayName,
                )
            } catch (e: GoogleIdTokenParsingException) {
                throw GoogleSignInError.Failed(e)
            }
        }
        throw GoogleSignInError.Failed(IllegalStateException("Неожиданный тип credential: ${credential.type}"))
    }
}
