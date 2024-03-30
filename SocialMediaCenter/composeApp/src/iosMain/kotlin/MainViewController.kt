import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.multiplatform.lifecycle.LifecycleTracker
import com.multiplatform.lifecycle.LocalLifecycleTracker
import com.multiplatform.lifecyle.LifecycleComposeUIVCDelegate
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIViewController
import util.InAppBrowserOpener
import util.LocalInAppBrowserOpener

fun MainViewController(): UIViewController {
    val lifecycleTracker = LifecycleTracker()

    var thisViewController by mutableStateOf<UIViewController?>(null)

    val composeUIViewController = ComposeUIViewController({
        delegate = LifecycleComposeUIVCDelegate(lifecycleTracker)
    }) {
        val inAppBrowserOpener = remember(thisViewController) {
            thisViewController?.let { createInAppBrowserOpener(it) }
        }

        CompositionLocalProvider(
            LocalLifecycleTracker provides lifecycleTracker,
            LocalInAppBrowserOpener provides inAppBrowserOpener
        ) {
            App()
        }
    }

    thisViewController = composeUIViewController

    return composeUIViewController
}

fun createInAppBrowserOpener(viewController: UIViewController): InAppBrowserOpener {
    return InAppBrowserOpener { uri ->
        val vc = SFSafariViewController(uRL = NSURL(string = uri))
        viewController.presentViewController(vc, animated = true, completion = null)
    }
}
