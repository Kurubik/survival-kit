package lv.putnik.survivalkit

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import kotlinx.coroutines.*

class Voice(private val context: Context, private val scope: CoroutineScope, private val status: (String)->Unit, private val result: (String)->Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech?=null
    var canSpeak=false; private set
    var listening=false; private set
    private var generation=0
    private var speech: SpeechRecognizer?=null
    private var service: SpeechService?=null
    private var model: Model?=null
    init {
        tts=TextToSpeech(context) { code -> main.post {
            val engine=tts
            val voice=engine?.voices?.filter { it.locale.language=="ru" && !it.isNetworkConnectionRequired }?.maxByOrNull { it.quality }
            canSpeak=code==TextToSpeech.SUCCESS && voice!=null
            if(canSpeak)engine?.voice=voice
            status(if(canSpeak) "Русский офлайн-голос готов" else "Установи русский офлайн-голос в настройках Android")
        } }
    }
    fun speak(text: String) {
        stopListening()
        if(canSpeak)tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"putnik") else status("Русский офлайн-голос не установлен")
    }
    fun stopSpeaking() { tts?.stop() }
    fun stopListening() { generation++;listening=false;speech?.cancel();service?.stop();service?.shutdown();service=null }
    fun listen() {
        stopSpeaking();stopListening();listening=true
        val token=generation
        status("Слушаю… Нажми ещё раз, чтобы остановить")
        if(Packs.installed(context,Packs.voice)) {
            scope.launch(Dispatchers.IO) {
                try {
                    if(model==null)model=Model(Packs.voiceDir(context).absolutePath)
                    val recognizer=Recognizer(model,16000f)
                    withContext(Dispatchers.Main) {
                        if(token!=generation || !listening) { recognizer.close();return@withContext }
                        val next=SpeechService(recognizer,16000f);service=next
                        next.startListening(object: org.vosk.android.RecognitionListener {
                            override fun onPartialResult(text: String) {}
                            override fun onResult(text: String) { deliver(token,JSONObject(text).optString("text")) }
                            override fun onFinalResult(text: String) { deliver(token,JSONObject(text).optString("text")) }
                            override fun onError(e: Exception) { if(token==generation){stopListening();status("Речь не распознана. Попробуй ещё раз или напиши текст.")} }
                            override fun onTimeout() { if(token==generation){stopListening();status("Не услышал вопрос. Попробуй ещё раз.")} }
                        },30000)
                    }
                } catch(e: Exception) { withContext(Dispatchers.Main) { if(token==generation){stopListening();status("Не удалось открыть русский голосовой пакет")} } }
            }
        } else if(Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            speech?.destroy();speech=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            speech?.setRecognitionListener(object: RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { if(token==generation){stopListening();status("Для надёжного русского офлайна скачай голосовой пакет в рюкзаке.")} }
                override fun onResults(results: Bundle?) { deliver(token,results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()) }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speech?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            })
        } else { listening=false;status("Скачай русское распознавание речи в рюкзаке. Текстовый ввод уже работает.") }
    }
    private fun deliver(token: Int, text: String) {
        if(token!=generation || !listening || text.isBlank())return
        stopListening();status("Распознано: $text");result(text)
    }
    fun close() { stopListening();speech?.destroy();speech=null;tts?.shutdown();model?.close();model=null }
}
