package com.nahabit.tracker.core.ui

import androidx.compose.runtime.Immutable

@Immutable
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val messageResId: Int, val details: String? = null) : UiState<Nothing>
}
