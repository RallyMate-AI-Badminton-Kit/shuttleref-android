package com.yahabib.shuttleref.core

import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

class MatchTest {
    private val id = "11111111111111111111111111111111"
    private class Memory : MatchPersistence { override var sequence = 0L; override var saved: String? = null }
    private fun frame(seq: Long, phase: Char = 'a', scores: String = "0,0,0,0,0", code: Int = 0, mid: String = id) = "@1:$mid:${seq.toString(16).padStart(8,'0')}:$phase:$scores:$code"
    private fun active(memory: Memory = Memory()): MatchSession = MatchSession(memory, true).also { s ->
        val q = requireNotNull(s.begin(MatchAction.QUERY)); s.receive(frame(q.sequence))
    }
    @Test fun vectorsMatchFirmwareEnvelope() {
        assertEquals("QhEREREREREREREREREREREAAAABAAAA", MatchWire.encode(MatchAction.BEGIN, id, 1))
        assertEquals("UhEREREREREREREREREREREAAAACDAkF", MatchWire.encode(MatchAction.RESUME, id, 2, listOf(12,9,1,0,1)))
        assertNull(MatchWire.encode(MatchAction.RESUME, id, 1, listOf(30,0,0,0,0)))
        assertNull(MatchWire.encode(MatchAction.RESUME, id, 1, listOf(21,18,0,0,0)))
        assertNull(MatchWire.encode(MatchAction.BEGIN, "invalid", 1))
        assertNull(MatchWire.encode(MatchAction.BEGIN, id, MAX_MATCH_SEQUENCE + 1))
    }
    @Test fun parserRejectsMalformedAndOutOfRangeSnapshots() {
        assertNotNull(MatchSnapshot.parse(frame(0xffffffffL)))
        assertNull(MatchSnapshot.parse(frame(1, scores="31,0,0,0,0")))
        assertNull(MatchSnapshot.parse(frame(1, scores="0,0,0,0")))
        assertNull(MatchSnapshot.parse(frame(1, scores="0,0,0,0,2")))
        assertNull(MatchSnapshot.parse(frame(1, code=5)))
        assertNull(MatchSnapshot.parse(frame(1, phase='z')))
    }
    @Test fun cacheAndPublishSuccessAreNotDeviceConfirmation() {
        val s = MatchSession(Memory(), true, frame(42, 'i', mid=EMPTY_MATCH_ID))
        assertFalse(s.can(MatchAction.BEGIN))
        val q = requireNotNull(s.begin(MatchAction.QUERY))
        assertEquals(43L,q.sequence)
        s.sent(q)
        assertNotNull(s.pending); assertFalse(s.fresh)
        s.receive(frame(43, 'i', mid=EMPTY_MATCH_ID))
        assertTrue(s.can(MatchAction.BEGIN))
        val b=requireNotNull(s.begin(MatchAction.BEGIN,id)); s.sent(b)
        assertEquals('i',s.current?.phase)
        s.receive(frame(b.sequence))
        assertTrue(s.can(MatchAction.RED))
    }
    @Test fun noDoubleSubmitAndOldAckDoesNotConfirmScore() {
        val s=active();val command=requireNotNull(s.begin(MatchAction.RED))
        assertNull(s.begin(MatchAction.RED))
        s.receive(frame(command.sequence-1))
        assertNotNull(s.pending)
        s.receive(frame(command.sequence,scores="1,0,0,0,0"))
        s.sent(command) // Late publish callback must not restore waiting state.
        assertNull(s.pending);assertEquals(MatchAction.RED,s.lastConfirmed)
        assertEquals(1,s.current?.scores?.get(0))
    }
    @Test fun timeoutOrSendFailureRequireQueryBeforeAnyMorePoints() {
        val s=active(); val command=requireNotNull(s.begin(MatchAction.RED))
        s.invalidate("结果未知")
        assertNull(s.begin(MatchAction.RED)); assertTrue(s.can(MatchAction.QUERY))
        s.receive(frame(command.sequence,scores="1,0,0,0,0"))
        assertFalse(s.fresh)
        val q=requireNotNull(s.begin(MatchAction.QUERY));s.receive(frame(q.sequence,scores="1,0,0,0,0"))
        val blue=requireNotNull(s.begin(MatchAction.BLUE));s.sent(blue,"发送失败")
        assertFalse(s.can(MatchAction.BLUE));assertTrue(s.can(MatchAction.QUERY))
    }
    @Test fun competingPhoneWatermarkRequiresNewQuery() {
        val s=active(); s.begin(MatchAction.RED)
        s.receive(frame(100,code=3))
        assertFalse(s.fresh); assertNull(s.pending)
        val q=requireNotNull(s.begin(MatchAction.QUERY));assertEquals(101L,q.sequence)
        s.receive(frame(101));assertTrue(s.can(MatchAction.RED))
    }
    @Test fun wrongMatchOrPhaseCannotConfirm() {
        val s=active();val p=requireNotNull(s.begin(MatchAction.PAUSE))
        s.receive(frame(p.sequence, 'p', mid="22222222222222222222222222222222"))
        assertFalse(s.fresh);assertNull(s.lastConfirmed)
        val q=requireNotNull(s.begin(MatchAction.QUERY));s.receive(frame(q.sequence))
        val pause=requireNotNull(s.begin(MatchAction.PAUSE));s.receive(frame(pause.sequence,'a'))
        assertFalse(s.fresh)
    }
    @Test fun pausedSnapshotAndSequenceSurviveRecreationResumeAndClear() {
        val storage=Memory();val s=active(storage)
        val p=requireNotNull(s.begin(MatchAction.PAUSE));s.receive(frame(p.sequence,'p',"12,9,1,0,1"))
        assertFalse(s.can(MatchAction.RED));assertNotNull(storage.saved)
        val restored=MatchSession(storage,true,frame(0,'i',mid=EMPTY_MATCH_ID))
        assertNotNull(restored.saved)
        assertFalse(restored.can(MatchAction.RESUME))
        val q=requireNotNull(restored.begin(MatchAction.QUERY));assertEquals(3L,q.sequence)
        restored.receive(frame(q.sequence,'i',mid=EMPTY_MATCH_ID))
        val r=requireNotNull(restored.begin(MatchAction.RESUME));restored.receive(frame(r.sequence,'a',"12,9,1,0,1"))
        assertNull(storage.saved);assertTrue(restored.can(MatchAction.BLUE))
        val a=requireNotNull(restored.begin(MatchAction.CLEAR));restored.receive(frame(a.sequence,'i'))
        assertTrue(restored.can(MatchAction.BEGIN)) // Old ID retained by firmware must not mean ended.
    }
    @Test fun offlineAndClosedSessionNeverSendOrAcceptLateResults() {
        val s=active();s.setOnline(false)
        assertFalse(s.can(MatchAction.QUERY));s.setOnline(true)
        assertFalse(s.can(MatchAction.RED));assertTrue(s.can(MatchAction.QUERY))
        s.close();s.receive(frame(90));assertEquals(1L,s.current?.sequence)
        assertFalse(s.can(MatchAction.QUERY))
    }
    @Test fun finishedMatchBlocksScoringButAllowsClear() {
        val s=active();s.receive(frame(2,scores="21,10,2,0,0"))
        assertTrue(s.current!!.finished)
        for (action in listOf(MatchAction.RED,MatchAction.BLUE,MatchAction.UNDO,MatchAction.PAUSE)) assertFalse(s.can(action))
        assertTrue(s.can(MatchAction.CLEAR))
        s.receive(frame(1));assertEquals(2L,s.current?.sequence)
    }
    @Test fun exhaustedSequenceAndStorageFailureCannotEmitCommand() {
        val m=Memory();m.sequence=MAX_MATCH_SEQUENCE
        assertNull(MatchSession(m,true).begin(MatchAction.QUERY))
        val broken=object:MatchPersistence {
            override var saved:String?=null
            override var sequence:Long get()=0; set(value) { error("disk full") }
        }
        val s=MatchSession(broken,true)
        assertNull(s.begin(MatchAction.QUERY));assertNull(s.pending)
    }
    @Test fun realFirmwareReplayBeginScoreUndoPauseResumeFinishClear() {
        val path=System.getenv("SHUTTLEREF_MATCH_HARNESS")
        assumeTrue("C offline harness optional; set env for integration replay",path!=null && File(path).canExecute())
        val process=ProcessBuilder(path!!).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val input=process.outputStream.bufferedWriter();val output=process.inputStream.bufferedReader()
        val s=MatchSession(Memory(),true)
        fun send(action:MatchAction) {
            val command=requireNotNull(s.begin(action,id))
            input.write(command.wire);input.newLine();input.flush()
            val raw=Regex("\\\"101\\\":\\\"([^\\\"]+)\\\"").find(output.readLine())!!.groupValues[1]
            s.receive(raw);assertTrue("$action: ${s.message}",s.fresh)
        }
        try {
            send(MatchAction.QUERY);send(MatchAction.BEGIN);send(MatchAction.RED);send(MatchAction.BLUE);send(MatchAction.UNDO)
            assertEquals(listOf(1,0,0,0,0),s.current!!.scores)
            send(MatchAction.PAUSE);assertTrue(s.can(MatchAction.RESUME));send(MatchAction.RESUME)
            repeat(41) { send(MatchAction.RED) }
            assertTrue(s.current!!.finished);assertEquals(2,s.current!!.scores[2])
            send(MatchAction.CLEAR);assertTrue(s.can(MatchAction.BEGIN))
        } finally { input.close();process.destroy() }
    }
}
