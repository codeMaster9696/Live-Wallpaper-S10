// =====================================================================
//  common.glsl — cabeçalho compartilhado por todos os wallpapers
//  GLES 2.0 (Android) e WebGL 1 (prévia web) usam exatamente este texto.
// =====================================================================
precision highp float;

varying vec2 vUv;

uniform vec2  uResolution;   // pixels da superfície (ex.: 1440 x 3040)
uniform float uTime;         // segundos já multiplicados pela velocidade
uniform float uBrightness;   // 0.5 .. 1.5
uniform float uSaturation;   // 0.0 .. 1.8
uniform float uGrain;        // 0.0 .. 0.25
uniform float uParallax;     // -1 .. 1 (páginas da tela inicial)

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// 5 oitavas: usado onde o detalhe importa e a área é pequena.
float fbm(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * vnoise(p);
        p = p * 2.02 + vec2(1.7, -2.3);
        a *= 0.5;
    }
    return s;
}

// 3 oitavas: versão barata para shaders de tela cheia com domain warping.
float fbm3(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec2(2.1, -1.4);
        a *= 0.5;
    }
    return s;
}

mat2 rot2(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

vec3 applySaturation(vec3 c, float s) {
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(l), c, s);
}

// Grading final: saturação, brilho, vinheta e granulação temporal.
// A vinheta é propositalmente suave — em AMOLED ela evita a sensação de
// "retângulo iluminado" sem escurecer o conteúdo central.
vec3 grade(vec3 c, vec2 uv) {
    c = max(c, 0.0);
    c = applySaturation(c, uSaturation);
    c *= uBrightness;
    vec2 d = uv - 0.5;
    c *= 1.0 - dot(d, d) * 0.45;
    float g = hash21(uv * uResolution * 0.5 + fract(uTime) * 137.0) - 0.5;
    c += g * uGrain;
    return clamp(c, 0.0, 1.0);
}
