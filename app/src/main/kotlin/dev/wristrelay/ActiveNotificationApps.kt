package dev.wristrelay

internal object ActiveNotificationApps {
    // Notification keys and source packages are held only in memory while Android is connected.
    private var packagesByKey: MutableMap<String, String>? = null
    private var observer: (() -> Unit)? = null
    var counts: Map<String, Int>? = null
        private set

    fun observe(callback: (() -> Unit)?) {
        observer = callback
    }

    fun connect(packages: Map<String, String>) {
        packagesByKey = packages.toMutableMap()
        publish()
    }

    fun disconnect() {
        packagesByKey = null
        publish()
    }

    fun post(key: String, sourcePackage: String) {
        val active = packagesByKey ?: return
        if (active.put(key, sourcePackage) != sourcePackage) publish()
    }

    fun remove(key: String) {
        if (packagesByKey?.remove(key) != null) publish()
    }

    private fun publish() {
        val snapshot = packagesByKey?.values?.groupingBy { it }?.eachCount()
        if (counts == snapshot) return
        counts = snapshot
        observer?.invoke()
    }
}
