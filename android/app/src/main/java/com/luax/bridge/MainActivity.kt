// Kotlin · MainActivity.kt · Android · LUAX BRIDGE prototype
package com.luax.bridge

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class MainActivity : Activity() {
    private val client = OkHttpClient()
    private var socket: WebSocket? = null
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var urlInput: EditText
    private lateinit var tokenInput: EditText
    private var connected = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 12, 12)
        window.navigationBarColor = Color.rgb(12, 12, 12)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(18))
            setBackgroundColor(Color.rgb(12, 12, 12))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(TextView(this).apply {
            text = "LUAX BRIDGE"
            textSize = 27f
            setTextColor(Color.rgb(255, 55, 65))
            gravity = Gravity.CENTER
        }, match())
        content.addView(TextView(this).apply {
            text = "CONTROLE • TELEMETRIA • UNITY"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }, marginTop(4))

        status = TextView(this).apply {
            text = "● DESCONECTADO"
            textSize = 15f
            setTextColor(Color.rgb(255, 90, 90))
            gravity = Gravity.CENTER
        }
        content.addView(status, marginTop(20))

        urlInput = edit("ws://10.0.2.2:8787", "URL WebSocket")
        tokenInput = edit("change-this-token", "Token do servidor")
        content.addView(urlInput, marginTop(12))
        content.addView(tokenInput, marginTop(8))

        content.addView(button("CONECTAR") { connect() }, marginTop(12))
        content.addView(button("DESCONECTAR") {
            socket?.close(1000, "User disconnected")
            socket = null
            setConnected(false, "Desconectado")
        }, marginTop(8))

        content.addView(TextView(this).apply {
            text = "CONTROLES DE DEMONSTRAÇÃO"
            textSize = 14f
            setTextColor(Color.WHITE)
        }, marginTop(22))

        listOf("ESP LINE", "ESP BOX", "ESP NAME", "AIM ASSIST (TESTE)").forEach { feature ->
            var enabled = false
            content.addView(button("$feature: OFF") {
                enabled = !enabled
                (it as Button).text = "$feature: ${if (enabled) "ON" else "OFF"}"
                send(JSONObject()
                    .put("type", "command")
                    .put("token", tokenInput.text.toString())
                    .put("payload", JSONObject()
                        .put("feature", feature.lowercase().replace(" ", "_"))
                        .put("enabled", enabled)))
            }, marginTop(8))
        }

        content.addView(button("ENVIAR TELEMETRIA DE TESTE") {
            val sample = JSONObject()
                .put("type", "telemetry")
                .put("token", tokenInput.text.toString())
                .put("payload", JSONObject()
                    .put("source", "android-demo")
                    .put("targets", org.json.JSONArray()
                        .put(JSONObject().put("id", "dummy-1")
                            .put("x", 0.5).put("y", 0.5).put("visible", true))))
            send(sample)
        }, marginTop(8))

        log = TextView(this).apply {
            text = "Log de eventos aparecerá aqui."
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        content.addView(log, marginTop(18))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun match() = LinearLayout.LayoutParams(-1, -2)
    private fun marginTop(top: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
    }

    private fun edit(value: String, hintText: String) = EditText(this).apply {
        setText(value)
        hint = hintText
        textSize = 14f
        setSingleLine(true)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundColor(Color.rgb(35, 35, 35))
    }

    private fun button(label: String, action: (Button) -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(180, 25, 35)))
        setOnClickListener { action(this) }
    }

    private fun connect() {
        val request = Request.Builder().url(urlInput.text.toString()).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = JSONObject().put("type", "hello")
                    .put("token", tokenInput.text.toString())
                    .put("payload", JSONObject())
                webSocket.send(hello.toString())
                runOnUiThread { setConnected(true, "Conectado ao servidor") }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread { log.text = "Recebido:\n$text\n\n${log.text}" }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread { setConnected(false, "Falha: ${t.message}") }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                runOnUiThread { setConnected(false, "Conexão encerrada") }
            }
        })
    }

    private fun send(message: JSONObject) {
        if (!connected || socket == null) {
            log.text = "Sem conexão. Conecte ao servidor primeiro."
            return
        }
        socket?.send(message.toString())
        log.text = "Enviado:\n$message\n\n${log.text}"
    }

    private fun setConnected(value: Boolean, message: String) {
        connected = value
        status.text = if (value) "● CONECTADO — $message" else "● DESCONECTADO — $message"
        status.setTextColor(if (value) Color.rgb(90, 230, 130) else Color.rgb(255, 90, 90))
    }

    override fun onDestroy() {
        socket?.close(1000, "Activity destroyed")
        client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }
}
