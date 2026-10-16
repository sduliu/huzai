package com.kudo.android.data

/** Main-thread account ledger; cursors deduplicate transport, this joins duplicate alert channels. */
class NotificationLedger {
    var count: Int = 0; private set
    var revision: Long = 0; private set
    private var accountId: Int? = null
    private var live = false
    private val seen = linkedSetOf<String>()
    private val correlations = linkedMapOf<String, Long>()
    val reads = linkedMapOf<Int, Boolean>()
    private val readRevisions = mutableMapOf<Int, Long>()

    fun account(member: Member?) {
        if (accountId != member?.id) {
            accountId = member?.id; live = false; seen.clear(); correlations.clear(); reads.clear(); readRevisions.clear(); revision++
        }
        if (!live) count = member?.unreadNotifications ?: 0
    }
    fun freshCount(member: Member, requestedRevision: Long) {
        if (member.id == accountId && revision == requestedRevision) {
            count = member.unreadNotifications; live = true; revision++
        }
    }
    fun accept(batch: NotificationBatch, now: Long = System.currentTimeMillis()): List<NotificationAlert> {
        val id = accountId ?: return emptyList()
        val candidates = mutableListOf<NotificationAlert>()
        batch.snapshots.forEach { snapshot ->
            revision++
            snapshot.count?.let { count = it; live = true }
            reads.putAll(snapshot.recent)
            snapshot.recent.keys.forEach { readRevisions[it] = revision }
            snapshot.notice?.let { notice ->
                reads[notice.id] = snapshot.recent[notice.id] ?: notice.read
                readRevisions[notice.id] = revision
                if (reads[notice.id] != true) candidates += NotificationAlert("notice:${notice.id}", notice.title,
                    notice.description, NotificationTarget(id, notice.id, notice.topicId, notice.postNumber,
                        notice.chatChannelId, notice.chatMessageId), notice.actor?.username.orEmpty())
            }
        }
        candidates += batch.alerts.filter { it.target.accountId == id }
        val result = candidates.filter { alert ->
            val fresh = seen.add(alert.key)
            val target = alert.target
            val identity = when {
                target.chatChannelId != null -> "chat:${target.chatChannelId}:${target.chatMessageId}:${alert.username}"
                target.topicId != null -> "topic:${target.topicId}:${target.postNumber}:${alert.username}"
                else -> alert.key
            }
            val previous = correlations[identity]
            if (fresh) correlations[identity] = now
            fresh && !batch.initial && (previous == null || now - previous > 30_000) &&
                (target.noticeId == null || reads[target.noticeId] != true)
        }
        while (seen.size > 512) seen.remove(seen.first())
        while (correlations.size > 512) correlations.remove(correlations.keys.first())
        while (reads.size > 512) { val oldest = reads.keys.first(); reads.remove(oldest); readRevisions.remove(oldest) }
        return result
    }
    fun reconcilePage(page: List<Notice>, requestedRevision: Long): List<Notice> = page.map { notice ->
        if ((readRevisions[notice.id] ?: -1L) > requestedRevision) notice.copy(read = reads[notice.id] ?: notice.read)
        else {
            reads[notice.id] = notice.read
            readRevisions[notice.id] = requestedRevision
            notice
        }
    }
    fun confirmRead(ids: Set<Int>, all: Boolean, requestedRevision: Long) {
        val unread = ids.count { reads[it] != true }
        if (revision == requestedRevision) {
            count = if (all) 0 else (count - unread).coerceAtLeast(0)
        }
        live = true; revision++
        ids.forEach { reads[it] = true; readRevisions[it] = revision }
    }
}

interface NotificationDelivery {
    fun account(id: Int?) {}
    fun show(alert: NotificationAlert) {}
    fun correlate(notice: Notice) {}
    fun readForum(ids: Set<Int>, all: Boolean = false) {}
    fun readChat(channelId: Int, messageId: Int) {}
    object None : NotificationDelivery
}
