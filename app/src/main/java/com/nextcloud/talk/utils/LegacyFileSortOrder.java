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

import android.text.TextUtils;

import com.nextcloud.talk.components.filebrowser.adapters.items.BrowserFileItem;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;

/**
 * Sort order
 */
@Deprecated
public class LegacyFileSortOrder {
    public static final String sort_a_to_z_id = "sort_a_to_z";
    public static final String sort_z_to_a_id = "sort_z_to_a";
    public static final String sort_old_to_new_id = "sort_old_to_new";
    public static final String sort_new_to_old_id = "sort_new_to_old";
    public static final String sort_small_to_big_id = "sort_small_to_big";
    public static final String sort_big_to_small_id = "sort_big_to_small";

    public static final LegacyFileSortOrder sort_a_to_z = new LegacyFileSortOrderByName(sort_a_to_z_id, true);
    public static final LegacyFileSortOrder sort_z_to_a = new LegacyFileSortOrderByName(sort_z_to_a_id, false);
    public static final LegacyFileSortOrder sort_old_to_new = new LegacyFileSortOrderByDate(sort_old_to_new_id, true);
    public static final LegacyFileSortOrder sort_new_to_old = new LegacyFileSortOrderByDate(sort_new_to_old_id, false);
    public static final LegacyFileSortOrder sort_small_to_big = new LegacyFileSortOrderBySize(sort_small_to_big_id, true);
    public static final LegacyFileSortOrder sort_big_to_small = new LegacyFileSortOrderBySize(sort_big_to_small_id, false);

    public static final Map<String, LegacyFileSortOrder> sortOrders;

    static {
        HashMap<String, LegacyFileSortOrder> temp = new HashMap<>();
        temp.put(sort_a_to_z.name, sort_a_to_z);
        temp.put(sort_z_to_a.name, sort_z_to_a);
        temp.put(sort_old_to_new.name, sort_old_to_new);
        temp.put(sort_new_to_old.name, sort_new_to_old);
        temp.put(sort_small_to_big.name, sort_small_to_big);
        temp.put(sort_big_to_small.name, sort_big_to_small);

        sortOrders = Collections.unmodifiableMap(temp);
    }

    public String name;
    public boolean isAscending;

    public LegacyFileSortOrder(String name, boolean ascending) {
        this.name = name;
        isAscending = ascending;
    }

    public static LegacyFileSortOrder getFileSortOrder(@Nullable String key) {
        if (TextUtils.isEmpty(key) || !sortOrders.containsKey(key)) {
            return sort_a_to_z;
        } else {
            return sortOrders.get(key);
        }
    }

    public List<BrowserFileItem> sortCloudFiles(List<BrowserFileItem> files) {
        return sortCloudFilesByFavourite(files);
    }

    /**
     * Sorts list by Favourites.
     *
     * @param files files to sort
     */
    public static List<BrowserFileItem> sortCloudFilesByFavourite(List<BrowserFileItem> files) {
        Collections.sort(files, (o1, o2) -> {
            if (o1.getModel().isFavorite() && o2.getModel().isFavorite()) {
                return 0;
            } else if (o1.getModel().isFavorite()) {
                return -1;
            } else if (o2.getModel().isFavorite()) {
                return 1;
            }
            return 0;
        });

        return files;
    }
}
