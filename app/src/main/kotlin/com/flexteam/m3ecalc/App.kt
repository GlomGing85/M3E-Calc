package com.flexteam.m3ecalc

import android.content.Context
import com.flexteam.m3ecalc.data.AppDatabase
import com.flexteam.m3ecalc.data.Settings
import com.flexteam.m3ecalc.data.Units

/** Process-wide singletons, created once in [MainActivity]. */
object App {
    lateinit var settings: Settings
        private set
    lateinit var db: AppDatabase
        private set

    fun init(context: Context) {
        if (!::settings.isInitialized) settings = Settings(context)
        if (!::db.isInitialized) {
            db = AppDatabase(context)
            db.ensureRates(Units.defaultRates)
            if (db.meta("rates_updated") == null) {
                db.setMeta("rates_updated", System.currentTimeMillis().toString())
            }
        }
    }
}
