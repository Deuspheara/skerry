package com.twitchplayer.phone.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** A whole row as one button target: announced as a button and never smaller than the 48 dp touch target. */
fun Modifier.clickableItem(onClick: () -> Unit): Modifier = minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick)
