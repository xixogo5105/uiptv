package com.uiptv.util

import org.koin.core.context.GlobalContext

inline fun <reified T : Any> koinOrNull(): T? = runCatching { GlobalContext.get().get<T>() }.getOrNull()
