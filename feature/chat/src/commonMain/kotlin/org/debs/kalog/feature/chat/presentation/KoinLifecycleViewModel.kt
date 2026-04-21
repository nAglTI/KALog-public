package org.debs.kalog.feature.chat.presentation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.currentKoinScope
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.parameter.ParametersDefinition

@Composable
@OptIn(KoinInternalApi::class)
inline fun <reified VM : ViewModel> koinLifecycleViewModel(
    key: String? = null,
    noinline parameters: ParametersDefinition? = null,
): VM {
    val scope = currentKoinScope()

    return viewModel(key = key) {
        val resolvedParameters = parameters?.invoke()
        if (resolvedParameters == null) {
            scope.get(VM::class, null)
        } else {
            scope.getWithParameters(VM::class, null, resolvedParameters)
        }
    }
}
