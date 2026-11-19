package com.example.canvaslauncher.shizuku

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.webkit.WebView
import android.widget.Toast
import androidx.core.view.drawToBitmap
import com.example.canvaslauncher.data.StorageManager
import com.example.canvaslauncher.model.CanvasItem
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.ConcurrentHashMap

class ManagedWindowSession(
    val itemId: String,
    val packageName: String,
    val virtualDisplay: VirtualDisplay,
    val displayId: Int,
    var widthPx: Int,
    var heightPx: Int,
    val densityDpi: Int,
    var attachedSurface: Surface? = null,
    var textureView: TextureView? = null
)

object VirtualDisplayWindowManager {

    private const val SHIZUKU_PERMISSION_REQ_CODE = 7001
    private const val WINDOWING_MODE_FREEFORM = 5

    // DISPLAY_IME_POLICY_LOCAL = 0 (tastiera dentro la mini-finestra)
    // DISPLAY_IME_POLICY_FALLBACK_DISPLAY = 1 (tastiera sullo schermo principale)
    const val IME_POLICY_LOCAL = 0
    const val IME_POLICY_FALLBACK_DISPLAY = 1

    private val activeSessions = ConcurrentHashMap<String, ManagedWindowSession>()
    val activeWebViews = ConcurrentHashMap<String, WebView>()

    fun isShizukuBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestShizukuPermission(context: Context) {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(
                    context,
                    "Avvia prima l'app Shizuku (ADB wireless) sul telefono",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQ_CODE)
            } else {
                Toast.makeText(context, "Shizuku già autorizzato!", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Throwable) {
            Toast.makeText(context, "Servizio Shizuku non rilevato", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasActiveSession(itemId: String): Boolean = activeSessions.containsKey(itemId)

    fun getSession(itemId: String): ManagedWindowSession? = activeSessions[itemId]

    /**
     * Avvia un'app dentro un VirtualDisplay su tela se Shizuku è attivo,
     * oppure ripiega automaticamente sulla modalità Freeform nativa di Android.
     */
    fun launchAppInWindowOrFallback(
        context: Context,
        item: CanvasItem,
        widthPx: Int,
        heightPx: Int,
        onVirtualDisplayCreated: () -> Unit,
        onFallbackFreeformLaunched: () -> Unit
    ) {
        val pkg = item.packageName ?: return

        if (!isShizukuReady()) {
            launchInNativeFreeform(context, pkg)
            onFallbackFreeformLaunched()
            return
        }

        val existing = activeSessions[item.id]
        if (existing != null) {
            relaunchAppOnDisplay(context, pkg, existing.displayId)
            onVirtualDisplayCreated()
            return
        }

        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val baseDpi = context.resources.displayMetrics.densityDpi
            val targetDpi = (baseDpi * 0.72f).toInt().coerceAtLeast(160)
            val safeW = widthPx.coerceAtLeast(360)
            val safeH = heightPx.coerceAtLeast(480)

            // VIRTUAL_DISPLAY_FLAG_PUBLIC (1) | PRESENTATION (2) | OWN_CONTENT_ONLY (8) | TRUSTED (1024)
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                (1 shl 10)

            val vd = dm.createVirtualDisplay(
                "CanvasWin_${item.id.take(8)}",
                safeW,
                safeH,
                targetDpi,
                null,
                flags
            ) ?: run {
                launchInNativeFreeform(context, pkg)
                onFallbackFreeformLaunched()
                return
            }

            val displayId = vd.display.displayId
            val imePolicy = if (item.imeFallbackDisplay) IME_POLICY_FALLBACK_DISPLAY else IME_POLICY_LOCAL
            setDisplayImePolicy(displayId, imePolicy)

            val session = ManagedWindowSession(
                itemId = item.id,
                packageName = pkg,
                virtualDisplay = vd,
                displayId = displayId,
                widthPx = safeW,
                heightPx = safeH,
                densityDpi = targetDpi
            )
            activeSessions[item.id] = session

            relaunchAppOnDisplay(context, pkg, displayId)
            onVirtualDisplayCreated()
        } catch (_: Throwable) {
            launchInNativeFreeform(context, pkg)
            onFallbackFreeformLaunched()
        }
    }

    private fun relaunchAppOnDisplay(context: Context, packageName: String, displayId: Int) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )
        val options = ActivityOptions.makeBasic()
        options.setLaunchDisplayId(displayId)
        context.startActivity(launchIntent, options.toBundle())
    }

    /**
     * Modalità di ripiego automatica quando Shizuku è spento (es. dopo un riavvio fuori casa):
     * apre l'app in finestra flottante nativa Freeform.
     */
    fun launchInNativeFreeform(context: Context, packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(context, "App non avviabile in finestra", Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val options = ActivityOptions.makeBasic()
            val setWindowingMode = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType
            )
            setWindowingMode.invoke(options, WINDOWING_MODE_FREEFORM)
            options.setLaunchBounds(Rect(80, 180, 960, 1340))
            context.startActivity(launchIntent, options.toBundle())
            Toast.makeText(
                context,
                "Shizuku inattivo: aperta in finestra Freeform nativa",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Throwable) {
            context.startActivity(launchIntent)
        }
    }

