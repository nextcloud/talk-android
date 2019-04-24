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

package com.nextcloud.talk.utils;

import com.nextcloud.talk.R;

import java.util.HashMap;
import java.util.Map;

public class DrawableUtils {


    public static int getDrawableResourceIdForMimeType(String mimetype) {
        Map<String, Integer> drawableMap = new HashMap<>();

        // Initial list of mimetypes was acquired from https://github.com/nextcloud/server/blob/694ba5435b2963e201f6a6d2c775836bde07aaef/core/js/mimetypelist.js
        drawableMap.put("application/coreldraw", R.drawable.ic_mimetype_image);
        drawableMap.put("application/epub+zip", R.drawable.ic_mimetype_text);
        drawableMap.put("application/font-sfnt", R.drawable.ic_mimetype_image);
        drawableMap.put("application/font-woff", R.drawable.ic_mimetype_image);
        drawableMap.put("application/gpx+xml", R.drawable.ic_mimetype_location);
        drawableMap.put("application/illustrator", R.drawable.ic_mimetype_image);
        drawableMap.put("application/javascript", R.drawable.ic_mimetype_text_code);
        drawableMap.put("application/json", R.drawable.ic_mimetype_text_code);
        drawableMap.put("application/msaccess", R.drawable.ic_mimetype_file);
        drawableMap.put("application/msexcel", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/msonenote", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/mspowerpoint", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/msword", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/octet-stream", R.drawable.ic_mimetype_file);
        drawableMap.put("application/postscript", R.drawable.ic_mimetype_image);
        drawableMap.put("application/rss+xml", R.drawable.ic_mimetype_text_code);
        drawableMap.put("application/vnd.android.package-archive", R.drawable.ic_mimetype_package_x_generic);
        drawableMap.put("application/vnd.lotus-wordpro", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.garmin.tcx+xml", R.drawable.ic_mimetype_location);
        drawableMap.put("application/vnd.google-earth.kml+xml", R.drawable.ic_mimetype_location);
        drawableMap.put("application/vnd.google-earth.kmz", R.drawable.ic_mimetype_location);
        drawableMap.put("application/vnd.ms-excel", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/vnd.ms-excel.addin.macroEnabled.12", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/vnd.ms-excel.sheet.binary.macroEnabled.12", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/vnd.ms-excel.sheet.macroEnabled.12", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/vnd.ms-excel.template.macroEnabled.12", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/vnd.ms-fontobject", R.drawable.ic_mimetype_image);
        drawableMap.put("application/vnd.ms-powerpoint", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/vnd.ms-powerpoint.addin.macroEnabled.12", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/vnd.ms-powerpoint.presentation.macroEnabled.12", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/vnd.ms-powerpoint.slideshow.macroEnabled.12", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/vnd.ms-powerpoint.template.macroEnabled.12", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/vnd.ms-visio.drawing.macroEnabled.12", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.ms-visio.drawing", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.ms-visio.stencil.macroEnabled.12", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.ms-visio.stencil", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.ms-visio.template.macroEnabled.12", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.ms-visio.template", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.ms-word.template.macroEnabled.12", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.oasis.opendocument.presentation", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/vnd.oasis.opendocument.presentation-template", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/vnd.oasis.opendocument.spreadsheet", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/vnd.oasis.opendocument.spreadsheet-template", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/vnd.oasis.opendocument.text", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.oasis.opendocument.text-master", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.oasis.opendocument.text-template", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.oasis.opendocument.text-web", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/vnd.openxmlformats-officedocument.presentationml.slideshow", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/vnd.openxmlformats-officedocument.presentationml.template", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/vnd.openxmlformats-officedocument.spreadsheetml.template", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.openxmlformats-officedocument.wordprocessingml.template", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.visio", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/vnd.wordperfect", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/x-7z-compressed", R.drawable.ic_mimetype_package_x_generic);
        drawableMap.put("application/x-bzip2", R.drawable.ic_mimetype_package_x_generic);
        drawableMap.put("application/x-cbr", R.drawable.ic_mimetype_text);
        drawableMap.put("application/x-compressed", R.drawable.ic_mimetype_package_x_generic);
        drawableMap.put("application/x-dcraw", R.drawable.ic_mimetype_image);
        drawableMap.put("application/x-deb", R.drawable.ic_mimetype_package_x_generic);
        drawableMap.put("application/x-fictionbook+xml", R.drawable.ic_mimetype_text);
        drawableMap.put("application/x-font", R.drawable.ic_mimetype_image);
        drawableMap.put("application/x-gimp", R.drawable.ic_mimetype_image);
        drawableMap.put("application/x-gzip", R.drawable.ic_mimetype_package_x_generic);
        drawableMap.put("application/x-iwork-keynote-sffkey", R.drawable.ic_mimetype_x_office_presentation);
        drawableMap.put("application/x-iwork-numbers-sffnumbers", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("application/x-iwork-pages-sffpages", R.drawable.ic_mimetype_x_office_document);
        drawableMap.put("application/x-mobipocket-ebook", R.drawable.ic_mimetype_text);
        drawableMap.put("application/x-perl", R.drawable.ic_mimetype_text_code);
        drawableMap.put("application/x-photoshop", R.drawable.ic_mimetype_image);
        drawableMap.put("application/x-php", R.drawable.ic_mimetype_text_code);
        drawableMap.put("application/x-rar-compressed", R.drawable.ic_mimetype_package_x_generic);
        drawableMap.put("application/x-tar", R.drawable.ic_mimetype_package_x_generic);
        drawableMap.put("application/x-tex", R.drawable.ic_mimetype_text);
        drawableMap.put("application/xml", R.drawable.ic_mimetype_text_code);
        drawableMap.put("application/yaml", R.drawable.ic_mimetype_text_code);
        drawableMap.put("application/zip", R.drawable.ic_mimetype_package_x_generic);
        drawableMap.put("database", R.drawable.ic_mimetype_file);
        drawableMap.put("httpd/unix-directory", R.drawable.ic_mimetype_folder);
        drawableMap.put("text/css", R.drawable.ic_mimetype_text_code);
        drawableMap.put("text/csv", R.drawable.ic_mimetype_x_office_spreadsheet);
        drawableMap.put("text/html", R.drawable.ic_mimetype_text_code);
        drawableMap.put("text/x-c", R.drawable.ic_mimetype_text_code);
        drawableMap.put("text/x-c++src", R.drawable.ic_mimetype_text_code);
        drawableMap.put("text/x-h", R.drawable.ic_mimetype_text_code);
        drawableMap.put("text/x-java-source", R.drawable.ic_mimetype_text_code);
        drawableMap.put("text/x-ldif", R.drawable.ic_mimetype_text_code);
        drawableMap.put("text/x-python", R.drawable.ic_mimetype_text_code);
        drawableMap.put("text/x-shellscript", R.drawable.ic_mimetype_text_code);
        drawableMap.put("web", R.drawable.ic_mimetype_text_code);
        drawableMap.put("application/internet-shortcut", R.drawable.ic_mimetype_link);

        drawableMap.put("inode/directory", R.drawable.ic_mimetype_folder);
        drawableMap.put("unknown", R.drawable.ic_mimetype_file);
        drawableMap.put("application/pdf", R.drawable.ic_mimetype_application_pdf);

        if ("DIR".equals(mimetype)) {
            mimetype = "inode/directory";
            return drawableMap.get(mimetype);
        }

        if (drawableMap.containsKey(mimetype)) {
            return drawableMap.get(mimetype);
        }

        if (mimetype.startsWith("image/")) {
            return R.drawable.ic_mimetype_image;
        }

        if (mimetype.startsWith("video/")) {
            return R.drawable.ic_mimetype_video;
        }

        if (mimetype.startsWith("text/")) {
            return R.drawable.ic_mimetype_text;
        }

        if (mimetype.startsWith("audio")) {
            return R.drawable.ic_mimetype_audio;
        }

        return drawableMap.get("unknown");
    }

}
