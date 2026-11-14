package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

/**
 * 「哪些记忆库要重新同步」的待办集合。
 *
 * 取代有容量上限、满了丢最旧的 SharedFlow：同一个库连着改很多次只同步一次，但不会有库被漏掉。
 * 先标记、后收集也不会丢，收集开始时一次取走。只给一个收集者用。
 */
internal class PendingIds {
    private val ids = ConcurrentHashMap.newKeySet<String>()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    fun mark(id: String) {
        ids.add(id)
        signal.trySend(Unit)
    }

    /** 每个待办的 id 发一次；发之前先从集合里拿掉，处理期间再被标记的会在下一轮再发 */
    val flow: Flow<String> = flow {
        while (true) {
            signal.receive()
            for (id in ids.toList()) {
                if (ids.remove(id)) emit(id)
            }
        }
    }
}
