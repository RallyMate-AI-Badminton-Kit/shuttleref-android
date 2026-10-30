package com.yahabib.shuttleref.core

/** Deliberately excludes exception messages, causes' messages and arbitrary SDK logs. */
fun safeCrash(error: Throwable): String = buildString {
    var current: Throwable? = error
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    repeat(5) {
        val item = current ?: return@buildString
        if (!seen.add(item)) return@buildString
        appendLine(item.javaClass.name)
        item.stackTrace.take(24).forEach { frame ->
            appendLine("  at ${frame.className}.${frame.methodName}:${frame.lineNumber}")
        }
        current = item.cause
    }
}.take(16000)
