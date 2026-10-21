package com.wledcontroller.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wledcontroller.app.data.LightType

/**
 * "Best for" chips: pick a kind of light to see only the effects that suit it (tap it again for
 * all of them). [mine] is starred: the kind of lights these are, when known.
 */
@Composable
fun LightTypeFilter(selected: LightType?, onSelect: (LightType?) -> Unit, modifier: Modifier = Modifier, mine: LightType? = null) {
    Column(modifier) {
        Text("Best for", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Any lights") })
            LightType.entries.forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = { onSelect(if (selected == type) null else type) },
                    label = { Text(type.label + if (type == mine) " ★" else "") },
                )
            }
        }
        selected?.let {
            Text(
                it.description + if (it == mine) " (★ the kind of lights these are)" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
