package org.openprt.app.data.truetime

import org.openprt.app.data.settings.ApiKeySettings

/**
 * A client using the key in [settings]: the one the user entered in the app, else the
 * PRT_API_KEY from local.properties. Saving a new key takes effect on the next call; without any
 * key every call returns [TrueTimeError.MissingApiKey]. Each [feed] needs its own client.
 */
fun TrueTimeClient.Companion.fromSettings(
    settings: ApiKeySettings,
    feed: DataFeed = DataFeed.BUS
): TrueTimeClient = TrueTimeClient(apiKey = settings::currentKey, feed = feed)
