package com.twitchplayer.phone

import androidx.media3.common.PlaybackException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.feature.player.ClipFailure
import com.twitchplayer.phone.feature.player.ReplayError
import com.twitchplayer.phone.feature.player.toClipFailure
import com.twitchplayer.phone.feature.player.toReplayError
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** `PlaybackException` reads the Android clock when built, so these run on the emulator rather than in the JVM unit tests. */
@RunWith(AndroidJUnit4::class)
class PlaybackErrorMappingTest {
    private fun error(code: Int) = PlaybackException("x", null, code)

    @Test fun aNetworkErrorFromThePlayerIsToldApartFromAFileProblem() {
        assertEquals(ReplayError.Network, error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED).toReplayError())
        assertEquals(ReplayError.Network, error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT).toReplayError())
        assertEquals(ReplayError.Playback, error(PlaybackException.ERROR_CODE_DECODING_FAILED).toReplayError())
        assertEquals(ReplayError.Playback, error(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS).toReplayError())
    }

    @Test fun aClipThatCannotBeResolvedIsNetworkOrUnavailableLikeTheReplayPath() {
        assertEquals(ClipFailure.Network, IOException("down").toClipFailure())
        assertEquals(ClipFailure.Unavailable, IllegalStateException("gone").toClipFailure())
    }

    @Test fun aClipTheNetworkDroppedMidPlaybackOffersTheNetworkMessage() {
        assertEquals(ClipFailure.Network, error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED).toClipFailure())
        assertEquals(ClipFailure.Unavailable, error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED).toClipFailure())
    }
}
