package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Цепной Разряд — СВОЙ зигзаг-разряд ИЗ ИГРОКА В ЦЕЛЬ (не из WeatherRenderer — тот бьёт с неба в
 * точку, это верно для Грозы, см. StormBoltScheduler, но не для прямого удара молнией по цели).
 * Ломаная линия строится рекурсивным смещением середины (тот же приём, что
 * WeatherRenderer.generateBolt) ОДИН раз в экранных координатах при касте — разряд живёт доли
 * секунды, камера не успевает сдвинуться (тот же принцип, что у ProjectileEffect.angleDeg).
 * Мировые концы хранятся отдельно — только для подсветки вдоль всего пути (см. collectLight),
 * т.к. свет считается в мировых координатах.
 *
 * По требованию пользователя: (1) бьёт 3 разряда подряд (см. trigger — три независимо
 * сгенерированных зигзага, каждый со своей случайной формой), (2) от основного пути иногда
 * отходят короткие ветви-тупики, которые никуда не ведут и просто затухают (см. buildBranches) —
 * тот же приём, что ветвление в WeatherRenderer.generateBolt, только здесь ветви декоративные и не
 * влияют на подсветку (см. collectLight — свет идёт только вдоль ОСНОВНОГО пути игрок→цель).
 */
public class ChainBoltEffect extends SkillEffect {
    private static final float LIFE = 0.22f;
    private static final int ITERATIONS = 4;           // сколько раз делим пополам со смещением
    private static final float DISPLACE_FRAC = 0.14f;  // макс. смещение середины сегмента, доля от общей длины
    private static final int LIGHT_SAMPLES = 4;        // точек подсветки вдоль пути
    private static final int BOLT_COUNT = 3;           // "пусть их будет 3"

    // ── Ответвления-тупики ───────────────────────────────────────────────────────────────────
    private static final float BRANCH_CHANCE = 0.03f;     // вероятность ответвления в каждой промежуточной точке (было 0.3, ×10 меньше)
    private static final int BRANCH_ITERATIONS = 2;       // ветви короче и грубее основного разряда
    private static final float BRANCH_LEN_MIN = 0.015f, BRANCH_LEN_MAX = 0.035f; // доля от общей длины основного разряда (было 0.15-0.35, ×10 меньше)
    private static final float BRANCH_SPREAD_DEG = 100f;  // разброс угла ветви вокруг перпендикуляра к основному пути

    public static void trigger(float fromWX, float fromWY, float toWX, float toWY, EffectSink sink) {
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
