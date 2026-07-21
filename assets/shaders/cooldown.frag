#ifdef GL_ES
    #define LOWP lowp
    precision mediump float;
#else
    #define LOWP
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;

// Доля перезарядки, которая ЕЩЁ ИДЁТ (1.0 — только что закастовано, вся ячейка серая; 0.0 — готово,
// серости нет совсем). Рисуется ПОВЕРХ уже нарисованной иконки умения тем же квадратом — серый
// "клин" убирается ИЗ ЦЕНТРА ПО ЧАСОВОЙ СТРЕЛКЕ, начиная с 12 часов (см. PlayerHud.renderSkillSlot).
uniform float u_remainFrac;

void main() {
    vec2 d = v_texCoords - vec2(0.5, 0.5);

    // Ячейка круглая (см. circularSkillIcon) — квадрат вокруг нужен только как холст, за пределами
    // вписанной окружности пелену не рисуем, иначе серость вылезала бы в квадратные углы за
    // пределами реального круга иконки.
    if (length(d) > 0.5) discard;

    // atan(-x, -y) даёт 0 наверху (12 часов) и растёт ПО ЧАСОВОЙ СТРЕЛКЕ.
    float angle = atan(-d.x, -d.y);
    if (angle < 0.0) angle += 6.283185307179586;
    float frac = angle / 6.283185307179586;
    float mask = step(frac, u_remainFrac); // 1.0 — ещё серый сектор, 0.0 — уже открыто (иконка видна)
    gl_FragColor = vec4(0.0, 0.0, 0.0, mask * 0.72 * v_color.a);
}
