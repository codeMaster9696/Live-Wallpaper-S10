// =====================================================================
//  video.frag — caminho do MediaPlayer (CENTER_CROP em espaço de textura)
// =====================================================================
//  uSTMatrix : matriz devolvida por SurfaceTexture.getTransformMatrix()
//              (corrige flip vertical e rotação do buffer do decoder)
//  uUvScale  : zoom do amostrador calculado por ScaleMath.compute()
//              FILL -> valores <= 1 (corta)   FIT -> valores >= 1 (bordas)
//  uUvOffset : deslocamento do paralaxe, limitado à folga do crop
// =====================================================================
#extension GL_OES_EGL_image_external : require
precision highp float;

varying vec2 vUv;

uniform samplerExternalOES uTex;
uniform mat4  uSTMatrix;
uniform vec2  uUvScale;
uniform vec2  uUvOffset;
uniform vec2  uResolution;
uniform float uTime;
uniform float uBrightness;
uniform float uSaturation;
uniform float uGrain;
uniform float uFade;      // crossfade procedural -> vídeo no primeiro frame

float hash21v(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    vec2 uv = (vUv - 0.5) * uUvScale + 0.5 + uUvOffset;

    // Amostra sempre dentro de [0,1] (evita artefato de CLAMP_TO_EDGE) e
    // zera o que cair fora do quadro: no modo "Ajustar" isso é a borda.
    vec2 cuv = clamp(uv, 0.0, 1.0);
    vec2 st = (uSTMatrix * vec4(cuv, 0.0, 1.0)).xy;
    vec3 c = texture2D(uTex, st).rgb;

    float inside = step(0.0, uv.x) * step(uv.x, 1.0) * step(0.0, uv.y) * step(uv.y, 1.0);
    c *= inside;

    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(l), c, uSaturation) * uBrightness;
    vec2 d = vUv - 0.5;
    c *= 1.0 - dot(d, d) * 0.35;
    c += (hash21v(vUv * uResolution * 0.5 + fract(uTime) * 91.0) - 0.5) * uGrain;

    gl_FragColor = vec4(clamp(c, 0.0, 1.0) * uFade, uFade);
}
