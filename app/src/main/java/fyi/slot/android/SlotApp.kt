package fyi.slot.android

import android.app.Application
import android.content.ContentProvider
import android.content.pm.ProviderInfo
import android.os.Build
import java.io.File

class SlotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // compose-resources sets up its Context through a ContentProvider, and providers only start in the
        // default process. The emulator process (":game") needs the same initialisation done by hand.
        if (currentProcessName().endsWith(":game")) {
            runCatching {
                val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
                    .getDeclaredConstructor().newInstance() as ContentProvider
                provider.attachInfo(this, ProviderInfo().apply { authority = "$packageName.resources.AndroidContextProvider" })
            }
        }
    }

    private fun currentProcessName(): String =
        if (Build.VERSION.SDK_INT >= 28) getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().trim('\u0000') }.getOrDefault("")
}
