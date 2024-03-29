/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.rx

import io.reactivex.disposables.Disposable

class DisposableSet {
    private val disposables = mutableSetOf<Disposable>()

    fun add(disposable: Disposable) {
        disposables.add(disposable)
    }

    fun dispose() {
        disposables.forEach { it.dispose() }
        disposables.clear()
    }
}
