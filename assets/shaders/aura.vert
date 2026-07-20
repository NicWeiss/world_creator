attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;

uniform mat4 u_projTrans;
// Экранный центр ауры — квадрат спрайта трансформируется вокруг него, а не вокруг своего локального
// (0,0), иначе пульсация/вращение "уезжали" бы в угол вместо равномерного эффекта из центра свечения.
uniform vec2  u_center;
uniform float u_time;

// u_mode выбирает анимацию (см. SkillEffectRenderer.AuraAnim/Mode) — один шейдер на все ауры,
// параметры ниже используются выборочно, в зависимости от режима:
//   0 — пульсация размера (яркость — в aura.frag)
//   1 — непрерывный "вдох": растёт/бледнеет, затем снова маленькая/яркая, по кругу (края колышутся — в frag)
//   2 — вращение (яркость — в aura.frag)
//   3 — вращение + треугольный цикл из 6 ступеней размера (3 растущих, потом те же 3 обратно вниз
//       до минимума), внутри каждой ступени яркость плавно падает (максимум до половины, см.
//       aura.frag), см. u_stepDuration/u_stepGrow
//   4 — лёгкое покачивание размера (основная "водная" рябь — в aura.frag, по UV)
//   5 — раз в u_burstPeriod секунд резкий рывок наружу, потом медленный "рост из центра" обратно
uniform float u_mode;
uniform float u_pulseSpeed;
uniform float u_pulseAmp;
uniform float u_rotSpeed;
uniform float u_stepDuration; // длительность одной ступени режима 3 (см. ТЗ — 1 сек)
uniform float u_stepGrow;     // прирост размера за ступень режима 3
uniform float u_burstPeriod;
uniform float u_burstScale;
// Сплюснутость спрайта по Y под изометрию (см. SkillEffectRenderer.AURA_ISO_SQUASH) — квадрат
// геометрии УЖЕ приходит сплющенным (w,h переданы такими из Java), поэтому вращение (режимы 2/3)
// должно временно "распрямлять" координаты в истинный круг перед rot2 и сплющивать обратно ПОСЛЕ
// неё. Без этого поворот на не кратный 180° угол превращал бы плоский эллипс в перекошенный овал
// (компенсация изометрии работала бы только в исходном угле, а не постоянно).
uniform float u_squash;

varying vec4  v_color;
varying vec2  v_texCoords;
varying float v_mode;
varying float v_fx; // общая "фаза" 0..1 для режимов 1/5 — используется в aura.frag для альфы

mat2 rot2(float a) {
    float s = sin(a), c = cos(a);
    return mat2(c, -s, s, c);
}

void main() {
    v_color = a_color;
    v_color.a = v_color.a * (255.0 / 254.0);
    v_texCoords = a_texCoord0;
    v_mode = u_mode;
    v_fx = 0.0;

    vec2 local = a_position.xy - u_center;
    float scale = 1.0;

    if (u_mode < 0.5) {
        scale = 1.0 + u_pulseAmp * sin(u_time * u_pulseSpeed);

    } else if (u_mode < 1.5) {
        float grow = 0.5 - 0.5 * cos(u_time * u_pulseSpeed); // непрерывно, без скачков на стыке цикла
        v_fx = grow;
        scale = 1.0 + u_pulseAmp * grow;

    } else if (u_mode < 2.5) {
        local.y /= u_squash;                      // распрямляем в истинный круг
        local = rot2(u_time * u_rotSpeed) * local; // крутим круг — без искажений на любом угле
        local.y *= u_squash;                       // возвращаем изометрическое сплющивание

    } else if (u_mode < 3.5) {
        local.y /= u_squash;
        local = rot2(u_time * u_rotSpeed) * local;
        local.y *= u_squash;

        // 6 ступеней по u_stepDuration сек: 3 растущих (уровни 1,2,3), затем 3 убывающих обратно
        // (уровни 2,1,0) — треугольный цикл, при достижении максимума идёт вниз ЧЕРЕЗ ТЕ ЖЕ этапы
        // обратно до минимума (см. ТЗ), а не мгновенный сброс. Полный период = u_stepDuration * 6.0.
        float cyclePos = mod(u_time, u_stepDuration * 6.0);
        float stage  = floor(cyclePos / u_stepDuration);  // 0..5
        float stageT = fract(cyclePos / u_stepDuration);  // прогресс внутри текущей ступени
        float level  = 3.0 - abs(stage - 2.0);            // 1,2,3,2,1,0

        scale = 1.0 + u_stepGrow * level;
        v_fx = stageT; // используется в aura.frag для потери яркости внутри каждой ступени

    } else if (u_mode < 4.5) {
        scale = 1.0 + 0.05 * sin(u_time * u_pulseSpeed);

    } else {
        float t = fract(u_time / u_burstPeriod);
        float burstFrac = 0.12; // короткая быстрая фаза рывка, остальное время — медленный возврат
        if (t < burstFrac) {
            float k = t / burstFrac;
            float eased = 1.0 - (1.0 - k) * (1.0 - k); // ease-out — резкий рывок наружу
            scale = mix(1.0, u_burstScale, eased);
            v_fx = eased;
        } else {
            float k = (t - burstFrac) / (1.0 - burstFrac);
            float eased = k * k; // ease-in — медленный "рост из центра" обратно
            scale = mix(0.08, 1.0, eased);
            v_fx = 1.0 - eased;
        }
    }

    local *= scale;
    vec2 pos = u_center + local;
    gl_Position = u_projTrans * vec4(pos, a_position.zw);
}
