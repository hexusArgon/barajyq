package kz.barajyq.app

import android.app.Application
import kz.barajyq.app.data.SessionStore

class BarajyqApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionStore.init(this)
    }
}
