package no.synth.where

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import no.synth.where.data.followGroupLink
import no.synth.where.data.parseWhereGroupLink
import no.synth.where.di.AppDependencies

/** Universal Links from Swift's onOpenURL. AppDependencies is set up in WhereApp.init, before any URL arrives. */
object IosLinks {
    fun handleUrl(url: String) {
        val ids = parseWhereGroupLink(url) ?: return
        MainScope().launch {
            followGroupLink(
                AppDependencies.userPreferences, AppDependencies.liveTrackingFollower, ids,
                AppDependencies.clientIdManager.getClientId()
            )
        }
    }
}
