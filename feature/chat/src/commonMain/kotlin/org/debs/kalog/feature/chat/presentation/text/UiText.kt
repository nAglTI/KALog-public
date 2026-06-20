package org.debs.kalog.feature.chat.presentation.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

sealed interface UiText {
    data class Resource(
        val resource: StringResource,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Raw(val value: String) : UiText
}

fun uiText(resource: StringResource, vararg args: Any): UiText =
    UiText.Resource(resource = resource, args = args.toList())

fun rawText(value: String): UiText = UiText.Raw(value)

@Composable
fun UiText.asString(): String {
    return when (this) {
        is UiText.Raw -> value
        is UiText.Resource -> stringResource(resource, *args.toTypedArray())
    }
}

suspend fun UiText.resolveString(): String {
    return when (this) {
        is UiText.Raw -> value
        is UiText.Resource -> getString(resource, *args.toTypedArray())
    }
}
