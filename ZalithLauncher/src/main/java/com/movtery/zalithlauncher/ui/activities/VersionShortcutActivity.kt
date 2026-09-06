package com.movtery.zalithlauncher.ui.activities

import android.content.Intent
import android.os.Bundle
import com.movtery.zalithlauncher.ui.base.BaseAppCompatActivity

class VersionShortcutActivity : BaseAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val versionName = intent.getStringExtra(EXTRA_LAUNCH_VERSION)
        val splashIntent = Intent(this, SplashActivity::class.java).apply {
            if (versionName != null) {
                putExtra(EXTRA_LAUNCH_VERSION, versionName)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(splashIntent)
        finish()
    }
}
