package com.pzdd.mydia.module.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * MCP server 前台服务。
 *
 * 保持 server 在后台存活（Doze/清理任务后不丢）。绑定地址读 SP：
 *  - mcp_enabled : 总开关
 *  - mcp_bind_lan : true=0.0.0.0（局域网直连）/ false=127.0.0.1（adb forward）
 *  - mcp_port : 端口（默认 8090）
 */
class McpServerService : Service() {

    companion object {
        private const val CHANNEL_ID = "mcp_server"
        private const val NOTIF_ID = 8090

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, McpServerService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, McpServerService::class.java))
        }
    }

    private var server: McpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("MCP 服务启动中…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动/停止由设置页 Action 按钮显式控制，服务本身不读开关
        startMcpServer(getSharedPreferences("digXposed", Context.MODE_PRIVATE))
        return START_STICKY
    }

    private fun startMcpServer(sp: android.content.SharedPreferences) {
        val lan = sp.getBoolean("mcp_bind_lan", false)
        val port = sp.getInt("mcp_port", 8090).coerceIn(1024, 65535)
        val host = if (lan) "0.0.0.0" else "127.0.0.1"
        // 服务在跑但绑定地址/端口与当前配置不符 → 重启重新绑定
        // （切局域网开关 / 改端口后生效；0.0.0.0 绑定同时覆盖本机 127.0.0.1 与局域网 IP）
        if (server?.isRunning == true) {
            if (server?.boundAddress == "$host:$port") return
            server?.stop()
            server = null
        }
        val s = McpServer(this, host, port)
        s.start()
        server = s
        updateNotification(if (lan) "MCP: ${host}:$port（局域网，本机 127.0.0.1 也可连）" else "MCP: ${host}:$port（adb forward）")
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    // ==================== 通知 ====================

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "MCP Server", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("MyDia MCP Server")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
