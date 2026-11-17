package lv.putnik.survivalkit

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import lv.putnik.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class Page { CAMP, CHAT, MAP, PACK, LIBRARY, GUIDE, HELP, SETTINGS }
data class ChatMessage(val text: String, val user: Boolean, val guideId: String?=null, val ai: Boolean=false, val mode: ChatMode?=null)

class PutnikModel(app: Application): AndroidViewModel(app), LocationListener {
    private val prefs=app.getSharedPreferences("putnik",Context.MODE_PRIVATE)
    private val engine=Companion()
    var page by mutableStateOf(Page.CAMP)
    var selectedGuide by mutableStateOf<Guide?>(null)
    var personality by mutableStateOf(runCatching { Personality.valueOf(prefs.getString("personality","ULTRA")!!) }.getOrDefault(Personality.ULTRA))
    var chatMode by mutableStateOf(runCatching { ChatMode.valueOf(prefs.getString("chatMode","FREE")!!) }.getOrDefault(ChatMode.FREE))
    var daylight by mutableStateOf(prefs.getBoolean("daylight",false))
    var note by mutableStateOf(prefs.getString("note","").orEmpty())
    var voiceStatus by mutableStateOf("Проверяю русский голос…")
    var listening by mutableStateOf(false)
    var recognizedDraft by mutableStateOf("")
    var autoRead by mutableStateOf(prefs.getBoolean("autoRead",false))
    var packStatus by mutableStateOf("")
    var activePack by mutableStateOf<String?>(null)
    var downloadBytes by mutableLongStateOf(0L)
    var packVersion by mutableIntStateOf(0)
    var modelReady by mutableStateOf(false)
    var thinking by mutableStateOf(false)
    var streaming by mutableStateOf("")
    var modelStatus by mutableStateOf("AI не загружен в память")
    val messages=mutableStateListOf<ChatMessage>()
    var choices by mutableStateOf(if(chatMode==ChatMode.GUIDED)listOf("Огонь", "Вода", "Укрытие", "Я потерялся")else emptyList())
    val checked=mutableStateMapOf<String,Boolean>()
    val bookmarks=mutableStateListOf<String>()
    var location by mutableStateOf<Location?>(null)
    var locationStatus by mutableStateOf("Координаты ещё не получены")
    var tracking by mutableStateOf(false)
    var camp by mutableStateOf<Location?>(null)
    val track=mutableStateListOf<Location>()
    private val locator=app.getSystemService(LocationManager::class.java)
    private var downloadJob: Job?=null
    private var generationJob: Job?=null
    private var idleJob: Job?=null
    private var requestId=0
    val voice=Voice(app,viewModelScope,{ voiceStatus=it;listening=it.startsWith("Слушаю") },{ recognizedDraft=it;page=Page.CHAT;voiceStatus="Проверь распознанный текст и отправь вопрос" })
    init {
        listOf("Вода","Аптечка","Фонарь","Огниво / зажигалка","Дождевик","Сухая одежда","Карта и компас","Еда","Пауэрбанк","Укрытие").forEach { checked[it]=prefs.getBoolean("gear:$it",false) }
        bookmarks.addAll(prefs.getStringSet("bookmarks",emptySet())!!.toList())
        runCatching {
            val array=JSONArray(prefs.getString("history","[]"))
            for(i in 0 until array.length()) { val o=array.getJSONObject(i);messages.add(ChatMessage(o.getString("text"),o.getBoolean("user"),o.optString("guide").ifBlank { null },o.optBoolean("ai",false),runCatching {ChatMode.valueOf(o.optString("mode"))}.getOrNull())) }
            if(prefs.contains("campLat"))camp=Location("saved").apply { latitude=prefs.getString("campLat","0")!!.toDouble();longitude=prefs.getString("campLon","0")!!.toDouble();time=prefs.getLong("campTime",0) }
            val points=JSONArray(prefs.getString("track","[]"))
            for(i in 0 until points.length()) { val p=points.getJSONArray(i);track.add(Location("saved").apply {latitude=p.getDouble(0);longitude=p.getDouble(1);time=p.getLong(2)}) }
        }
        if(messages.isEmpty())messages.add(ChatMessage("Я Путник. Можно просто поболтать, обсудить идеи или собраться в поход. Для живого разговора нужна локальная AI-модель; памятки по шагам работают и без неё.",false))
    }
    fun openGuide(guide: Guide) { selectedGuide=guide;page=Page.GUIDE }
    fun ask(text: String, fromVoice: Boolean=false, guide: Guide?=null) {
        val input=text.trim().take(1500);if(input.isEmpty())return
        cancelGeneration();page=Page.CHAT
        messages.add(ChatMessage(input,true,guideId=guide?.takeUnless {it.critical}?.id,mode=chatMode));persistHistory()
        val reply=Conversation.emergency(input) ?: if(chatMode==ChatMode.GUIDED)engine.ask(input,personality)else null
        if(reply!=null) {
            choices=reply.choices
            messages.add(ChatMessage(reply.text,false,reply.guide?.id,mode=chatMode));persistHistory()
            if(fromVoice||autoRead)voice.speak(reply.text)
            return
        }
        choices=emptyList()
        if(!installed(Packs.model)) {modelStatus="Скачай AI-модель для свободного разговора. Вопрос сохранён.";return}
        generateConversation(input,fromVoice)
    }
    private suspend fun loadModel(request: Long) {
        check(NativeLlama.available) {"Локальный AI не поддерживается на этом устройстве"}
        if(!NativeLlama.isLoaded()) {
            val info=ActivityManager.MemoryInfo();getApplication<Application>().getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            if(BuildConfig.DEBUG)android.util.Log.i("PutnikAI","load: availableMiB=${info.availMem/1048576}; totalMiB=${info.totalMem/1048576}")
            check(info.availMem>3_200_000_000L) {"Для AI сейчас мало свободной памяти. Закрой другие приложения; памятки доступны и без модели."}
        }
        check(NativeLlama.load(Packs.file(getApplication(),Packs.model).absolutePath,request).isBlank()) {"Не удалось открыть AI-модель. Можно повторить загрузку в память."}
        if(BuildConfig.DEBUG)android.util.Log.i("PutnikAI","load completed")
    }
    private fun generateConversation(input: String,fromVoice: Boolean=false) {
        idleJob?.cancel();thinking=true;streaming="";modelStatus="Открываю спутника на устройстве…"
        val token=++requestId
        val request=if(NativeLlama.available)NativeLlama.startRequest()else 0L
        // A casual message must not acquire unrelated cards through fuzzy search.
        // Only the explicit "Discuss with AI" action supplies local source context.
        val guide=messages.lastOrNull()?.guideId?.let(GuideLibrary::byId)?.takeUnless {it.critical}
        val history=messages.filter {it.mode==ChatMode.FREE}.map {DialogueTurn(it.text,it.user)}
        val prompt=Conversation.prompt(history,personality,guide)
        generationJob=viewModelScope.launch {
            try {
                val answer=withContext(Dispatchers.IO) {
                    loadModel(request)
                    withContext(Dispatchers.Main) {if(token==requestId){modelReady=true;modelStatus="Живой диалог · на устройстве"}}
                    if(BuildConfig.DEBUG)android.util.Log.i("PutnikAI","generation started")
                    var firstChunk=true
                    val listener=NativeLlama.Listener { bytes->
                        val decoded=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.IGNORE).onUnmappableCharacter(CodingErrorAction.IGNORE).decode(ByteBuffer.wrap(bytes)).toString()
                        val visible=Conversation.visibleAnswer(decoded)
                        if(firstChunk&&visible.isNotBlank()){firstChunk=false;if(BuildConfig.DEBUG)android.util.Log.i("PutnikAI","first visible chunk: characters=${visible.length}")}
                        viewModelScope.launch(Dispatchers.Main.immediate) {if(token==requestId&&thinking)streaming=visible}
                    }
                    val detailed=GuideLibrary.normalize(input).let {it.contains("подроб")||it.contains("развернут")||it.contains("длинн")||it.contains("истори")}
                    val tokenLimit = if (detailed) 384 else 192
                    Conversation.visibleAnswer(NativeLlama.generate(prompt.toByteArray(Charsets.UTF_8),listener,request,tokenLimit).toString(Charsets.UTF_8))
                }
                if(token!=requestId)return@launch
                check(answer.isNotBlank()) {"Спутник не закончил ответ. Попробуй ещё раз."}
                messages.add(ChatMessage(answer,false,guide?.id,ai=true,mode=ChatMode.FREE));persistHistory()
                if(fromVoice||autoRead)voice.speak(answer)
            } catch(_: CancellationException) { }
            catch(e: Exception) {
                if(BuildConfig.DEBUG)android.util.Log.w("PutnikAI","generation failed: ${e.javaClass.simpleName}")
                if(token==requestId)modelStatus=e.message ?: "Не удалось получить ответ. Можно повторить вопрос."
            } finally {
                if(token==requestId){thinking=false;streaming="";if(modelReady)scheduleIdleUnload()}
            }
        }
    }
    fun selectChatMode(value: ChatMode) {
        cancelGeneration();engine.reset();chatMode=value;prefs.edit().putString("chatMode",value.name).apply()
        choices=if(value==ChatMode.GUIDED)listOf("Огонь", "Вода", "Укрытие", "Я потерялся")else emptyList()
    }
    fun discussGuide(guide: Guide) {selectChatMode(ChatMode.GUIDED);ask(guide.title)}
    fun discussGuideWithAI(guide: Guide) {selectChatMode(ChatMode.FREE);ask("Давай обсудим: ${guide.title}",guide=guide)}
    fun retryConversation() {
        if(thinking)return
        val last=messages.lastOrNull()?:return
        if(last.user&&last.mode==ChatMode.FREE&&installed(Packs.model))generateConversation(last.text)
    }
    fun cancelGeneration() { requestId++;generationJob?.cancel();if(NativeLlama.available)NativeLlama.cancel();if(thinking)modelStatus="Ответ остановлен";thinking=false;streaming="";voice.stopSpeaking();if(modelReady)scheduleIdleUnload() }
    fun clearChat() { cancelGeneration();engine.reset();messages.clear();messages.add(ChatMessage("Начнём новую беседу. О чём поговорим?",false));persistHistory() }
    private fun persistHistory() {
        val array=JSONArray();messages.takeLast(40).forEach { m -> array.put(JSONObject().put("text",m.text).put("user",m.user).put("guide",m.guideId.orEmpty()).put("ai",m.ai).put("mode",m.mode?.name.orEmpty())) }
        prefs.edit().putString("history",array.toString()).apply()
    }
    fun setTone(value: Personality) { personality=value;prefs.edit().putString("personality",value.name).apply() }
    fun toggleDay() { daylight=!daylight;prefs.edit().putBoolean("daylight",daylight).apply() }
    fun setRead(value: Boolean) { autoRead=value;prefs.edit().putBoolean("autoRead",value).apply() }
    fun saveNote(value: String) { note=value;prefs.edit().putString("note",value).apply() }
    fun checkGear(item: String,value: Boolean) { checked[item]=value;prefs.edit().putBoolean("gear:$item",value).apply() }
    fun bookmark(id: String) { if(id in bookmarks)bookmarks.remove(id) else bookmarks.add(id);prefs.edit().putStringSet("bookmarks",bookmarks.toSet()).apply() }
    fun installed(pack: Pack): Boolean { packVersion;return Packs.installed(getApplication(),pack) }
    fun install(pack: Pack) {
        if(activePack!=null||installed(pack))return
        activePack=pack.id;downloadBytes=0;packStatus="Загрузка: ${pack.title}"
        downloadJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Packs.install(getApplication(),pack) { count -> downloadBytes=count } }
                packVersion++;packStatus="${pack.title}: установлен, SHA-256 проверен"
                if(pack.id=="model"&&chatMode==ChatMode.FREE&&messages.lastOrNull()?.user==true){page=Page.CHAT;retryConversation()}
            } catch(_: CancellationException) { packStatus="Загрузка остановлена; прежние пакеты сохранены" }
            catch(e: Exception) { packStatus=e.message ?: "Не удалось загрузить пакет" }
            finally { activePack=null }
        }
    }
    fun cancelDownload() { downloadJob?.cancel() }
    fun enableModel() {
        if(!installed(Packs.model)) { page=Page.PACK;packStatus="Сначала скачай AI-пакет";return }
        if(thinking)return
        if(chatMode==ChatMode.FREE&&messages.lastOrNull()?.let {it.user&&it.mode==ChatMode.FREE}==true){page=Page.CHAT;retryConversation();return}
        thinking=true;modelStatus="Открываю модель на устройстве…"
        val token=++requestId
        val request=if(NativeLlama.available)NativeLlama.startRequest()else 0L
        generationJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {loadModel(request)}
                if(token==requestId){modelReady=true;modelStatus="Qwen3 4B · живой диалог на устройстве";scheduleIdleUnload()}
            } catch(e: Exception) { if(token==requestId)modelStatus=e.message ?: "Не удалось открыть модель" }
            finally { if(token==requestId)thinking=false }
        }
    }
    fun unloadModel() { cancelGeneration();idleJob?.cancel();modelReady=false;modelStatus="AI отдыхает; откроется при следующей реплике";if(NativeLlama.available){val request=NativeLlama.startRequest();viewModelScope.launch(Dispatchers.IO) {NativeLlama.unload(request)}} }
    private fun scheduleIdleUnload() { idleJob?.cancel();idleJob=viewModelScope.launch { delay(120000);unloadModel() } }
    fun startPosition(record: Boolean=false) {
        try {
            locator.removeUpdates(this);tracking=record
            val provider=if(locator.isProviderEnabled(LocationManager.GPS_PROVIDER))LocationManager.GPS_PROVIDER else { locationStatus="Включи геолокацию Android";return }
            locationStatus="Жду актуальную точку GPS…"
            locator.requestLocationUpdates(provider,3000,4f,this)
        } catch(_: SecurityException) { locationStatus="Нужно разрешение геолокации" }
    }
    override fun onLocationChanged(value: Location) {
        location=value;locationStatus="GPS · точность ±${value.accuracy.toInt()} м"
        if(tracking) {
            track.add(value);if(track.size>5000)track.removeAt(0)
            val array=JSONArray();track.forEach { array.put(JSONArray().put(it.latitude).put(it.longitude).put(it.time)) };prefs.edit().putString("track",array.toString()).apply()
        }
    }
    override fun onProviderDisabled(provider: String) { locationStatus="GPS отключён" }
    override fun onProviderEnabled(provider: String) {}
    @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?,status: Int,extras: Bundle?) {}
    fun stopPosition() { runCatching { locator.removeUpdates(this) };tracking=false }
    fun saveCamp() { val current=location ?: return;camp=Location(current);prefs.edit().putString("campLat",current.latitude.toString()).putString("campLon",current.longitude.toString()).putLong("campTime",current.time).apply() }
    fun clearTrack() { track.clear();prefs.edit().remove("track").apply() }
    fun pause() { voice.stopListening();voice.stopSpeaking();listening=false;stopPosition();unloadModel() }
    override fun onCleared() { voice.close();locator.removeUpdates(this);if(NativeLlama.available)NativeLlama.cancel();super.onCleared() }
}
