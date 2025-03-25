package com.example.software_eng

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.software_eng.ui.theme.Software_engTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

// Data class for Device (can be moved to a separate Device.kt file)
data class Device(
    val id: Int,
    val name: String,
    val description: String,
    val status: Boolean,
    val type: String,
    val value: Double
)

// Base URL for your backend – change this to your host's IP if needed
const val BASE_URL = "http://192.168.0.100:5000"

class MainActivity : ComponentActivity() {

    // Configure OkHttp logging interceptor for full HTTP logs
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Build OkHttpClient with the interceptor
    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Software_engTheme {
                DeviceUI()
            }
        }
    }

    @Composable
    fun DeviceUI() {
        var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
        var statusMessage by remember { mutableStateOf("Loading...") }

        // Fetch devices when the UI first launches
        LaunchedEffect(Unit) {
            devices = fetchDevices()
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text("Devices", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(12.dp))

                devices.forEach { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Name: ${device.name}")
                            Text("Type: ${device.type}")
                            Text("Status: ${if (device.status) "ON" else "OFF"}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                toggleDevice(device.id, device.name, device.status) { result ->
                                    statusMessage = result
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val updatedDevices = fetchDevices()
                                        withContext(Dispatchers.Main) {
                                            devices = updatedDevices
                                        }
                                    }
                                }
                            }) {
                                Text("Toggle")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Status: $statusMessage")
            }
        }
    }

    // Fetch devices from the backend; response is automatically closed using use { }
    private suspend fun fetchDevices(): List<Device> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/device/all")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(bodyString)
            val deviceArray = root.getJSONObject("data").getJSONArray("devices")
            val result = mutableListOf<Device>()
            for (i in 0 until deviceArray.length()) {
                val obj = deviceArray.getJSONObject(i)
                result.add(
                    Device(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        description = obj.getString("description"),
                        status = obj.getBoolean("status"),
                        type = obj.getString("type"),
                        value = obj.getDouble("value")
                    )
                )
            }
            result
        }
    }

    // Toggle device status by sending a PATCH request.
    // This function logs the JSON payload to both Logcat and the terminal.
    private fun toggleDevice(id: Int, deviceName: String, currentStatus: Boolean, onResult: (String) -> Unit) {
        val newStatus = !currentStatus
        // Build JSON payload including both "name" and "status"
        val json = JSONObject().apply {
            put("name", deviceName)
            put("status", newStatus)
        }
        // Log the payload to Logcat
        Log.d("ToggleDevice", "Sending JSON to /device/$id: $json")
        // Also print it to the terminal
        println("PATCH Payload for /device/$id: $json")
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/device/$id")
            .patch(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("Failed: ${e.message}")
                Log.e("ToggleDevice", "Request failed", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResult("Device $id updated: ${it.code}")
                    Log.d("ToggleDevice", "Response: ${it.body?.string()}")
                }
            }
        })
    }
}
