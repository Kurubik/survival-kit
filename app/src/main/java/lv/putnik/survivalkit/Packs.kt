package lv.putnik.survivalkit

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class Pack(val id: String, val title: String, val bytes: Long, val sha256: String, val url: String, val filename: String)

object Packs {
    val voice = Pack("voice", "Русское распознавание речи", 46236750,
        "961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4",
        "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip", "voice.zip")
    val map = Pack("map", "Карта Латвии", 110239439,
        "72c93a1527458ab589accba21f85cdd8a529381a7c549346701ef3efbf756b2d",
        "https://download.mapsforge.org/maps/v5/europe/latvia.map", "latvia.map")
    val model = Pack("model", "Локальный AI · Qwen3 4B", 2497280256,
        "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",
        "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/bc640142c66e1fdd12af0bd68f40445458f3869b/Qwen3-4B-Q4_K_M.gguf", "companion.gguf")
    val all = listOf(voice, map, model)
    fun file(context: Context, pack: Pack) = File(context.filesDir, "packs/${pack.filename}")
    fun voiceDir(context: Context) = File(context.filesDir,"packs/vosk-model-small-ru-0.22")
    fun installed(context: Context, pack: Pack): Boolean = if (pack.id == "voice") File(voiceDir(context),"am/final.mdl").isFile else file(context,pack).isFile

    suspend fun install(context: Context, pack: Pack, progress: (Long) -> Unit) {
        val dir = File(context.filesDir, "packs").apply { mkdirs() }
        require(dir.usableSpace > pack.bytes + 200_000_000L) { "Недостаточно свободного места" }
        val staging = File(dir,pack.filename + ".part")
        val connection = URL(pack.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000; connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent","PutnikSurvivalKit/0.1")
        try {
            require(connection.responseCode == 200) { "Сервер загрузки ответил ${connection.responseCode}" }
            val sha = MessageDigest.getInstance("SHA-256")
            var count=0L
            connection.inputStream.use { source -> staging.outputStream().use { target ->
                val buffer=ByteArray(64*1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n=source.read(buffer); if(n<0)break
                    count+=n; require(count<=pack.bytes) { "Размер пакета изменился" }
                    sha.update(buffer,0,n);target.write(buffer,0,n);progress(count)
                }
            } }
            require(count==pack.bytes) { "Пакет загружен не полностью" }
            require(sha.digest().joinToString("") { "%02x".format(it) }==pack.sha256) { "Контрольная сумма не совпала. Данные на сервере могли обновиться; нужна новая версия пакета." }
            if(pack.id=="voice") {
                val extract=File(dir,"voice-staging").apply { mkdirs() }
                var expanded=0L
                ZipInputStream(staging.inputStream()).use { zip ->
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val entry=zip.nextEntry ?: break
                        val out=File(extract,entry.name)
                        require(out.canonicalPath.startsWith(extract.canonicalPath+File.separator)) { "Небезопасный путь в архиве" }
                        if(entry.isDirectory)out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { target ->
                                val b=ByteArray(64*1024)
                                while(true) {
                                    currentCoroutineContext().ensureActive()
                                    val n=zip.read(b);if(n<0)break
                                    expanded+=n;require(expanded<250_000_000L) { "Архив слишком велик" };target.write(b,0,n)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
                val ready=File(extract,"vosk-model-small-ru-0.22")
                require(File(ready,"am/final.mdl").isFile) { "В пакете нет модели" }
                require(ready.renameTo(voiceDir(context))) { "Не удалось установить голос" }
                extract.deleteRecursively();staging.delete()
            } else require(staging.renameTo(file(context,pack))) { "Не удалось установить пакет" }
        } finally { connection.disconnect() }
    }
}
