package com.example.workpilotmini.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Turns raw Firebase/Firestore exceptions into short, user-facing messages
 * instead of leaking internal English error strings straight to the UI.
 *
 * Kept in one place on purpose: this is the single seam we'll swap over to
 * string resources (EN/BN) when the language toggle is added, so nothing
 * else in the app needs to change.
 */
object AppError {

    fun message(t: Throwable): String = when (t) {
        is FirebaseNetworkException ->
            "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না। কানেকশন চেক করে আবার চেষ্টা করুন।"

        is FirebaseTooManyRequestsException ->
            "অনেকবার চেষ্টা করা হয়েছে। কিছুক্ষণ পর আবার চেষ্টা করুন।"

        is FirebaseAuthInvalidCredentialsException ->
            "ইমেইল বা পাসওয়ার্ড সঠিক নয়।"

        is FirebaseAuthInvalidUserException ->
            "এই ইমেইলে কোনো অ্যাকাউন্ট পাওয়া যায়নি।"

        is FirebaseAuthUserCollisionException ->
            "এই ইমেইল দিয়ে আগেই একটি অ্যাকাউন্ট আছে। লগইন করুন।"

        is FirebaseAuthWeakPasswordException ->
            "পাসওয়ার্ড খুবই দুর্বল। কমপক্ষে ৬ ক্যারেক্টার, চেষ্টা করুন আরেকটু শক্তিশালী পাসওয়ার্ড দিতে।"

        is FirebaseAuthRecentLoginRequiredException ->
            "নিরাপত্তার জন্য পাসওয়ার্ড পরিবর্তন করতে আবার লগইন করতে হবে। লগ আউট করে আবার লগইন করে চেষ্টা করুন।"

        is FirebaseFirestoreException -> when (t.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "এই কাজ করার অনুমতি নেই।"
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "সার্ভারে পৌঁছানো যাচ্ছে না। ইন্টারনেট চেক করে আবার চেষ্টা করুন।"
            else ->
                "কিছু একটা সমস্যা হয়েছে। আবার চেষ্টা করুন।"
        }

        else -> {
            // Firebase's low-level "configuration-not-found" / internal-error
            // surfaces as a plain RuntimeException with this text in it —
            // catch it specifically so we don't show a cryptic message.
            val raw = t.message.orEmpty()
            when {
                raw.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
                    raw.contains("internal error", ignoreCase = true) ->
                    "অ্যাপের Firebase সেটআপ সম্পূর্ণ নয় (Authentication enable করা নেই)। ডেভেলপারকে জানান।"
                raw.isNotBlank() && raw.length < 120 -> raw
                else -> "কিছু একটা সমস্যা হয়েছে। আবার চেষ্টা করুন।"
            }
        }
    }
}
