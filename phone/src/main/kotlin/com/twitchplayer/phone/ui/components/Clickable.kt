package com.twitchplayer.phone.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** A whole row as one button target (at least 48 dp tall through the list item's own padding). */
fun Modifier.clickableItem(onClick: () -> Unit): Modifier = clickable(role = Role.Button, onClick = onClick)
