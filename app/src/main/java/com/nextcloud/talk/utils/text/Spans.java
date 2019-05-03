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

package com.nextcloud.talk.utils.text;

import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import com.facebook.widget.text.span.BetterImageSpan;
import lombok.Data;

public class Spans {

    @Data
    public static class MentionChipSpan extends BetterImageSpan {
        String id;
        String label;

        public MentionChipSpan(@NonNull Drawable drawable, int verticalAlignment, String id, String label) {
            super(drawable, verticalAlignment);
            this.id = id;
            this.label = label;
        }
    }

}
