package com.galrae.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.galrae.app.ui.group.GroupScreen
import com.galrae.app.service.KeepAliveService
import com.galrae.app.ui.theme.GALRAETheme

class MainActivity : ComponentActivity() {

    // 위치: 포그라운드 서비스(location 유형) 시작 조건. 알림: 서비스 알림 표시 (거부해도 서비스는 동작)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { startKeepAliveIfAllowed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GALRAETheme {
                GroupScreen((application as GalraeApp).group, lifecycle, onExit = { finish() })
            }
        }

        // Back = 앱 종료(Finish). 기본 동작은 태스크를 뒤로 보내기만 해서 Pleos 분할 창에선 아무 변화가 없다 (docs 1-7).
        // 종료되면 onDestroy에서 포그라운드 서비스도 끈다
        onBackPressedDispatcher.addCallback(this) { finish() }

        val missing = REQUIRED_PERMISSIONS.filter { !isGranted(it) }
        if (missing.isEmpty()) startKeepAliveIfAllowed() else permissionLauncher.launch(missing.toTypedArray())
    }

    override fun onDestroy() {
        // 회전 등으로 다시 만들어지는 경우가 아니라 앱을 끝낼 때만 서비스를 끈다
        if (isFinishing) KeepAliveService.stop(this)
        super.onDestroy()
    }

    private fun startKeepAliveIfAllowed() {
        val locationGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (locationGranted) {
            KeepAliveService.start(this)
        } else {
            Log.w(TAG, "location permission denied, keep-alive service not started")
        }
    }

    private fun isGranted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "GALRAE/Main"
        val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}
