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

enum class Page { CAMP, CHAT, MAP, PACK, LIBRARY, GUIDE, HELP, SETTINGS }
data class ChatMessage(val text: String, val user: Boolean, val guideId: String?=null)

class PutnikModel(app: Application): AndroidViewModel(app), LocationListener {
    private val prefs=app.getSharedPreferences("putnik",Context.MODE_PRIVATE)
    private val engine=Companion()
    var page by mutableStateOf(Page.CAMP)
    var selectedGuide by mutableStateOf<Guide?>(null)
    var personality by mutableStateOf(runCatching { Personality.valueOf(prefs.getString("personality","ULTRA")!!) }.getOrDefault(Personality.ULTRA))
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
    var modelStatus by mutableStateOf("AI не загружен в память")
    val messages=mutableStateListOf<ChatMessage>()
    var choices by mutableStateOf(listOf("Огонь", "Вода", "Укрытие", "Я потерялся"))
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
            for(i in 0 until array.length()) { val o=array.getJSONObject(i);messages.add(ChatMessage(o.getString("text"),o.getBoolean("user"),o.optString("guide").ifBlank { null })) }
            if(prefs.contains("campLat"))camp=Location("saved").apply { latitude=prefs.getString("campLat","0")!!.toDouble();longitude=prefs.getString("campLon","0")!!.toDouble();time=prefs.getLong("campTime",0) }
            val points=JSONArray(prefs.getString("track","[]"))
            for(i in 0 until points.length()) { val p=points.getJSONArray(i);track.add(Location("saved").apply {latitude=p.getDouble(0);longitude=p.getDouble(1);time=p.getLong(2)}) }
        }
        if(messages.isEmpty())messages.add(ChatMessage("Я Путник. Помогу найти памятку, собрать рюкзак и пройти действия по шагам. Что нужно сейчас?",false))
    }
    fun openGuide(guide: Guide) { selectedGuide=guide;page=Page.GUIDE }
    fun ask(text: String, fromVoice: Boolean=false) {
        val input=text.trim().take(1500);if(input.isEmpty())return
        cancelGeneration();page=Page.CHAT
        messages.add(ChatMessage(input,true))
        val reply=engine.ask(input,personality)
        choices=reply.choices
        messages.add(ChatMessage(reply.text,false,reply.guide?.id));persistHistory()
        if(fromVoice || autoRead)voice.speak(reply.text)
        val explanation=GuideLibrary.normalize(input).let { it.contains("объясни")||it.contains("почему")||it.contains("подробнее") }
        val guide=reply.guide
        if(modelReady && explanation && guide!=null && !reply.critical) {
            thinking=true;val token=++requestId
            generationJob=viewModelScope.launch {
                try {
                    val answer=withContext(Dispatchers.IO) { NativeLlama.generate(engine.prompt(input,guide,personality)).toString(Charsets.UTF_8).trim() }
                    if(token!=requestId)return@launch
                    if(engine.validModelAnswer(answer,guide)) {
                        messages.add(ChatMessage("Пояснение AI · сверяй действия с памяткой\n\n$answer",false,guide.id));persistHistory()
                        if(fromVoice||autoRead)voice.speak(answer)
                    } else messages.add(ChatMessage("AI не дал достаточно надёжного пояснения. Используй текст памятки выше.",false,guide.id))
                } catch(_: CancellationException) { } catch(_: Exception) {
                    if(token==requestId)modelStatus="AI недоступен; памятки продолжают работать"
                } finally { if(token==requestId)thinking=false;scheduleIdleUnload() }
            }
        }
    }
    fun cancelGeneration() { requestId++;generationJob?.cancel();if(NativeLlama.available)NativeLlama.cancel();thinking=false;voice.stopSpeaking() }
    fun clearChat() { cancelGeneration();engine.reset();messages.clear();messages.add(ChatMessage("Начнём с новой задачи. Что случилось?",false));persistHistory() }
    private fun persistHistory() {
        val array=JSONArray();messages.takeLast(40).forEach { m -> array.put(JSONObject().put("text",m.text).put("user",m.user).put("guide",m.guideId.orEmpty())) }
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
            } catch(_: CancellationException) { packStatus="Загрузка остановлена; прежние пакеты сохранены" }
            catch(e: Exception) { packStatus=e.message ?: "Не удалось загрузить пакет" }
            finally { activePack=null }
        }
    }
    fun cancelDownload() { downloadJob?.cancel() }
    fun enableModel() {
        if(!installed(Packs.model)) { page=Page.PACK;packStatus="Сначала скачай AI-пакет";return }
        if(thinking)return
        thinking=true;modelStatus="Открываю модель на устройстве…"
        val token=++requestId
        generationJob=viewModelScope.launch {
            try {
                val error=withContext(Dispatchers.IO) {
                    check(NativeLlama.available) { "AI runtime не поддерживается на этом устройстве" }
                    val info=ActivityManager.MemoryInfo();getApplication<Application>().getSystemService(ActivityManager::class.java).getMemoryInfo(info)
                    check(info.availMem>3_200_000_000L) { "Для этой модели сейчас мало свободной RAM. Закрой другие приложения или используй справочник." }
                    NativeLlama.load(Packs.file(getApplication(),Packs.model).absolutePath)
                }
                if(token==requestId){modelReady=error.isBlank();modelStatus=if(modelReady)"Qwen3 4B · на устройстве" else "Не удалось открыть AI-модель";scheduleIdleUnload()}
            } catch(e: Exception) { if(token==requestId)modelStatus=e.message ?: "Не удалось открыть модель" }
            finally { if(token==requestId)thinking=false }
        }
    }
    fun unloadModel() { cancelGeneration();idleJob?.cancel();modelReady=false;modelStatus="AI выгружен; справочник доступен";viewModelScope.launch(Dispatchers.IO) { if(NativeLlama.available)NativeLlama.unload() } }
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
