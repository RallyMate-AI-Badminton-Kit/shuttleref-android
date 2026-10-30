package com.yahabib.shuttleref.core

import java.util.Base64
import java.util.UUID

const val MAX_MATCH_SEQUENCE = 0xffffffffL
const val EMPTY_MATCH_ID = "00000000000000000000000000000000"
enum class MatchAction(val wire: Char) { QUERY('Q'), BEGIN('B'), RED('r'), BLUE('b'), UNDO('U'), PAUSE('P'), RESUME('R'), CLEAR('A') }
data class MatchSnapshot(val id: String, val sequence: Long, val phase: Char, val scores: List<Int>, val code: Int) {
    val hasId get() = id != EMPTY_MATCH_ID
    val finished get() = scores[2] >= 2 || scores[3] >= 2
    fun raw(): String = "@1:$id:${sequence.toString(16).padStart(8, '0')}:$phase:${scores.joinToString(",")}:$code"
    companion object {
        fun parse(raw: String): MatchSnapshot? {
            val p = raw.split(':')
            if (p.size != 6 || p[0] != "@1" || !p[1].matches(Regex("[0-9a-fA-F]{32}")) || !p[2].matches(Regex("[0-9a-fA-F]{8}")) || p[3] !in listOf("i", "a", "p")) return null
            val code = p[5].toIntOrNull()?.takeIf { it in 0..4 } ?: return null
            val values = p[4].split(',')
            if (values.size != 5 || values.any { !it.matches(Regex("[0-9]{1,2}")) }) return null
            val scores = values.map { it.toInt() }
            if (scores[0] > 30 || scores[1] > 30 || scores[2] > 2 || scores[3] > 2 || scores[4] > 1) return null
            return MatchSnapshot(p[1].lowercase(), p[2].toLong(16), p[3][0], scores, code)
        }
    }
}
data class MatchCommand(val action: MatchAction, val id: String, val sequence: Long, val wire: String)
object MatchWire {
    fun encode(action: MatchAction, id: String, sequence: Long, scores: List<Int> = listOf(0,0,0,0,0)): String? {
        if (!id.matches(Regex("[0-9a-fA-F]{32}")) || sequence !in 1..MAX_MATCH_SEQUENCE || scores.size != 5) return null
        val bytes = ByteArray(24)
        bytes[0] = action.wire.code.toByte()
        for (i in 0..15) bytes[i+1] = id.substring(i*2,i*2+2).toInt(16).toByte()
        for (i in 0..3) bytes[17+i] = (sequence shr (24-8*i)).toByte()
        if (action == MatchAction.RESUME) {
            if (scores[0] !in 0..29 || scores[1] !in 0..29 || scores.drop(2).any { it !in 0..1 } || (maxOf(scores[0], scores[1]) >= 21 && kotlin.math.abs(scores[0] - scores[1]) >= 2)) return null
            bytes[21] = scores[0].toByte(); bytes[22] = scores[1].toByte()
            bytes[23] = (scores[2] or (scores[3] shl 1) or (scores[4] shl 2)).toByte()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }
}
interface MatchPersistence {
    var sequence: Long
    var saved: String?
}
class MatchSession(private val persistence: MatchPersistence, online: Boolean, cache: String? = null) {
    var current: MatchSnapshot? = cache?.let(MatchSnapshot::parse); private set
    var saved: MatchSnapshot? = persistence.saved?.let(MatchSnapshot::parse)?.takeIf { it.phase == 'p' && it.hasId && it.code == 0 }; private set
    var pending: MatchCommand? = null; private set
    var fresh = false; private set
    var online = online; private set
    var message = "等待同步设备状态"; private set
    var lastConfirmed: MatchAction? = null; private set
    private var valid = true
    private val hasOldScore get() = current?.scores?.take(4)?.any { it != 0 } == true
    fun can(action: MatchAction): Boolean {
        if (!valid || !online || pending != null) return false
        if (action == MatchAction.QUERY) return true
        val s = current ?: return false
        if (!fresh) return false
        return when(action) {
            MatchAction.RED, MatchAction.BLUE, MatchAction.UNDO, MatchAction.PAUSE -> s.phase == 'a' && s.hasId && !s.finished
            MatchAction.BEGIN -> s.phase == 'i' && !hasOldScore && saved == null
            MatchAction.RESUME -> saved != null && s.phase != 'a'
            MatchAction.CLEAR -> s.hasId || hasOldScore || saved != null
            MatchAction.QUERY -> true
        }
    }
    fun begin(action: MatchAction, matchId: String = UUID.randomUUID().toString().replace("-", "")): MatchCommand? {
        if (!can(action)) return null
        val snapshot = if (action == MatchAction.RESUME) saved else current
        val id = if (action == MatchAction.BEGIN || snapshot?.hasId != true) matchId else snapshot.id
        val stored = persistence.sequence.takeIf { it in 0..MAX_MATCH_SEQUENCE } ?: 0
        val previous = maxOf(stored, current?.sequence ?: 0)
        if (previous >= MAX_MATCH_SEQUENCE) { message = "指令序号已耗尽，请联系开发者"; return null }
        val sequence = previous + 1
        val wire = MatchWire.encode(action, id, sequence, snapshot?.scores ?: listOf(0,0,0,0,0))
        if (wire == null) { message = "此比分无法恢复，请核对主机状态"; return null }
        try { persistence.sequence = sequence } catch (e: Exception) { invalidate("无法保存指令序号，未发送操作"); return null }
        lastConfirmed = null
        message = "正在等待设备确认…"
        return MatchCommand(action,id,sequence,wire).also { pending = it }
    }
    fun sent(command: MatchCommand, error: String? = null) {
        if (!valid || pending != command) return
        if (error != null) invalidate("发送异常，结果尚未确认。请同步比分，不要重复加分。")
        else message = "已发送，等待设备确认…"
    }
    fun receive(raw: String) {
        if (!valid) return
        val s = MatchSnapshot.parse(raw) ?: return
        if (s.sequence < (current?.sequence ?: 0)) return
        current = s
        try {
            if (s.phase == 'p' && s.hasId && s.code == 0) save(s)
            val p = pending ?: return
            if (s.sequence != p.sequence && !(s.code == 3 && s.sequence > p.sequence)) return
            pending = null
            val identityOk = p.action == MatchAction.QUERY || s.id == p.id
            val phaseOk = when (p.action) {
                MatchAction.BEGIN, MatchAction.RESUME -> s.phase == 'a'
                MatchAction.PAUSE -> s.phase == 'p'
                MatchAction.CLEAR -> s.phase == 'i'
                else -> true
            }
            fresh = identityOk && s.code == 0 && phaseOk
            if (fresh) {
                if (p.action in listOf(MatchAction.CLEAR, MatchAction.RESUME)) save(null)
                lastConfirmed = p.action
                message = when(p.action) {
                    MatchAction.QUERY -> "比分已同步"
                    MatchAction.PAUSE -> "已暂停，比分已暂存"
                    MatchAction.RESUME -> "已继续比赛"
                    MatchAction.CLEAR -> "比分已清除，可以准备下一场"
                    else -> "设备已确认"
                }
            } else {
                message = when(s.code) {
                    1 -> "主机当前状态不允许此操作，请同步后检查"
                    2 -> "主机已有其他球局或旧比分，请同步后检查"
                    3 -> "其他操作已更新序号，请重新同步比分"
                    4 -> "主机存储失败，请检查设备"
                    else -> "回执不匹配，请同步后检查"
                }
            }
        } catch (e: Exception) { invalidate("保存比分失败，请同步后检查") }
    }
    fun invalidate(message: String = "结果尚未确认。请同步比分，不要重复加分。") { pending = null; fresh = false; lastConfirmed = null; this.message = message }
    fun setOnline(value: Boolean) { online = value; if (!value) invalidate("主机或手机已离线，重连后请同步比分") }
    fun close() { valid = false; online = false; invalidate("计分连接已断开") }
    private fun save(value: MatchSnapshot?) { persistence.saved = value?.raw(); saved = value }
}
