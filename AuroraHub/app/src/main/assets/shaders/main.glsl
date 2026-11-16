// main.glsl — ponto de entrada dos wallpapers procedurais.
// Cada arquivo <nome>.frag define apenas: vec3 scene(vec2 p, vec2 uv, float t)
//   p  = coordenada centrada e corrigida pela proporção da tela
//   uv = 0..1 na tela inteira
//   t  = tempo em segundos (já com a velocidade do usuário aplicada)
void main() {
    vec2 uv = vUv;
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    p.x += uParallax * 0.05;
    vec3 c = scene(p, uv, uTime);
    gl_FragColor = vec4(grade(c, uv), 1.0);
}
