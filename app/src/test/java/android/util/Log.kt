/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package android.util

/**
 * Dummy implementation of android.util.Log to be used in unit tests.
 *
 *
 * The Android Gradle plugin provides a library with the APIs of the Android framework that throws an exception if any
 * of them are called. This class is loaded before that library and therefore becomes the implementation used during the
 * tests, simply printing the messages to the system console.
 */
object Log {

    @JvmStatic
    fun d(tag: String, msg: String): Int {
        println("DEBUG: $tag: $msg")

        return 1
    }

    @JvmStatic
    fun e(tag: String, msg: String): Int {
        println("ERROR: $tag: $msg")

        return 1
    }

    @JvmStatic
    fun i(tag: String, msg: String): Int {
        println("INFO: $tag: $msg")

        return 1
    }

    @JvmStatic
    fun isLoggable(tag: String?, level: Int): Boolean = true

    @JvmStatic
    fun v(tag: String, msg: String): Int {
        println("VERBOSE: $tag: $msg")

        return 1
    }

    @JvmStatic
    fun w(tag: String, msg: String): Int {
        println("WARN: $tag: $msg")

        return 1
    }
}
