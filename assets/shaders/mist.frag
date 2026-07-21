#ifdef GL_ES
    #define LOWP lowp
    precision mediump float;
#else
    #define LOWP
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_time;
uniform float u_alphaMul; // общая огибающая появления/угасания всей группы клочков (см. SkillEffectRenderer.MistEffect)

// Текстуры тумана (fog_01..09.png) не бесшовные — вместо прокрутки используем ОГРАНИЧЕННОЕ
// покачивание UV: клочок туман "дышит"/колышется на месте, не заворачиваясь по кругу.
void main() {
    vec2 uv = v_texCoords;
    uv.y += sin(u_time * 0.35) * 0.02;
    uv.x += sin(uv.y * 6.0 + u_time * 0.6) * 0.015;
    vec4 base = texture2D(u_texture, uv);
    gl_FragColor = v_color * vec4(base.rgb, base.a * u_alphaMul);
}
