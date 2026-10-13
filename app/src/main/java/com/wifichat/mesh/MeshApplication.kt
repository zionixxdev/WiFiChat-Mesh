package com.wifichat.mesh

import android.app.Application
import com.wifichat.mesh.util.MeshLog

class MeshApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MeshLog.i("WiFiChat Mesh starting; peerId=${container.settings.peerId.take(8)}")
    }
}
