package com.ecoachsolutions.ecoachbooks.Helpers

import java.util.LinkedList
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Created by Daniel on 5/24/2014.
 * Code source: https://stackoverflow.com/questions/4010185/parallel-for-for-java

 */
object Parallel {
    private val NUM_CORES = Runtime.getRuntime().availableProcessors()

    private val forPool = Executors.newFixedThreadPool(NUM_CORES * 2)

    fun <T> parallelFor(elements: Iterable<T>, operation: Operation<T>) {
        try {
            // invokeAll blocks for us until all submitted tasks in the call complete
            forPool.invokeAll(createCallables(elements, operation))
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    fun <T> createCallables(elements: Iterable<T>, operation: Operation<T>): Collection<Callable<Void>> {
        val callables = LinkedList<Callable<Void>>()
        for (elem in elements) {
            callables.add(object : Callable<Void> {
                override fun call(): Void? {
                    operation.perform(elem)
                    return null
                }
            })
        }

        return callables
    }

    interface Operation<T> {
        fun perform(pParameter: T)
    }
}
