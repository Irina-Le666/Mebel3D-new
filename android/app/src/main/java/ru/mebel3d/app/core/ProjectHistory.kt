package ru.mebel3d.app.core

import java.util.ArrayDeque

/** Immutable snapshot history for project-level undo/redo. Keeps a bounded number of states. */
class ProjectHistory<T>(initial: T, private val capacity: Int = 100) {
    init { require(capacity >= 2) }
    private val undoStack = ArrayDeque<T>().apply { addLast(initial) }
    private val redoStack = ArrayDeque<T>()
    private var transactionUndo: List<T>? = null
    private var transactionRedo: List<T>? = null

    /** Start a compound operation whose intermediate snapshots can be rolled back on cancellation. */
    fun beginTransaction() {
        require(transactionUndo == null) { "Транзакция истории уже начата" }
        transactionUndo = undoStack.toList()
        transactionRedo = redoStack.toList()
    }

    /** Keep all changes made since beginTransaction as the current history state. */
    fun commitTransaction() {
        transactionUndo = null
        transactionRedo = null
    }

    /** Restore both history branches exactly as they were before the compound operation. */
    fun cancelTransaction(): T {
        val undo = transactionUndo ?: return current
        val redo = transactionRedo.orEmpty()
        undoStack.clear(); undo.forEach(undoStack::addLast)
        redoStack.clear(); redo.forEach(redoStack::addLast)
        transactionUndo = null
        transactionRedo = null
        return current
    }

    val canUndo: Boolean get() = undoStack.size > 1
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val current: T get() = undoStack.last()

    /** Record only a real change; a new edit invalidates the redo branch. */
    fun commit(next: T): T {
        if (next == current) return current
        undoStack.addLast(next)
        while (undoStack.size > capacity) undoStack.removeFirst()
        redoStack.clear()
        return current
    }

    /**
     * Replace the current gesture snapshot without adding another undo step.
     * If the pointer returns exactly to the state immediately before this gesture,
     * collapse the temporary snapshot instead of leaving a phantom undo operation.
     */
    fun replaceCurrent(next: T): T {
        if (next == current) return current
        if (undoStack.size > 1) {
            val iterator = undoStack.descendingIterator()
            iterator.next() // current snapshot
            val baseline = iterator.next()
            if (next == baseline) {
                undoStack.removeLast()
                return current
            }
            undoStack.removeLast()
            undoStack.addLast(next)
        } else {
            // The gesture may already have returned to its baseline and collapsed
            // its temporary entry. A subsequent pointer update starts a fresh step.
            undoStack.addLast(next)
        }
        return current
    }

    fun undo(): T {
        if (canUndo) redoStack.addLast(undoStack.removeLast())
        return current
    }

    fun redo(): T {
        if (canRedo) {
            undoStack.addLast(redoStack.removeLast())
            while (undoStack.size > capacity) undoStack.removeFirst()
        }
        return current
    }

    /** Reset history after loading or opening another project. */
    fun reset(state: T) {
        transactionUndo = null
        transactionRedo = null
        undoStack.clear(); undoStack.addLast(state); redoStack.clear()
    }
}
