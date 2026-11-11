package com.twitchplayer.phone

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import java.io.File

/**
 * Saves what the test sees as `<name>.png` under the app's external files folder (`/sdcard/Android/data/<package>/files/`), so a person
 * can pull it and look at it. Only meaningful when the test run does not uninstall the app afterwards (`am instrument`).
 */
fun ComposeTestRule.saveScreenshot(name: String) {
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    val dir = ApplicationProvider.getApplicationContext<Context>().getExternalFilesDir(null) ?: return
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
