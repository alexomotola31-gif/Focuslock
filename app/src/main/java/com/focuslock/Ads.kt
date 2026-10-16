package com.focuslock

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object Ads {
    // Your real AdMob banner unit ID
    const val BANNER_ID = "ca-app-pub-8156993824451105/3521533832"
    // TEST BUILDS ONLY: shows a status line under the banner. Set to false before release.
    const val SHOW_STATUS = true
    var ready by mutableStateOf(false)
    var status by mutableStateOf("Ad status: checking consent...")
    private var started = false

    fun init(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                    if (err != null) status = "Ad status: consent form error: ${err.message}"
                    start(activity, info)
                }
            },
            { e ->
                status = "Ad status: consent check failed: ${e.message}"
                start(activity, info)
            }
        )
        start(activity, info)
    }

    private fun start(ctx: Context, info: ConsentInformation) {
        if (started || !info.canRequestAds()) return
        started = true
        status = "Ad status: starting Google Ads..."
        MobileAds.initialize(ctx) {
            ready = true
            status = "Ad status: requesting a banner..."
        }
    }
}

@Composable
fun NxAdBanner(unitId: String) {
    Column(Modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = unitId
                    adListener = object : AdListener() {
                        override fun onAdLoaded() { Ads.status = "Ad status: ad loaded" }
                        override fun onAdFailedToLoad(e: LoadAdError) {
                            Ads.status = "Ad status: no ad - ${e.message} (code ${e.code})"
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
        if (Ads.SHOW_STATUS) {
            Text(Ads.status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
