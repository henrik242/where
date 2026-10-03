package no.synth.where.data.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Dispatchers.IO is internal on native; Default is the off-main background pool here, and SQLiter
// serializes DB access regardless. (The CPU-pool contention that motivates IO is Android-specific.)
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
