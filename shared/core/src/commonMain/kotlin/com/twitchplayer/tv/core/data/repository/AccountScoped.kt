package com.twitchplayer.tv.core.data.repository

/** Something that keeps data in memory for the account and must forget it when the session ends (the disk copies go with the snapshots). */
interface AccountScoped {
    suspend fun forgetAccount()
}
