package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.simulation.CombatSystem;
import com.nicweiss.editor.simulation.SimCreature;
import com.nicweiss.editor.utils.SkillCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Цепной Разряд — СВОЙ зигзаг-разряд ИЗ ИГРОКА В ЦЕЛЬ (не из WeatherRenderer — тот бьёт с неба в
 * точку, это верно для Грозы, см. StormBoltScheduler, но не для прямого удара молнией по цели).
 * Ломаная линия строится рекурсивным смещением середины (тот же приём, что
 * WeatherRenderer.generateBolt) ОДИН раз в экранных координатах при касте — разряд живёт доли
 * секунды, камера не успевает сдвинуться (тот же принцип, что у ProjectileEffect.angleDeg).
 * Мировые концы хранятся отдельно — только для подсветки вдоль всего пути (см. collectLight),
 * т.к. свет считается в мировых координатах.
 *
 * Урон (см. CombatSystem) — цепочка ПОСЛЕДОВАТЕЛЬНЫХ перескоков, строго по одному за раз (не
 * ветвится): первая цель — ближайшая к курсору, каждый следующий хоп — ближайшее ЕЩЁ НЕ задетое
 * существо от позиции предыдущей цели, НЕ ДАЛЬШЕ JUMP_RANGE_TILES (2 клетки — "может перепрыгнуть
 * только если между врагами не более 2х клеток"), до max_targets хопов или пока не кончатся цели в
 * радиусе перескока. Вся цепочка целей просчитывается СРАЗУ (существа неподвижны), но каждый хоп
 * визуально/по урону применяется с задержкой JUMP_DELAY_SEC относительно предыдущего (см.
 * DelayedCallbackEffect) — "перед прыжком нужна небольшая задержка", а не мгновенно вся цепь разом.
 * Урон каждого хопа растёт на jump_damage_bonus_pct за хоп (см. SkillCatalog). Каждый хоп рисуется
 * своим сегментом разряда (см. spawnBolts) — 3 независимо изломанных нити подряд (BOLT_COUNT,
 * "пусть их будет 3" — визуальное разнообразие одного сегмента, по требованию пользователя), от
 * предыдущей точки к следующей цели, а не всегда в одну и ту же точку курсора.
 *
 * Ответвления-тупики (см. buildBranches) — чисто декоративные, никуда не ведут и не участвуют ни
 * в подсветке, ни в уроне.
 */
public class ChainBoltEffect extends SkillEffect {
    private static final float LIFE = 0.22f;
    private static final int ITERATIONS = 4;           // сколько раз делим пополам со смещением
    private static final float DISPLACE_FRAC = 0.14f;  // макс. смещение середины сегмента, доля от общей длины
    private static final int LIGHT_SAMPLES = 4;        // точек подсветки вдоль пути
    private static final int BOLT_COUNT = 3;           // "пусть их будет 3"

    private static final float AIM_SNAP_RANGE_TILES = 3f;  // радиус поиска первой цели у курсора
    private static final float JUMP_RANGE_TILES = 2f;      // "не более 2х клеток" между врагами для перескока
    private static final float JUMP_DELAY_SEC = 0.15f;     // небольшая задержка перед каждым следующим прыжком

    // ── Ответвления-тупики ───────────────────────────────────────────────────────────────────
    private static final float BRANCH_CHANCE = 0.03f;     // вероятность ответвления в каждой промежуточной точке (было 0.3, ×10 меньше)
    private static final int BRANCH_ITERATIONS = 2;       // ветви короче и грубее основного разряда
    private static final float BRANCH_LEN_MIN = 0.015f, BRANCH_LEN_MAX = 0.035f; // доля от общей длины основного разряда (было 0.15-0.35, ×10 меньше)
    private static final float BRANCH_SPREAD_DEG = 100f;  // разброс угла ветви вокруг перпендикуляра к основному пути

    public static void trigger(float fromWX, float fromWY, float toWX, float toWY, int level, EffectSink sink) {
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get("elem_lightning_chain");
        LinkedHashMap<String, Double> stats = def != null ? def.compute(level) : new LinkedHashMap<>();
        double baseDamage = stats.getOrDefault("base_damage", 0.0);
        double jumpBonusPct = stats.getOrDefault("jump_damage_bonus_pct", 0.0);
        int maxTargets = (int) Math.round(stats.getOrDefault("max_targets", 1.0));

        float aimRange = FxContext.store.tileSizeWidth * AIM_SNAP_RANGE_TILES;
        float jumpRange = FxContext.store.tileSizeWidth * JUMP_RANGE_TILES;

        SimCreature first = CombatSystem.findNearestToCursor(aimRange);
        if (first == null || maxTargets <= 0) {
            // нет цели — чисто визуальный разряд в сторону курсора, без урона (не теряем эффект целиком)
            spawnBolts(fromWX, fromWY, toWX, toWY, sink);
            return;
        }

        // Вся цепочка целей известна заранее (существа неподвижны) — так проще расставить задержки
        // между хопами, чем искать следующую цель "по факту" в момент срабатывания таймера.
        List<SimCreature> chain = new ArrayList<>();
        Set<SimCreature> hit = new LinkedHashSet<>();
        SimCreature current = first;
        while (current != null && chain.size() < maxTargets) {
            chain.add(current);
            hit.add(current);
            current = CombatSystem.findNearestUnhit(current.worldX, current.worldY, hit, jumpRange);
        }

        float prevX = fromWX, prevY = fromWY;
        for (int hop = 0; hop < chain.size(); hop++) {
            SimCreature victim = chain.get(hop);
            float segFromX = prevX, segFromY = prevY;
            double damage = baseDamage * (1.0 + jumpBonusPct / 100.0 * hop);
            float delay = hop * JUMP_DELAY_SEC;
            sink.spawn(new DelayedCallbackEffect(delay, () -> {
                spawnBolts(segFromX, segFromY, victim.worldX, victim.worldY, sink);
                CombatSystem.applyDamage(victim, damage);
            }));
            prevX = victim.worldX;
            prevY = victim.worldY;
        }
    }

    private static void spawnBolts(float fromWX, float fromWY, float toWX, float toWY, EffectSink sink) {
        for (int i = 0; i < BOLT_COUNT; i++) {
            sink.spawn(new ChainBoltEffect(fromWX, fromWY, toWX, toWY));
        }
    }

    private final float wx1, wy1, wx2, wy2;
    private final float[] segX1, segY1, segX2, segY2;

    private ChainBoltEffect(float fromWX, float fromWY, float toWX, float toWY) {
        this.wx1 = fromWX; this.wy1 = fromWY; this.wx2 = toWX; this.wy2 = toWY;

        float[] s1 = FxContext.worldToScreen(fromWX, fromWY);
        float[] s2 = FxContext.worldToScreen(toWX, toWY);
        float totalLen = (float) Math.hypot(s2[0] - s1[0], s2[1] - s1[1]);

        List<float[]> points = buildJaggedPath(s1[0], s1[1], s2[0], s2[1], totalLen * DISPLACE_FRAC, ITERATIONS);

        List<float[]> segs = new ArrayList<>(); // {x1,y1,x2,y2}
        for (int i = 0; i < points.size() - 1; i++) {
            float[] a = points.get(i), b = points.get(i + 1);
            segs.add(new float[]{a[0], a[1], b[0], b[1]});
        }

        // Ответвления-тупики — рождаются в ПРОМЕЖУТОЧНЫХ точках основного пути (не в начале/конце,
        // чтобы не путаться с самим разрядом), никуда не ведут, просто короткие рваные хвосты.
        for (int i = 1; i < points.size() - 1; i++) {
            if (Math.random() > BRANCH_CHANCE) continue;
            float[] origin = points.get(i);
            float[] prev = points.get(i - 1), next = points.get(i + 1);
            float dirX = next[0] - prev[0], dirY = next[1] - prev[1];
            float dirLen = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            float baseAngle = dirLen > 0.001f
                ? (float) Math.toDegrees(Math.atan2(dirY, dirX)) + 90f // перпендикуляр к основному пути
                : (float) (Math.random() * 360.0);
            float angle = (float) Math.toRadians(baseAngle + (Math.random() * 2 - 1) * BRANCH_SPREAD_DEG / 2f);
            float branchLen = totalLen * (BRANCH_LEN_MIN + (float) Math.random() * (BRANCH_LEN_MAX - BRANCH_LEN_MIN));
            float endX = origin[0] + (float) Math.cos(angle) * branchLen;
            float endY = origin[1] + (float) Math.sin(angle) * branchLen;

            List<float[]> branchPoints = buildJaggedPath(origin[0], origin[1], endX, endY,
                branchLen * DISPLACE_FRAC, BRANCH_ITERATIONS);
            for (int j = 0; j < branchPoints.size() - 1; j++) {
                float[] a = branchPoints.get(j), b = branchPoints.get(j + 1);
                segs.add(new float[]{a[0], a[1], b[0], b[1]});
            }
        }

        int segCount = segs.size();
        segX1 = new float[segCount]; segY1 = new float[segCount];
        segX2 = new float[segCount]; segY2 = new float[segCount];
        for (int i = 0; i < segCount; i++) {
            float[] s = segs.get(i);
            segX1[i] = s[0]; segY1[i] = s[1];
            segX2[i] = s[2]; segY2[i] = s[3];
        }
    }

    /** Рекурсивное смещение середины — тот же приём, что WeatherRenderer.generateBolt: на каждой
     *  итерации середина каждого сегмента сдвигается вбок на случайную величину, амплитуда
     *  которой дробится пополам с каждой итерацией (классическое затухание зигзага молнии). */
    private static List<float[]> buildJaggedPath(float x1, float y1, float x2, float y2, float displace, int iterations) {
        List<float[]> points = new ArrayList<>();
        points.add(new float[]{x1, y1});
        points.add(new float[]{x2, y2});
        for (int iter = 0; iter < iterations; iter++) {
            List<float[]> next = new ArrayList<>(points.size() * 2);
            for (int i = 0; i < points.size() - 1; i++) {
                float[] a = points.get(i), b = points.get(i + 1);
                next.add(a);
                float mx = (a[0] + b[0]) / 2f, my = (a[1] + b[1]) / 2f;
                float dx = b[0] - a[0], dy = b[1] - a[1];
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len > 0.001f) {
                    float nx = -dy / len, ny = dx / len; // единичный перпендикуляр к сегменту
                    float off = (float) (Math.random() * 2 - 1) * displace;
                    mx += nx * off;
                    my += ny * off;
                }
                next.add(new float[]{mx, my});
            }
            next.add(points.get(points.size() - 1));
            points = next;
            displace *= 0.5f;
        }
        return points;
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        return age < LIFE;
    }

    @Override
    public void render(SpriteBatch batch) {
        float alpha = 1f - age / LIFE;
        for (int i = 0; i < segX1.length; i++) {
            // Яркая фиолетовая обводка + тонкое белое ядро — тот же грозовой цвет, что у
            // WeatherRenderer.renderBolt.
            FxContext.drawSeg(batch, segX1[i], segY1[i], segX2[i], segY2[i], 5f, 0.65f, 0.05f, 1.0f, alpha * 0.85f);
            FxContext.drawSeg(batch, segX1[i], segY1[i], segX2[i], segY2[i], 1.2f, 1f, 1f, 1f, alpha);
        }
    }

    @Override
    public void collectLight(float[][] pts, int[] idxRef) {
        // Подсветка ВДОЛЬ ВСЕГО ПУТИ от игрока до цели (несколько точек по прямой в мировых
        // координатах, БЕЗ ответвлений — те чисто декоративные), а не один разряд-точка, как у
        // Грозы. Сила — та же LIGHT_INTENSITY, что у фаербола/остальных эффектов ("примерно как у
        // фаербола"), цвет — грозовой фиолетовый.
        for (int s = 0; s < LIGHT_SAMPLES; s++) {
            float t = LIGHT_SAMPLES > 1 ? s / (float) (LIGHT_SAMPLES - 1) : 0f;
            FxContext.writeLight(pts, idxRef, wx1 + (wx2 - wx1) * t, wy1 + (wy2 - wy1) * t, FxContext.LIGHT_COLOR_LIGHTNING);
        }
    }
}
