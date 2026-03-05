#version 300 es
/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

precision mediump float;

uniform sampler2D u_FrameTexture;
uniform sampler2D u_BlurredTexture;
uniform sampler2D u_MaskTexture;

in vec2 v_TexCoord;
out vec4 FragColor;

bool checkNotInBounds(vec2 coords) {
    return coords.x < 0.0 || coords.x > 1.0 || coords.y < 0.0 || coords.y > 1.0;
}

void main() {

    vec2 nv_TexCoord = vec2(v_TexCoord.x, 1.0 - v_TexCoord.y);

    if (checkNotInBounds(nv_TexCoord)) {
        FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // Since input is byte 1, OpenGL normalizes it to ~0.0039 (1/255)
    // 0.0     -> 0.0 (Black)
    // (1/255) -> 1.0 (White)
    float maskValue = texture(u_MaskTexture, nv_TexCoord).r * 255.0;

    vec4 blurredValue = texture(u_BlurredTexture, nv_TexCoord);

    vec4 originalValue = texture(u_FrameTexture, nv_TexCoord);

    // Only blur the white pixels
    if (maskValue > 0.0) {
        FragColor = blurredValue;
    } else {
        FragColor = originalValue;
    }
}