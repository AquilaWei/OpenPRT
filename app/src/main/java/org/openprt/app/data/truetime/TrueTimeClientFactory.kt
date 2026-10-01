package org.openprt.app.data.truetime

import org.openprt.app.BuildConfig

/**
 * A client using the PRT_API_KEY from local.properties. When the key was not set at build
 * time every call returns [TrueTimeError.MissingApiKey].
 */
fun TrueTimeClient.Companion.fromBuildConfig(): TrueTimeClient =
    TrueTimeClient(apiKey = BuildConfig.PRT_API_KEY)
