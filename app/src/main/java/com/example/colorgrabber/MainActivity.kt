package com.example.colorgrabber

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.colorgrabber.ui.CaptureFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, CaptureFragment())
                .commit()
        }
    }
}
