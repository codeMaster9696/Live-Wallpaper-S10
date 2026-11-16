// =====================================================================
//  iOS · Gradiente (mesh gradient animado)
//  Cinco fontes de cor ancoradas em alturas diferentes da tela, com
//  órbitas lentas. Em 19:9 elas ficam separadas o suficiente para ler
//  cada matiz — é isso que diferencia "mesh gradient" de "borrão roxo".
//  Fallback procedural do vídeo ios_mesh.mp4.
// =====================================================================
vec3 blob(vec2 p, vec2 c, vec3 col, float r) {
    vec2 d = p - c;
    return col * exp(-dot(d, d) / (r * r));
}

vec3 scene(vec2 p, vec2 uv, float t) {
    float tt = t * 0.22;
    vec3 col = vec3(0.015, 0.012, 0.040);

    // base: índigo profundo
    col += blob(p, vec2(sin(tt * 0.70) * 0.16, -0.34 + cos(tt * 0.50) * 0.13),
                vec3(0.30, 0.28, 0.95), 0.26) * 0.95;
    // coração: magenta
    col += blob(p, vec2(cos(tt * 0.60 + 1.2) * 0.18, -0.04 + sin(tt * 0.45 + 2.0) * 0.15),
                vec3(1.00, 0.20, 0.72), 0.22) * 0.85;
    // topo: azul elétrico
    col += blob(p, vec2(sin(tt * 0.90 + 2.5) * 0.17, 0.23 + cos(tt * 0.35 + 0.6) * 0.14),
                vec3(0.02, 0.45, 1.00), 0.25) * 0.90;
    // realce: âmbar
    col += blob(p, vec2(cos(tt * 0.40 + 0.4) * 0.14, 0.45 + sin(tt * 0.80 + 1.1) * 0.09),
                vec3(1.00, 0.62, 0.10), 0.18) * 0.70;
    // viajante: turquesa cruzando a composição
    col += blob(p, vec2(sin(tt * 1.10 + 3.1) * 0.13, 0.05 + cos(tt * 0.75 + 1.9) * 0.30),
                vec3(0.10, 0.95, 0.75), 0.15) * 0.55;

    // Tonemap forte: os realces ficam cremosos sem lavar os matizes.
    col = col / (1.0 + col * 0.85);
    col = pow(col, vec3(1.06));

    // Névoa sutil para evitar banding em AMOLED.
    col += (fbm3(p * 3.4 + t * 0.03) - 0.5) * 0.030;
    return col;
}
