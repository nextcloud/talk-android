#version 300 es
/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

precision mediump float;

uniform sampler2D u_FrameTexture;

in vec2 v_TexCoord;
out vec4 FragColor;

void main() {
     FragColor = texture(u_FrameTexture, v_TexCoord);
}