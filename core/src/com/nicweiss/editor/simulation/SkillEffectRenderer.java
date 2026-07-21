package com.nicweiss.editor.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.utils.SkillCatalog;
import com.nicweiss.editor.utils.ShaderLibrary;
import com.nicweiss.editor.utils.Transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Визуальный слой применения умений (см. SkillCaster.cast) — ЧИСТО визуальные эффекты, без урона и
 * попаданий: боевой системы для NPC пока нет (SpawnManager никому не уменьшает health), поэтому
 * реальное нанесение эффектов — отдельная будущая задача. Переиспользует уже готовые в проекте
 * приёмы:
 *  - разряд молнии из WeatherRenderer (jagged bolt + вспышка + динамический свет) — просто выставляем
 *    Store.lightningTargetWX/Y и Store.lightningBoltNew=true, WeatherRenderer сам подхватит на
 *    следующем кадре (см. scheduleBolt/updatePendingBolts — там же очередь, т.к. WeatherRenderer
 *    держит только ОДИН активный разряд одновременно);
 *  - повёрнутый растянутый пиксель для линий (тот же приём, что WeatherRenderer.drawSeg);
 *  - пунктирное кольцо (тот же приём, что SystemUI.drawBranchConnector — точки по кругу вместо
 *    прямой линии), сплюснутое по Y для изометрического вида "под ногами".
 */
public class SkillEffectRenderer {
    public static Store store;

    private final Texture pixel;

