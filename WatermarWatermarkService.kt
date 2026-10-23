package com.example.twaddresswatermark

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class WatermarkService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var tvWatermark: TextView
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var lastResolvedLocation: Location? = null
    private var currentAddress: String = "正在搜尋 GPS..."

    companion object {
        const val ACTION_UPDATE_STYLE = "ACTION_UPDATE_STYLE"
        const val EXTRA_COLOR = "EXTRA_COLOR"
        const val EXTRA_ROTATION = "EXTRA_ROTATION"
        private const val CHANNEL_ID = "watermark_location_channel"
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        initFloatingWindow()
        initLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_STYLE) {
            val color = intent.getIntExtra(EXTRA_COLOR, Color.WHITE)
            val rotation = intent.getFloatExtra(EXTRA_ROTATION, 0f)
            
            // 更新文字顏色與旋轉角度
            tvWatermark.setTextColor(color)
            floatingView.rotation = rotation
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_watermark, null)
        tvWatermark = floatingView.findViewById(R.id.tv_watermark_text)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // FLAG_NOT_FOCUSABLE 確保不攔截鍵盤輸入
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        // 支援拖曳浮水印
        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
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
    private fun initLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateDistanceMeters(2f) // 移動超過 2 公尺才回傳
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                handleNewLocation(location)
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun handleNewLocation(location: Location) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date(location.time))

        // 檢查是否需要向 Nominatim 發送地址查詢 (移動超過 15 公尺或首次定位)
        val shouldFetchAddress = lastResolvedLocation == null ||
                lastResolvedLocation!!.distanceTo(location) > 15f

        if (shouldFetchAddress) {
            lastResolvedLocation = location
            serviceScope.launch {
                currentAddress = NominatimGeocoder.getTaiwanAddress(location.latitude, location.longitude)
                updateWatermarkText(timeStr, location)
            }
        } else {
            updateWatermarkText(timeStr, location)
        }
    }

    private fun updateWatermarkText(timeStr: String, location: Location) {
        val text = "📍 $currentAddress\n⏱ $timeStr | 🧭 ${String.format("%.4f, %.4f", location.latitude, location.longitude)}"
        tvWatermark.text = text
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "浮水印定位服務",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("即時地址浮水印運作中")
            .setContentText("正在追蹤即時 GPS 與台灣地址")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(101, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}