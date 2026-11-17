/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.dagger.modules

import javax.inject.Qualifier

/** The dispatcher for blocking I/O, replaceable in tests. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
