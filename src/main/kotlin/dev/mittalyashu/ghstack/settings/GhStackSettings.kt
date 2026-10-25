package dev.mittalyashu.ghstack.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

enum class ShelveMode { ASK, ALWAYS, NEVER }

@Service(Service.Level.APP)
@State(name = "GhStackSettings", storages = [Storage("ghstack.xml")])
class GhStackSettings : PersistentStateComponent<GhStackSettings.State> {
    class State {
        var shelveMode: ShelveMode = ShelveMode.ASK
    }

    private var state = State()

    var shelveMode: ShelveMode
        get() = state.shelveMode
        set(value) {
            state.shelveMode = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): GhStackSettings = ApplicationManager.getApplication().getService(GhStackSettings::class.java)
    }
}
