package com.watchbot.mathsync.mobile

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class DataLayerManager(private val context: Context) {

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)

    private val _connectedNodes = MutableStateFlow<List<Node>>(emptyList())
    val connectedNodes: StateFlow<List<Node>> = _connectedNodes.asStateFlow()

    companion object {
        private const val TAG = "DataLayerManager"
        const val MATH_DATA_PATH = "/math_solution"
        const val MATH_MESSAGE_PATH = "/instant_solution_sync"
        const val KEY_CONTENT = "content"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_TITLE = "title"
    }

    suspend fun refreshConnectedNodes() {
        withContext(Dispatchers.IO) {
            try {
                val nodes = nodeClient.connectedNodes.await()
                _connectedNodes.value = nodes
                Log.d(TAG, "Connected nodes: ${nodes.map { it.displayName }}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get connected nodes", e)
            }
        }
    }

    suspend fun sendSolutionToWatch(title: String, content: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()

                // 1. Send via DataClient (guaranteed delivery and caching)
                val putDataMapReq = PutDataMapRequest.create(MATH_DATA_PATH).apply {
                    dataMap.putString(KEY_TITLE, title)
                    dataMap.putString(KEY_CONTENT, content)
                    dataMap.putLong(KEY_TIMESTAMP, timestamp)
                }
                val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
                dataClient.putDataItem(putDataReq).await()

                // 2. Also send via MessageClient to wake up UI instantly
                val nodes = nodeClient.connectedNodes.await()
                val payload = content.toByteArray(Charsets.UTF_8)
                for (node in nodes) {
                    messageClient.sendMessage(node.id, MATH_MESSAGE_PATH, payload).await()
                }

                Log.d(TAG, "Successfully synced solution to ${nodes.size} nodes at $timestamp")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing solution to watch", e)
                false
            }
        }
    }
}
