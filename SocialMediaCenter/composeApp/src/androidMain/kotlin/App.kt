import android.app.Application

// Very dirty, just to make AndroidPlatform easier to initialize
lateinit var app: App

class App: Application() {
    init {
        app = this
    }
}