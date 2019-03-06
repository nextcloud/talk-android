/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.components.filebrowser.models.properties;

import android.text.TextUtils;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.XmlUtils;
import com.nextcloud.talk.components.filebrowser.webdav.DavUtils;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class OCFavorite implements Property {
    public static final Property.Name NAME = new Property.Name(DavUtils.OC_NAMESPACE, DavUtils.EXTENDED_PROPERTY_FAVORITE);

    @Getter
    @Setter
    private boolean ocFavorite;

    OCFavorite(boolean isFavorite) {
        ocFavorite = isFavorite;
    }

    public static class Factory implements PropertyFactory {

        @Nullable
        @Override
        public Property create(@NotNull XmlPullParser xmlPullParser) {
            try {
                String text = XmlUtils.INSTANCE.readText(xmlPullParser);
                if (!TextUtils.isEmpty(text)) {
                    return new OCFavorite(Boolean.parseBoolean(text));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            }

            return new OCFavorite(false);
        }

        @NotNull
        @Override
        public Property.Name getName() {
            return NAME;
        }
    }
}
