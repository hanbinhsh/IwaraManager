package com.ice.iwaramanager.domain.scanner

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class LibraryScanCoordinator(
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    fun launch(
        onStarted: () -> Unit,
        scan: suspend () -> Unit,
        onCompleted: () -> Unit,
        onFailed: (Throwable) -> Unit
    ) {
        job?.cancel()
        job = scope.launch {
            onStarted()
            try {
                scan()
                onCompleted()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onFailed(error)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
