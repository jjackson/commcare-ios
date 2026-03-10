package org.commcare.core.network

import okhttp3.OkHttpClient

interface HttpBuilderConfig {
    fun performCustomConfig(client: OkHttpClient.Builder): OkHttpClient.Builder
}
