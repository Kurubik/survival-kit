package lv.putnik.survivalkit

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class PutnikUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(700)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply {mkdirs()}
        instrumentation.uiAutomation.takeScreenshot().let { bitmap-> File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle() }
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
        compose.onNodeWithText("Вода").assertIsDisplayed().performClick()
        capture("04-backpack")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab-PACK").performClick()
        compose.onNodeWithText("Перед выходом").assertIsDisplayed()
    }
    @Test fun mapAndEmergencyDoNotInventPosition() {
        compose.onNodeWithTag("tab-MAP").performClick()
        compose.onNodeWithText("Координаты ещё не получены").assertExists()
        capture("05-map")
        compose.onNodeWithTag("help").performClick()
        compose.onNodeWithText("Актуальная точка ещё не получена.").assertExists()
        capture("06-help")
    }
}
