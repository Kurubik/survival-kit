package lv.putnik.survivalkit

object NativeLlama {
    val available: Boolean = runCatching { System.loadLibrary("putnik");true }.getOrDefault(false)
    fun interface Listener { fun onText(bytes: ByteArray) }
    external fun startRequest(): Long
    external fun isLoaded(): Boolean
    external fun load(path: String, request: Long): String
    external fun generate(prompt: ByteArray, listener: Listener?, request: Long): ByteArray
    external fun cancel()
    external fun unload(request: Long)
}
