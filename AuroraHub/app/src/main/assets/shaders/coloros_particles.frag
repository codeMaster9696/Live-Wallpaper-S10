// =====================================================================
//  ColorOS · Partículas Douradas
//  Bokeh em três planos de profundidade, cada um com desfoque próprio
//  (depth of field barato: o raio de queda muda por camada).
// =====================================================================
float bokehLayer(vec2 p, float scale, float t, float seed, float softness) {
    vec2 g = p * scale;
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    float acc = 0.0;

    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 o = vec2(float(i), float(j));
            vec2 cid = id + o;
            float h1 = hash21(cid + seed);
            float h2 = hash21(cid + seed + 17.3);

            vec2 drift = vec2(
                sin(t * (0.22 + h1 * 0.35) + h1 * 6.2831),
                cos(t * (0.18 + h2 * 0.30) + h2 * 6.2831)
            ) * 0.34;

            vec2 delta = o + drift - f;
            float d = length(delta);
            float rad = 0.09 + 0.20 * h2;
            acc += smoothstep(rad, rad * softness, d) * (0.30 + 0.70 * h1);
        }
    }
    return acc;
}

vec3 scene(vec2 p, vec2 uv, float t) {
    // Fundo âmbar profundo, mais quente embaixo.
    vec3 col = mix(vec3(0.035, 0.022, 0.014), vec3(0.075, 0.040, 0.018), 1.0 - uv.y);
    col += vec3(0.10, 0.05, 0.015) * smoothstep(0.8, 0.0, length(p * vec2(1.0, 0.6)));

    // Camada distante: pequenas, nítidas, lentas.
    float far = bokehLayer(p + vec2(uParallax * 0.02, 0.0), 9.0, t * 0.55, 3.1, 0.55);
    col += vec3(1.00, 0.72, 0.28) * far * 0.20;

    // Camada média.
    float mid = bokehLayer(p + vec2(uParallax * 0.05, 0.0), 5.0, t * 0.80, 11.7, 0.30);
    col += vec3(1.00, 0.80, 0.42) * mid * 0.30;

    // Camada próxima: grandes, muito desfocadas, rápidas (bokeh de lente).
    float near = bokehLayer(p + vec2(uParallax * 0.10, 0.0), 2.6, t * 1.15, 27.4, 0.05);
    col += vec3(1.00, 0.88, 0.62) * near * 0.24;
    // Núcleos incandescentes: dão brilho de lente real ao bokeh.
    col += vec3(1.00, 0.94, 0.78) * pow(clamp(mid, 0.0, 1.0), 3.0) * 0.55;
    col += vec3(1.00, 0.80, 0.40) * pow(clamp(far, 0.0, 1.0), 4.0) * 0.45;

    // Feixe de luz diagonal, bem discreto.
    float sx = (p.x + p.y * 0.35 - 0.06 * sin(t * 0.2)) / 0.16;
    float shaft = exp(-sx * sx);
    col += vec3(0.35, 0.22, 0.08) * shaft * 0.35;

    // Tonemap para os realces não virarem manchas brancas.
    col = col / (1.0 + col * 0.35);
    return col;
}
