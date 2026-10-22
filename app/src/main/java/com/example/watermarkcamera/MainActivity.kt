package com.example.watermarkcamera

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var selectedColor: Int = Color.parseColor("#00FF66")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rgColors = findViewById<RadioGroup>(R.id.rgColors)
        rgColors.setOnCheckedChangeListener { _, checkedId ->
            selectedColor = when (checkedId) {
                R.id.rbWhite -> Color.parseColor("#FFFFFF")
                R.id.rbYellow -> Color.parseColor("#FFD700")
                R.id.rbCyan -> Color.parseColor("#00E5FF")
                else -> Color.parseColor("#00FF66")
            }
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            checkPermissionsAndStart()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, FloatingAddressService::class.java))
            Toast.makeText(this, "已關閉懸浮窗", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart() {
        // 1. 檢查懸浮窗權限
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "請先開啟「顯示在其他應用程式上層」權限", Toast.LENGTH_LONG).show()
            return
        }

        // 2. 檢查 GPS 權限
        val locationPermission = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.any { it }) {
                startFloatingService()
            } else {
                Toast.makeText(this, "請給予定位權限以取得地址", Toast.LENGTH_SHORT).show()
            }
        }

        locationPermission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingAddressService::class.java).apply {
            putExtra("TEXT_COLOR", selectedColor)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "懸浮窗已啟動，可用手指拖曳調整位置", Toast.LENGTH_LONG).show()
    }
}