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

import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.components.filebrowser.models.properties.NCEncrypted;
import com.nextcloud.talk.components.filebrowser.models.properties.NCPermission;
import com.nextcloud.talk.components.filebrowser.models.properties.NCPreview;
import com.nextcloud.talk.components.filebrowser.models.properties.OCFavorite;
import com.nextcloud.talk.components.filebrowser.models.properties.OCId;
import com.nextcloud.talk.components.filebrowser.models.properties.OCSize;

import org.parceler.Parcel;

import java.io.File;
import java.util.List;

import at.bitfire.dav4jvm.Property;
import at.bitfire.dav4jvm.Response;
import at.bitfire.dav4jvm.property.DisplayName;
import at.bitfire.dav4jvm.property.GetContentType;
import at.bitfire.dav4jvm.property.GetLastModified;
import at.bitfire.dav4jvm.property.ResourceType;

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
    public String permissions;
    private boolean isAllowedToReShare = false;

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

            if (property instanceof NCPermission) {
                browserFile.setPermissions(((NCPermission) property).getNcPermission());
            }
        }

        if (browserFile.getPermissions().contains("R")) {
            browserFile.isAllowedToReShare = true;
        }

        if (TextUtils.isEmpty(browserFile.getMimeType()) && !browserFile.isFile()) {
            browserFile.setMimeType("inode/directory");
        }

        return browserFile;
    }

    public String getPath() {
        return this.path;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public long getModifiedTimestamp() {
        return this.modifiedTimestamp;
    }

    public long getSize() {
        return this.size;
    }

    public boolean isFile() {
        return this.isFile;
    }

    public String getRemoteId() {
        return this.remoteId;
    }

    public boolean isHasPreview() {
        return this.hasPreview;
    }

    public boolean isFavorite() {
        return this.favorite;
    }

    public boolean isEncrypted() {
        return this.encrypted;
    }

    public String getPermissions() {
        return this.permissions;
    }

    public boolean isAllowedToReShare() {
        return this.isAllowedToReShare;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setModifiedTimestamp(long modifiedTimestamp) {
        this.modifiedTimestamp = modifiedTimestamp;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setFile(boolean isFile) {
        this.isFile = isFile;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    public void setHasPreview(boolean hasPreview) {
        this.hasPreview = hasPreview;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public void setAllowedToReShare(boolean isAllowedToReShare) {
        this.isAllowedToReShare = isAllowedToReShare;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof BrowserFile)) {
            return false;
        }
        final BrowserFile other = (BrowserFile) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$path = this.getPath();
        final Object other$path = other.getPath();
        if (this$path == null ? other$path != null : !this$path.equals(other$path)) {
            return false;
        }
        final Object this$displayName = this.getDisplayName();
        final Object other$displayName = other.getDisplayName();
        if (this$displayName == null ? other$displayName != null : !this$displayName.equals(other$displayName)) {
            return false;
        }
        final Object this$mimeType = this.getMimeType();
        final Object other$mimeType = other.getMimeType();
        if (this$mimeType == null ? other$mimeType != null : !this$mimeType.equals(other$mimeType)) {
            return false;
        }
        if (this.getModifiedTimestamp() != other.getModifiedTimestamp()) {
            return false;
        }
        if (this.getSize() != other.getSize()) {
            return false;
        }
        if (this.isFile() != other.isFile()) {
            return false;
        }
        final Object this$remoteId = this.getRemoteId();
        final Object other$remoteId = other.getRemoteId();
        if (this$remoteId == null ? other$remoteId != null : !this$remoteId.equals(other$remoteId)) {
            return false;
        }
        if (this.isHasPreview() != other.isHasPreview()) {
            return false;
        }
        if (this.isFavorite() != other.isFavorite()) {
            return false;
        }
        if (this.isEncrypted() != other.isEncrypted()) {
            return false;
        }
        final Object this$permissions = this.getPermissions();
        final Object other$permissions = other.getPermissions();
        if (this$permissions == null ? other$permissions != null : !this$permissions.equals(other$permissions)) {
            return false;
        }

        return this.isAllowedToReShare() == other.isAllowedToReShare();
    }

    protected boolean canEqual(final Object other) {
        return other instanceof BrowserFile;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $path = this.getPath();
        result = result * PRIME + ($path == null ? 43 : $path.hashCode());
        final Object $displayName = this.getDisplayName();
        result = result * PRIME + ($displayName == null ? 43 : $displayName.hashCode());
        final Object $mimeType = this.getMimeType();
        result = result * PRIME + ($mimeType == null ? 43 : $mimeType.hashCode());
        final long $modifiedTimestamp = this.getModifiedTimestamp();
        result = result * PRIME + (int) ($modifiedTimestamp >>> 32 ^ $modifiedTimestamp);
        final long $size = this.getSize();
        result = result * PRIME + (int) ($size >>> 32 ^ $size);
        result = result * PRIME + (this.isFile() ? 79 : 97);
        final Object $remoteId = this.getRemoteId();
        result = result * PRIME + ($remoteId == null ? 43 : $remoteId.hashCode());
        result = result * PRIME + (this.isHasPreview() ? 79 : 97);
        result = result * PRIME + (this.isFavorite() ? 79 : 97);
        result = result * PRIME + (this.isEncrypted() ? 79 : 97);
        final Object $permissions = this.getPermissions();
        result = result * PRIME + ($permissions == null ? 43 : $permissions.hashCode());
        result = result * PRIME + (this.isAllowedToReShare() ? 79 : 97);
        return result;
    }

    public String toString() {
        return "BrowserFile(path=" + this.getPath() + ", displayName=" + this.getDisplayName() + ", mimeType=" + this.getMimeType() + ", modifiedTimestamp=" + this.getModifiedTimestamp() + ", size=" + this.getSize() + ", isFile=" + this.isFile() + ", remoteId=" + this.getRemoteId() + ", hasPreview=" + this.isHasPreview() + ", favorite=" + this.isFavorite() + ", encrypted=" + this.isEncrypted() + ", permissions=" + this.getPermissions() + ", isAllowedToReShare=" + this.isAllowedToReShare() + ")";
    }
}
