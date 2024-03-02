package com.cbruegg.socialmediacenter.frontend

import SocialMediaCenterApp
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.multiplatform.lifecycle.LifecycleTracker
import com.multiplatform.lifecycle.LocalLifecycleTracker
import com.multiplatform.lifecyle.AndroidLifecycleEventObserver

class MainActivity : AppCompatActivity() {
    private val lifecycleTracker = LifecycleTracker()
    private val observer = AndroidLifecycleEventObserver(lifecycleTracker)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(observer)

        setContent {
            CompositionLocalProvider(LocalLifecycleTracker provides lifecycleTracker) {
                SocialMediaCenterApp()
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    SocialMediaCenterApp()
}