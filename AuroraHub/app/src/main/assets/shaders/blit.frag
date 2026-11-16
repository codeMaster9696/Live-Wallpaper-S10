// blit.frag — copia o FBO (renderizado em resolução reduzida pela
// preferência "Qualidade") para a superfície final, com filtro linear.
precision mediump float;
varying vec2 vUv;
uniform sampler2D uTex;

void main() {
    gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0);
}
