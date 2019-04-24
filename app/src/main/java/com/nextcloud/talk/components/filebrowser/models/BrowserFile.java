/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.components.filebrowser.models;

import android.net.Uri;
import android.text.TextUtils;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.Response;
import at.bitfire.dav4android.property.DisplayName;
import at.bitfire.dav4android.property.GetContentType;
import at.bitfire.dav4android.property.GetLastModified;
import at.bitfire.dav4android.property.ResourceType;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.components.filebrowser.models.properties.*;
import lombok.Data;
import org.parceler.Parcel;

import java.io.File;
import java.util.List;

@Data
@JsonObject
@Parcel
public class BrowserFile {
    public String path;
    public String displayName;
    public String mimeType;
    public long modifiedTimestamp;
    public long size;
    public boolean isFile;
    // Used for remote files
    public String remoteId;
    public boolean hasPreview;
    public boolean favorite;
    public boolean encrypted;

    public static BrowserFile getModelFromResponse(Response response, String remotePath) {
        BrowserFile browserFile = new BrowserFile();
        browserFile.setPath(Uri.decode(remotePath));
        browserFile.setDisplayName(Uri.decode(new File(remotePath).getName()));
        final List<Property> properties = response.getProperties();

        for (Property property : properties) {
            if (property instanceof OCId) {
                browserFile.setRemoteId(((OCId) property).getOcId());
            }

            if (property instanceof ResourceType) {
                browserFile.isFile =
                        !(((ResourceType) property).getTypes().contains(ResourceType.Companion.getCOLLECTION()));
            }

            if (property instanceof GetLastModified) {
                browserFile.setModifiedTimestamp(((GetLastModified) property).getLastModified());
            }

            if (property instanceof GetContentType) {
                browserFile.setMimeType(((GetContentType) property).getType());
            }

            if (property instanceof OCSize) {
                browserFile.setSize(((OCSize) property).getOcSize());
            }

            if (property instanceof NCPreview) {
                browserFile.setHasPreview(((NCPreview) property).isNcPreview());
            }

            if (property instanceof OCFavorite) {
                browserFile.setFavorite(((OCFavorite) property).isOcFavorite());
            }

            if (property instanceof DisplayName) {
                browserFile.setDisplayName(((DisplayName) property).getDisplayName());
            }

            if (property instanceof NCEncrypted) {
                browserFile.setEncrypted(((NCEncrypted) property).isNcEncrypted());
            }
        }

        if (TextUtils.isEmpty(browserFile.getMimeType()) && !browserFile.isFile()) {
            browserFile.setMimeType("inode/directory");
        }

        return browserFile;
    }
}
