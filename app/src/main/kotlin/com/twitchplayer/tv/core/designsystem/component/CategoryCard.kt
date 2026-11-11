package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvAsyncImage
import com.twitchplayer.tv.core.ui.sharedArt
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.focusRing

/** 3:4 category poster with its name. Scale applies to the whole card, the ring to the poster only. */
@Composable
fun CategoryCard(
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = TvDimens.CategoryCardWidth,
) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Card,
        focusScale = TvDimens.FocusScalePoster,
        description = category.name,
        drawRing = false,
        modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()),
    ) { focus ->
        Column {
            TvAsyncImage(
                category.artUrl, null, TvDimens.CategoryCardWidth, 160.dp,
                Modifier.fillMaxWidth().aspectRatio(3f / 4f).focusRing(focus, TvShapes.Card).sharedArt("cat:${category.id}").clip(TvShapes.Card),
            )
            Text(
                category.name,
                style = TvType.Title,
                color = TvColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp),
            )
        }
    }
}
