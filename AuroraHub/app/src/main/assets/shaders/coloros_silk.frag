// =====================================================================
//  ColorOS · Seda Fluida
//  Domain warping em duas passadas: o tecido magenta/ciano se dobra.
//  Usa fbm3 (3 oitavas) para caber em 60 fps a 1440x3040.
// =====================================================================
vec3 scene(vec2 p, vec2 uv, float t) {
    // Escala anisotrópica: as dobras ficam alongadas, como tecido.
    vec2 q = p * vec2(3.4, 2.1);
    float t1 = t * 0.11;

    vec2 w = vec2(
        fbm3(q + vec2(0.0, t1)),
        fbm3(q + vec2(5.2, -t1))
    );

    vec2 r2 = vec2(
        fbm3(q + 2.0 * w + vec2(1.7, 9.2) + t1 * 0.6),
        fbm3(q + 2.0 * w + vec2(8.3, 2.8) - t1 * 0.4)
    );

    float f = fbm3(q + 2.2 * r2);

    vec3 deep    = vec3(0.040, 0.012, 0.090);
    vec3 violet  = vec3(0.480, 0.160, 1.000);
    vec3 magenta = vec3(1.000, 0.240, 0.800);
    vec3 cyan    = vec3(0.000, 0.900, 1.000);

    vec3 col = mix(deep, violet, clamp(f * f * 2.6, 0.0, 1.0));
    col = mix(col, magenta, clamp(pow(length(w), 1.3) * 1.30, 0.0, 1.0));
    col = mix(col, cyan, clamp(r2.y * r2.y * 2.1, 0.0, 1.0) * 0.78);

    // Fios de brilho: onde o campo atinge o pico, o tecido "vinca".
    float crease = pow(clamp(f, 0.0, 1.0), 11.0);
    col += vec3(1.0, 0.90, 0.99) * crease * 0.95;

    // Contraste direcional: dobra clara / dobra escura.
    col *= 0.70 + 0.62 * smoothstep(0.22, 0.86, f);
    col *= 0.90 + 0.20 * sin(uv.y * 3.0 + t * 0.18);
    return col;
}
