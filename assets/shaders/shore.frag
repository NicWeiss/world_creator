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
uniform vec2  u_worldPos; // позиция ТЕКУЩЕГО тайла в сетке карты (см. Editor.drawTile/markShores)

// Берег (см. Editor.markShores) — суша, граничащая с водой. Базовое тонирование (темнее/холоднее)
// уже задаётся статично в MapObject.calcLitColor; этот шейдер добавляет ЛЁГКУЮ анимированную
// подсветку у края тайла — как блик от плещущейся рядом воды. НИКАКОГО пересэмплирования текстуры
// по смещённым координатам (в отличие от water.frag) — тут это не нужно, только аддитивная
// добавка к уже прочитанному (по НЕизменным координатам) пикселю, поэтому риска "провалиться" в
// прозрачное поле вокруг ромба здесь в принципе нет.
void main() {
    vec4 base = texture2D(u_texture, v_texCoords);

    // Грубая (не пиксель-точная — тут это не критично, эффект мягкий) оценка "у края тайла":
    // расстояние от центра UV-квадрата. Чем дальше от центра — тем ближе к кромке ромба.
    float distFromCenter = length(v_texCoords - vec2(0.5, 0.5));
    float edgeGlow = smoothstep(0.22, 0.42, distFromCenter);

    // Фаза — от u_worldPos (позиция тайла в сетке карты, см. water.frag про то же самое) — блик
    // на соседних береговых тайлах согласован по фазе, а не мигает на каждом тайле отдельно.
    float shimmer = sin(u_worldPos.x * 0.5 + u_worldPos.y * 0.5 - u_time * 2.2) * 0.5 + 0.5;

    vec3 highlighted = base.rgb + vec3(shimmer * edgeGlow * 0.10);
    gl_FragColor = v_color * vec4(highlighted, base.a);
}
