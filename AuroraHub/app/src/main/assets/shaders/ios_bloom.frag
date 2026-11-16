// =====================================================================
//  iOS · Bloom Noturno
//  Pétalas polares iridescentes com anel de luz e núcleo incandescente.
// =====================================================================
vec3 scene(vec2 p, vec2 uv, float t) {
    vec2 q = p * 1.75;
    q = rot2(sin(t * 0.08) * 0.25) * q;

    float r = length(q);
    float a = atan(q.y, q.x);

    float breathe = 0.055 * sin(t * 0.45);
    float petals = 6.0;
    float k = cos(a * petals + t * 0.22 + sin(r * 3.2 - t * 0.40) * 0.85);
    float shape = 0.44 + 0.15 * k + breathe;

    float body = smoothstep(shape, shape - 0.30, r);
    // pow() com base negativa é indefinido em GLSL: use sempre x*x.
    float e1 = (r - shape) / 0.030;
    float rim = exp(-e1 * e1);

    vec3 col = vec3(0.010, 0.026, 0.070);
    vec3 petal = mix(vec3(0.02, 0.30, 0.86), vec3(0.36, 0.86, 1.00),
                     0.5 + 0.5 * sin(a * 2.0 + t * 0.30));
    col += petal * body * 0.50;

    vec3 iris = mix(vec3(1.00, 0.22, 0.42), vec3(0.42, 0.92, 1.00),
                    0.5 + 0.5 * sin(a * 3.0 - t * 0.25));
    col += iris * rim * 0.85;

    // Segunda corola, defasada, dando profundidade de "flor dupla".
    float shape2 = 0.24 + 0.09 * cos(a * petals - t * 0.35) + breathe * 0.6;
    float e2 = (r - shape2) / 0.022;
    float rim2 = exp(-e2 * e2);
    col += vec3(0.85, 0.95, 1.0) * rim2 * 0.35;

    // Núcleo
    col += vec3(0.90, 0.96, 1.0) * exp(-r * r / 0.0035) * 0.55;

    // Ondas concêntricas suaves
    col += vec3(0.10, 0.42, 0.95) * 0.05 * sin(r * 26.0 - t * 1.1)
           * smoothstep(0.95, 0.05, r);

    col *= mix(0.80, 1.05, smoothstep(0.0, 0.6, 1.0 - abs(uv.y - 0.5) * 1.5));
    return col;
}
