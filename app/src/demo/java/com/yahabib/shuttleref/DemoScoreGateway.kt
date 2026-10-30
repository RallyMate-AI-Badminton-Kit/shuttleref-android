package com.yahabib.shuttleref

import android.os.Handler
import android.os.Looper
import com.yahabib.shuttleref.core.*
import java.util.Base64

/** UI-only simulated host. Compiled exclusively into .demo; no Tuya or server calls. */
class DemoScoreGateway : ScoreGateway {
    private var current = MatchSnapshot(EMPTY_MATCH_ID, 0, 'i', listOf(0,0,0,0,0), 0)
    private val history = mutableListOf<List<Int>>()
    override fun open(deviceId: String, events: ScoreEvents): ScoreConnection = object : ScoreConnection {
        private var closed = false
        override val cachedSnapshot get() = current.raw()
        override val online get() = !closed
        override fun close() { closed = true }
        override fun publish(wire: String, completed: (String?) -> Unit) {
            completed(null)
            Handler(Looper.getMainLooper()).postDelayed({
                if (closed) return@postDelayed
                val b = Base64.getDecoder().decode(wire)
                val op = b[0].toInt().toChar()
                val id = b.slice(1..16).joinToString("") { "%02x".format(it) }
                val seq = b.slice(17..20).fold(0L) { n, v -> (n shl 8) or (v.toLong() and 255) }
                current = current.copy(sequence = seq, code = 0)
                when(op) {
                    'B' -> { history.clear(); current = current.copy(id = id, phase = 'a', scores = listOf(0,0,0,0,0)) }
                    'A' -> { history.clear(); current = current.copy(phase = 'i', scores = listOf(0,0,0,0,0)) }
                    'P' -> current = current.copy(phase = 'p')
                    'R' -> { history.clear(); val bits = b[23].toInt(); current = current.copy(id=id, phase='a', scores = listOf(b[21].toInt(), b[22].toInt(), bits and 1, (bits shr 1) and 1, (bits shr 2) and 1)) }
                    'U' -> if (history.isNotEmpty()) current = current.copy(scores = history.removeAt(history.lastIndex)) else current = current.copy(code=1)
                    'r', 'b' -> {
                        history.add(current.scores)
                        val scores = current.scores.toMutableList(); val side = if (op == 'r') 0 else 1
                        scores[side]++; scores[4] = side
                        if (scores[side] == 30 || (scores[side] >= 21 && scores[side] - scores[1-side] >= 2)) {
                            scores[side+2]++
                            if (scores[side+2] < 2) { scores[0]=0; scores[1]=0 }
                        }
                        current = current.copy(scores = scores.toList())
                    }
                }
                events.snapshot(current.raw())
            }, 600)
        }
    }
}
