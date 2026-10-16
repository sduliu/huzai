package com.kudo.android.data

data class TopicReadingBatch(val topicId: Int, val topicTime: Long, val timings: Map<Int, Long>)

/** Foreground exposure only; no loaded/adjacent floors or retryable write backlog. */
class TopicReadingProgress(private val topicId: Int) {
    private var onscreen = emptySet<Int>()
    private var lastTick: Long? = null
    private var lastActivity = 0L
    private var topicTime = 0L
    private val pending = mutableMapOf<Int, Long>()
    private val total = mutableMapOf<Int, Long>()
    private val notified = mutableSetOf<Int>()

    fun visible(numbers: Set<Int>, now: Long) {
        tick(now)
        onscreen = numbers.filter { it > 0 }.toSet()
        lastActivity = now
        lastTick = now
    }

    fun tick(now: Long) {
        val previous = lastTick
        lastTick = now
        if (previous == null || onscreen.isEmpty()) return
        // Missed frames/background suspension must never become a large read-time jump.
        val delta = (minOf(now, lastActivity + 180_000) - previous).coerceIn(0, 1500)
        if (delta == 0L) return
        var recorded = false
        onscreen.forEach { number ->
            val amount = minOf(delta, (360_000 - (total[number] ?: 0)).coerceAtLeast(0))
            if (amount > 0) {
                pending[number] = (pending[number] ?: 0) + amount
                total[number] = (total[number] ?: 0) + amount
                recorded = true
            }
        }
        if (recorded) topicTime += delta
    }

    fun drain(): TopicReadingBatch? {
        val eligible = pending.filterValues { it >= 1000 }
        if (eligible.isEmpty()) return null
        eligible.keys.forEach(pending::remove)
        return TopicReadingBatch(topicId, topicTime, eligible).also { topicTime = 0 }
    }

    /** Local feedback is independent of the batched network drain. */
    fun takeReadNumbers(): Set<Int> = total.filter { (number, time) -> time >= 1000 && number !in notified }
        .keys.toSet().also { notified += it }
}
