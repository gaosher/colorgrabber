package com.example.colorgrabber.ui

import android.widget.SeekBar

inline fun simpleSeek(crossinline onChange: (Int) -> Unit) =
    object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }
