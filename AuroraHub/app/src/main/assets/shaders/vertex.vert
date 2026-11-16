// Vertex shader único do projeto: um quad que cobre o viewport inteiro.
// vUv = (0,0) no canto inferior esquerdo (mesma convenção do WebGL).
attribute vec2 aPos;
attribute vec2 aUv;
varying vec2 vUv;

void main() {
    vUv = aUv;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
