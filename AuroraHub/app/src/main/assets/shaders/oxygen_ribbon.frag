// =====================================================================
//  OxygenOS · Abstrato Vermelho
//  Fitas de luz carmesim sobre grafite quente, com brilho especular.
//  Fallback procedural do vídeo oxygen_ribbon.mp4 (mesma direção de arte).
// =====================================================================
vec3 scene(vec2 p, vec2 uv, float t) {
    vec2 q = rot2(0.30) * p;

    float warp = fbm3(q * 2.3 + vec2(0.0, t * 0.10));

    // Base grafite com um leve rubro no centro — nunca preto puro.
    vec3 col = vec3(0.040, 0.021, 0.028);
    col += vec3(0.10, 0.015, 0.030) * smoothstep(0.75, 0.0, length(p * vec2(1.2, 0.65)));

    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float phase = t * 0.32 + fi * 1.7;

        float center = sin(q.y * 2.4 + phase) * 0.105
                     + sin(q.y * 5.1 - phase * 0.7) * 0.042
                     + (fi - 1.5) * 0.075;

        float width = 0.016 + 0.012 * sin(q.y * 3.0 + phase * 1.3) + 0.010 * warp;
        float d = abs(q.x + warp * 0.085 - center);

        float core = smoothstep(width, 0.0, d);
        float glow = exp(-d * d / (0.0026 + 0.0032 * fi));

        vec3 hot = mix(vec3(0.92, 0.045, 0.13), vec3(1.0, 0.40, 0.14),
                       0.5 + 0.5 * sin(q.y * 2.0 + phase));

        col += hot * glow * (0.26 + 0.10 * fi);
        col += mix(hot, vec3(1.0, 0.86, 0.74), 0.55) * core * 0.85;
    }

    // Poeira fina suspensa: dá escala e evita bandas de cor lisas.
    float dust = pow(hash21(floor(p * 420.0)), 42.0);
    col += vec3(1.0, 0.72, 0.6) * dust * 0.55 * (0.6 + 0.4 * sin(t * 1.7 + p.y * 40.0));

    // Escurece as extremidades verticais: legibilidade do relógio na lockscreen.
    col *= mix(0.72, 1.06, smoothstep(0.0, 0.55, 1.0 - abs(uv.y - 0.45) * 1.6));
    return col;
}
