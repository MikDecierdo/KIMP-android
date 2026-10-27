package com.kimgdecierdo.kimp

import android.app.Application
import com.kimgdecierdo.kimp.engine.Engine
import com.kimgdecierdo.kimp.engine.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KimpApp : Application() {
    /** Lives as long as the process; used for work that must outlive a screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Prefs.load(this)
        Engine.start(this, scope)
        scope.launch { Updater.check(quiet = true) }
    }
}
