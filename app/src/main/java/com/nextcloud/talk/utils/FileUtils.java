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
}
