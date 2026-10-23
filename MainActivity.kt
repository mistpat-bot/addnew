package com.example.twaddresswatermark

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var selectedColor = Color.YELLOW
    private var currentRotation = 0f

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!fineGranted) {
            Toast.makeText(this, "需精確定位權限才能正常反查地址", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        val btnStart = findViewById<Button>(R.id.btn_start)
        val btnStop = findViewById<Button>(R.id.btn_stop)
        val btnColorWhite = findViewById<Button>(R.id.btn_color_white)
        val btnColorYellow = findViewById<Button>(R.id.btn_color_yellow)
        val btnColorRed = findViewById<Button>(R.id.btn_color_red)
        val btnRotate = findViewById<Button>(R.id.btn_rotate)

        btnStart.setOnClickListener {
            if (checkOverlayPermission()) {
                val serviceIntent = Intent(this, WatermarkService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                updateStyle()
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, WatermarkService::class.java))
        }

        // 調整顏色
        btnColorWhite.setOnClickListener { selectedColor = Color.WHITE; updateStyle() }
        btnColorYellow.setOnClickListener { selectedColor = Color.YELLOW; updateStyle() }
        btnColorRed.setOnClickListener { selectedColor = Color.RED; updateStyle() }

        // 調整方向 (0 -> 90 -> 180 -> 270 循環)
        btnRotate.setOnClickListener {
            currentRotation = (currentRotation + 90f) % 360f
            updateStyle()
        }
    }

    private fun updateStyle() {
        val intent = Intent(this, WatermarkService::class.java).apply {
            action = WatermarkService.ACTION_UPDATE_STYLE
            putExtra(WatermarkService.EXTRA_COLOR, selectedColor)
            putExtra(WatermarkService.EXTRA_ROTATION, currentRotation)
        }
        startService(intent)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        locationPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "請允許「顯示在其他應用程式上層」", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }
}