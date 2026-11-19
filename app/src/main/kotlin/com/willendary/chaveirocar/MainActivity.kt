package com.willendary.chaveirocar

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.willendary.chaveirocar.ui.theme.ChaveiroCarTheme
import com.willendary.chaveirocar.util.Erros

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Erros.instalar(this)

    enableEdgeToEdge()
    setContent {
      val escuro = isSystemInDarkTheme()
      DisposableEffect(escuro) {
        enableEdgeToEdge(
          statusBarStyle = if (escuro) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
          } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
          },
          navigationBarStyle = if (escuro) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
          } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
          },
        )
        onDispose {}
      }
      ChaveiroCarTheme(escuro = escuro) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          ChaveiroCarApp()
        }
      }
    }
  }
}
