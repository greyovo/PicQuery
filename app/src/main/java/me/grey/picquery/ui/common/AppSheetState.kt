/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package me.grey.picquery.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppBottomSheetState(): AppBottomSheetState {
    val isVisibleState = rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    return AppBottomSheetState(sheetState, isVisibleState)
}

@OptIn(ExperimentalMaterial3Api::class)
class AppBottomSheetState(
    val sheetState: SheetState,
    private val isVisibleState: MutableState<Boolean>
) {

    val isVisible: Boolean
        get() = isVisibleState.value

    suspend fun show() {
        if (!isVisible) {
            isVisibleState.value = true
            delay(10)
            sheetState.show()
        }
    }

    suspend fun hide() {
        if (isVisible) {
            sheetState.hide()
            delay(10)
            isVisibleState.value = false
        }
    }
}
