package com.example.software_eng

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.software_eng.ui.theme.Software_engTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

// Data class for User
data class User(
    val id: Int,
    val username: String,
    val email: String,
    val password: String
)

// Define the BASE_URL for your backend
const val URL = "http://194.47.40.250:5000"

class UserAuth : ComponentActivity() {

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
                UserUI()
            }
        }
    }

    @Composable
    fun UserUI() {
        var users by remember { mutableStateOf<List<User>>(emptyList()) }
        var statusMessage by remember { mutableStateOf("Loading...") }
        var username by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var selectedUserId by remember { mutableStateOf<Int?>(null) }

        // Poll users every 5 seconds to update automatically
        LaunchedEffect(Unit) {
            while (true) {
                users = fetchUsers()
                delay(5000)
            }
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("User Management", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        createUser(username, email, password) { result ->
                            statusMessage = result
                        }
                    }) {
                        Text("Create User")
                    }
                    Button(onClick = {
                        selectedUserId?.let {
                            updateUser(it, username, email, password) { result ->
                                statusMessage = result
                            }
                        }
                    }) {
                        Text("Update User")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    // Fetch users on demand
                    CoroutineScope(Dispatchers.IO).launch {
                        users = fetchUsers()
                    }
                }) {
                    Text("Fetch Users")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Status: $statusMessage")
                Spacer(modifier = Modifier.height(12.dp))
                users.forEach { user ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("ID: ${user.id}")
                            Text("Username: ${user.username}")
                            Text("Email: ${user.email}")
                            Text("Password: ${user.password}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                // Populate fields for update
                                username = user.username
                                email = user.email
                                password = user.password
                                selectedUserId = user.id
                            }) {
                                Text("Select")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    // Fetch users from the backend
    private suspend fun fetchUsers(): List<User> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$URL/users")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(bodyString)
            // Use "users" key as per the documentation
            val userArray = root.getJSONObject("data").getJSONArray("users")
            val result = mutableListOf<User>()
            for (i in 0 until userArray.length()) {
                val obj = userArray.getJSONObject(i)
                result.add(
                    User(
                        id = obj.getInt("id"),
                        username = obj.getString("username"),
                        email = obj.getString("email"),
                        password = obj.getString("password")
                    )
                )
            }
            result
        }
    }

    // Create a new user (POST /user/register)
    private fun createUser(username: String, email: String, password: String, onResult: (String) -> Unit) {
        val json = JSONObject().apply {
            put("username", username)
            put("email", email)
            put("password", password)
        }
        Log.d("UserRoutes", "Creating user with JSON: $json")
        println("POST Payload for /user/register: $json")
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$URL/user/register")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("Create User Failed: ${e.message}")
                Log.e("UserRoutes", "Create User Request failed", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResult("User created: ${it.code}")
                    Log.d("UserRoutes", "Create User Response: ${it.body?.string()}")
                }
            }
        })
    }

    // Update an existing user (PATCH /user/<userId>/)
    private fun updateUser(userId: Int, username: String, email: String, password: String, onResult: (String) -> Unit) {
        val json = JSONObject().apply {
            put("username", username)
            put("email", email)
            put("password", password)
        }
        Log.d("UserRoutes", "Updating user $userId with JSON: $json")
        println("PATCH Payload for /user/$userId: $json")
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$URL/user/$userId")
            .patch(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("Update User Failed: ${e.message}")
                Log.e("UserRoutes", "Update User Request failed", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResult("User updated: ${it.code}")
                    Log.d("UserRoutes", "Update User Response: ${it.body?.string()}")
                }
            }
        })
    }
}
