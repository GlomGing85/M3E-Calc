package com.flexteam.m3ecalc.ui

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView

/**
 * Material Symbols Rounded.
 *
 * The real Google icon font is bundled as a subset (see tools/make-font.py):
 * the variable WOFF2 from the `material-symbols` npm package, pinned to
 * wght 400 / opsz 24 and trimmed to the glyphs this app draws. Icons are set
 * with [text] on any TextView and render exactly like the web font.
 */
object Icons {

    private var cached: Typeface? = null

    fun typeface(context: Context): Typeface =
        cached ?: Typeface.createFromAsset(context.assets, "fonts/MaterialSymbolsRounded.ttf")
            .also { cached = it }

    /** Sets the glyph as text and switches the view to the symbol typeface. */
    fun TextView.icon(glyph: String, sizeSp: Float) {
        typeface = typeface(context)
        textSize = sizeSp
        text = glyph
        includeFontPadding = false
    }

    const val ADD = "\ue145"
    const val ANDROID = "\ue859"
    const val ARROW_BACK = "\ue5c4"
    const val BACKSPACE = "\ue14a"
    const val BOLT = "\uea0b"
    const val BRIGHTNESS_AUTO = "\ue1ab"
    const val CALCULATE = "\uea5f"
    const val CANCEL = "\ue5c9"
    const val CHECK = "\ue5ca"
    const val CHECK_CIRCLE = "\ue86c"
    const val CHEVRON_RIGHT = "\ue409"
    const val CLOSE = "\ue14c"
    const val CLOUD_OFF = "\ue2c1"
    const val CODE = "\ue86f"
    const val CONTENT_COPY = "\ue14d"
    const val CURRENCY_EXCHANGE = "\ueb70"
    const val DARK_MODE = "\ue51c"
    const val DELETE = "\ue872"
    const val DEPLOYED_CODE = "\uf720"
    const val DESCRIPTION = "\ue873"
    const val DEVICE_THERMOSTAT = "\ue1ff"
    const val DOWNLOAD = "\ue171"
    const val DOWNLOAD_DONE = "\ue9aa"
    const val DOWNLOADING = "\uf001"
    const val DRAG_HANDLE = "\ue25d"
    const val EQUAL = "\uf77b"
    const val ERROR = "\ue000"
    const val FAVORITE = "\ue87d"
    const val FOLDER_OPEN = "\ue2c8"
    const val FUNCTIONS = "\ue24a"
    const val GRID_VIEW = "\ue9b0"
    const val GROUPS = "\uf233"
    const val HANDSHAKE = "\uebcb"
    const val HISTORY = "\ue28e"
    const val HISTORY_2 = "\uf3e6"
    const val INFO = "\ue88e"
    const val KEYBOARD_ARROW_DOWN = "\ue313"
    const val KEYBOARD_ARROW_UP = "\ue316"
    const val LANGUAGE = "\ue894"
    const val LIGHT_MODE = "\ue518"
    const val LINK = "\ue157"
    const val LIST = "\ue896"
    const val MEMORY = "\ue322"
    const val MONITOR_WEIGHT = "\uf039"
    const val MORE_VERT = "\ue5d4"
    const val NUMBERS = "\ueac7"
    const val OPEN_IN_NEW = "\ue895"
    const val PALETTE = "\ue3b7"
    const val PERCENT = "\ueb58"
    const val PIN = "\uf045"
    const val REFRESH = "\ue5d5"
    const val REMOVE = "\ue15b"
    const val SCHEDULE = "\ue192"
    const val SCIENCE = "\uea4b"
    const val SEARCH = "\ue8b6"
    const val SETTINGS = "\ue8b8"
    const val SHARE = "\ue80d"
    const val SORT = "\ue164"
    const val SQUARE_FOOT = "\uea49"
    const val STAR = "\ue838"
    const val STORAGE = "\ue1db"
    const val STRAIGHTEN = "\ue41c"
    const val SWAP_VERT = "\ue0c3"
    const val THERMOSTAT = "\uf076"
    const val TRANSLATE = "\ue8e2"
    const val TUNE = "\ue429"
    const val UNFOLD_MORE = "\ue5d7"
    const val UPDATE = "\ue923"
    const val VOLUNTEER_ACTIVISM = "\uea70"
    const val WEIGHT = "\ue13d"
    const val WIFI_OFF = "\ue648"
}
