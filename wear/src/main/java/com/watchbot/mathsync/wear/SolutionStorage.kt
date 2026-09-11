package com.watchbot.mathsync.wear

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class WatchSolution(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SolutionStorage {
    private const val PREFS_NAME = "math_solutions_prefs"
    private const val KEY_SOLUTIONS = "saved_solutions"
    private const val MAX_SAVED = 15

    private val _solutionsFlow = MutableStateFlow<List<WatchSolution>>(emptyList())
    val solutionsFlow: StateFlow<List<WatchSolution>> = _solutionsFlow.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadFromPrefs()
        }
    }

    private fun loadFromPrefs() {
        val jsonStr = prefs?.getString(KEY_SOLUTIONS, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<WatchSolution>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    WatchSolution(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        title = obj.optString("title", "Решение"),
                        content = obj.getString("content"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            _solutionsFlow.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addSolution(title: String, content: String, timestamp: Long = System.currentTimeMillis()) {
        val current = _solutionsFlow.value.toMutableList()
        // Avoid duplicate consecutive entries
        if (current.isNotEmpty() && current.first().content == content) {
            return
        }

        val newItem = WatchSolution(
            id = timestamp,
            title = title.ifBlank { "Решение ${current.size + 1}" },
            content = content,
            timestamp = timestamp
        )
        current.add(0, newItem)

        // Keep last MAX_SAVED
        val trimmed = if (current.size > MAX_SAVED) current.subList(0, MAX_SAVED) else current
        _solutionsFlow.value = trimmed

        saveToPrefs(trimmed)
    }

    fun clearAll() {
        _solutionsFlow.value = emptyList()
        prefs?.edit()?.remove(KEY_SOLUTIONS)?.apply()
    }

    private fun saveToPrefs(list: List<WatchSolution>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("content", item.content)
                put("timestamp", item.timestamp)
            }
            jsonArray.put(obj)
        }
        prefs?.edit()?.putString(KEY_SOLUTIONS, jsonArray.toString())?.apply()
    }
}
