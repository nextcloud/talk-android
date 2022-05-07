/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017/2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.utils

import com.nextcloud.talk.R
import java.util.HashMap

object DrawableUtils {

    fun getDrawableResourceIdForMimeType(mimetype: String?): Int {
        var localMimetype = mimetype
        val drawableMap = HashMap<String, Int>()

        // Initial list of mimetypes was acquired from https://github.com/nextcloud/server/blob/694ba5435b2963e201f6a6d2c775836bde07aaef/core/js/mimetypelist.js
        drawableMap["application/coreldraw"] = R.drawable.ic_mimetype_image
        drawableMap["application/epub+zip"] = R.drawable.ic_mimetype_text
        drawableMap["application/font-sfnt"] = R.drawable.ic_mimetype_image
        drawableMap["application/font-woff"] = R.drawable.ic_mimetype_image
        drawableMap["application/gpx+xml"] = R.drawable.ic_mimetype_location
        drawableMap["application/illustrator"] = R.drawable.ic_mimetype_image
        drawableMap["application/javascript"] = R.drawable.ic_mimetype_text_code
        drawableMap["application/json"] = R.drawable.ic_mimetype_text_code
        drawableMap["application/msaccess"] = R.drawable.ic_mimetype_file
        drawableMap["application/msexcel"] = R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/msonenote"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/mspowerpoint"] = R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/msword"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/octet-stream"] = R.drawable.ic_mimetype_file
        drawableMap["application/postscript"] = R.drawable.ic_mimetype_image
        drawableMap["application/rss+xml"] = R.drawable.ic_mimetype_text_code
        drawableMap["application/vnd.android.package-archive"] = R.drawable.ic_mimetype_package_x_generic
        drawableMap["application/vnd.lotus-wordpro"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.garmin.tcx+xml"] = R.drawable.ic_mimetype_location
        drawableMap["application/vnd.google-earth.kml+xml"] = R.drawable.ic_mimetype_location
        drawableMap["application/vnd.google-earth.kmz"] = R.drawable.ic_mimetype_location
        drawableMap["application/vnd.ms-excel"] = R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/vnd.ms-excel.addin.macroEnabled.12"] = R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/vnd.ms-excel.sheet.binary.macroEnabled.12"] =
            R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/vnd.ms-excel.sheet.macroEnabled.12"] = R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/vnd.ms-excel.template.macroEnabled.12"] = R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/vnd.ms-fontobject"] = R.drawable.ic_mimetype_image
        drawableMap["application/vnd.ms-powerpoint"] = R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/vnd.ms-powerpoint.addin.macroEnabled.12"] =
            R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/vnd.ms-powerpoint.presentation.macroEnabled.12"] =
            R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/vnd.ms-powerpoint.slideshow.macroEnabled.12"] =
            R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/vnd.ms-powerpoint.template.macroEnabled.12"] =
            R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/vnd.ms-visio.drawing.macroEnabled.12"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.ms-visio.drawing"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.ms-visio.stencil.macroEnabled.12"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.ms-visio.stencil"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.ms-visio.template.macroEnabled.12"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.ms-visio.template"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.ms-word.template.macroEnabled.12"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.oasis.opendocument.presentation"] = R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/vnd.oasis.opendocument.presentation-template"] =
            R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/vnd.oasis.opendocument.spreadsheet"] = R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/vnd.oasis.opendocument.spreadsheet-template"] =
            R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/vnd.oasis.opendocument.text"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.oasis.opendocument.text-master"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.oasis.opendocument.text-template"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.oasis.opendocument.text-web"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.openxmlformats-officedocument.presentationml.presentation"] =
            R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/vnd.openxmlformats-officedocument.presentationml.slideshow"] =
            R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/vnd.openxmlformats-officedocument.presentationml.template"] =
            R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"] =
            R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/vnd.openxmlformats-officedocument.spreadsheetml.template"] =
            R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/vnd.openxmlformats-officedocument.wordprocessingml.document"] =
            R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.openxmlformats-officedocument.wordprocessingml.template"] =
            R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.visio"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/vnd.wordperfect"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/x-7z-compressed"] = R.drawable.ic_mimetype_package_x_generic
        drawableMap["application/x-bzip2"] = R.drawable.ic_mimetype_package_x_generic
        drawableMap["application/x-cbr"] = R.drawable.ic_mimetype_text
        drawableMap["application/x-compressed"] = R.drawable.ic_mimetype_package_x_generic
        drawableMap["application/x-dcraw"] = R.drawable.ic_mimetype_image
        drawableMap["application/x-deb"] = R.drawable.ic_mimetype_package_x_generic
        drawableMap["application/x-fictionbook+xml"] = R.drawable.ic_mimetype_text
        drawableMap["application/x-font"] = R.drawable.ic_mimetype_image
        drawableMap["application/x-gimp"] = R.drawable.ic_mimetype_image
        drawableMap["application/x-gzip"] = R.drawable.ic_mimetype_package_x_generic
        drawableMap["application/x-iwork-keynote-sffkey"] = R.drawable.ic_mimetype_x_office_presentation
        drawableMap["application/x-iwork-numbers-sffnumbers"] = R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["application/x-iwork-pages-sffpages"] = R.drawable.ic_mimetype_x_office_document
        drawableMap["application/x-mobipocket-ebook"] = R.drawable.ic_mimetype_text
        drawableMap["application/x-perl"] = R.drawable.ic_mimetype_text_code
        drawableMap["application/x-photoshop"] = R.drawable.ic_mimetype_image
        drawableMap["application/x-php"] = R.drawable.ic_mimetype_text_code
        drawableMap["application/x-rar-compressed"] = R.drawable.ic_mimetype_package_x_generic
        drawableMap["application/x-tar"] = R.drawable.ic_mimetype_package_x_generic
        drawableMap["application/x-tex"] = R.drawable.ic_mimetype_text
        drawableMap["application/xml"] = R.drawable.ic_mimetype_text_code
        drawableMap["application/yaml"] = R.drawable.ic_mimetype_text_code
        drawableMap["application/zip"] = R.drawable.ic_mimetype_package_x_generic
        drawableMap["database"] = R.drawable.ic_mimetype_file
        drawableMap["httpd/unix-directory"] = R.drawable.ic_mimetype_folder
        drawableMap["text/css"] = R.drawable.ic_mimetype_text_code
        drawableMap["text/csv"] = R.drawable.ic_mimetype_x_office_spreadsheet
        drawableMap["text/html"] = R.drawable.ic_mimetype_text_code
        drawableMap["text/vcard"] = R.drawable.ic_mimetype_text_vcard
        drawableMap["text/x-c"] = R.drawable.ic_mimetype_text_code
        drawableMap["text/x-c++src"] = R.drawable.ic_mimetype_text_code
        drawableMap["text/x-h"] = R.drawable.ic_mimetype_text_code
        drawableMap["text/x-java-source"] = R.drawable.ic_mimetype_text_code
        drawableMap["text/x-ldif"] = R.drawable.ic_mimetype_text_code
        drawableMap["text/x-python"] = R.drawable.ic_mimetype_text_code
        drawableMap["text/x-shellscript"] = R.drawable.ic_mimetype_text_code
        drawableMap["web"] = R.drawable.ic_mimetype_text_code
        drawableMap["application/internet-shortcut"] = R.drawable.ic_mimetype_link

        drawableMap["inode/directory"] = R.drawable.ic_mimetype_folder
        drawableMap["unknown"] = R.drawable.ic_mimetype_file
        drawableMap["application/pdf"] = R.drawable.ic_mimetype_application_pdf

        if (localMimetype.isNullOrEmpty()) {
            return drawableMap["unknown"]!!
        }

        if ("DIR" == localMimetype) {
            localMimetype = "inode/directory"
            return drawableMap[localMimetype]!!
        }

        if (drawableMap.containsKey(localMimetype)) {
            return drawableMap[localMimetype]!!
        }

        if (localMimetype.startsWith("image/")) {
            return R.drawable.ic_mimetype_image
        }

        if (localMimetype.startsWith("video/")) {
            return R.drawable.ic_mimetype_video
        }

        if (localMimetype.startsWith("text/")) {
            return R.drawable.ic_mimetype_text
        }

        return if (localMimetype.startsWith("audio")) {
            R.drawable.ic_mimetype_audio
        } else drawableMap["unknown"]!!
    }
}
