package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.User

/**
 * Manages persistent user session and role authorization checks.
 */
object SessionManager {
    private const val PREF_NAME = "dairy_user_session"
    private const val KEY_USERNAME = "username"
    private const val KEY_FULL_NAME = "full_name"
    private const val KEY_ROLE = "role"
    private const val KEY_PHONE = "phone"
    private const val KEY_PROFILE_INDEX = "profile_index"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Persists the authenticated user details.
     */
    fun saveUser(context: Context, user: User) {
        getPrefs(context).edit().apply {
            putString(KEY_USERNAME, user.username)
            putString(KEY_FULL_NAME, user.fullName)
            putString(KEY_ROLE, user.role)
            putString(KEY_PHONE, user.phoneNumber)
            putInt(KEY_PROFILE_INDEX, user.profileIndex)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    /**
     * Clears user session on logout.
     */
    fun clearUser(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    /**
     * Retrieves the stored user session if available.
     */
    fun getSavedUser(context: Context): User? {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_IS_LOGGED_IN, false)) return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val fullName = prefs.getString(KEY_FULL_NAME, "") ?: ""
        val role = prefs.getString(KEY_ROLE, "customer") ?: "customer"
        val phone = prefs.getString(KEY_PHONE, "") ?: ""
        val profileIndex = prefs.getInt(KEY_PROFILE_INDEX, 0)
        return User(
            username = username,
            passwordHash = "",
            fullName = fullName,
            role = role,
            phoneNumber = phone,
            profileIndex = profileIndex
        )
    }

    /**
     * Validates whether a given role string corresponds to an administrator or super administrator.
     */
    fun isAdminOrSuperAdmin(role: String?): Boolean {
        if (role == null) return false
        val r = role.trim().lowercase()
        return r == "admin" || r == "super admin" || r == "superadmin" || r == "super_admin" || r.contains("admin")
    }

    /**
     * Checks if the currently authenticated user on this device is an admin or super admin.
     */
    fun isAdminOrSuperAdminLoggedIn(context: Context): Boolean {
        val user = getSavedUser(context) ?: return false
        return isAdminOrSuperAdmin(user.role)
    }
}
