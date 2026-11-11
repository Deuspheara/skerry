package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvAsyncImage
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.formatViewers
import com.twitchplayer.tv.feature.player.playback.LocalStreamPreview
import com.twitchplayer.tv.feature.player.playback.StreamPreviewLayer

/** 16:9 live card: thumbnail with LIVE + viewers, avatar and two-line title, channel · category. */
@Composable
fun StreamCard(
    stream: LiveStream,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = TvDimens.LiveCardWidth,
) {
    val description = stringResource(R.string.cd_stream, stream.name, formatViewers(stream.viewers), stream.categoryName)
    val subtitle = remember(stream.name, stream.categoryName) { "${stream.name} · ${stream.categoryName}" }
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Card,
        focusScale = TvDimens.FOCUS_SCALE_CARD,
        description = description,
        modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()),
    ) { focus ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(TvShapes.Card)) {
                TvAsyncImage(stream.thumbnailUrl, null, TvDimens.LiveCardWidth, 113.dp, Modifier.matchParentSize())
                // After a second of focus: a muted low quality live preview fades in over the thumbnail.
                LocalStreamPreview.current?.let { StreamPreviewLayer(stream.login, focus, it, Modifier.matchParentSize()) }
                LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
                ViewerCount(stream.viewers, Modifier.align(Alignment.BottomStart).padding(6.dp), overlay = true)
            }
            Row(Modifier.padding(end = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(stream.name, stream.avatarUrl, size = TvDimens.AvatarCard)
                Column(
                    Modifier.weight(1f).graphicsLayer {
                        // Focus reveals the text: resting cards step back a little so the focused one reads first.
                        alpha = REST_TEXT_ALPHA + (1f - REST_TEXT_ALPHA) * focus.value
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
                ) {
                    Text(
                        stream.title,
                        style = TvType.Title,
                        color = TvColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        minLines = 2,
                    )
                    Text(subtitle, style = TvType.Meta, color = TvColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private const val REST_TEXT_ALPHA = 0.8f
