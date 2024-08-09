/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.sync

import android.os.Bundle
import android.util.Log
import com.nextcloud.talk.data.changeListVersion.SyncableModel
import kotlin.coroutines.cancellation.CancellationException

/**
 * Interface marker for a class that manages synchronization between local data and a remote
 * source for a [Syncable].
 */
interface Synchronizer {

    // TODO include any other helper functions here that the Synchronizer needs

    /**
     * Syntactic sugar to call [Syncable.syncWith] while omitting the synchronizer argument
     */
    suspend fun Syncable.sync(bundle: Bundle) = this@sync.syncWith(bundle, this@Synchronizer)
}

/**
 * Interface marker for a class that is synchronized with a remote source. Syncing must not be
 * performed concurrently and it is the [Synchronizer]'s responsibility to ensure this.
 */
interface Syncable {
    /**
     * Synchronizes the local database backing the repository with the network.
     * Takes in a [bundle] to retrieve other metadata needed
     *
     * Returns if the sync was successful or not.
     */
    suspend fun syncWith(bundle: Bundle, synchronizer: Synchronizer): Boolean
}

/**
 * Attempts [block], returning a successful [Result] if it succeeds, otherwise a [Result.Failure]
 * taking care not to break structured concurrency
 */
private suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (exception: Exception) {
        Log.e(
            "suspendRunCatching",
            "Failed to evaluate a suspendRunCatchingBlock. Returning failure Result",
            exception
        )
        Result.failure(exception)
    }

/**
 * Utility function for syncing a repository with the network.
 * [modelFetcher] Fetches the change list for the model
 * [versionUpdater] Updates the version after a successful sync
 * [modelDeleter] Deletes models by consuming the ids of the models that have been deleted.
 * [modelUpdater] Updates models by consuming the ids of the models that have changed.
 *
 * Note that the blocks defined above are never run concurrently, and the [Synchronizer]
 * implementation must guarantee this.
 */
suspend fun Synchronizer.changeListSync(
    modelFetcher: suspend () -> List<SyncableModel>,
    versionUpdater: (Long) -> Unit,
    modelDeleter: suspend (List<Long>) -> Unit,
    modelUpdater: suspend (List<SyncableModel>) -> Unit
) = suspendRunCatching {
    // Fetch the change list since last sync (akin to a git fetch)
    val changeList = modelFetcher()
    if (changeList.isEmpty()) return@suspendRunCatching true

    // Splits the models marked for deletion from the ones that are updated or new
    val (deleted, updated) = changeList.partition(SyncableModel::markedForDeletion)

    // Delete models that have been deleted server-side
    modelDeleter(deleted.map(SyncableModel::id))

    // Using the fetch list, pull down and upsert the changes (akin to a git pull)
    modelUpdater(updated)

    // Update the last synced version (akin to updating local git HEAD)
    val latestVersion = changeList.last().id
    versionUpdater(latestVersion)
}.isSuccess
