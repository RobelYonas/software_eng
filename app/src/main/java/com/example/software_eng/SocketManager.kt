package com.example.software_eng

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject


object SocketManager {
    //these two variables just basically connects socket to the server
    //the deviceupdatecallback allows the UI to react to updates pretty much
    private lateinit var socket: Socket
    private var onDeviceUpdateCallback: ((JSONObject) -> Unit)? = null

    fun connect() {
        try {
            //IP here to change to IP of host server
            socket = IO.socket("http://192.168.50.252:5001")
            socket.on(Socket.EVENT_CONNECT) {
                //just checking to make sure its working right in the logcat
                Log.d("SocketIO", "connected")
            }

            socket.on("device_update") { args ->
                val data = args[0] as JSONObject
                Log.d("SocketIO", "update received -> $data")
                onDeviceUpdateCallback?.invoke(data)
            }
            socket.connect()
        } catch (e: Exception){
            Log.e("SocketIO", "cant connect", e)
        }
    }

    //this function pretty much takes the command and puts it together to emits the command to server
    fun emitUpdate(deviceName: String, status: Boolean) {
        val command = JSONObject().apply {
            put("device_name", deviceName)
            put("status", if (status) "ON" else "OFF")
        }
        //here the device:update should stay like this because it matches the communication protocol
        //layout of the server and allows the server to understand the command.
        socket.emit("device:update", command)
        //making sure it actually sent, check logcat
        Log.d("SocketIO", "Command sent: $command")
    }

    //this it the functio to run when there is an update received
    fun onUpdate(callback: (JSONObject) -> Unit){
        onDeviceUpdateCallback = callback
    }

    //disconnects the socket once app is closed
    fun disconnect() {
        if(::socket.isInitialized) {
            socket.disconnect()
            socket.off()
        }
    }
}