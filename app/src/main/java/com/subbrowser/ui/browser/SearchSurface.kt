package com.subbrowser.ui.browser

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subbrowser.ui.theme.SubSaffron

@Composable
fun SearchSurface(
    modifier: Modifier = Modifier
) {
    val searchState = rememberTextFieldState()

    OutlinedTextField(
        state = searchState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        placeholder = {
            Text(
                text = "Search or enter address"
            )
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SubSaffron,
            cursorColor = SubSaffron
        )
    )
}
