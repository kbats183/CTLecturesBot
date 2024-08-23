package ru.kbats.youtube.broadcastscheduler.states

class UserStateStorage {
    private val states = mutableMapOf<Long, UserState>()

    operator fun get(id: Long?) = id?.let { states[it] } ?: UserState.Default
    operator fun set(id: Long, newState: UserState) {
        states[id] = newState
    }
}
