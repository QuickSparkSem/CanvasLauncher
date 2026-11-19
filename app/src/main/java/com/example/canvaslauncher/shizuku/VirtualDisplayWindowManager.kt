package com.example.canvaslauncher.shizuku

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.webkit.WebView
import android.widget.Toast
import com.example.canvaslauncher.model.CanvasItem
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object VirtualDisplayWindowManager {
    const val IME_POLICY_LOCAL = 0
    const val IME_POLICY_FALLBACK_DISPLAY = 1

    data class WindowSession(
        val itemId: String,
        val packageName: String,
        val virtualDisplay: VirtualDisplay,
        val displayId: Int,
        var widthPx: Int,
        var heightPx: Int,
        var attachedSurface: Surface? = null,
        var textureView: TextureView? = null
    )

    private val sessions = ConcurrentHashMap<String, WindowSession>()
    val activeWebViews = ConcurrentHashMap<String, WebView>()

    private var cachedInputManager: Any? = null
    private var cachedActivityTaskManager: Any? = null
    private var cachedWindowManager: Any? = null

    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    fun ensureHiddenApiBypass() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
                val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                    "getDeclaredMethod",
                    String::class.java,
                    arrayOf<Class<*>>()::class.java
                )
                val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
                val getRuntime = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null) as Method
                val setHiddenApiExemptions = getDeclaredMethod.invoke(
                    vmRuntimeClass,
                    "setHiddenApiExemptions",
                    arrayOf<Class<*>>(Array<String>::class.java)
                ) as Method
                val runtime = getRuntime.invoke(null)
                setHiddenApiExemptions.invoke(runtime, arrayOf("L"))
            }
        }
    }

    fun isShizukuReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestShizukuPermission(requestCode: Int = 1001) {
        runCatching {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        }
    }

    fun hasActiveSession(itemId: String): Boolean = sessions.containsKey(itemId)

    fun getSession(itemId: String): WindowSession? = sessions[itemId]

    fun launchAppInWindowOrFallback(
        context: Context,
        item: CanvasItem,
        widthPx: Int,
        heightPx: Int,
        initialUrl: String? = null,
        onVirtualDisplayCreated: (WindowSession) -> Unit,
        onFallbackFreeformLaunched: () -> Unit
    ) {
        val pkg = item.packageName ?: item.browserPackage ?: return
        val safeW = widthPx.coerceAtLeast(360)
        val safeH = heightPx.coerceAtLeast(480)

        if (!isShizukuReady()) {
            launchInAndroidFreeformFallback(context, pkg, initialUrl)
            onFallbackFreeformLaunched()
            return
        }

        ensureHiddenApiBypass()

        runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val densityDpi = context.resources.displayMetrics.densityDpi
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY

            val vd = dm.createVirtualDisplay(
                "CanvasWin_${item.id}",
                safeW,
                safeH,
                densityDpi,
                null,
                flags
            ) ?: throw IllegalStateException("Impossibile creare VirtualDisplay")

            val displayId = vd.display.displayId
            val policy = if (item.imeFallbackDisplay) IME_POLICY_FALLBACK_DISPLAY else IME_POLICY_LOCAL
            setDisplayImePolicy(displayId, policy)

            val session = WindowSession(
                itemId = item.id,
                packageName = pkg,
                virtualDisplay = vd,
                displayId = displayId,
                widthPx = safeW,
                heightPx = safeH
            )
            sessions[item.id] = session

            val launchIntent = if (!initialUrl.isNullOrBlank()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(initialUrl)).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
            } ?: throw IllegalStateException("Intent di avvio non trovato per $pkg")

            val launchedViaShell = startActivityOnDisplayViaShizuku(launchIntent, displayId)
            if (!launchedViaShell) {
                val opts = ActivityOptions.makeBasic().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        launchDisplayId = displayId
                    }
                }
                context.startActivity(launchIntent, opts.toBundle())
            }

            onVirtualDisplayCreated(session)
        }.onFailure {
            closeSession(item.id)
            launchInAndroidFreeformFallback(context, pkg, initialUrl)
            onFallbackFreeformLaunched()
        }
    }

    private fun launchInAndroidFreeformFallback(context: Context, packageName: String, initialUrl: String?) {
        runCatching {
            val intent = if (!initialUrl.isNullOrBlank()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(initialUrl)).apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                }
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val options = ActivityOptions.makeBasic().apply {
                    setLaunchBounds(Rect(100, 180, 960, 1550))
                }
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
        }.onFailure {
            Toast.makeText(context, "Impossibile avviare l'app", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("PrivateApi")
    private fun startActivityOnDisplayViaShizuku(intent: Intent, displayId: Int): Boolean = runCatching {
        val atm = getActivityTaskManagerService() ?: return false
        val options = ActivityOptions.makeBasic().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                launchDisplayId = displayId
            }
        }.toBundle()

        val methods = atm.javaClass.methods.filter { it.name == "startActivityAsUser" }
        for (m in methods) {
            val params = m.parameterTypes
            if (params.size == 11) {
                m.invoke(
                    atm,
                    null,
                    "com.android.shell",
                    null,
                    intent,
                    intent.type,
                    null,
                    null,
                    0,
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                    null,
                    options
                )
                return true
            } else if (params.size == 12) {
                m.invoke(
                    atm,
                    null,
                    "com.android.shell",
                    null,
                    intent,
                    intent.type,
                    null,
                    null,
                    0,
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                    null,
                    options,
                    0
                )
                return true
            }
        }
        false
    }.getOrDefault(false)

    @SuppressLint("PrivateApi")
    fun setDisplayImePolicy(displayId: Int, policy: Int) {
        if (!isShizukuReady()) return
        ensureHiddenApiBypass()
        runCatching {
            val wm = getWindowManagerService() ?: return
            val method = wm.javaClass.methods.firstOrNull {
                it.name == "setDisplayImePolicy" && it.parameterTypes.size == 2
            }
            method?.invoke(wm, displayId, policy)
        }
    }

    fun updateSurfaceAttachment(itemId: String, surface: Surface?, isVisible: Boolean) {
        val session = sessions[itemId] ?: return
        session.attachedSurface = surface
        runCatching {
            session.virtualDisplay.surface = if (isVisible && surface != null && surface.isValid) surface else null
        }
    }

    fun resizeSession(itemId: String, widthPx: Int, heightPx: Int) {
        val session = sessions[itemId] ?: return
        val safeW = widthPx.coerceAtLeast(360)
        val safeH = heightPx.coerceAtLeast(480)
        if (session.widthPx == safeW && session.heightPx == safeH) return
        session.widthPx = safeW
        session.heightPx = safeH
        runCatching {
            session.virtualDisplay.resize(safeW, safeH, 320)
        }
    }

    fun injectMappedTouch(
        itemId: String,
        action: Int,
        downTime: Long,
        localX: Float,
        localY: Float,
        viewWidth: Float,
        viewHeight: Float
    ) {
        val session = sessions[itemId] ?: return
        val mappedX = ((localX / viewWidth.coerceAtLeast(1f)) * session.widthPx)
            .coerceIn(0f, (session.widthPx - 1).toFloat())
        val mappedY = ((localY / viewHeight.coerceAtLeast(1f)) * session.heightPx)
            .coerceIn(0f, (session.heightPx - 1).toFloat())

        val event = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            mappedX,
            mappedY,
            0
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
            setDisplayIdCompat(this, session.displayId)
        }
        injectInputEvent(event)
        event.recycle()
    }

    fun injectBackKey(itemId: String) {
        val session = sessions[itemId] ?: return
        val now = SystemClock.uptimeMillis()
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            val keyEvent = KeyEvent(
                now,
                now,
                action,
                KeyEvent.KEYCODE_BACK,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
            ).apply {
                setDisplayIdCompat(this, session.displayId)
            }
            injectInputEvent(keyEvent)
        }
    }

    private fun setDisplayIdCompat(event: Any, displayId: Int) {
        runCatching {
            val m = event.javaClass.getMethod("setDisplayId", Int::class.javaPrimitiveType)
            m.invoke(event, displayId)
        }
    }

    @SuppressLint("PrivateApi")
    private fun injectInputEvent(event: android.view.InputEvent) {
        if (!isShizukuReady()) return
        ensureHiddenApiBypass()
        runCatching {
            val im = getInputManagerService() ?: return
            val injectMethod = im.javaClass.methods.firstOrNull {
                it.name == "injectInputEvent" && it.parameterTypes.size == 2
            }
            injectMethod?.invoke(im, event, 0)
        }
    }

    @SuppressLint("PrivateApi")
    private fun getInputManagerService(): Any? {
        cachedInputManager?.let { return it }
        return runCatching {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            asInterface.invoke(null, binder).also { cachedInputManager = it }
        }.getOrNull()
    }

    @SuppressLint("PrivateApi")
    private fun getActivityTaskManagerService(): Any? {
        cachedActivityTaskManager?.let { return it }
        return runCatching {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task"))
            val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            asInterface.invoke(null, binder).also { cachedActivityTaskManager = it }
        }.getOrNull()
    }

    @SuppressLint("PrivateApi")
    private fun getWindowManagerService(): Any? {
        cachedWindowManager?.let { return it }
        return runCatching {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("window"))
            val stubClass = Class.forName("android.view.IWindowManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            asInterface.invoke(null, binder).also { cachedWindowManager = it }
        }.getOrNull()
    }

    fun closeSession(itemId: String) {
        sessions.remove(itemId)?.let { session ->
            runCatching {
                session.virtualDisplay.surface = null
                session.virtualDisplay.release()
            }
        }
    }

    fun releaseAll() {
        sessions.keys.toList().forEach { closeSession(it) }
        activeWebViews.values.forEach { runCatching { it.destroy() } }
        activeWebViews.clear()
    }
}
