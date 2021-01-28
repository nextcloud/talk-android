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

package com.nextcloud.talk.models;


import com.nextcloud.talk.models.database.UserEntity;

import org.parceler.Parcel;

@Parcel
public class SignatureVerification {
    public boolean signatureValid;
    public UserEntity userEntity;

    public SignatureVerification() {
    }

    public boolean isSignatureValid() {
        return this.signatureValid;
    }

    public UserEntity getUserEntity() {
        return this.userEntity;
    }

    public void setSignatureValid(boolean signatureValid) {
        this.signatureValid = signatureValid;
    }

    public void setUserEntity(UserEntity userEntity) {
        this.userEntity = userEntity;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof SignatureVerification)) return false;
        final SignatureVerification other = (SignatureVerification) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.isSignatureValid() != other.isSignatureValid()) return false;
        final Object this$userEntity = this.getUserEntity();
        final Object other$userEntity = other.getUserEntity();
        if (this$userEntity == null ? other$userEntity != null : !this$userEntity.equals(other$userEntity))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof SignatureVerification;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isSignatureValid() ? 79 : 97);
        final Object $userEntity = this.getUserEntity();
        result = result * PRIME + ($userEntity == null ? 43 : $userEntity.hashCode());
        return result;
    }

    public String toString() {
        return "SignatureVerification(signatureValid=" + this.isSignatureValid() + ", userEntity=" + this.getUserEntity() + ")";
    }
}
