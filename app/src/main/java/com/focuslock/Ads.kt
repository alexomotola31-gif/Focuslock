package com.focuslock

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object Ads {
    // Google's TEST banner ID. Replace with your real AdMob banner unit ID before release.
    const val BANNER_ID = "ca-app-pub-8156993824451105/3521533832"
    var ready by mutableStateOf(false)
    private var started = false 

    fun init(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { start(activity, info) } },
            { start(activity, info) }
        )
        start(activity, info)
    }

    private fun start(ctx: Context, info: ConsentInformation) {
        if (started || !info.canRequestAds()) return
        started = true
        MobileAds.initialize(ctx) { ready = true }
    }
}

@Composable
fun NxAdBanner(unitId: String) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = unitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
