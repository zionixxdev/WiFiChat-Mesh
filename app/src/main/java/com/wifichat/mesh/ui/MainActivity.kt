package com.wifichat.mesh.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.wifichat.mesh.R
import com.wifichat.mesh.databinding.ActivityMainBinding
import com.wifichat.mesh.repository.SettingsStore
import com.wifichat.mesh.service.MeshService
import com.wifichat.mesh.ui.onboarding.OnboardingActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Discovery re-checks permission state on each start; nothing to do. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val container = container

        if (!container.settings.onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        requestRuntimePermissions()
        MeshService.start(this)
    }

    private fun applyTheme() {
        val mode = container.settings.themeMode.value
        val nightMode = when (mode) {
            SettingsStore.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsStore.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }
}
