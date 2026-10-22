package com.example.watermarkcamera

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.location.Geocoder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.util.Locale

class FloatingAddressService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: TextView? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val color = intent?.getIntExtra("TEXT_COLOR", Color.parseColor("#00FF66")) ?: Color.parseColor("#00FF66")
        startForegroundNotification()
        showFloatingWindow(color)
        startLocationUpdates()
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWindow(textColor: Int) {
        if (floatingView != null) {
            floatingView?.setTextColor(textColor)
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 建立全透明背景的 TextView，並加上文字黑邊陰影防背景反光
        floatingView = TextView(this).apply {
            text = "📍 定位中..."
            setTextColor(textColor)
            textSize = 17f
            setBackgroundColor(Color.TRANSPARENT) // 背景全透明
            setPadding(16, 8, 16, 8)
            setShadowLayer(5f, 2f, 2f, Color.BLACK) // 加上黑邊陰影，在地圖等複雜背景下也能看清
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        // 支援手勢拖曳懸浮窗
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500)
            .setMinUpdateDistanceMeters(2f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    reverseGeocode(loc.latitude, loc.longitude)
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        scope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@FloatingAddressService, Locale.TAIWAN)
                val list = geocoder.getFromLocation(lat, lng, 1)
                val address = if (!list.isNullOrEmpty()) {
                    list[0].getAddressLine(0)?.replace("台灣", "")?.replace("Taiwan", "")?.trim() ?: "未知路段"
                } else {
                    "定位中..."
                }
                withContext(Dispatchers.Main) {
                    floatingView?.text = "📍 $address"
                }
            } catch (e: Exception) {
                // 地圖查尋容錯
            }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "floating_address_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "即時地址懸浮服務", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("即時地址懸浮窗運作中")
            .setContentText("正在背景即時反查 GPS 地址...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (floatingView != null) windowManager.removeView(floatingView)
        if (::fusedLocationClient.isInitialized) fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}