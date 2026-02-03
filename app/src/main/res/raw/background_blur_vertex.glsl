#version 300 es
/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

in vec4 a_Position;
in vec2 a_TexCoord;

out vec2 v_TexCoord;

void main() {
    gl_Position = a_Position;
    v_TexCoord = a_TexCoord;
}