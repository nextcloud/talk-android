/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.events;

public class BottomSheetLockEvent {
    private final boolean cancelable;
    private final int delay;
    private final boolean shouldRefreshData;
    private final boolean cancel;
    private boolean dismissView;

    public BottomSheetLockEvent(boolean cancelable, int delay, boolean shouldRefreshData, boolean cancel) {
        this.cancelable = cancelable;
        this.delay = delay;
        this.shouldRefreshData = shouldRefreshData;
        this.cancel = cancel;
        this.dismissView = true;
    }

    public BottomSheetLockEvent(boolean cancelable, int delay, boolean shouldRefreshData, boolean cancel, boolean
            dismissView) {
        this.cancelable = cancelable;
        this.delay = delay;
        this.shouldRefreshData = shouldRefreshData;
        this.cancel = cancel;
        this.dismissView = dismissView;
    }

    public boolean isCancelable() {
        return this.cancelable;
    }

    public int getDelay() {
        return this.delay;
    }

    public boolean isShouldRefreshData() {
        return this.shouldRefreshData;
    }

    public boolean isCancel() {
        return this.cancel;
    }

    public boolean isDismissView() {
        return this.dismissView;
    }

    public void setDismissView(boolean dismissView) {
        this.dismissView = dismissView;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof BottomSheetLockEvent)) {
            return false;
        }
        final BottomSheetLockEvent other = (BottomSheetLockEvent) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.isCancelable() != other.isCancelable()) {
            return false;
        }
        if (this.getDelay() != other.getDelay()) {
            return false;
        }
        if (this.isShouldRefreshData() != other.isShouldRefreshData()) {
            return false;
        }
        if (this.isCancel() != other.isCancel()) {
            return false;
        }
        if (this.isDismissView() != other.isDismissView()) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof BottomSheetLockEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isCancelable() ? 79 : 97);
        result = result * PRIME + this.getDelay();
        result = result * PRIME + (this.isShouldRefreshData() ? 79 : 97);
        result = result * PRIME + (this.isCancel() ? 79 : 97);
        result = result * PRIME + (this.isDismissView() ? 79 : 97);
        return result;
    }

    public String toString() {
        return "BottomSheetLockEvent(cancelable=" + this.isCancelable() + ", delay=" + this.getDelay() + ", shouldRefreshData=" + this.isShouldRefreshData() + ", cancel=" + this.isCancel() + ", dismissView=" + this.isDismissView() + ")";
    }
}
