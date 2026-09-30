package com.example.solarpredict.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Wrapper around FirebaseAuth providing suspend functions for sign-in flows.
 */
class AuthRepository(private val auth: FirebaseAuth) {

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isGuest: Boolean get() = currentUser?.isAnonymous == true

    suspend fun signIn(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return requireNotNull(result.user) { "Firebase returned a null user after sign-in" }
    }

    suspend fun register(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return requireNotNull(result.user) { "Firebase returned a null user after sign-up" }
    }

    suspend fun signInAnonymously(): FirebaseUser {
        val result = auth.signInAnonymously().await()
        return requireNotNull(result.user) { "Firebase returned a null user after anonymous sign-in" }
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    /** Upgrades the currently-anonymous user to a permanent email/password account. */
    suspend fun linkAnonymousWithEmail(email: String, password: String): FirebaseUser {
        val current = currentUser ?: error("No anonymous user to link")
        val credential = EmailAuthProvider.getCredential(email, password)
        val result = current.linkWithCredential(credential).await()
        return requireNotNull(result.user) { "Firebase returned a null user after link" }
    }

    fun signOut() {
        auth.signOut()
        // Clear every cross-screen cache so a fresh session starts blank.
        // Without this, the previous prediction (Result), favorites, history
        // etc. could leak across accounts when the user signs back in.
        GuestStore.reset()
        WizardSession.clear()
    }
}
