package com.subbrowser.ui.browser

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subbrowser.ui.theme.SubSaffron

@Composable
fun SearchSurface(
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit
) {
    val state = rememberTextFieldState(value)

    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text("Search or enter address")
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SubSaffron,
            cursorColor = SubSaffron
        )
    )
}
