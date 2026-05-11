package com.uiptv.server

import com.uiptv.service.ImdbMetadataService
import org.koin.core.module.Module
import org.koin.dsl.module

fun imdbOverrideModule(imdbMetadataService: ImdbMetadataService): Module = module {
    single<ImdbMetadataService> { imdbMetadataService }
}
