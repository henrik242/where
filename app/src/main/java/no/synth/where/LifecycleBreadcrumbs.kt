package no.synth.where

import android.app.Activity
import android.app.Application
import android.os.Bundle
import no.synth.where.util.CrashReporter

/** Crash-report breadcrumbs for activity lifecycle, to place native crashes that carry no app frames. */
object LifecycleBreadcrumbs : Application.ActivityLifecycleCallbacks {
    private fun log(activity: Activity, event: String) {
        val orientation = activity.resources.configuration.orientation
        CrashReporter.log("${activity.javaClass.simpleName} $event (orientation=$orientation)")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
        log(activity, if (savedInstanceState != null) "recreated" else "created")

    override fun onActivityStarted(activity: Activity) = log(activity, "started")
    override fun onActivityResumed(activity: Activity) = log(activity, "resumed")
    override fun onActivityPaused(activity: Activity) = log(activity, "paused")
    override fun onActivityStopped(activity: Activity) = log(activity, "stopped")
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) =
        log(activity, if (activity.isChangingConfigurations) "destroyed for config change" else "destroyed")
}
