package rew.lightgames.sudoku2

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import rew.lightgames.sudoku2.BuildConfig

object ConsentManager {

    private const val TAG = "ConsentManager"

    private var consentInformation: ConsentInformation? = null
    private var adsInitialized = false
    private var canRequestAds = false

    fun canRequestAds(): Boolean = canRequestAds

    fun isPrivacyOptionsRequired(): Boolean {
        return consentInformation?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun initialize(activity: Activity, onReady: () -> Unit) {
        if (adsInitialized) {
            onReady()
            return
        }

        consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        val paramsBuilder = ConsentRequestParameters.Builder()

        if (BuildConfig.DEBUG) {
            val debugSettings = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(
                    ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                )
                .build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
        }

        val params = paramsBuilder.build()

        consentInformation!!.requestConsentInfoUpdate(
            activity,
            params,
            {
                Log.d(TAG, "Consent info updated successfully")
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    canRequestAds = consentInformation?.canRequestAds() == true
                    if (!adsInitialized) {
                        adsInitialized = true
                    }
                    onReady()
                }
            },
            { requestConsentError ->
                Log.w(TAG, "Consent info update failed: ${requestConsentError.message}")
                canRequestAds = consentInformation?.canRequestAds() == true
                if (!adsInitialized) {
                    adsInitialized = true
                }
                onReady()
            }
        )
    }

    fun showPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form error: ${formError.message}")
            }
        }
    }
}
