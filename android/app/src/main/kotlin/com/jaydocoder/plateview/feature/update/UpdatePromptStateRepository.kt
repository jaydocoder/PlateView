package com.jaydocoder.plateview.feature.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jaydocoder.plateview.domain.update.AppUpdate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.updatePromptDataStore by preferencesDataStore("update_prompt_state")

interface UpdatePromptStateRepository {
    suspend fun handledVersion(userId: Long): String?
    suspend fun markHandled(userId: Long, versionName: String)
    suspend fun cacheForcedUpdate(userId: Long, update: AppUpdate)
    suspend fun cachedForcedUpdate(userId: Long): AppUpdate?
    suspend fun clearCachedForcedUpdate(userId: Long)
}

@Singleton
class DataStoreUpdatePromptStateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : UpdatePromptStateRepository {
    override suspend fun handledVersion(userId: Long): String? = context.updatePromptDataStore.data.first()[handledVersionKey(userId)]

    override suspend fun markHandled(userId: Long, versionName: String) {
        context.updatePromptDataStore.edit { it[handledVersionKey(userId)] = versionName }
    }

    override suspend fun cacheForcedUpdate(userId: Long, update: AppUpdate) {
        context.updatePromptDataStore.edit { preferences ->
            preferences[cachedVersionKey(userId)] = update.versionName
            preferences[cachedNotesKey(userId)] = update.releaseNotes
            preferences[cachedUrlsKey(userId)] = update.downloadUrls.joinToString(separator = "\n")
            preferences[cachedArchitectureKey(userId)] = update.architecture
            preferences[cachedArtifactNameKey(userId)] = update.artifactName
            update.sizeBytes?.let { preferences[cachedSizeKey(userId)] = it.toString() }
                ?: preferences.remove(cachedSizeKey(userId))
            update.sha256?.let { preferences[cachedShaKey(userId)] = it } ?: preferences.remove(cachedShaKey(userId))
        }
    }

    override suspend fun cachedForcedUpdate(userId: Long): AppUpdate? {
        val preferences = context.updatePromptDataStore.data.first()
        val versionName = preferences[cachedVersionKey(userId)] ?: return null
        val urls = preferences[cachedUrlsKey(userId)]?.lineSequence()?.filter(String::isNotBlank)?.toList().orEmpty()
        return urls.takeIf(List<String>::isNotEmpty)?.let {
            AppUpdate(
                versionName = versionName,
                releaseNotes = preferences[cachedNotesKey(userId)].orEmpty(),
                downloadUrls = it,
                sha256 = preferences[cachedShaKey(userId)],
                architecture = preferences[cachedArchitectureKey(userId)] ?: "universal",
                artifactName = preferences[cachedArtifactNameKey(userId)] ?: "PlateView-$versionName.apk",
                sizeBytes = preferences[cachedSizeKey(userId)]?.toLongOrNull(),
            )
        }
    }

    override suspend fun clearCachedForcedUpdate(userId: Long) {
        context.updatePromptDataStore.edit { preferences ->
            preferences.remove(cachedVersionKey(userId))
            preferences.remove(cachedNotesKey(userId))
            preferences.remove(cachedUrlsKey(userId))
            preferences.remove(cachedArchitectureKey(userId))
            preferences.remove(cachedArtifactNameKey(userId))
            preferences.remove(cachedSizeKey(userId))
            preferences.remove(cachedShaKey(userId))
        }
    }

    private fun handledVersionKey(userId: Long) = stringPreferencesKey("user_${userId}_handled_version")
    private fun cachedVersionKey(userId: Long) = stringPreferencesKey("user_${userId}_forced_version")
    private fun cachedNotesKey(userId: Long) = stringPreferencesKey("user_${userId}_forced_notes")
    private fun cachedUrlsKey(userId: Long) = stringPreferencesKey("user_${userId}_forced_urls")
    private fun cachedArchitectureKey(userId: Long) = stringPreferencesKey("user_${userId}_forced_architecture")
    private fun cachedArtifactNameKey(userId: Long) = stringPreferencesKey("user_${userId}_forced_artifact")
    private fun cachedSizeKey(userId: Long) = stringPreferencesKey("user_${userId}_forced_size")
    private fun cachedShaKey(userId: Long) = stringPreferencesKey("user_${userId}_forced_sha256")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdatePromptStateModule {
    @Binds
    @Singleton
    abstract fun bindUpdatePromptStateRepository(repository: DataStoreUpdatePromptStateRepository): UpdatePromptStateRepository
}
