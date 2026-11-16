// =====================================================================
//  OxygenOS · Aurora Grafite
//  Cortinas de aurora esmeralda + poeira de estrelas sobre quase-preto.
// =====================================================================
vec3 scene(vec2 p, vec2 uv, float t) {
    vec3 col = mix(vec3(0.012, 0.020, 0.030), vec3(0.020, 0.045, 0.052), uv.y);

    // Campo de estrelas: ponto dentro da célula (não a célula inteira),
    // com cintilação individual — nada de "pixels quadrados".
    vec2 g = p * 150.0;
    vec2 cell = floor(g);
    vec2 fc = fract(g) - 0.5;
    float rnd = hash21(cell);
    vec2 jitter = (vec2(hash21(cell + 3.7), hash21(cell + 9.1)) - 0.5) * 0.55;
    float star = pow(rnd, 42.0) * smoothstep(0.30, 0.02, length(fc - jitter));
    col += vec3(0.80, 0.90, 1.0) * star * (0.55 + 0.45 * sin(t * 2.2 + rnd * 60.0)) * 2.2;

    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float drift = t * (0.06 + 0.02 * fi);

        // A cortina serpenteia na horizontal conforme sobe na tela.
        float sway = fbm3(vec2(p.y * 1.7 + fi * 4.0, drift)) - 0.5;
        float x = p.x + sway * 0.28 + (fi - 1.0) * 0.085;

        float width = 0.055 + 0.035 * fbm3(vec2(fi * 7.0, drift * 1.6));
        float band = exp(-x * x / (width * width));

        // Estrias verticais: assinatura visual de aurora.
        float striae = 0.55 + 0.45 * vnoise(vec2(x * 55.0, drift * 2.2 + fi));

        float vertical = smoothstep(-0.52, -0.05, p.y) * smoothstep(0.58, 0.02, p.y);
        float energy = band * striae * vertical;

        vec3 green = mix(vec3(0.10, 0.90, 0.55), vec3(0.64, 1.0, 0.24),
                         clamp(p.y + 0.45, 0.0, 1.0));
        vec3 teal = vec3(0.05, 0.55, 0.62);

        col += mix(teal, green, 0.65) * energy * (0.55 - 0.12 * fi);
        col += green * pow(energy, 3.0) * 0.55;
    }

    // Reflexo frio na base, como neve fora de quadro.
    col += vec3(0.03, 0.10, 0.12) * smoothstep(0.35, 0.0, uv.y);
    return col;
}
