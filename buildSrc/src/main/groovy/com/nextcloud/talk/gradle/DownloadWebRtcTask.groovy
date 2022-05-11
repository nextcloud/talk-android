/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class DownloadWebRtcTask extends DefaultTask {
    @Input
    abstract Property<String> getVersion()

    @OutputFile
    File getLibFile() {
        return new File("${project.buildDir}/download/${getFileName()}")
    }

    private String getFileName() {
        def webRtcVersion = version.get()
        return "libwebrtc-${webRtcVersion}.aar"
    }

    private String getDownloadUrl() {
        def webRtcVersion = version.get()
        return "https://github.com/nextcloud-releases/talk-clients-webrtc/releases/download/${webRtcVersion}-RC1/${getFileName()}"
    }

    @TaskAction
    def run() {
        libFile.parentFile.mkdirs()
        if (!libFile.exists()) {
            new URL(getDownloadUrl()).withInputStream { downloadStream ->
                libFile.withOutputStream { fileOut ->
                    fileOut << downloadStream
                }
            }
        }
    }
}