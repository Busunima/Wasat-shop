package com.wasat.shop.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.wasat.shop.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Custom claims, выставляемые сервером после POST /api/stores/init. */
data class StoreClaims(val storeId: String?, val role: String?)

/**
 * Вход через Sign in with Google (Credential Manager, ТЗ §4.1) поверх Firebase Auth.
 * [firebaseAuth] nullable: без google-services.json аутентификация недоступна,
 * UI гейтится через [isConfigured].
 */
@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth?,
) {
    val isConfigured: Boolean
        get() = firebaseAuth != null && BuildConfig.GOOGLE_WEB_CLIENT_ID != "MISSING_WEB_CLIENT_ID"

    fun currentUser(): FirebaseUser? = firebaseAuth?.currentUser

    /**
     * Credential Manager → GoogleIdTokenCredential → FirebaseAuth.signInWithCredential.
     * [activityContext] обязан быть Activity-контекстом (требование Credential Manager);
     * репозиторий его не хранит.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> = runCatching {
        val auth = checkNotNull(firebaseAuth) { "Firebase не сконфигурирован" }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            // false: показывать и аккаунты, ещё не использовавшиеся в приложении (первый вход)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
        val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.signInWithCredential(firebaseCredential).await().user
        checkNotNull(user) { "Firebase вернул пустого пользователя" }
    }

    /**
     * Принудительно обновляет ID-токен (getIdToken(true)), чтобы подтянуть custom claims
     * (storeId, role), выставленные сервером при создании магазина.
     */
    suspend fun refreshClaims(): Result<StoreClaims> = runCatching {
        val user = checkNotNull(firebaseAuth?.currentUser) { "Пользователь не аутентифицирован" }
        val result = user.getIdToken(true).await()
        StoreClaims(
            storeId = result.claims["storeId"] as? String,
            role = result.claims["role"] as? String,
        )
    }

    fun signOut() {
        firebaseAuth?.signOut()
    }
}
