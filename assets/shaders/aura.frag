#ifdef GL_ES
    #define LOWP lowp
    precision mediump float;
#else
    #define LOWP
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
varying float v_mode;
varying float v_fx;

uniform sampler2D u_texture;
uniform float u_time;
uniform float u_pulseSpeed;
uniform float u_brightAmp;
uniform float u_alphaAmp;

// Режимы см. aura.vert. Тут — то, что не завязано на геометрию: колыхание краёв (1), рябь "как
// вода" (4), пульсация яркости (0/2) и альфа, ведомая фазой v_fx (1/5).
void main() {
    vec2 uv = v_texCoords;

    if (v_mode > 0.5 && v_mode < 1.5) {
        vec2 d = uv - vec2(0.5);
        float ang = atan(d.y, d.x);
        float wob = sin(ang * 6.0 + u_time * 3.0) * 0.02;
        uv = vec2(0.5) + d * (1.0 + wob);
    } else if (v_mode > 3.5 && v_mode < 4.5) {
        uv.x += sin(uv.y * 10.0 + u_time * 2.0) * 0.015;
        uv.y += cos(uv.x * 10.0 + u_time * 2.3) * 0.015;
    }

    vec4 base = texture2D(u_texture, uv);
    float bright = 1.0;
    float alphaMult = 1.0;

    if (v_mode < 0.5 || (v_mode > 1.5 && v_mode < 2.5)) {
        bright = 1.0 + u_brightAmp * sin(u_time * u_pulseSpeed);
    }
    if (v_mode > 0.5 && v_mode < 1.5) {
        alphaMult = 1.0 - u_alphaAmp * v_fx;
    }
    if (v_mode > 2.5 && v_mode < 3.5) {
        // Внутри каждой ступени роста (см. aura.vert) яркость плавно падает, максимум до половины.
        bright = 1.0 - 0.5 * v_fx;
    }
    if (v_mode > 4.5) {
        alphaMult = 1.0 - v_fx;
    }

    gl_FragColor = v_color * vec4(base.rgb * bright, base.a * alphaMult);
}
