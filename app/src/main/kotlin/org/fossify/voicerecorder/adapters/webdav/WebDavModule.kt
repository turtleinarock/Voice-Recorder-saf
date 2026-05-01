package org.fossify.voicerecorder.adapters.webdav

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val storagePluginModuleWebDav = module {
    single { WebDavHandler(androidContext(), get(), get(), get()) }
}
