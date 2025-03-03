/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.filebrowser.webdav;

import com.nextcloud.talk.filebrowser.models.properties.NCEncrypted;
import com.nextcloud.talk.filebrowser.models.properties.NCPermission;
import com.nextcloud.talk.filebrowser.models.properties.NCPreview;
import com.nextcloud.talk.filebrowser.models.properties.OCFavorite;
import com.nextcloud.talk.filebrowser.models.properties.OCId;
import com.nextcloud.talk.filebrowser.models.properties.OCSize;

import java.util.ArrayList;
import java.util.List;

import at.bitfire.dav4jvm.Property;
import at.bitfire.dav4jvm.PropertyRegistry;
import at.bitfire.dav4jvm.property.CreationDate;
import at.bitfire.dav4jvm.property.DisplayName;
import at.bitfire.dav4jvm.property.GetContentLength;
import at.bitfire.dav4jvm.property.GetContentType;
import at.bitfire.dav4jvm.property.GetETag;
import at.bitfire.dav4jvm.property.GetLastModified;
import at.bitfire.dav4jvm.property.ResourceType;

public class DavUtils {

    public static final String OC_NAMESPACE = "http://owncloud.org/ns";
    public static final String NC_NAMESPACE = "http://nextcloud.org/ns";
    public static final String DAV_PATH = "/remote.php/dav/files/";

    public static final String EXTENDED_PROPERTY_NAME_PERMISSIONS = "permissions";
    public static final String EXTENDED_PROPERTY_NAME_REMOTE_ID = "id";
    public static final String EXTENDED_PROPERTY_NAME_SIZE = "size";
    public static final String EXTENDED_PROPERTY_FAVORITE = "favorite";
    public static final String EXTENDED_PROPERTY_IS_ENCRYPTED = "is-encrypted";
    public static final String EXTENDED_PROPERTY_MOUNT_TYPE = "mount-type";
    public static final String EXTENDED_PROPERTY_OWNER_ID = "owner-id";
    public static final String EXTENDED_PROPERTY_OWNER_DISPLAY_NAME = "owner-display-name";
    public static final String EXTENDED_PROPERTY_UNREAD_COMMENTS = "comments-unread";
    public static final String EXTENDED_PROPERTY_HAS_PREVIEW = "has-preview";
    public static final String EXTENDED_PROPERTY_NOTE = "note";

    // public static final String TRASHBIN_FILENAME = "trashbin-filename";
    // public static final String TRASHBIN_ORIGINAL_LOCATION = "trashbin-original-location";
    // public static final String TRASHBIN_DELETION_TIME = "trashbin-deletion-time";

    // public static final String PROPERTY_QUOTA_USED_BYTES = "quota-used-bytes";
    // public static final String PROPERTY_QUOTA_AVAILABLE_BYTES = "quota-available-bytes";

    static Property.Name[] getAllPropSet() {
        List<Property.Name> props = new ArrayList<>(20);

        props.add(DisplayName.NAME);
        props.add(GetContentType.NAME);
        props.add(GetContentLength.NAME);
        props.add(GetContentType.NAME);
        props.add(GetContentLength.NAME);
        props.add(GetLastModified.NAME);
        props.add(CreationDate.NAME);
        props.add(GetETag.NAME);
        props.add(ResourceType.NAME);

        props.add(NCPermission.NAME);
        props.add(OCId.NAME);
        props.add(OCSize.NAME);
        props.add(OCFavorite.NAME);
        props.add(new Property.Name(OC_NAMESPACE, EXTENDED_PROPERTY_OWNER_ID));
        props.add(new Property.Name(OC_NAMESPACE, EXTENDED_PROPERTY_OWNER_DISPLAY_NAME));
        props.add(new Property.Name(OC_NAMESPACE, EXTENDED_PROPERTY_UNREAD_COMMENTS));

        props.add(NCEncrypted.NAME);
        props.add(new Property.Name(NC_NAMESPACE, EXTENDED_PROPERTY_MOUNT_TYPE));
        props.add(NCPreview.NAME);
        props.add(new Property.Name(NC_NAMESPACE, EXTENDED_PROPERTY_NOTE));

        return props.toArray(new Property.Name[0]);
    }

    public static void registerCustomFactories() {
        PropertyRegistry propertyRegistry = PropertyRegistry.INSTANCE;

        propertyRegistry.register(new OCId.Factory());
        propertyRegistry.register(new NCPreview.Factory());
        propertyRegistry.register(new NCEncrypted.Factory());
        propertyRegistry.register(new OCFavorite.Factory());
        propertyRegistry.register(new OCSize.Factory());
        propertyRegistry.register(new NCPermission.Factory());
    }
}
