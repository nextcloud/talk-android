package com.nextcloud.talk.models.json.capabilities;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class UserStatusCapability {
    @JsonField(name = "enabled")
    boolean enabled;

    @JsonField(name = "supports_emoji")
    boolean supportsEmoji;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSupportsEmoji() {
        return supportsEmoji;
    }

    public void setSupportsEmoji(boolean supportsEmoji) {
        this.supportsEmoji = supportsEmoji;
    }
}
