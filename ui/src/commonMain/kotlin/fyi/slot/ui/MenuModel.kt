package fyi.slot.ui

sealed interface MenuItem {
    val label: String
    val selectable: Boolean get() = true
}

class MenuAction(override val label: String, val detail: String? = null, val run: () -> Unit) : MenuItem
class MenuToggle(override val label: String, val get: () -> Boolean, val set: (Boolean) -> Unit) : MenuItem
class MenuChoice(override val label: String, val options: List<String>, val get: () -> Int, val set: (Int) -> Unit) : MenuItem
class MenuSub(override val label: String, val build: () -> MenuPage) : MenuItem
class MenuInfo(override val label: String, val value: String = "") : MenuItem {
    override val selectable get() = false
}

class MenuPage(val title: String, val items: List<MenuItem>, val subtitle: String? = null)

/** Things the emulator host must do in response to menu choices. */
sealed interface GameAction {
    data object Resume : GameAction
    data object Reset : GameAction
    data class Eject(val save: Boolean) : GameAction
    data class SaveState(val slot: Int) : GameAction
    data class LoadState(val slot: Int) : GameAction
    data class DeleteState(val slot: Int) : GameAction
    data object UndoLoad : GameAction
    data object RefreshStates : GameAction
    data class FastForward(val on: Boolean) : GameAction
    data class Rewind(val on: Boolean) : GameAction
}

fun SlotState.screenChoices() = ShaderChoice.entries.map { it.label }
