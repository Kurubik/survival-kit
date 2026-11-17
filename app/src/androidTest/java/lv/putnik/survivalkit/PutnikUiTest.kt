package lv.putnik.survivalkit

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.ViewModelProvider
import lv.putnik.core.ChatMode
import lv.putnik.core.Personality
import lv.putnik.core.Conversation
import lv.putnik.core.DialogueTurn
import org.json.JSONArray
import org.json.JSONObject
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
        compose.onNodeWithTag("mode-GUIDED").performClick()
        compose.onNodeWithTag("question").performTextInput("Как разжечь огонь после дождя?")
        compose.onNodeWithTag("send").performClick()
        compose.onNodeWithText("Зажигалка",useUnmergedTree=true).assertExists()
        capture("02-companion")
        compose.onNodeWithText("Ничего",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Не рассчитывай на трение",substring=true).assertExists()
    }
    @Test fun missingModelIsExplicitAndDoesNotPretendToBeAConversation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=Packs.file(context,Packs.model)
        val hidden=java.io.File(file.parentFile,"companion-ci-hidden.gguf")
        val present=file.exists()
        if(present)check(file.renameTo(hidden))
        try {
            lateinit var vm: PutnikModel
            compose.runOnIdle {vm=ViewModelProvider(compose.activity)[PutnikModel::class.java];vm.clearChat();vm.setChatMode(ChatMode.FREE)}
            compose.onNodeWithTag("tab-CHAT").performClick()
            compose.onNodeWithTag("download-chat-model").assertIsDisplayed()
            compose.onNodeWithTag("question").performTextInput("Привет! Хочу просто поболтать")
            compose.onNodeWithTag("send").performClick()
            compose.runOnIdle {
                check(vm.messages.last().user)
                check(!vm.messages.any {it.ai})
                check(!vm.messages.any {it.text.contains("не нашлось надёжного ответа")})
            }
            capture("08-chat-setup")
        } finally {if(present)check(hidden.renameTo(file))}
    }

    @Test fun freeConversationUsesRealNativeModelAndKeepsRecentDetails() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(Packs.file(context,Packs.model).length()==Packs.model.bytes) {"Real pinned Qwen fixture is required, not a canned response"}
        lateinit var vm: PutnikModel
        compose.runOnIdle {
            vm=ViewModelProvider(compose.activity)[PutnikModel::class.java]
            vm.clearChat();vm.setChatMode(ChatMode.FREE);vm.setTone(Personality.WARM)
        }
        compose.onNodeWithTag("tab-CHAT").performClick()
        val questions=listOf(
            "Привет! Меня зовут Саша, а моего пса — Рыжик. Давай просто поболтаем, не про выживание. Поздоровайся с нами коротко.",
            "Как зовут моего пса? Ответь одной короткой фразой.",
            "Придумай очень длинную сказку про путешествие говорящего рюкзака, не меньше двадцати абзацев."
        )
        val answers=mutableListOf<String>()
        val timings=mutableListOf<Long>()
        for(question in questions.take(2)) {
            val before=System.currentTimeMillis()
            compose.onNodeWithTag("question").performTextInput(question)
            compose.onNodeWithTag("send").performClick()
            compose.waitUntil(timeoutMillis=240_000) {!vm.thinking&&vm.messages.count {it.ai}>answers.size}
            val answer=vm.messages.last {it.ai}.text
            check(answer.isNotBlank()&&Regex("[А-Яа-яЁё]").containsMatchIn(answer))
            check(!answer.contains("<think>")&&!answer.contains("В загруженных памятках не нашлось"))
            answers.add(answer);timings.add(System.currentTimeMillis()-before)
        }
        check(answers.last().lowercase().contains("рыжик")) {"Actual model did not recall the earlier personal detail"}
        capture("07-free-conversation")
        compose.onNodeWithTag("question").performTextInput(questions.last())
        compose.onNodeWithTag("send").performClick()
        compose.waitUntil(timeoutMillis=240_000) {vm.streaming.isNotBlank()}
        compose.onNodeWithContentDescription("Остановить AI").performClick()
        compose.waitUntil(timeoutMillis=10_000) {!vm.thinking}
        Thread.sleep(1200)
        check(vm.messages.count {it.ai}==2) {"A late response was committed after cancellation"}
        // Stale lifecycle work must not unload a more recent request's model.
        val stale=NativeLlama.startRequest();NativeLlama.startRequest()
        NativeLlama.unload(stale);check(NativeLlama.isLoaded())
        check(NativeLlama.generate(Conversation.prompt(listOf(DialogueTurn("Привет",true)),Personality.WARM).toByteArray(),null,stale).isEmpty())
        val proof=JSONObject().put("model",Packs.model.filename).put("sha256",Packs.model.sha256)
            .put("questions",JSONArray(questions)).put("answers",JSONArray(answers)).put("responseMilliseconds",JSONArray(timings))
            .put("recentDetailRecalled",true).put("streamedTextObserved",true).put("lateResponseAfterCancel",false)
            .put("staleRequestRejected",true).put("runtime","actual Android emulator JNI, no cloud API")
        val resolver=context.contentResolver
        val values=ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME,"llm-smoke.json");put(MediaStore.Downloads.MIME_TYPE,"application/json")
            put(MediaStore.Downloads.RELATIVE_PATH,"Download/PutnikCI");put(MediaStore.Downloads.IS_PENDING,1)
        }
        val uri=requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        resolver.openOutputStream(uri)!!.use {it.write(proof.toString(2).toByteArray(Charsets.UTF_8))}
        values.clear();values.put(MediaStore.Downloads.IS_PENDING,0);resolver.update(uri,values,null,null)
        compose.activityRule.scenario.recreate()
        compose.runOnIdle {
            val restored=ViewModelProvider(compose.activity)[PutnikModel::class.java]
            check(restored.chatMode==ChatMode.FREE)
            check(restored.messages.count {it.ai}==2)
            check(restored.messages.last {it.ai}.text==answers.last())
        }
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
