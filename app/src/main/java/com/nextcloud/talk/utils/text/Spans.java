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
import third.parties.fresco.BetterImageSpan;

public class Spans {

    public static class MentionChipSpan extends BetterImageSpan {
        public String id;
        public CharSequence label;

        public MentionChipSpan(@NonNull Drawable drawable, int verticalAlignment, String id, CharSequence label) {
            super(drawable, verticalAlignment);
            this.id = id;
            this.label = label;
        }

        public String getId() {
            return this.id;
        }

        public CharSequence getLabel() {
            return this.label;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setLabel(CharSequence label) {
            this.label = label;
        }

        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof MentionChipSpan)) {
                return false;
            }
            final MentionChipSpan other = (MentionChipSpan) o;
            if (!other.canEqual((Object) this)) {
                return false;
            }
            final Object this$id = this.getId();
            final Object other$id = other.getId();
            if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
                return false;
            }
            final Object this$label = this.getLabel();
            final Object other$label = other.getLabel();

            return this$label == null ? other$label == null : this$label.equals(other$label);
        }

        protected boolean canEqual(final Object other) {
            return other instanceof MentionChipSpan;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $id = this.getId();
            result = result * PRIME + ($id == null ? 43 : $id.hashCode());
            final Object $label = this.getLabel();
            result = result * PRIME + ($label == null ? 43 : $label.hashCode());
            return result;
        }

        public String toString() {
            return "Spans.MentionChipSpan(id=" + this.getId() + ", label=" + this.getLabel() + ")";
        }
    }

}
