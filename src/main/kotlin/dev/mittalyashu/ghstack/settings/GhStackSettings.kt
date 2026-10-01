package dev.mittalyashu.ghstack.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

enum class StashMode { ASK, ALWAYS, NEVER }

@Service(Service.Level.APP)
@State(name = "GhStackSettings", storages = [Storage("ghstack.xml")])
class GhStackSettings : PersistentStateComponent<GhStackSettings.State> {
    class State {
        var stashMode: StashMode = StashMode.ASK
        var ghExecutablePath: String = ""
    }

    private var state = State()

    var stashMode: StashMode
        get() = state.stashMode
        set(value) {
            state.stashMode = value
        }

    var ghExecutablePath: String
        get() = state.ghExecutablePath
        set(value) {
            state.ghExecutablePath = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): GhStackSettings = ApplicationManager.getApplication().getService(GhStackSettings::class.java)
    }
}
