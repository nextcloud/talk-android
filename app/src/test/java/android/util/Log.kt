/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package android.util;

/**
 * Dummy implementation of android.util.Log to be used in unit tests.
 * <p>
 * The Android Gradle plugin provides a library with the APIs of the Android framework that throws an exception if any
 * of them are called. This class is loaded before that library and therefore becomes the implementation used during the
 * tests, simply printing the messages to the system console.
 */
public class Log {

    public static int d(String tag, String msg) {
        System.out.println("DEBUG: " + tag + ": " + msg);

        return 1;
    }

    public static int e(String tag, String msg) {
        System.out.println("ERROR: " + tag + ": " + msg);

        return 1;
    }

    public static int i(String tag, String msg) {
        System.out.println("INFO: " + tag + ": " + msg);

        return 1;
    }

    public static boolean isLoggable(String tag, int level) {
        return true;
    }

    public static int v(String tag, String msg) {
        System.out.println("VERBOSE: " + tag + ": " + msg);

        return 1;
    }

    public static int w(String tag, String msg) {
        System.out.println("WARN: " + tag + ": " + msg);

        return 1;
    }
}
