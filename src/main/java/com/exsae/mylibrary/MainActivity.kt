package com.exsae.mylibrary

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AutomationEvents(json:String):JSONObject(json) {
    val eventType:String = this.optString("event_type")
    val eventName:String = this.optString("event")
}

open class MainActivity : AppCompatActivity() {
    private val appClient = OkHttpClient()
    private var appSocket: WebSocket? = null
    private var sharedPref: SharedPreferences? = null
    private var imageView: ImageView? = null
    private var connected: Boolean = false
    private var toast: Toast? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this.sharedPref = getSharedPreferences("MySettings", Context.MODE_PRIVATE)

        this.imageView = findViewById(R.id.imageView)
        toast = Toast.makeText(applicationContext,null,Toast.LENGTH_LONG)

        createConnection()
    }

    fun openPreferences(view: View?=null){

        if (this.appSocket !== null){
            this.appSocket?.close(1000,"Reconnecting!")
        }

        val intent = Intent(this,SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun updateConnectionStatus(text: String){
        runOnUiThread { findViewById<TextView>(R.id.connectionStatus).text = text }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> sendClickCommand()
        }
        return true
    }

    fun reconnectClient(view: View){

        if (this.appSocket !== null){
            this.appSocket?.close(1000,"Reconnecting!")
        }
        overridePendingTransition(0,0)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        finish()
        overridePendingTransition(0,0)
        startActivity(intent)
    }

    private fun createConnection(){
        updateConnectionStatus("Connecting...")
        imageView?.post {
            imageView?.setImageResource(R.mipmap.connecting_foreground)
        }

        val sharedPref = getSharedPreferences("MySettings", Context.MODE_PRIVATE)

        val hostIp = sharedPref.getString("hostIp","192.168.43.145")
        val hostPort = sharedPref.getInt("hostPort",8000).toString()
        val hostCode = sharedPref.getString("hostCode","sample")

        val host = "ws://$hostIp:$hostPort/ws/pcautomation/$hostCode/"
        val appRequest = Request.Builder().url(host).build()

        try {
            appClient.newWebSocket(appRequest,object:WebSocketListener(){
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connected = true
                    appSocket = webSocket
                    webSocket.send("{\"event_type\":\"connection\",\"event\":\"phone_connected\",\"args\":[],\"kwargs\":{}}")
                    val txt = "Connected!"
                    updateConnectionStatus(txt)
                    imageView?.post {
                        imageView?.setImageResource(R.mipmap.check_foreground)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val automationEvents = AutomationEvents(text)
                    runOnUiThread {
                        toast?.setText("${automationEvents.eventType} -> ${automationEvents.eventName}")
                        toast?.show()
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    connected = false
                    updateConnectionStatus("Disconnected!")
                    imageView?.post {
                        imageView?.setImageResource(R.mipmap.plug_foreground)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    connected = false
                    updateConnectionStatus("Error! Falling back on non-realtime")
                    imageView?.post {
                        imageView?.setImageResource(R.mipmap.error_foreground)
                        val toast = Toast.makeText(applicationContext,t.toString(),Toast.LENGTH_LONG)
                        toast.show()
                    }
                }
            })
            appClient.dispatcher.executorService.shutdown()
        } catch (ex: Exception){
            updateConnectionStatus(ex.toString())
        }
    }

    private fun sendClickCommand(){
        val json = "{\"event_type\":\"mouse\",\"event\":\"click\",\"args\":[],\"kwargs\":{}}"
        sendCommand(json)
    }

    fun sendCloseCommand(view: View){
        val json = "{\"event_type\":\"webdriver\",\"event\":\"close\",\"args\":[],\"kwargs\":{}}"
        sendCommand(json)
    }

    private fun sendCommand(json:String){
        if (connected){
            try {
                this.appSocket?.send(json)
            } catch (ex: Exception) {
                runOnUiThread {
                    toast?.setText(ex.toString())
                    toast?.show()
                }
            }
        } else {
            runOnUiThread {
                toast?.setText("Sending command API...")
                toast?.show()
            }
            val thread = Thread {
                val hostIp = sharedPref?.getString("hostIp", "192.168.43.145")
                val hostPort = sharedPref?.getInt("hostPort", 8000).toString()
                val hostCode = sharedPref?.getString("hostCode", "sample")

                try {
                    val body = json.toRequestBody("application/json".toMediaTypeOrNull())
                    val host = "http://$hostIp:$hostPort/api/pcautomation/$hostCode/"
                    val appRequest = Request.Builder().url(host).post(body).build()
                    val call = appClient.newCall(appRequest)
                    val response = call.execute()
                    if (response.code == 200) {
                        val automationEvents = response.body?.string()?.let { AutomationEvents(it) }
                        runOnUiThread {
                            toast?.setText("${automationEvents?.eventType} -> ${automationEvents?.eventName}")
                            toast?.show()
                        }
                    } else {
                        toast?.setText("Error -> ${response.code}")
                        toast?.show()
                    }
                } catch (ex: Exception) {
                    runOnUiThread {
                        toast?.setText(ex.toString())
                        toast?.show()
                    }
                }
            }
            thread.start()

        }
    }
}