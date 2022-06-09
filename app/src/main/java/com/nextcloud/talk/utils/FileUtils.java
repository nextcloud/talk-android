/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Stefan Niedermann
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Stefan Niedermann <info@niedermann.it>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import androidx.annotation.NonNull;

public class FileUtils {
    private static final String TAG = FileUtils.class.getSimpleName();

    /**
     * Creates a new {@link File}
     */
    public static File getTempCacheFile(@NonNull Context context, String fileName) throws IOException {
        File cacheFile = new File(context.getApplicationContext().getFilesDir().getAbsolutePath() + "/" + fileName);

        Log.v(TAG, "Full path for new cache file:" + cacheFile.getAbsolutePath());

        final File tempDir = cacheFile.getParentFile();
        if (tempDir == null) {
            throw new FileNotFoundException("could not cacheFile.getParentFile()");
        }
        if (!tempDir.exists()) {
            Log.v(TAG,
                  "The folder in which the new file should be created does not exist yet. Trying to create it…");
            if (tempDir.mkdirs()) {
                Log.v(TAG, "Creation successful");
            } else {
                throw new IOException("Directory for temporary file does not exist and could not be created.");
            }
        }

        Log.v(TAG, "- Try to create actual cache file");
        if (cacheFile.createNewFile()) {
            Log.v(TAG, "Successfully created cache file");
        } else {
            throw new IOException("Failed to create cacheFile");
        }

        return cacheFile;
    }

    /**
     * Creates a new {@link File}
     */
    public static void removeTempCacheFile(@NonNull Context context, String fileName) throws IOException {
        File cacheFile = new File(context.getApplicationContext().getFilesDir().getAbsolutePath() + "/" + fileName);

        Log.v(TAG, "Full path for new cache file:" + cacheFile.getAbsolutePath());

        if (cacheFile.exists()) {
            if(cacheFile.delete()) {
                Log.v(TAG, "Deletion successful");
            } else {
                throw new IOException("Directory for temporary file does not exist and could not be created.");
            }
        }
    }
}
