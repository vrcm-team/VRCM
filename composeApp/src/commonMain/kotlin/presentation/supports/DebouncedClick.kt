package io.github.vrcmteam.vrcm.presentation.supports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEBOUNCE_TIME_MILLIS = 5000L

/**
 * 防抖动点击事件
 */
@Composable
fun debouncedClick(
    onClick: suspend () -> Unit,
): () -> Unit {
    var lastJob:Boolean by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val newOnClick: () -> Unit = {
        if (lastJob){
            lastJob = false
            scope.launch {
                onClick()
                delay(DEBOUNCE_TIME_MILLIS)
                lastJob = true
            }
        }
    }
//    var lastJob:Job? by remember { mutableStateOf(null) }
//    val scope = rememberCoroutineScope()
//    val newOnClick: () -> Unit = {
//        lastJob?.cancel()
//        lastJob = scope.launch {
//            onClick()
//            delay(DEBOUNCE_TIME_MILLIS)
//            lastJob = null
//        }
//    }
    return newOnClick
}