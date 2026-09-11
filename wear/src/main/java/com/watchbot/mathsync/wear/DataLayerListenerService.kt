package com.watchbot.mathsync.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class DataLayerListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WatchDataListener"
        private const val MATH_DATA_PATH = "/math_solution"
        private const val MATH_MESSAGE_PATH = "/instant_solution_sync"
        private const val KEY_CONTENT = "content"
        private const val KEY_TITLE = "title"
        private const val KEY_TIMESTAMP = "timestamp"
    }

    override fun onCreate() {
        super.onCreate()
        SolutionStorage.init(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: ${dataEvents.count} events")
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                if (uri.path == MATH_DATA_PATH) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val content = dataMap.getString(KEY_CONTENT) ?: ""
                    val title = dataMap.getString(KEY_TITLE) ?: "Решение"
                    val timestamp = dataMap.getLong(KEY_TIMESTAMP, System.currentTimeMillis())

                    if (content.isNotBlank()) {
                        Log.d(TAG, "Received new solution from DataLayer: $title")
                        SolutionStorage.addSolution(title, content, timestamp)
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == MATH_MESSAGE_PATH) {
            val content = String(messageEvent.data, Charsets.UTF_8)
            Log.d(TAG, "Received instant message solution: ${content.take(50)}...")
            if (content.isNotBlank()) {
                SolutionStorage.addSolution("Новое решение", content)
            }
        }
    }
}
