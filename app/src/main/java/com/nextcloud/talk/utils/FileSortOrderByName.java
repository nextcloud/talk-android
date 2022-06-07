/*
 * Nextcloud Talk application
 *
 * @author Sven R. Kunze
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Sven R. Kunze
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

import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem;

import java.util.Collections;
import java.util.List;

import third_parties.daveKoeller.AlphanumComparator;

/**
 * Created by srkunze on 28.08.17.
 */
public class FileSortOrderByName extends FileSortOrder {

    FileSortOrderByName(String name, boolean ascending) {
        super(name, ascending);
    }

    /**
     * Sorts list by Name.
     *
     * @param files files to sort
     */
    @SuppressWarnings("Bx")
    public List<RemoteFileBrowserItem> sortCloudFiles(List<RemoteFileBrowserItem> files) {
        final int multiplier = isAscending ? 1 : -1;

        Collections.sort(files, (o1, o2) -> {
            if (!o1.isFile() && !o2.isFile()) {
                return multiplier * new AlphanumComparator().compare(o1, o2);
            } else if (!o1.isFile()) {
                return -1;
            } else if (!o2.isFile()) {
                return 1;
            }
            return multiplier * new AlphanumComparator().compare(o1, o2);
        });

        return super.sortCloudFiles(files);
    }
}
