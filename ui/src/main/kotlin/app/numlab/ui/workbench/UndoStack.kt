package app.numlab.ui.workbench

/** Linear undo history; [commit] drops any redo branch. Not thread-safe (main thread only). */
class UndoStack<T>(initial: T, private val limit: Int = 100) {
    private val stack = mutableListOf(initial)
    private var i = 0

    val current: T get() = stack[i]
    val canUndo get() = i > 0
    val canRedo get() = i < stack.lastIndex

    fun commit(value: T) {
        if (value == stack[i]) return
        while (stack.lastIndex > i) stack.removeAt(stack.lastIndex)
        stack += value
        if (stack.size > limit) stack.removeAt(0)
        i = stack.lastIndex
    }

    /** Replaces the whole history with [value] (startup defaults, not an undoable edit). */
    fun reset(value: T) {
        stack.clear()
        stack += value
        i = 0
    }

    fun undo(): T? = if (canUndo) stack[--i] else null
    fun redo(): T? = if (canRedo) stack[++i] else null
}
