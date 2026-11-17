package lv.putnik.survivalkit

object NativeLlama {
    val available: Boolean = runCatching { System.loadLibrary("putnik");true }.getOrDefault(false)
    external fun load(path: String): String
    external fun generate(prompt: String): ByteArray
    external fun cancel()
    external fun unload()
}
