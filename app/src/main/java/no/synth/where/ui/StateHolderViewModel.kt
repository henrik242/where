package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope

/** Keeps a shared screen state holder across configuration changes, running on the ViewModel scope. */
class StateHolderViewModel<T>(create: (CoroutineScope) -> T) : ViewModel() {
    val state: T = create(viewModelScope)
}

@Composable
inline fun <reified T> rememberRetainedState(noinline create: (CoroutineScope) -> T): T =
    viewModel(key = T::class.qualifiedName) { StateHolderViewModel(create) }.state
