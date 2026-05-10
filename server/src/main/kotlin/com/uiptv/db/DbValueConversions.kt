package com.uiptv.db

internal fun Boolean.asDbBoolean(): String = if (this) "1" else "0"
