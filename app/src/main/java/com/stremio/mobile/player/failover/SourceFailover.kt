package com.stremio.mobile.player.failover

import java.text.Normalizer

/**
 * Registered sources for the automatic switch when a stream does not start. Addons are matched
 * by name (accents, case, spaces and punctuation ignored), so "Best Cine", "BestCine" and
 * "Torrentio Brazuca" all match. Only addons the user installed take part.
 */
data class SourceFailoverConfig(
    val enabled: Boolean = true,
    /** Time a source has to actually start playing (not to answer the addon request). */
    val startTimeoutMs: Long = DEFAULT_START_TIMEOUT_MS,
    /** Order in which addons are tried after the chosen stream. */
    val addonOrder: List<String> = DEFAULT_ADDON_ORDER,
    /** Hard cap on attempts per playback, the chosen stream included. */
    val maxAttempts: Int = DEFAULT_ADDON_ORDER.size + 1,
) {
    companion object {
        const val DEFAULT_START_TIMEOUT_MS = 30_000L
        val DEFAULT_ADDON_ORDER = listOf("Torrentio", "FrostStream", "BestCine")

        fun normalize(name: String): String =
            Normalizer.normalize(name, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
                .filter { it.isLetterOrDigit() }
    }

    /** Position of [addonTitle] in [addonOrder], or null when it is not a registered source. */
    fun rankOf(addonTitle: String): Int? {
        val normalized = normalize(addonTitle)
        if (normalized.isEmpty()) return null
        return addonOrder.indexOfFirst { normalized.contains(normalize(it)) }.takeIf { it >= 0 }
    }
}

enum class AttemptOutcome { STARTED, TIMEOUT, FAILED, CANCELLED }

/** One attempt, kept for diagnostics. Times are milliseconds since the attempt began. */
data class FailoverAttempt(
    val index: Int,
    val key: String,
    val addon: String,
    val outcome: AttemptOutcome? = null,
    val elapsedMs: Long? = null,
    val reason: String? = null,
)

/**
 * The state machine of one playback request: the chosen stream first, then the best stream of
 * each registered addon not tried yet, in [SourceFailoverConfig.addonOrder]. A stream that failed
 * is never tried again in the same session and each addon gets one automatic attempt, so the
 * chain always ends. Once a stream starts playing the session is finished and nothing switches.
 *
 * Pure logic: the caller owns the timer and the player; [now] is injectable for tests.
 */
class SourceFailoverSession<T>(
    private val config: SourceFailoverConfig,
    private val keyOf: (T) -> String,
    private val addonOf: (T) -> String,
    private val now: () -> Long,
) {
    private val failedKeys = mutableSetOf<String>()
    private val triedAddons = mutableSetOf<String>()
    private val _attempts = mutableListOf<FailoverAttempt>()
    private var attemptStartedAt = 0L

    var current: T? = null
        private set

    /** True once a stream played: from then on the session never switches. */
    var started = false
        private set

    val attempts: List<FailoverAttempt> get() = _attempts.toList()

    /** True while an attempt is waiting to start playing. */
    val isWaiting: Boolean get() = current != null && !started

    fun begin(first: T) {
        current = first
        triedAddons += SourceFailoverConfig.normalize(addonOf(first))
        attemptStartedAt = now()
        _attempts += FailoverAttempt(_attempts.size + 1, keyOf(first), addonOf(first))
    }

    /** Playback really started; returns the time it took, or null if nothing was waiting. */
    fun onStarted(): Long? {
        if (!isWaiting) return null
        started = true
        return finish(AttemptOutcome.STARTED, null)
    }

    /** The user stopped playback (closed the player, paused before start…). */
    fun cancel() {
        if (isWaiting) finish(AttemptOutcome.CANCELLED, null)
        current = null
    }

    /**
     * The current attempt timed out or failed for good. Returns the next stream to try from
     * [available], or null when the chain is over (the caller then reports that every source failed).
     */
    fun onFailed(outcome: AttemptOutcome, reason: String?, available: List<T>, best: Comparator<T>): T? {
        if (!isWaiting) return null
        require(outcome == AttemptOutcome.TIMEOUT || outcome == AttemptOutcome.FAILED)
        failedKeys += keyOf(current!!)
        finish(outcome, reason)
        if (!config.enabled || _attempts.size >= config.maxAttempts) {
            current = null
            return null
        }
        val next = nextCandidate(available, best)
        if (next == null) {
            current = null
            return null
        }
        begin(next)
        return next
    }

    private fun nextCandidate(available: List<T>, best: Comparator<T>): T? {
        val usable = available.filter { keyOf(it) !in failedKeys }
        for (addon in config.addonOrder) {
            val normalizedAddon = SourceFailoverConfig.normalize(addon)
            if (triedAddons.any { it.contains(normalizedAddon) }) continue
            usable.filter { SourceFailoverConfig.normalize(addonOf(it)).contains(normalizedAddon) }
                .sortedWith(best)
                .firstOrNull()
                ?.let { return it }
        }
        return null
    }

    private fun finish(outcome: AttemptOutcome, reason: String?): Long {
        val elapsed = now() - attemptStartedAt
        val last = _attempts.removeAt(_attempts.lastIndex)
        _attempts += last.copy(outcome = outcome, elapsedMs = elapsed, reason = reason)
        return elapsed
    }
}

/**
 * Detects that a player really started: position moving forward while playing and not
 * buffering, for at least [requiredProgressMs]. Opening the player, buffering, an API answer or
 * the resume seek do not count. Fed with the engine's runtime updates.
 */
class PlaybackStartDetector(private val requiredProgressMs: Long = 1_000L) {
    private var lastPosition: Long? = null
    private var progress = 0L

    var started = false
        private set

    fun reset() {
        lastPosition = null
        progress = 0L
        started = false
    }

    /** Returns true the first time playback counts as started. */
    fun onUpdate(isPlaying: Boolean, isBuffering: Boolean, positionMs: Long, hasError: Boolean): Boolean {
        if (started) return false
        if (!isPlaying || isBuffering || hasError) {
            lastPosition = null
            return false
        }
        val previous = lastPosition
        lastPosition = positionMs
        if (previous == null) return false
        val delta = positionMs - previous
        // Seeks (resume position, user) jump; real playback advances in small steps.
        if (delta in 1..MAX_STEP_MS) progress += delta
        if (progress >= requiredProgressMs) {
            started = true
            return true
        }
        return false
    }

    private companion object {
        const val MAX_STEP_MS = 5_000L
    }
}

/** What the player shows about the automatic switch. */
data class SourceFailoverUi(
    /** Short message such as "Torrentio não iniciou em 30 s. Tentando FrostStream…". */
    val notice: String? = null,
    /** Every source failed (or the last one is still not playing): offer retry / another source. */
    val allFailed: Boolean = false,
)

/** Portuguese messages shared by the players. */
object SourceFailoverMessages {
    const val ALL_FAILED = "Nenhuma fonte conseguiu iniciar a reprodução."
    const val ALL_FAILED_HINT = "Tente novamente ou escolha outra fonte."

    fun switching(from: String, outcome: AttemptOutcome, to: String, timeoutMs: Long): String = when (outcome) {
        AttemptOutcome.TIMEOUT -> "$from não iniciou em ${timeoutMs / 1000} s. Tentando $to…"
        else -> "$from falhou. Tentando $to…"
    }

    fun switched(to: String): String = "Reproduzindo por $to"
}

/**
 * Candidates for the switch: the streams of [video] only (never another episode's), read from
 * the core first (always up to date, also when playback started without the stream sheet), then
 * from the sheet's list, each once.
 */
fun <T> sameVideoStreams(video: String, fromCore: List<T>, fromSheet: List<T>, videoOf: (T) -> String, keyOf: (T) -> String): List<T> =
    (fromCore + fromSheet).filter { videoOf(it) == video }.distinctBy(keyOf)