    /**
     * Surface Detaching per Viewport Culling:
     * quando la finestra esce dallo schermo, scollega la Surface per azzerare il carico GPU.
     */
    fun updateSurfaceAttachment(itemId: String, surface: Surface?, isVisibleOnScreen: Boolean) {
        val session = activeSessions[itemId] ?: return
        try {
            if (isVisibleOnScreen && surface != null && surface.isValid) {
                if (session.attachedSurface !== surface) {
                    session.virtualDisplay.setSurface(surface)
                    session.attachedSurface = surface
                }
            } else {
                if (session.attachedSurface != null) {
                    session.virtualDisplay.setSurface(null)
                    session.attachedSurface = null
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun resizeSession(itemId: String, newWidthPx: Int, newHeightPx: Int) {
        val session = activeSessions[itemId] ?: return
        val w = newWidthPx.coerceAtLeast(320)
        val h = newHeightPx.coerceAtLeast(420)
        if (w == session.widthPx && h == session.heightPx) return
        try {
            session.virtualDisplay.resize(w, h, session.densityDpi)
            session.widthPx = w
            session.heightPx = h
        } catch (_: Throwable) {
        }
    }

    fun setDisplayImePolicy(displayId: Int, policy: Int) {
        if (!isShizukuReady()) return
        try {
            val wmBinder = SystemServiceHelper.getSystemService("window") ?: return
            val iwmStub = Class.forName("android.view.IWindowManager\$Stub")
            val asInterface = iwmStub.getMethod("asInterface", IBinder::class.java)
            val wm = asInterface.invoke(null, ShizukuBinderWrapper(wmBinder))
            val setImeMethod = wm.javaClass.getMethod(
                "setDisplayImePolicy",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            setImeMethod.invoke(wm, displayId, policy)
        } catch (_: Throwable) {
        }
    }

    /**
     * Mappa le coordinate del tocco dalla finestra zoomata/spostata sulla tela
     * al sistema di coordinate reale del VirtualDisplay e le inietta via Shizuku.
     */
    fun injectMappedTouch(
        itemId: String,
        action: Int,
        downTime: Long,
        localX: Float,
        localY: Float,
        renderedWidthPx: Float,
        renderedHeightPx: Float
    ) {
        val session = activeSessions[itemId] ?: return
        if (!isShizukuReady() || renderedWidthPx <= 1f || renderedHeightPx <= 1f) return

        val mappedX = ((localX / renderedWidthPx) * session.widthPx)
            .coerceIn(0f, (session.widthPx - 1).toFloat())
        val mappedY = ((localY / renderedHeightPx) * session.heightPx)
            .coerceIn(0f, (session.heightPx - 1).toFloat())

        val now = SystemClock.uptimeMillis()
        val motionEvent = MotionEvent.obtain(
            downTime,
            now,
            action,
            mappedX,
            mappedY,
            0
        )
        motionEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        injectInputEventToDisplay(motionEvent, session.displayId)
        motionEvent.recycle()
    }

    fun injectBackKey(itemId: String) {
        val session = activeSessions[itemId] ?: return
        if (!isShizukuReady()) return
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
        down.source = InputDevice.SOURCE_KEYBOARD
        val up = KeyEvent(now, now + 25L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)
        up.source = InputDevice.SOURCE_KEYBOARD
        injectInputEventToDisplay(down, session.displayId)
        injectInputEventToDisplay(up, session.displayId)
    }

    private fun injectInputEventToDisplay(event: InputEvent, displayId: Int) {
        try {
            val imBinder = SystemServiceHelper.getSystemService("input") ?: return
            val iimStub = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterface = iimStub.getMethod("asInterface", IBinder::class.java)
            val im = asInterface.invoke(null, ShizukuBinderWrapper(imBinder))

            val setDisplayId = InputEvent::class.java.getMethod(
                "setDisplayId",
                Int::class.javaPrimitiveType
            )
            setDisplayId.invoke(event, displayId)

            val injectMethod = im.javaClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
            injectMethod.invoke(im, event, 0)
        } catch (_: Throwable) {
        }
    }

    fun closeSession(itemId: String) {
        val session = activeSessions.remove(itemId) ?: return
        try {
            session.virtualDisplay.setSurface(null)
            session.virtualDisplay.release()
        } catch (_: Throwable) {
        }
    }

    /**
     * Salva su disco uno screenshot di tutte le finestre App e WebView aperte
     * così dopo un riavvio l'utente vede esattamente a che punto era arrivato.
     */
    fun captureAllSnapshots(context: Context, items: MutableList<CanvasItem>) {
        for (i in items.indices) {
            val item = items[i]
            val texView = activeSessions[item.id]?.textureView
            if (texView != null && texView.isAvailable && texView.width > 0 && texView.height > 0) {
                try {
                    val bmp = texView.bitmap
                    if (bmp != null) {
                        val savedPath = StorageManager.saveWindowSnapshotBitmap(context, item.id, bmp)
                        bmp.recycle()
                        if (savedPath != null) {
                            items[i] = item.copy(lastSnapshotPath = savedPath)
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            val webView = activeWebViews[item.id]
            if (webView != null && webView.width > 0 && webView.height > 0) {
                try {
                    val currentUrl = webView.url ?: item.url
                    val bmp = webView.drawToBitmap()
                    val savedPath = StorageManager.saveWindowSnapshotBitmap(context, item.id, bmp)
                    bmp.recycle()
                    items[i] = items[i].copy(
                        url = currentUrl,
                        lastSnapshotPath = savedPath ?: items[i].lastSnapshotPath
                    )
                } catch (_: Throwable) {
                }
            }
        }
    }
}
