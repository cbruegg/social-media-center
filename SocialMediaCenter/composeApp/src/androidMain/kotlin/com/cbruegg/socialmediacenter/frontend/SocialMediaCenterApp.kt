package com.cbruegg.socialmediacenter.frontend

import android.app.Application

// Very dirty, just to make AndroidPlatform easier to initialize
lateinit var app: SocialMediaCenterApp

class SocialMediaCenterApp: Application() {
    init {
        app = this
    }
}