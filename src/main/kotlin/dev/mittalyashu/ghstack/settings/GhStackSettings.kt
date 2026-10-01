package dev.mittalyashu.ghstack.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "GhStackSettings", storages = [Storage("gh-stack.xml")])
class GhStackSettings : PersistentStateComponent<GhStackSettings.State> {
    class State {
        var ghExecutablePath: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var ghExecutablePath: String
        get() = state.ghExecutablePath
        set(value) {
            state.ghExecutablePath = value
        }

    companion object {
        fun getInstance(): GhStackSettings =
            ApplicationManager.getApplication().getService(GhStackSettings::class.java)
    }
}
