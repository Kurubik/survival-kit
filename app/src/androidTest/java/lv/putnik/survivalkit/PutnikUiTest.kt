package lv.putnik.survivalkit

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import org.junit.Rule
import org.junit.Test

class PutnikUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(700)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val resolver=instrumentation.targetContext.contentResolver
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png")
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/PutnikCI")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
        instrumentation.uiAutomation.takeScreenshot().let { bitmap-> resolver.openOutputStream(uri)!!.use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle() }
        values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);resolver.update(uri,values,null,null)
    }
    @Test fun campDisplaysSelectedDirection() {
        compose.onNodeWithText("Что нужно сейчас?").assertIsDisplayed()
        compose.onNodeWithTag("topic-fire").assertIsDisplayed()
        capture("01-camp")
    }
    @Test fun russianQuestionAsksForEquipmentThenProgresses() {
        compose.onNodeWithTag("tab-CHAT").performClick()
        compose.onNodeWithTag("question").performTextInput("Как разжечь огонь после дождя?")
        compose.onNodeWithTag("send").performClick()
        compose.onNodeWithText("Зажигалка",useUnmergedTree=true).assertExists()
        capture("02-companion")
        compose.onNodeWithText("Ничего",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Не рассчитывай на трение",substring=true).assertExists()
    }
    @Test fun guidesAreReadableWithoutAModel() {
        compose.onNodeWithTag("topic-fire").performClick()
        compose.onNodeWithText("Огонь и тепло").assertIsDisplayed()
        capture("03-guide")
    }
    @Test fun backpackStatesAreHonestAndGearPersists() {
        compose.onNodeWithTag("tab-PACK").performClick()
        val water=compose.onNodeWithTag("gear-Вода")
        val before=water.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]==ToggleableState.On
        water.assertIsDisplayed().performClick()
        capture("04-backpack")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab-PACK").performClick()
        compose.onNodeWithText("Перед выходом").assertIsDisplayed()
        if(before)compose.onNodeWithTag("gear-Вода").assertIsOff() else compose.onNodeWithTag("gear-Вода").assertIsOn()
        org.junit.Assert.assertEquals(!before,compose.activity.getSharedPreferences("putnik",0).getBoolean("gear:Вода",false))
    }
    @Test fun mapAndEmergencyDoNotInventPosition() {
        compose.onNodeWithTag("tab-MAP").performClick()
        compose.onNodeWithText("Координаты ещё не получены").assertExists()
        val mapBounds=compose.onNodeWithTag("offline-map").fetchSemanticsNode().boundsInRoot
        try {
            // Native renderer workers are not covered by Compose's idle detection.
            // Inspect the actual central map pixels, not a loaded-file flag or label.
            compose.waitUntil(timeoutMillis=30_000) {
                val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                val colors=mutableMapOf<Int,Int>()
                for(x in 0 until 30)for(y in 0 until 30) {
                    val px=(mapBounds.left+mapBounds.width*(.1f+.8f*x/29)).toInt().coerceIn(0,bitmap.width-1)
                    val py=(mapBounds.top+mapBounds.height*(.1f+.8f*y/29)).toInt().coerceIn(0,bitmap.height-1)
                    val pixel=bitmap.getPixel(px,py)
                    val quantized=pixel and 0x00F0F0F0
                    colors[quantized]=(colors[quantized]?:0)+1
                }
                bitmap.recycle()
                colors.size>=12&&(colors.values.maxOrNull()?:900)<765
            }
        } finally {capture("05-map")}
        compose.onNodeWithTag("help").performClick()
        compose.onNodeWithText("Актуальная точка ещё не получена.").assertExists()
        capture("06-help")
    }
}
