package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * FirebaseManager provides the structure for Firebase Auth and Firestore integration.
 * Since this is currently running as a "Demo App" without a google-services.json file,
 * these functions are available but typically wrapped in try-catch to prevent crashes.
 */
object FirebaseManager {
    private const val TAG = "FirebaseManager"
    
    // Lazy initialization so it doesn't crash if Firebase isn't configured at startup
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /**
     * Signs in a user securely via Firebase Authentication (Google Sign-In).
     * @param idToken The token retrieved from Google Sign-In intent.
     */
    suspend fun signInWithGoogle(idToken: String): Boolean {
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            Log.d(TAG, "Successfully signed in via Firebase: ${authResult.user?.uid}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Auth failed (Are google-services.json and Web Client ID configured?): ${e.message}")
            false
        }
    }

    /**
     * Syncs local user data and orders to Firestore.
     */
    suspend fun syncDataToFirestore(user: UserEntity, orders: List<OrderEntity>) {
        try {
            val uid = auth.currentUser?.uid ?: return
            
            val userMap = hashMapOf(
                "name" to user.name,
                "phone" to user.phone,
                "location" to user.selectedLocation,
                "uiTier" to user.uiTier,
                "updatedAt" to System.currentTimeMillis()
            )
            
            // Sync User Profile
            firestore.collection("users").document(uid).set(userMap).await()
            
            // Sync Orders
            for (order in orders) {
                val orderMap = hashMapOf(
                    "id" to order.id,
                    "shopId" to order.shopId,
                    "totalAmount" to order.totalAmount,
                    "status" to order.status,
                    "timestamp" to order.timestamp
                )
                firestore.collection("users").document(uid)
                    .collection("orders").document(order.id.toString())
                    .set(orderMap).await()
            }
            Log.d(TAG, "Data successfully synced to Firestore.")
        } catch (e: Exception) {
            Log.e(TAG, "Firestore sync failed: ${e.message}")
        }
    }
}
