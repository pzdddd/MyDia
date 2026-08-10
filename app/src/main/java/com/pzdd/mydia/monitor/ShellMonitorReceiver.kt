package com.pzdd.mydia.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import timber.log.Timber

/**
 * 接收被注入进程通过广播回传的 Shell 监控数据。
 * 在 AndroidManifest.xml 里静态注册（targetSdk 限制隐式广播必须静态注册）。
 *
 * 拿到数据后：MVP 先打日志；后续可改成喂给一个 LiveData/Flow，由 RecyclerView 展示。
 */
class ShellMonitorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: "?"
        val cmd = intent.getStringExtra(EXTRA_COMMAND) ?: ""
        val line = "[$pkg] $cmd"
        Log.i(TAG, line)
        Timber.i("SHELL: %s", line)

        // TODO: 想做实时列表展示，把 line 塞进一个全局 MutableLiveData / StateFlow，
        //       然后在一个 RecyclerView Activity/Fragment 里订阅。
    }

    companion object {
        const val TAG = "MyDia/Shell"
        const val ACTION = "com.pzdd.mydia.ACTION_SHELL_MONITOR"
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_COMMAND = "command"
    }
}
