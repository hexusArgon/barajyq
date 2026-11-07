package kz.astana.afisha

import android.app.Application
import kz.astana.afisha.data.SessionStore

class AstanaAfishaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionStore.init(this)
    }
}