    public SkillEffectRenderer() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
    }

    // ── Разовые эффекты (line/ring) — заводятся из trigger(), сами исчезают по истечении life ────
    private static final class Streak {
        float x1, y1, x2, y2, age, life, r, g, b;
    }

    private static final class Ring {
        float cx, cy, age, life, maxRadius, r, g, b;
    }

    /** ДУМ — не разовый эффект, а "источник" наземного огня, живущий DOOM_LIFE секунд: КАЖДЫЙ тик
     *  спавнит одиночный GroundFire в случайной точке в radiusTiles от ТЕКУЩЕЙ (не зафиксированной
     *  на момент каста) позиции игрока — "если игрок двигается, точка спавна тоже двигается" (см.
     *  triggerFireDoom/updateDoomEffects). */
    private static final class DoomEffect {
        float age, spawnTimer, radiusTiles;
    }

    /** Летящий снаряд с покадровой анимацией — летит по прямой от старта к цели В МИРОВЫХ
     *  координатах (см. класс-комментарий GroundFire — без этого при движении игрока камера
     *  "утаскивала" бы снаряд с его настоящей траектории), поворачивается по направлению полёта
     *  (угол считается один раз при спавне из ЭКРАННОЙ проекции — прямая в мире линейна и в
     *  изометрии, угол не меняется по пути), останавливается на первом непроходимом препятствии
     *  (см. Player.isBlockedAt — "снаряды могут двигаться только там, где может двигаться персонаж")
     *  и в точке остановки (по препятствию или по прибытии) поджигает землю. */
    private static final class Projectile {
        float wx1, wy1, wx2, wy2, angleDeg, age, life, w, h;
        Texture[] frames;
        float frameRate;
        boolean igniteFireOnImpact;   // Огненный Шар — поджигает GroundFire в точке остановки
        boolean iceShatterOnImpact;   // Ледяной Шип — разовая анимация раскола в точке остановки
        float lightR, lightG, lightB; // цвет динамического света снаряда (см. updateLightSnapshot) —
                                       // огонь тёплый оранжевый, лёд холодный синий, а не одна константа на всё
    }

    /** Разовая (не зацикленная) анимация в точке удара — например раскол Ледяного Шипа. Проигрывает
     *  все кадры ОДИН раз и убирается — в отличие от GroundFire, который зацикливает кадры на всё
     *  время жизни. Мировые координаты — как и у Projectile/GroundFire, без привязки к экрану. */
    private static final class ImpactAnim {
        float wx, wy, age, life, w, h;
        Texture[] frames;
        float frameRate;
    }

    /** Наземный огонь — стоит на месте (импакт Огненного Шара) либо разлетается от игрока по дуге
     *  (Волна Огня, см. triggerFireWave) — работает В МИРОВЫХ координатах (wx/wy), на экран
     *  переводится только при отрисовке (см. worldToScreen), чтобы не "отставать" от камеры на
     *  долгоживущих эффектах (импакт держится ~1.3 сек, волна летит несколько секунд). */
    private static final class GroundFire {
        float wx, wy;
        float dirX, dirY, speed, travelled, maxDistance; // только для "летящих" (traveling=true)
        boolean traveling;
        float age, life; // используется и как таймер угасания в конце пути у "летящих"
        float w, h;
    }

    /** Ледяной Туман — MIST_PUFF_COUNT неподвижных клочков ОДНОЙ И ТОЙ ЖЕ текстуры (fog.png,
     *  см. ShaderLibrary.mist), разбросанных В МИРОВОЙ точке каста (не следует за игроком, в отличие
     *  от ДУМа — это область НА МЕСТНОСТИ) с СИЛЬНЫМ перекрытием (размер каждого клочка сравним с
     *  радиусом всей области, разброс позиций — узкий) — по требованию пользователя туман должен
     *  читаться ОДНИМ цельным облаком, а не отдельными вкраплениями. Параметры (позиция/поворот/
     *  размер) генерируются ОДИН раз при касте — "не раскладываться" (не двигаются и не
     *  переставляются), а вся группа вместе проступает из нулевой прозрачности и обратно гаснет в
     *  неё же по истечении жизни. */
    private static final class MistEffect {
        float wx, wy, age, life, radiusTiles;
        float[] offX, offY, rotDeg, sizeMul;
    }

    /** Хрупкость — одна "частица" (см. triggerColdFragility): неподвижная точка в области (той же,
     *  что у Ледяного Тумана), которая ждёт delay сек (случайная задержка появления, см. "появляться
     *  с произвольной задержкой"), затем за FRAGILE_LIFE сек проигрывает "шар поднимается и лопается"
     *  (см. updateAndDrawFragility): быстрое проявление из невидимости, подъём вверх, на середине
     *  пути запускается сама анимация лопания (см. assets/skills/warriors/icefragile/fragile_XX.png). */
    private static final class FragilityParticle {
        float wx, wy, delay, age;
        boolean started;
    }

    private final List<Streak> streaks = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<GroundFire> groundFires = new ArrayList<>();
    private final List<DoomEffect> doomEffects = new ArrayList<>();
    private final List<ImpactAnim> impactAnims = new ArrayList<>();
    private final List<MistEffect> mistEffects = new ArrayList<>();
    private final List<FragilityParticle> fragilityParticles = new ArrayList<>();

    // ── Отложенные удары молнии для "Грозы"/"Цепного Разряда" — см. класс-комментарий: у
    // WeatherRenderer только ОДИН активный разряд за раз, поэтому несколько ударов "Грозы"
    // разносятся по времени, а не бьют одновременно. ──────────────────────────────────────────
    private final List<float[]> pendingBolts = new ArrayList<>(); // {worldX, worldY, delaySec}

    // ── Цвета персистентных ауры/стойки/тумблера — см. Player.activeAuras, переключается в
    // SkillCaster.cast() для SkillKind.AURA/SUSTAINED/STANCE. ────────────────────────────────────
    private static final Map<String, float[]> TOGGLE_COLORS = new HashMap<>();
    static {
        TOGGLE_COLORS.put("herald_heal",           new float[]{0.35f, 0.90f, 0.45f});
        TOGGLE_COLORS.put("herald_defense",        new float[]{0.55f, 0.65f, 0.95f});
        TOGGLE_COLORS.put("herald_evasion",        new float[]{0.85f, 0.85f, 0.95f});
        TOGGLE_COLORS.put("herald_steel_will",     new float[]{0.80f, 0.80f, 0.85f});
        TOGGLE_COLORS.put("herald_suppression",    new float[]{0.55f, 0.25f, 0.65f});
        TOGGLE_COLORS.put("herald_stupor",         new float[]{0.40f, 0.75f, 0.95f});
        TOGGLE_COLORS.put("warrior_death_whirl",   new float[]{0.75f, 0.20f, 0.20f});
        TOGGLE_COLORS.put("warrior_madness",       new float[]{0.90f, 0.15f, 0.15f});
        TOGGLE_COLORS.put("elem_lightning_shield", new float[]{0.55f, 0.75f, 1.00f});
    }
    private float auraPulseT = 0f;

    // ── Настоящие спрайты аур (см. assets/skills/auras/) — там, где текстура уже есть, рисуем её
    // ВМЕСТО пунктирного кольца-заглушки (см. TOGGLE_COLORS выше), с анимацией через шейдер (см.
    // ShaderLibrary.aura(), assets/shaders/aura.vert|frag — один шейдер, режим анимации выбирается
    // юниформом u_mode). Умения без записи тут — просто ещё не готова картинка, откатываются на
    // дефолтное кольцо в drawPersistentAuras.
    private enum Mode { PULSE, BREATHE, SPIN, SPIN_STEP_FADE, RIPPLE, BURST }

    private static final class AuraAnim {
        final String file;
        final Mode mode;
        float pulseSpeed = 2.2f, pulseAmp = 0.12f, brightAmp = 0.35f, alphaAmp = 0.8f;
        float rotSpeed = 0.8f;
        // SPIN_STEP_FADE (режим 3): треугольный цикл — 3 ступени роста по stepDuration сек каждая
        // (растут на stepGrow за раз), потом те же 3 ступени обратно вниз до минимума (см. ТЗ
        // пользователя: "0.5 сек", "размер увеличения — небольшой", "идём вниз через все этапы обратно").
        float stepDuration = 0.5f, stepGrow = 0.12f;
        float burstPeriod = 4.5f, burstScale = 1.9f;

        AuraAnim(String file, Mode mode) { this.file = file; this.mode = mode; }
    }

    private static final Map<String, AuraAnim> AURA_ANIM = new HashMap<>();
    static {
        // 1 — Аура Исцеления: пульсация размера+яркости (уже было).
        AURA_ANIM.put("herald_heal", new AuraAnim("auras/texture_1.png", Mode.PULSE));
        // 2 — Аура Защиты: непрерывный "вдох" (растёт/бледнеет, снова маленькая/яркая), колышущиеся края.
        AURA_ANIM.put("herald_defense", new AuraAnim("auras/texture_2.png", Mode.BREATHE));
        // 3 — Аура Уклонения: вращение + пульсация яркости.
        AURA_ANIM.put("herald_evasion", new AuraAnim("auras/texture_3.png", Mode.SPIN));
        // 4 — Аура Стальной Воли: вращение + 3 ступенчатых скачка размера подряд с потерей яркости.
        AURA_ANIM.put("herald_steel_will", new AuraAnim("auras/texture_4.png", Mode.SPIN_STEP_FADE));
        // 5 — Аура Подавления: покачивание "как вода".
        AURA_ANIM.put("herald_suppression", new AuraAnim("auras/texture_5.png", Mode.RIPPLE));
        // 6 — Аура Оцепенения: раз в несколько секунд резкий рывок наружу+прозрачность, потом
        // медленный "рост из центра" обратно.
        AURA_ANIM.put("herald_stupor", new AuraAnim("auras/texture_6.png", Mode.BURST));
    }
    private static final float AURA_SPRITE_SIZE = 160f;
    // Сплюснутость по Y — та же изометрическая "тарелка под ногами", что у drawDottedRing, только
    // применена к самому спрайту (а не наложена рисованием отдельных точек по эллипсу): без этого
    // круглая текстура смотрела прямо в камеру, как наклейка, а не лежала на изометрическом полу.
    private static final float AURA_ISO_SQUASH = 0.55f;
    private final Map<String, Texture> auraTextureCache = new HashMap<>();

    /** Ауры/тумблеры (Player.activeAuras) — ЗЕМЛЯНОЙ слой под ногами игрока. Вызывается ДО
     *  Player.draw() (см. Editor.renderMap) — иначе спрайт ауры перекрывал бы персонажа сверху. */
    public void renderGround(SpriteBatch batch) {
        drawPersistentAuras(batch, Gdx.graphics.getDeltaTime());
    }

    /** Разовые эффекты (росчерки/кольца-всплески/молнии) — вызывается ПОСЛЕ отрисовки карты/игрока,
     *  тем же батчем/местом, что и WeatherRenderer.render (это летящие/бьющие эффекты поверх сцены,
     *  а не земляная "подложка" — им, в отличие от ауры, место над персонажем корректно). */
    public void render(SpriteBatch batch) {
        float dt = Gdx.graphics.getDeltaTime();
        updatePendingBolts(dt);
        updateAndDrawRings(batch, dt);
        updateAndDrawStreaks(batch, dt);
        updateAndDrawProjectiles(batch, dt);
        updateDoomEffects(dt);
        updateAndDrawGroundFires(batch, dt);
        updateAndDrawImpactAnims(batch, dt);
        updateAndDrawMist(batch, dt);
        updateAndDrawFragility(batch, dt);
    }

    /** Снимок текущих источников света от эффектов умений в Store.skillLightPoints (см. её
     *  описание) — читается MapObject.calcLitColor/Lighting.computeLitColor КАЖДЫЙ тайл. Вызывать
     *  РАНЬШЕ отрисовки карты за кадр (см. Editor.renderMap), а не из render()/renderGround() —
     *  иначе освещение отставало бы на кадр от только что подвинутых снарядов/огня. Позиции тут не
     *  меняются (движение — в updateAndDrawProjectiles/GroundFires), только читаются "как есть". */
    public void updateLightSnapshot() {
        float[][] pts = store.skillLightPoints;
        float radius = SKILL_LIGHT_RADIUS_TILES * store.tileSizeWidth;
        int idx = 0;
        for (Projectile p : projectiles) {
            if (idx >= pts.length) break;
            float t = p.life > 0 ? Math.min(1f, p.age / p.life) : 0f;
            pts[idx][0] = 1f;
            pts[idx][1] = p.wx1 + (p.wx2 - p.wx1) * t;
            pts[idx][2] = p.wy1 + (p.wy2 - p.wy1) * t;
            pts[idx][3] = radius;
            pts[idx][4] = SKILL_LIGHT_INTENSITY;
            pts[idx][5] = p.lightR; pts[idx][6] = p.lightG; pts[idx][7] = p.lightB;
            idx++;
        }
        for (GroundFire f : groundFires) {
            if (idx >= pts.length) break;
            pts[idx][0] = 1f;
            pts[idx][1] = f.wx;
            pts[idx][2] = f.wy;
            pts[idx][3] = radius;
            pts[idx][4] = SKILL_LIGHT_INTENSITY;
            // Наземный огонь пока только от огненных умений (импакт Огненного Шара/ДУМ/Волна Огня) —
            // тёплый цвет фиксирован, отдельного поля цвета в GroundFire не заводили.
            pts[idx][5] = SKILL_LIGHT_COLOR_FIRE[0]; pts[idx][6] = SKILL_LIGHT_COLOR_FIRE[1]; pts[idx][7] = SKILL_LIGHT_COLOR_FIRE[2];
            idx++;
        }
        // Хрупкость — тот же холодный свет, что у Ледяного Шипа, но ТОЛЬКО пока шар ещё не лопнул
        // (первая половина FRAGILE_LIFE, см. updateAndDrawFragility — "подсветка как у ледяного шара
        // пока не лопнет").
        for (FragilityParticle fp : fragilityParticles) {
            if (idx >= pts.length) break;
            if (!fp.started || fp.age >= FRAGILE_LIFE * 0.5f) continue;
            pts[idx][0] = 1f;
            pts[idx][1] = fp.wx;
            pts[idx][2] = fp.wy;
            pts[idx][3] = radius;
            pts[idx][4] = SKILL_LIGHT_INTENSITY;
            pts[idx][5] = SKILL_LIGHT_COLOR_ICE[0]; pts[idx][6] = SKILL_LIGHT_COLOR_ICE[1]; pts[idx][7] = SKILL_LIGHT_COLOR_ICE[2];
            idx++;
        }
        for (; idx < pts.length; idx++) pts[idx][0] = 0f;
    }

    // "2-3 клетки вокруг" (по требованию пользователя) — считается напрямую в тайлах карты
    // (store.tileSizeWidth), а не через торч-калибровку MapObject.torchRadius(lightPower) — та
    // калибрована отдельно (px/1 очко силы света) и не даёт ровно N тайлов радиуса.
    private static final float SKILL_LIGHT_RADIUS_TILES = 4f;
    private static final float SKILL_LIGHT_INTENSITY = 1.7f; // в 2 раза ярче прежнего (0.85)
    // Температура света: огонь — тёплый оранжевый, лёд — холодный синий (по требованию
    // пользователя, "не тёплый как у огня"), у каждого источника свой цвет (см. Projectile.lightR/G/B).
    private static final float[] SKILL_LIGHT_COLOR_FIRE = {1.00f, 0.55f, 0.20f};
    private static final float[] SKILL_LIGHT_COLOR_ICE  = {0.35f, 0.65f, 1.00f};

    /** Точка входа из SkillCaster — по id умения проигрывает соответствующий визуальный эффект.
     *  AURA/SUSTAINED/STANCE (тумблер) сюда не попадают — их включение/выключение обрабатывает сам
     *  SkillCaster через Player.activeAuras, а отрисовка идёт через drawPersistentAuras. */
    public void trigger(String skillId, int level) {
        float[] center = playerScreenPos();
        float[] target = aimScreenPos();

        switch (skillId) {
            // ── Воитель: ближний бой — росчерк от игрока к точке прицела (курсор) ───────────────
            case "warrior_strike":
                addStreak(center, target, 1f, 1f, 1f, 0.18f);
                addSplashIfInvested(target);
                break;
            case "warrior_blade_dash":
                addStreak(center, target, 1f, 1f, 1f, 0.22f);
                addRing(target, 26f, 0.9f, 0.9f, 1f, 0.30f);
                addSplashIfInvested(target);
                break;
            case "warrior_shadow_blade":
                addStreak(center, target, 0.55f, 0.35f, 0.75f, 0.22f);
                addStreak(offset(center, 6, -4), offset(target, 6, -4), 0.55f, 0.35f, 0.75f, 0.20f);
                addSplashIfInvested(target);
                break;

            // ── Стихийник: Огонь — снаряд/конус/горящая земля ──────────────────────────────────
            case "elem_fire_ball":
                if (store.player != null) {
                    addFireballProjectile(store.player.worldX, store.player.worldY, store.cursorWorldX, store.cursorWorldY);
                }
                break;
            case "elem_fire_wave":
                triggerFireWave();
                break;
            case "elem_fire_doom":
                triggerFireDoom(level);
                break;

            // ── Стихийник: Холод — снаряд/область/накопительный дебафф ─────────────────────────
            case "elem_cold_spike":
                if (store.player != null) {
                    addIceSpikeProjectile(store.player.worldX, store.player.worldY, store.cursorWorldX, store.cursorWorldY);
                }
                break;
            case "elem_cold_mist":
                triggerIceMist(level);
                break;
            case "elem_cold_fragility":
                triggerColdFragility(level);
                break;

            // ── Стихийник: Электро — переиспользуем разряд молнии из WeatherRenderer ───────────
            case "elem_lightning_chain":
                scheduleBolt(store.cursorWorldX, store.cursorWorldY, 0f);
                break;
            case "elem_lightning_storm": { // "Гроза" — несколько ударов подряд по области цели
                float baseX = store.cursorWorldX, baseY = store.cursorWorldY;
                scheduleBolt(baseX,      baseY,      0.00f);
                scheduleBolt(baseX + 40, baseY - 30, 0.15f);
                scheduleBolt(baseX - 30, baseY + 45, 0.30f);
                break;
            }

            default:
                break; // warrior_stun/warrior_crit/warrior_splash — чистые пассивные статы, без анимации на каст (не бинд.)
        }
    }

    // ── Персистентные ауры/стойки/тумблер (Player.activeAuras) ─────────────────────────────────
    private void drawPersistentAuras(SpriteBatch batch, float dt) {
        if (store.player == null || store.player.activeAuras.isEmpty()) return;
        auraPulseT += dt; // только для отката на пунктирное кольцо ниже — у настоящих спрайтов
                           // пульсацию делает шейдер (см. drawAuraSprite/store.cloudTime).
        float pulse = 0.75f + 0.25f * (float) Math.sin(auraPulseT * 3f);
        float[] center = playerScreenPos();
        // Ауры-спрайты садятся под ноги (ниже центра спрайта игрока), а не в грудь — центр игрока
        // (см. playerScreenPos/Player.draw) визуально приходится примерно на середину тела.
        float feetY = center[1] - store.tileSizeHeight * 0.45f;
        int i = 0;
        for (String id : store.player.activeAuras) {
            AuraAnim anim = AURA_ANIM.get(id);
            Texture tex = anim != null ? loadAuraTexture(anim) : null;
            if (tex != null) {
                drawAuraSprite(batch, tex, anim, center[0], feetY);
            } else {
                float[] c = TOGGLE_COLORS.getOrDefault(id, new float[]{0.8f, 0.8f, 0.8f});
                float radius = 46f + i * 8f; // разные ауры — разные вложенные кольца, чтобы не сливались
                drawDottedRing(batch, center[0], center[1], radius, c[0], c[1], c[2], 0.5f * pulse);
            }
            i++;
        }
    }

    // Базовая непрозрачность спрайта ауры — по требованию пользователя (шейдер поверх неё ещё
    // добавляет свою пульсацию яркости/альфы в зависимости от режима, см. AuraAnim.Mode).
    private static final float AURA_ALPHA = 0.5f;

    /** Настоящий спрайт ауры (см. AURA_ANIM) с анимацией через aura.vert/frag (см.
     *  ShaderLibrary.aura()) — тот же паттерн setShader/uniform/setShader(null), что Editor
     *  использует для water/shore-тайлов. Без шейдера (не скомпилировался) — просто статичный
     *  спрайт, без анимации, лучше, чем совсем ничего. */
    private void drawAuraSprite(SpriteBatch batch, Texture tex, AuraAnim anim, float cx, float cy) {
        float w = AURA_SPRITE_SIZE;
        float h = AURA_SPRITE_SIZE * AURA_ISO_SQUASH;
        ShaderProgram shader = ShaderLibrary.aura();
        batch.setColor(1f, 1f, 1f, AURA_ALPHA);
        if (shader == null) {
            batch.draw(tex, cx - w / 2f, cy - h / 2f, w, h);
            batch.setColor(1f, 1f, 1f, 1f);
            return;
        }
        batch.setShader(shader);
        shader.setUniformf("u_center", cx, cy);
        shader.setUniformf("u_time", store.cloudTime);
        shader.setUniformf("u_mode", (float) anim.mode.ordinal());
        shader.setUniformf("u_pulseSpeed", anim.pulseSpeed);
        shader.setUniformf("u_pulseAmp", anim.pulseAmp);
        shader.setUniformf("u_brightAmp", anim.brightAmp);
        shader.setUniformf("u_alphaAmp", anim.alphaAmp);
        shader.setUniformf("u_rotSpeed", anim.rotSpeed);
        shader.setUniformf("u_stepDuration", anim.stepDuration);
        shader.setUniformf("u_stepGrow", anim.stepGrow);
        shader.setUniformf("u_burstPeriod", anim.burstPeriod);
        shader.setUniformf("u_burstScale", anim.burstScale);
        shader.setUniformf("u_squash", AURA_ISO_SQUASH);
        batch.draw(tex, cx - w / 2f, cy - h / 2f, w, h);
        batch.setShader(null);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    /** Грузит и кэширует текстуру ауры по относительному пути (см. AuraAnim.file) — тот же
     *  паттерн, что SystemUI.loadSkillIcon (assets/skills/... — реальный путь на диске). */
    private Texture loadAuraTexture(AuraAnim anim) {
        Texture cached = auraTextureCache.get(anim.file);
        if (cached != null) return cached;
        java.io.File f = Gdx.files.internal("assets/skills/" + anim.file).file();
        if (!f.exists()) return null;
        try {
            Texture tex = new Texture(Gdx.files.absolute(f.getAbsolutePath()));
            auraTextureCache.put(anim.file, tex);
            return tex;
        } catch (Exception e) {
            Gdx.app.error("SkillEffectRenderer", "Не удалось загрузить текстуру ауры: " + anim.file, e);
            return null;
        }
    }

    // ── Разовые эффекты: обновление/отрисовка/удаление по истечении life ───────────────────────
    private void updateAndDrawRings(SpriteBatch batch, float dt) {
        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring ring = it.next();
            ring.age += dt;
            if (ring.age >= ring.life) { it.remove(); continue; }
            float t = ring.age / ring.life;
            float radius = ring.maxRadius * (0.3f + 0.7f * t);
            drawDottedRing(batch, ring.cx, ring.cy, radius, ring.r, ring.g, ring.b, 1f - t);
        }
    }

    private void updateAndDrawStreaks(SpriteBatch batch, float dt) {
        for (Iterator<Streak> it = streaks.iterator(); it.hasNext(); ) {
            Streak s = it.next();
            s.age += dt;
            if (s.age >= s.life) { it.remove(); continue; }
            float alpha = 1f - (s.age / s.life);
            drawSeg(batch, s.x1, s.y1, s.x2, s.y2, 6f, s.r, s.g, s.b, alpha * 0.5f); // мягкое "свечение"
            drawSeg(batch, s.x1, s.y1, s.x2, s.y2, 2f, 1f, 1f, 1f, alpha);           // яркое ядро
        }
    }

    /** Огненный Шар — летящий снаряд с покадровой анимацией пламени (28 кадров, см. assets/skills/
     *  warriors/fireball/) вместо прежнего мгновенного росчерка+кольца: реально летит от игрока к
     *  курсору, поворачиваясь по направлению полёта, и по прибытии рождает импакт-кольцо. Кадры уже
     *  смотрят "вправо" (яркое ядро справа, хвост пламени слева) — поворот считается напрямую через
     *  atan2 направления полёта, без доп. смещения (в отличие от drawSeg, который крутит вертикальную
     *  полоску-пиксель). */
    private static final float FIREBALL_SPEED = 900f; // px/сек экранных
    // Размер отрисовки — фикс. (кадры чуть разного px-размера при экспорте). История правок по
    // требованию пользователя: было 96×55 → -70% (×0.3) → +50% от того результата (×0.3×1.5=×0.45).
    private static final float FIREBALL_W = 96f * 0.45f, FIREBALL_H = 55f * 0.45f;
    private static Texture[] fireballFrames; // лениво грузится один раз, общий на все касты

    private void addFireballProjectile(float fromWX, float fromWY, float toWX, float toWY) {
        Texture[] frames = loadFireballFrames();
        float[] s1 = worldToScreen(fromWX, fromWY);
        float[] s2 = worldToScreen(toWX, toWY);
        if (frames == null) { // текстуры не нашлись — не теряем эффект целиком, откат на старое поведение
            addStreak(s1, s2, 1f, 0.45f, 0.15f, 0.20f);
            addRing(s2, 24f, 1f, 0.50f, 0.15f, 0.35f);
            return;
        }
        float screenDist = (float) Math.hypot(s2[0] - s1[0], s2[1] - s1[1]);
        Projectile p = new Projectile();
        p.wx1 = fromWX; p.wy1 = fromWY; p.wx2 = toWX; p.wy2 = toWY;
        p.angleDeg = (float) Math.toDegrees(Math.atan2(s2[1] - s1[1], s2[0] - s1[0]));
        p.life = Math.max(0.15f, Math.min(0.6f, screenDist / FIREBALL_SPEED));
        p.age = 0f;
        p.w = FIREBALL_W; p.h = FIREBALL_H;
        p.frames = frames;
        p.frameRate = 24f;
        p.igniteFireOnImpact = true;
        p.lightR = SKILL_LIGHT_COLOR_FIRE[0]; p.lightG = SKILL_LIGHT_COLOR_FIRE[1]; p.lightB = SKILL_LIGHT_COLOR_FIRE[2];
        projectiles.add(p);
    }

    private Texture[] loadFireballFrames() {
        if (fireballFrames != null) return fireballFrames;
        Texture[] frames = new Texture[28];
        for (int i = 0; i < 28; i++) {
            String name = String.format("warriors/fireball/Effects_Fire_0_%02d.png", i + 1);
            java.io.File f = Gdx.files.internal("assets/skills/" + name).file();
            if (!f.exists()) return null; // не все кадры на месте — не рискуем показать рваную анимацию
            try {
                frames[i] = new Texture(Gdx.files.absolute(f.getAbsolutePath()));
            } catch (Exception e) {
                Gdx.app.error("SkillEffectRenderer", "Не удалось загрузить кадр фаербола: " + name, e);
                return null;
            }
        }
        fireballFrames = frames;
        return frames;
    }

    /** Ледяной Шип — тот же подход, что Огненный Шар: летящий снаряд (10 кадров, см. assets/skills/
     *  warriors/iceball/) от игрока к курсору, по прибитии/на препятствии — разовая анимация раскола
     *  (5 кадров, см. assets/skills/warriors/ice/) в точке остановки, БЕЗ зацикленного наземного
     *  огня (лёд не горит долго — см. spawnIceShatter/ImpactAnim). */
    private static final float ICE_SPIKE_SPEED = 900f;
    private static final float ICE_SPIKE_SIZE = 42f; // кадры квадратные (см. нарезку water_ice_*)
    private static final int ICE_SPIKE_FRAME_COUNT = 10;
    private static Texture[] iceSpikeFrames;

    private static final float ICE_SHATTER_SIZE = 64f;
    private static final int ICE_SHATTER_FRAME_COUNT = 5;
    private static Texture[] iceShatterFrames;

    private void addIceSpikeProjectile(float fromWX, float fromWY, float toWX, float toWY) {
        Texture[] frames = loadIceSpikeFrames();
        float[] s1 = worldToScreen(fromWX, fromWY);
        float[] s2 = worldToScreen(toWX, toWY);
        if (frames == null) { // текстур нет — откат на прежний росчерк+кольцо, эффект не теряется совсем
            addStreak(s1, s2, 0.55f, 0.85f, 1f, 0.20f);
            addRing(s2, 22f, 0.60f, 0.90f, 1f, 0.35f);
            return;
        }
        float screenDist = (float) Math.hypot(s2[0] - s1[0], s2[1] - s1[1]);
        Projectile p = new Projectile();
        p.wx1 = fromWX; p.wy1 = fromWY; p.wx2 = toWX; p.wy2 = toWY;
        p.angleDeg = (float) Math.toDegrees(Math.atan2(s2[1] - s1[1], s2[0] - s1[0]));
        p.life = Math.max(0.15f, Math.min(0.6f, screenDist / ICE_SPIKE_SPEED));
        p.age = 0f;
        p.w = ICE_SPIKE_SIZE; p.h = ICE_SPIKE_SIZE;
        p.frames = frames;
        p.frameRate = 20f;
        p.iceShatterOnImpact = true;
        p.lightR = SKILL_LIGHT_COLOR_ICE[0]; p.lightG = SKILL_LIGHT_COLOR_ICE[1]; p.lightB = SKILL_LIGHT_COLOR_ICE[2];
        projectiles.add(p);
    }

    private Texture[] loadIceSpikeFrames() {
        if (iceSpikeFrames != null) return iceSpikeFrames;
        Texture[] frames = new Texture[ICE_SPIKE_FRAME_COUNT];
        for (int i = 0; i < ICE_SPIKE_FRAME_COUNT; i++) {
            String name = String.format("warriors/iceball/water_ice_%02d.png", i + 1);
            java.io.File f = Gdx.files.internal("assets/skills/" + name).file();
            if (!f.exists()) return null;
            try {
                frames[i] = new Texture(Gdx.files.absolute(f.getAbsolutePath()));
            } catch (Exception e) {
                Gdx.app.error("SkillEffectRenderer", "Не удалось загрузить кадр ледяного шипа: " + name, e);
                return null;
            }
        }
        iceSpikeFrames = frames;
        return frames;
    }

    private Texture[] loadIceShatterFrames() {
        if (iceShatterFrames != null) return iceShatterFrames;
        Texture[] frames = new Texture[ICE_SHATTER_FRAME_COUNT];
        for (int i = 0; i < ICE_SHATTER_FRAME_COUNT; i++) {
            String name = String.format("warriors/ice/ice_shatter_%02d.png", i + 1);
            java.io.File f = Gdx.files.internal("assets/skills/" + name).file();
            if (!f.exists()) return null;
            try {
                frames[i] = new Texture(Gdx.files.absolute(f.getAbsolutePath()));
            } catch (Exception e) {
                Gdx.app.error("SkillEffectRenderer", "Не удалось загрузить кадр раскола льда: " + name, e);
                return null;
            }
        }
        iceShatterFrames = frames;
        return frames;
    }

    /** Разовая анимация раскола льда в точке остановки снаряда (см. Projectile.iceShatterOnImpact). */
    private void spawnIceShatter(float wx, float wy) {
        Texture[] frames = loadIceShatterFrames();
        if (frames == null) { addRing(worldToScreen(wx, wy), 22f, 0.6f, 0.9f, 1f, 0.35f); return; }
        ImpactAnim anim = new ImpactAnim();
        anim.wx = wx; anim.wy = wy;
        anim.age = 0f;
        anim.frameRate = 18f;
        anim.life = frames.length / anim.frameRate;
        anim.w = ICE_SHATTER_SIZE; anim.h = ICE_SHATTER_SIZE;
        anim.frames = frames;
        impactAnims.add(anim);
    }

    private void updateAndDrawImpactAnims(SpriteBatch batch, float dt) {
        for (Iterator<ImpactAnim> it = impactAnims.iterator(); it.hasNext(); ) {
            ImpactAnim a = it.next();
            a.age += dt;
            if (a.age >= a.life) { it.remove(); continue; }
            int frameIdx = Math.min(a.frames.length - 1, (int) (a.age * a.frameRate));
            float[] screen = worldToScreen(a.wx, a.wy);
            batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(a.frames[frameIdx], screen[0] - a.w / 2f, screen[1] - a.h / 2f, a.w, a.h);
        }
    }

    private void updateAndDrawProjectiles(SpriteBatch batch, float dt) {
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            p.age += dt;
            float t = Math.min(1f, p.life > 0 ? p.age / p.life : 1f);
            float wx = p.wx1 + (p.wx2 - p.wx1) * t;
            float wy = p.wy1 + (p.wy2 - p.wy1) * t;

            // Снаряды летят по воздуху — вода им не помеха (см. Player.isFlightBlockedAt, без учёта
            // воды, в отличие от наземных эффектов), но настоящее препятствие по высоте (лес/камни/
            // здания) всё равно останавливает — снаряд не долетает до курсора, а сразу переходит в
            // огонь/раскол ТАМ, где остановился.
            boolean blocked = store.player != null && store.player.isFlightBlockedAt(wx, wy);
            if (blocked || p.age >= p.life) {
                if (p.igniteFireOnImpact) igniteGroundFire(wx, wy);
                if (p.iceShatterOnImpact) spawnIceShatter(wx, wy);
                it.remove();
                continue;
            }

            float[] screen = worldToScreen(wx, wy);
            int frameIdx = ((int) (p.age * p.frameRate)) % p.frames.length;
            Texture frame = p.frames[frameIdx];
            batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(frame, screen[0] - p.w / 2f, screen[1] - p.h / 2f, p.w / 2f, p.h / 2f, p.w, p.h,
                1f, 1f, p.angleDeg, 0, 0, frame.getWidth(), frame.getHeight(), false, false);
        }
    }

    // ── Наземный огонь (импакт Огненного Шара + Волна Огня) ────────────────────────────────────
    private static final int FIRE_FRAME_COUNT = 25;
    private static Texture[] fireFrames; // лениво грузится один раз, общий на импакт+волну
    private static final float FIRE_W = 48f, FIRE_H = 48f;

    private Texture[] loadFireFrames() {
        if (fireFrames != null) return fireFrames;
        Texture[] frames = new Texture[FIRE_FRAME_COUNT];
        for (int i = 0; i < FIRE_FRAME_COUNT; i++) {
            String name = String.format("warriors/fire/fireB%04d.png", i + 1);
            java.io.File f = Gdx.files.internal("assets/skills/" + name).file();
            if (!f.exists()) return null;
            try {
                frames[i] = new Texture(Gdx.files.absolute(f.getAbsolutePath()));
            } catch (Exception e) {
                Gdx.app.error("SkillEffectRenderer", "Не удалось загрузить кадр огня: " + name, e);
                return null;
            }
        }
        fireFrames = frames;
        return frames;
    }

    /** Огненный Шар — недолгое возгорание земли в точке попадания (см. addFireballProjectile). */
    private void igniteGroundFire(float wx, float wy) {
        if (loadFireFrames() == null) return; // текстур нет — просто ничего не поджигаем, не критично
        GroundFire fire = new GroundFire();
        fire.wx = wx; fire.wy = wy;
        fire.traveling = false;
        fire.life = 1.3f;
        fire.w = FIRE_W; fire.h = FIRE_H;
        groundFires.add(fire);
    }

    /** Волна Огня — по требованию пользователя: угол охвата считается по направлению на курсор
     *  (с небольшого радиуса от игрока, т.е. просто направление, а не точная удалённая точка); в
     *  позиции игрока разом спавнится десяток очагов огня, они расходятся дугой (веером по углам
     *  внутри FIRE_WAVE_ARC_DEG) и гаснут через FIRE_WAVE_MAX_TILES клеток пути. */
    private static final int FIRE_WAVE_COUNT = 10;
    private static final float FIRE_WAVE_ARC_DEG = 70f;
    private static final float FIRE_WAVE_MAX_TILES = 5f;               // дальность вдвое меньше прежней (было 10)
    private static final float FIRE_WAVE_SPEED_TILES_PER_SEC = 6f;     // скорость вдвое больше прежней (было 3)

    private void triggerFireWave() {
        if (store.player == null || loadFireFrames() == null) return;
        float px = store.player.worldX, py = store.player.worldY;
        float dx = store.cursorWorldX - px, dy = store.cursorWorldY - py;
        float baseAngle = (float) Math.atan2(dy, dx);
        float arcRad = (float) Math.toRadians(FIRE_WAVE_ARC_DEG);
        float tile = store.tileSizeWidth;

        for (int i = 0; i < FIRE_WAVE_COUNT; i++) {
            float t = FIRE_WAVE_COUNT > 1 ? i / (float) (FIRE_WAVE_COUNT - 1) : 0.5f;
            float angle = baseAngle - arcRad / 2f + arcRad * t;
            GroundFire fire = new GroundFire();
            fire.wx = px; fire.wy = py;
            fire.traveling = true;
            fire.dirX = (float) Math.cos(angle);
            fire.dirY = (float) Math.sin(angle);
            fire.speed = FIRE_WAVE_SPEED_TILES_PER_SEC * tile;
            fire.maxDistance = FIRE_WAVE_MAX_TILES * tile;
            fire.travelled = 0f;
            fire.w = FIRE_W; fire.h = FIRE_H;
            groundFires.add(fire);
        }
    }

    /** ДУМ — по требованию пользователя: после активации в течение DOOM_LIFE (6) сек в области
     *  radiusTiles (м=клетки, см. SkillCatalog "elem_fire_doom" radius_m) вокруг игрока загорается
     *  множество очагов огня; область следует за игроком (позиция берётся заново на каждый тик, а
     *  не фиксируется на момент каста); каждая точка спавна проверяется на проходимость (см.
     *  Player.isBlockedAt) — огонь появляется только там, где мог бы пройти сам игрок. */
    private static final float DOOM_LIFE = 6f;
    // Интервал спавна ПРИ РАДИУСЕ=1 клетка (кол-во огней ×8 по прошлому требованию пользователя,
    // см. историю); фактический интервал делится на radiusTiles (см. updateDoomEffects) — "кол-во
    // огней умножай на радиус": чем больше зона, тем больше очагов огня, а не одна и та же плотность.
    private static final float DOOM_SPAWN_INTERVAL_AT_R1 = 0.15f / 8f / 4f; // изначальное число огней ×4

    private void triggerFireDoom(int level) {
        if (store.player == null || loadFireFrames() == null) return;
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get("elem_fire_doom");
        double radiusM = def != null ? def.compute(level).getOrDefault("radius_m", 3.0) : 3.0;
        DoomEffect doom = new DoomEffect();
        doom.age = 0f;
        doom.spawnTimer = 0f;
        doom.radiusTiles = (float) radiusM;
        doomEffects.add(doom);
    }

    private void updateDoomEffects(float dt) {
        if (doomEffects.isEmpty() || store.player == null) return;
        for (Iterator<DoomEffect> it = doomEffects.iterator(); it.hasNext(); ) {
            DoomEffect doom = it.next();
            doom.age += dt;
            if (doom.age >= DOOM_LIFE) { it.remove(); continue; }
            doom.spawnTimer -= dt;
            if (doom.spawnTimer > 0f) continue;
            doom.spawnTimer += DOOM_SPAWN_INTERVAL_AT_R1 / Math.max(1f, doom.radiusTiles);

            float radiusPx = doom.radiusTiles * store.tileSizeWidth;
            float ang = (float) (Math.random() * Math.PI * 2);
            float dist = (float) (Math.sqrt(Math.random()) * radiusPx); // равномерно по площади круга
            float wx = store.player.worldX + (float) Math.cos(ang) * dist;
            float wy = store.player.worldY + (float) Math.sin(ang) * dist;
            if (store.player.isBlockedAt(wx, wy)) continue; // непроходимая точка — просто пропускаем тик
            igniteGroundFire(wx, wy);
        }
    }

    // ── Ледяной Туман (см. assets/skills/warriors/icefog/fog.png, ShaderLibrary.mist) ───────────────
    private static final float MIST_LIFE = 5f;          // см. SkillCatalog "elem_cold_mist" — "5 сек"
    private static final float MIST_FADE_IN_TIME = 0.6f;  // проступают из нулевой прозрачности за N сек
    private static final float MIST_FADE_OUT_TIME = 1.0f; // и обратно гаснут в неё за N сек в конце жизни
    private static final int MIST_PUFF_COUNT = 10;        // было 15, потом 5 — по требованию пользователя
    // Клочки размером СОПОСТАВИМЫМ с радиусом всей области и УЗКИМ разбросом позиций (0.35 радиуса,
    // меньше половины размера клочка) — гарантирует сильное перекрытие, чтобы 15 копий одной и той
    // же текстуры сливались в единое облако, а не читались отдельными пятнами (см. класс-комментарий).
    private static final float MIST_PUFF_SIZE_MIN = 0.9f, MIST_PUFF_SIZE_MAX = 1.3f; // × radiusPx
    private static final float MIST_PUFF_SPREAD = 0.5f; // × radiusPx (немного больше, было 0.35)
    private static final float MIST_PUFF_ROT_RANGE = 20f; // ± градусов от горизонтали (0/180)
    private static final float MIST_MAX_ALPHA = 0.5f; // туман полупрозрачный даже на пике огибающей
    private static Texture fogTexture;

    private Texture loadFogTexture() {
        if (fogTexture != null) return fogTexture;
        java.io.File f = Gdx.files.internal("assets/skills/warriors/icefog/fog.png").file();
        if (!f.exists()) return null;
        try {
            fogTexture = new Texture(Gdx.files.absolute(f.getAbsolutePath()));
            return fogTexture;
        } catch (Exception e) {
            Gdx.app.error("SkillEffectRenderer", "Не удалось загрузить fog.png", e);
            return null;
        }
    }

    /** Ледяной Туман — "в точке каста должен подниматься туман": в отличие от ДУМа область НЕ
     *  следует за игроком, она встаёт неподвижно на месте курсора (это область на местности, см.
     *  SkillCatalog "elem_cold_mist" — "создаёт на местности область тумана"). несколько клочков тумана
     *  разбрасываются ОДИН раз при касте (случайные позиция/поворот/размер, сильно перекрываясь) —
     *  дальше не двигаются, вся группа вместе проступает из прозрачности и гаснет обратно в неё. */
    private void triggerIceMist(int level) {
        if (loadFogTexture() == null) return;
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get("elem_cold_mist");
        double radiusM = def != null ? def.compute(level).getOrDefault("radius_m", 4.0) : 4.0;
        float radiusPx = (float) radiusM * store.tileSizeWidth;

        MistEffect mist = new MistEffect();
        mist.wx = store.cursorWorldX; mist.wy = store.cursorWorldY;
        mist.age = 0f;
        mist.life = MIST_LIFE;
        mist.radiusTiles = (float) radiusM;
        mist.offX = new float[MIST_PUFF_COUNT];
        mist.offY = new float[MIST_PUFF_COUNT];
        mist.rotDeg = new float[MIST_PUFF_COUNT];
        mist.sizeMul = new float[MIST_PUFF_COUNT];
        for (int i = 0; i < MIST_PUFF_COUNT; i++) {
            float ang = (float) (Math.random() * Math.PI * 2);
            float dist = (float) (Math.sqrt(Math.random()) * radiusPx * MIST_PUFF_SPREAD); // узкий разброс — см. выше
            mist.offX[i] = (float) Math.cos(ang) * dist;
            mist.offY[i] = (float) Math.sin(ang) * dist;
            // Текстура — широкое плоское облако (см. triggerIceMist/loadFogTexture): поворот держим
            // БЛИЗКО к горизонтали (±MIST_PUFF_ROT_RANGE от 0 или 180), не в полном диапазоне 0..360 —
            // повороты ближе к 90°/270° превращают широкий пласт в узкую вертикальную полоску
            // (по требованию пользователя: "повороты на 90 градусов плохо выглядят").
            float baseAngle = Math.random() < 0.5 ? 0f : 180f;
            mist.rotDeg[i] = baseAngle + (float) ((Math.random() * 2 - 1) * MIST_PUFF_ROT_RANGE);
            mist.sizeMul[i] = radiusPx * (MIST_PUFF_SIZE_MIN + (float) Math.random() * (MIST_PUFF_SIZE_MAX - MIST_PUFF_SIZE_MIN));
        }
        mistEffects.add(mist);
    }

    private void updateAndDrawMist(SpriteBatch batch, float dt) {
        if (mistEffects.isEmpty()) return;
        Texture tex = fogTexture;
        if (tex == null) { mistEffects.clear(); return; }
        ShaderProgram shader = ShaderLibrary.mist();

        for (Iterator<MistEffect> it = mistEffects.iterator(); it.hasNext(); ) {
            MistEffect mist = it.next();
            mist.age += dt;
            if (mist.age >= mist.life) { it.remove(); continue; }

            // Вся группа проступает из нулевой прозрачности и гаснет обратно в неё же — единая
            // огибающая на все клочки (см. класс-комментарий MistEffect: "не раскладываться").
            float fadeOutStart = mist.life - MIST_FADE_OUT_TIME;
            float envelope = mist.age < MIST_FADE_IN_TIME ? mist.age / MIST_FADE_IN_TIME
                : mist.age <= fadeOutStart ? 1f
                : Math.max(0f, 1f - (mist.age - fadeOutStart) / MIST_FADE_OUT_TIME);
            // Туман должен быть полупрозрачным даже "на пике" — envelope достигает 1.0, но итоговая
            // альфа капается MIST_MAX_ALPHA (см. константу), сквозь него видно землю/траву под ним.
            float alphaMul = envelope * MIST_MAX_ALPHA;
            if (alphaMul <= 0f) continue;

            for (int i = 0; i < MIST_PUFF_COUNT; i++) {
                float[] screen = worldToScreen(mist.wx + mist.offX[i], mist.wy + mist.offY[i]);
                // sizeMul уже хранит готовую ширину клочка (см. triggerIceMist) — высота сохраняет
                // исходную пропорцию текстуры (широкое плоское облако, не квадрат).
                float w = mist.sizeMul[i];
                float h = w * ((float) tex.getHeight() / tex.getWidth());

                if (shader == null) {
                    batch.setColor(1f, 1f, 1f, alphaMul);
                    batch.draw(tex, screen[0] - w / 2f, screen[1] - h / 2f,
                        w / 2f, h / 2f, w, h, 1f, 1f, mist.rotDeg[i],
                        0, 0, tex.getWidth(), tex.getHeight(), false, false);
                    continue;
                }
                batch.setShader(shader);
                shader.setUniformf("u_time", store.cloudTime + i); // сдвиг фазы — клочки колышутся не в такт
                shader.setUniformf("u_alphaMul", alphaMul);
                batch.setColor(1f, 1f, 1f, 1f);
                batch.draw(tex, screen[0] - w / 2f, screen[1] - h / 2f,
                    w / 2f, h / 2f, w, h, 1f, 1f, mist.rotDeg[i],
                    0, 0, tex.getWidth(), tex.getHeight(), false, false);
                batch.setShader(null);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    // ── Хрупкость (см. assets/skills/warriors/icefragile/fragile_01..40.png) ───────────────────────
    private static final int FRAGILE_FRAME_COUNT = 40;
    private static final int FRAGILE_PARTICLE_COUNT = 30;    // "их должно быть штук 30 суммарно"
    private static final float FRAGILE_SPAWN_WINDOW = 1.2f;  // разброс случайных задержек появления
    private static final float FRAGILE_LIFE = 1f;            // "длительность общей анимации 1 сек"
    private static final float FRAGILE_FADE_IN_TIME = 0.15f; // "быстро становится видимым"
    private static final float FRAGILE_POP_START = 0.5f;     // "где-то на середине пути — запускается лопание"
    private static final float FRAGILE_RISE_HEIGHT = 34f;    // на сколько px "поднимается" за всю жизнь
    private static final float FRAGILE_SIZE = 70f;
    private static Texture[] fragileFrames;

    private Texture[] loadFragileFrames() {
        if (fragileFrames != null) return fragileFrames;
        Texture[] frames = new Texture[FRAGILE_FRAME_COUNT];
        for (int i = 0; i < FRAGILE_FRAME_COUNT; i++) {
            String name = String.format("warriors/icefragile/fragile_%02d.png", i + 1);
            java.io.File f = Gdx.files.internal("assets/skills/" + name).file();
            if (!f.exists()) return null;
            try {
                frames[i] = new Texture(Gdx.files.absolute(f.getAbsolutePath()));
            } catch (Exception e) {
                Gdx.app.error("SkillEffectRenderer", "Не удалось загрузить кадр Хрупкости: " + name, e);
                return null;
            }
        }
        fragileFrames = frames;
        return frames;
    }

    /** Хрупкость — работает в той же области, что и Ледяной Туман (см. SkillCatalog "elem_cold_mist"
     *  radius_m — у самой Хрупкости своего радиуса нет). 30 частиц разбрасываются по области сразу,
     *  но каждая ждёт свою случайную задержку (см. FragilityParticle.delay) перед тем как начать
     *  анимацию "шар поднимается и лопается" (см. updateAndDrawFragility). */
    private void triggerColdFragility(int level) {
        if (loadFragileFrames() == null) return;
        SkillCatalog.SkillDef mistDef = SkillCatalog.SKILLS.get("elem_cold_mist");
        double radiusM = mistDef != null ? mistDef.compute(level).getOrDefault("radius_m", 4.0) : 4.0;
        // Область каста Хрупкости — вдвое меньше, чем у Ледяного Тумана (по требованию пользователя),
        // не путать с самим радиусом умения elem_cold_mist — тот не трогаем.
        float radiusPx = (float) radiusM * store.tileSizeWidth * 0.5f;
        float cx = store.cursorWorldX, cy = store.cursorWorldY;

        for (int i = 0; i < FRAGILE_PARTICLE_COUNT; i++) {
            float ang = (float) (Math.random() * Math.PI * 2);
            float dist = (float) (Math.sqrt(Math.random()) * radiusPx); // равномерно по площади круга
            FragilityParticle fp = new FragilityParticle();
            fp.wx = cx + (float) Math.cos(ang) * dist;
            fp.wy = cy + (float) Math.sin(ang) * dist;
            fp.delay = (float) (Math.random() * FRAGILE_SPAWN_WINDOW);
            fp.age = 0f;
            fp.started = false;
            fragilityParticles.add(fp);
        }
    }

    private void updateAndDrawFragility(SpriteBatch batch, float dt) {
        if (fragilityParticles.isEmpty()) return;
        Texture[] frames = fragileFrames;
        if (frames == null) { fragilityParticles.clear(); return; }

        for (Iterator<FragilityParticle> it = fragilityParticles.iterator(); it.hasNext(); ) {
            FragilityParticle fp = it.next();
            if (!fp.started) {
                fp.delay -= dt;
                if (fp.delay <= 0f) fp.started = true;
                continue; // невидим, пока не подошла своя случайная задержка
            }
            fp.age += dt;
            if (fp.age >= FRAGILE_LIFE) { it.remove(); continue; }

            float t = fp.age / FRAGILE_LIFE;
            float alpha = Math.min(1f, t / FRAGILE_FADE_IN_TIME); // "быстро становится видимым"
            int frameIdx;
            if (t < FRAGILE_POP_START) {
                frameIdx = 0; // ещё поднимается, не лопнул — держим первый кадр (собранный "шар")
            } else {
                float frac = (t - FRAGILE_POP_START) / (1f - FRAGILE_POP_START);
                frameIdx = Math.min(FRAGILE_FRAME_COUNT - 1, (int) (frac * FRAGILE_FRAME_COUNT));
            }

            float[] screen = worldToScreen(fp.wx, fp.wy);
            float riseY = screen[1] + FRAGILE_RISE_HEIGHT * t; // "от поверхности вверх поднимается"
            Texture frame = frames[frameIdx];
            batch.setColor(1f, 1f, 1f, alpha);
            batch.draw(frame, screen[0] - FRAGILE_SIZE / 2f, riseY - FRAGILE_SIZE / 2f, FRAGILE_SIZE, FRAGILE_SIZE);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void updateAndDrawGroundFires(SpriteBatch batch, float dt) {
        if (groundFires.isEmpty()) return;
        Texture[] frames = fireFrames;
        if (frames == null) { groundFires.clear(); return; }

        // Наземный огонь экспортирован БЕЗ альфа-канала (чёрный фон) — рисуется аддитивным
        // смешиванием (чёрное ничего не добавляет, яркое пламя складывается поверх сцены), иначе
        // был бы виден чёрный квадрат вокруг каждого языка пламени.
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (Iterator<GroundFire> it = groundFires.iterator(); it.hasNext(); ) {
            GroundFire fire = it.next();
            float alpha;
            if (fire.traveling) {
                fire.travelled += fire.speed * dt;
                fire.wx += fire.dirX * fire.speed * dt;
                fire.wy += fire.dirY * fire.speed * dt;
                if (fire.travelled >= fire.maxDistance) { it.remove(); continue; }
                // "Огонь из стены огня развеивается" на непроходимом препятствии — так же, как
                // снаряд у Огненного Шара (см. updateAndDrawProjectiles/Player.isBlockedAt).
                if (store.player != null && store.player.isBlockedAt(fire.wx, fire.wy)) { it.remove(); continue; }
                // Гаснет плавно на последних 25% пути.
                float fadeStart = fire.maxDistance * 0.75f;
                alpha = fire.travelled <= fadeStart ? 1f
                    : 1f - (fire.travelled - fadeStart) / (fire.maxDistance - fadeStart);
            } else {
                fire.age += dt;
                if (fire.age >= fire.life) { it.remove(); continue; }
                float t = fire.age / fire.life;
                alpha = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f; // гаснет в последние 40% жизни
            }

            float[] screen = worldToScreen(fire.wx, fire.wy);
            float animT = fire.traveling ? fire.travelled / fire.speed : fire.age;
            int frameIdx = ((int) (animT * 20f)) % frames.length;
            batch.setColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));
            batch.draw(frames[frameIdx], screen[0] - fire.w / 2f, screen[1] - fire.h / 2f, fire.w, fire.h);
        }
        batch.setColor(1f, 1f, 1f, 1f);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); // возвращаем обычное смешивание
    }

    private void updatePendingBolts(float dt) {
        if (pendingBolts.isEmpty()) return;
        for (float[] b : pendingBolts) b[2] -= dt;
        for (Iterator<float[]> it = pendingBolts.iterator(); it.hasNext(); ) {
            float[] b = it.next();
            if (b[2] <= 0f) {
                store.lightningTargetWX = b[0];
                store.lightningTargetWY = b[1];
                store.lightningBoltNew = true;
                it.remove();
                break; // максимум один новый разряд за кадр — см. класс-комментарий
            }
        }
    }

    // ── Примитивы рисования ─────────────────────────────────────────────────────────────────────
    private void drawSeg(SpriteBatch batch, float x1, float y1, float x2, float y2,
                          float thick, float r, float g, float b, float alpha) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        float angle = (float) Math.toDegrees(Math.atan2(-dx, dy));
        batch.setColor(r, g, b, alpha);
        batch.draw(pixel, x1 - thick / 2f, y1, thick / 2f, 0f, thick, len, 1f, 1f, angle, 0, 0, 1, 1, false, false);
        batch.setColor(1, 1, 1, 1);
    }

    /** Кольцо из точек, сплюснутое по Y (тот же изометрический трюк, что тень/лужа под ногами) —
     *  визуально читается как область на земле, а не как окружность в экранной плоскости. */
    private void drawDottedRing(SpriteBatch batch, float cx, float cy, float radius,
                                 float r, float g, float b, float alpha) {
        int count = Math.max(8, (int) (radius / 6f));
        float dotSize = 5f;
        batch.setColor(r, g, b, alpha);
        for (int i = 0; i < count; i++) {
            float ang = (float) (i * 2 * Math.PI / count);
            float px = cx + (float) Math.cos(ang) * radius;
            float py = cy + (float) Math.sin(ang) * radius * 0.5f;
            batch.draw(pixel, px - dotSize / 2f, py - dotSize / 2f, dotSize, dotSize);
        }
        batch.setColor(1, 1, 1, 1);
    }

    private void addStreak(float[] from, float[] to, float r, float g, float b, float life) {
        Streak s = new Streak();
        s.x1 = from[0]; s.y1 = from[1]; s.x2 = to[0]; s.y2 = to[1];
        s.r = r; s.g = g; s.b = b; s.life = life; s.age = 0f;
        streaks.add(s);
    }

    private void addRing(float[] pos, float maxRadius, float r, float g, float b, float life) {
        Ring ring = new Ring();
        ring.cx = pos[0]; ring.cy = pos[1]; ring.maxRadius = maxRadius;
        ring.r = r; ring.g = g; ring.b = b; ring.life = life; ring.age = 0f;
        rings.add(ring);
    }

    private void scheduleBolt(float wx, float wy, float delaySec) {
        pendingBolts.add(new float[]{wx, wy, delaySec});
    }

    /** Широкий взмах (Сплеш) — пассивка, не кастуется напрямую (см. SkillCaster), но "если сплеш
     *  есть - то работает всегда" (правка пользователя): добавляем кольцо-всплеск к КАЖДОЙ атаке
     *  Воителя, если хотя бы 1 очко вложено. */
    private void addSplashIfInvested(float[] target) {
        if (store.player != null && store.player.skillLevels.getOrDefault("warrior_splash", 0) > 0) {
            addRing(target, 34f, 0.85f, 0.85f, 0.5f, 0.35f);
        }
    }

    // ── Координаты: игрок всегда рисуется в центре экрана (камера следит за ним, см. Player.draw),
    // произвольная мировая точка (курсор) переводится в экранные той же формулой, что тайлы/дропы. ─
    private float[] playerScreenPos() {
        return new float[]{store.display.get("width") / 2f, store.display.get("height") / 2f};
    }

    private float[] aimScreenPos() {
        return worldToScreen(store.cursorWorldX, store.cursorWorldY);
    }

    private float[] worldToScreen(float wx, float wy) {
        float[] iso = Transform.cartesianToIsometric(wx, wy);
        return new float[]{iso[0] + store.shiftX, iso[1] + store.shiftY};
    }

    private float[] offset(float[] p, float dx, float dy) {
        return new float[]{p[0] + dx, p[1] + dy};
    }
}
