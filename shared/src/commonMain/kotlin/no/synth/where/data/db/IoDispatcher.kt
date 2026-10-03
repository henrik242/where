package no.synth.where.data.db

import kotlinx.coroutines.CoroutineDispatcher

/** Dispatcher for blocking SQLite I/O. Dispatchers.IO isn't in commonMain, so it's expect/actual. */
expect val ioDispatcher: CoroutineDispatcher
