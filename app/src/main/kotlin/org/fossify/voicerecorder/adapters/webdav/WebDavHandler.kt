package org.fossify.voicerecorder.adapters.webdav

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import org.fossify.voicerecorder.R
import org.fossify.voicerecorder.backend.BackendManager
import org.fossify.voicerecorder.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fossify.voicerecorder.core.backends.Backend
import org.fossify.voicerecorder.core.backends.BackendFactory
import org.fossify.voicerecorder.core.backends.webdav.WebDavConfig
import org.fossify.voicerecorder.core.backends.webdav.WebDavProperties
import java.io.IOException

internal sealed interface WebDavConfigState {
    object Empty : WebDavConfigState
    object Checking : WebDavConfigState
    class Success(
        val properties: WebDavProperties,
        val backend: Backend,
    ) : WebDavConfigState

    class Error(val e: Exception?) : WebDavConfigState
}

private val TAG = WebDavHandler::class.java.simpleName

internal class WebDavHandler(
    private val context: Context,
    private val backendFactory: BackendFactory,
    private val settingsManager: SettingsManager,
    private val backendManager: BackendManager,
) {

    companion object {
        fun createWebDavProperties(context: Context, config: WebDavConfig): WebDavProperties {
            val host = config.url.removePrefix("https://")
            return WebDavProperties(
                config = config,
                name = context.getString(R.string.storage_webdav_name, host),
            )
        }
    }

    private val mConfigState = MutableStateFlow<WebDavConfigState>(WebDavConfigState.Empty)
    val configState = mConfigState.asStateFlow()

    suspend fun onConfigReceived(config: WebDavConfig) {
        mConfigState.value = WebDavConfigState.Checking
        val backend = backendFactory.createWebDavBackend(config)
        try {
            if (backend.test()) {
                val properties = createWebDavProperties(context, config)
                mConfigState.value = WebDavConfigState.Success(properties, backend)
            } else {
                mConfigState.value = WebDavConfigState.Error(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error testing WebDAV config at ${config.url}", e)
            mConfigState.value = WebDavConfigState.Error(e)
        }
    }

    fun resetConfigState() {
        mConfigState.value = WebDavConfigState.Empty
    }

    /**
     * Searches if there's really a backup available in the given storage location.
     * Returns true if at least one was found and false otherwise.
     */
    @WorkerThread
    @Throws(IOException::class)
    suspend fun hasBackup(backend: Backend): Boolean {
        return backend.getAvailableBackupFileHandles().isNotEmpty()
    }

    fun save(properties: WebDavProperties) {
        settingsManager.saveWebDavConfig(properties.config)
    }

    @WorkerThread
    fun setPlugin(properties: WebDavProperties, backend: Backend) {
        backendManager.changePlugins(
            backend = backend,
            storageProperties = properties,
        )
    }
}
