package com.github.mittalyashu.ghstack.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.github.mittalyashu.ghstack.cli.GhCommandRun
import com.github.mittalyashu.ghstack.settings.ConsoleVerbosity
import com.github.mittalyashu.ghstack.settings.GhStackSettings

/**
 * Project-level buffer of `gh` invocations. Keeping the history here rather than only in the console component
 * means commands run before the tool window is opened (e.g. the initial stack load) are still shown.
 */
@Service(Service.Level.PROJECT)
class GhStackConsoleService(private val project: Project) {
    fun interface Listener {
        fun commandLogged(entry: GhCommandLogEntry)
    }

    private val lock = Any()
    private val history = ArrayDeque<GhCommandLogEntry>()
    private var sequence = 0L

    fun record(run: GhCommandRun) {
        if (GhStackSettings.getInstance().consoleVerbosity == ConsoleVerbosity.OFF) return
        synchronized(lock) {
            val entry = GhCommandLogEntry(++sequence, run)
            history.addLast(entry)
            while (history.size > MAX_ENTRIES) {
                history.removeFirst()
            }
            // Publish while holding the lock so listeners see entries in sequence order; the console drops
            // entries it has already replayed from history by comparing sequences.
            project.messageBus.syncPublisher(TOPIC).commandLogged(entry)
        }
    }

    fun history(): List<GhCommandLogEntry> = synchronized(lock) { history.toList() }

    companion object {
        private const val MAX_ENTRIES = 200

        val TOPIC: Topic<Listener> = Topic.create("GH Stack commands", Listener::class.java)

        fun getInstance(project: Project): GhStackConsoleService =
            project.getService(GhStackConsoleService::class.java)
    }
}
