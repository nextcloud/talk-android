#version 300 es
/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

precision mediump float;

uniform sampler2D u_BlurredTexture;
uniform vec2 u_Direction;

in vec2 v_TexCoord;
out vec4 FragColor;

// Hard coded Gaussian blur weights and offsets, obtained from
// https://lisyarus.github.io/blog/posts/blur-coefficients-generator.html
const int SAMPLE_COUNT = 19;

const float OFFSETS[19] = float[19](
-17.381076274832935,
-15.394230424364446,
-13.407541291721701,
-11.420990335232771,
-9.434557915309014,
-7.448223675960468,
-5.461967313484028,
-3.475769408144678,
-1.489609431487625,
0.4965349085037341,
2.4826862657413393,
4.468862236297167,
6.4550869992703435,
8.441379804986935,
10.427760570612987,
12.414249746021257,
14.400867399097198,
16.387632648451888,
18.0
);

const float WEIGHTS[19] = float[19](
0.001960284463396841,
0.004861807629662949,
0.01080079750703472,
0.02149275164743333,
0.03830956291913094,
0.06116524317539527,
0.08747606946505149,
0.1120632810827934,
0.12859526767421955,
0.13218368064800906,
0.12170845606525565,
0.10038126557565077,
0.0741606178432157,
0.04907754925768548,
0.029092234724842715,
0.015447267260520894,
0.007346906112284092,
0.0031299390474930173,
0.0007470179009241477
);

// blurDirection is:
//     vec2(1,0) for horizontal pass
//     vec2(0,1) for vertical pass
// The sourceTexture is whats being blurred
// pixelCoord is in [0..1]
//
// You can read more about Gaussian Blurring here:
// https://www.intel.com/content/www/us/en/developer/articles/technical/an-investigation-of-fast-real-time-gpu-based-image-blur-algorithms.html
vec4 blur(in sampler2D sourceTexture, vec2 blurDirection, vec2 pixelCoord)
{
    vec4 result = vec4(0.0);
    ivec2 isize = textureSize(sourceTexture, 0);
    vec2 size = vec2(isize);
    for (int i = 0; i < SAMPLE_COUNT; ++i)
    {
        vec2 offset = blurDirection * OFFSETS[i] / size;
        float weight = WEIGHTS[i];
        result += texture(sourceTexture, pixelCoord + offset) * weight;
    }
    return result;
}

void main() {
    FragColor = blur(u_BlurredTexture, u_Direction, v_TexCoord);
}