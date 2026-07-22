package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.simulation.CombatSystem;
import com.nicweiss.editor.simulation.SimCreature;
import com.nicweiss.editor.utils.SkillCatalog;

import java.util.List;

/**
 * Хрупкость — одна "частица": неподвижная точка в области (той же, что у Ледяного Тумана — см.
 * triggerGroup), которая ждёт delay сек (случайная задержка появления — "появляться с
 * произвольной задержкой"), затем за LIFE (1) сек проигрывает "шар поднимается и лопается":
 * быстрое проявление из невидимости, лёгкий подъём вверх, на середине пути запускается сама
 * анимация лопания (см. assets/skills/mage/icefragile/fragile_01..40.png). Подсветка — тот же
 * холодный свет, что у Ледяного Шипа, но ТОЛЬКО пока шар ещё не лопнул (первая половина LIFE).
 */
public class FragilityParticleEffect extends SkillEffect {
    private static final int FRAME_COUNT = 40;
    private static final int PARTICLE_COUNT = 30;     // "их должно быть штук 30 суммарно"
    private static final float SPAWN_WINDOW = 1.2f;   // разброс случайных задержек появления
    private static final float LIFE = 1f;             // "длительность общей анимации 1 сек"
    private static final float FADE_IN_TIME = 0.15f;  // "быстро становится видимым"
    private static final float POP_START = 0.5f;      // "где-то на середине пути — запускается лопание"
    private static final float RISE_HEIGHT = 34f;     // на сколько px "поднимается" за всю жизнь
    private static final float SIZE = 70f;

    private static Texture[] frames;

    private static Texture[] loadFrames() {
        if (frames != null) return frames;
        Texture[] loaded = new Texture[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            Texture tex = FxContext.loadSkillTexture(String.format("mage/icefragile/fragile_%02d.png", i + 1));
            if (tex == null) return null;
            loaded[i] = tex;
        }
        frames = loaded;
        return frames;
    }

    /** Работает в той же области, что и Ледяной Туман (см. SkillCatalog "elem_cold_mist" radius_m —
     *  у самой Хрупкости своего радиуса нет), но вдвое меньшей (по требованию пользователя). 30
     *  частиц разбрасываются по области сразу, но каждая ждёт свою случайную задержку. */
    public static void triggerGroup(int level, EffectSink sink) {
        Texture[] loaded = loadFrames();
        if (loaded == null) return;
        SkillCatalog.SkillDef mistDef = SkillCatalog.SKILLS.get("elem_cold_mist");
        double radiusM = mistDef != null ? mistDef.compute(level).getOrDefault("radius_m", 4.0) : 4.0;
        // Область каста Хрупкости — вдвое меньше, чем у Ледяного Тумана (по требованию пользователя),
        // не путать с самим радиусом умения elem_cold_mist — тот не трогаем.
        float radiusPx = (float) radiusM * FxContext.store.tileSizeWidth * 0.5f;
        float cx = FxContext.store.cursorWorldX, cy = FxContext.store.cursorWorldY;

        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get("elem_cold_fragility");
        if (def != null) {
            double shredPct = def.compute(level).getOrDefault("shred_per_stack_pct", 0.0);
            int maxStacks = def.fixed.getOrDefault("max_stacks", 5.0).intValue();
            double durationSec = def.fixed.getOrDefault("stack_duration_sec", 6.0);
            List<SimCreature> hits = CombatSystem.findAllInRadius(cx, cy, radiusPx);
            for (SimCreature victim : hits) {
                CombatSystem.applyFragilityStack(victim, (float) shredPct, maxStacks, (float) durationSec);
            }
        }

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float ang = (float) (Math.random() * Math.PI * 2);
            float dist = (float) (Math.sqrt(Math.random()) * radiusPx); // равномерно по площади круга
            float wx = cx + (float) Math.cos(ang) * dist;
            float wy = cy + (float) Math.sin(ang) * dist;
            float delay = (float) (Math.random() * SPAWN_WINDOW);
            sink.spawn(new FragilityParticleEffect(wx, wy, loaded, delay));
        }
    }

    private final float wx, wy;
    private final Texture[] myFrames;
    private float delay;
    private boolean started;

    private FragilityParticleEffect(float wx, float wy, Texture[] frames, float delay) {
        this.wx = wx; this.wy = wy; this.myFrames = frames; this.delay = delay;
    }

    @Override
    public boolean update(float dt) {
        if (!started) {
            delay -= dt;
            if (delay <= 0f) started = true;
            return true; // невидим, пока не подошла своя случайная задержка
        }
        age += dt;
        return age < LIFE;
    }

    private int currentFrameIdx() {
        float t = age / LIFE;
        if (t < POP_START) return 0; // ещё поднимается, не лопнул — держим первый кадр (собранный "шар")
        float frac = (t - POP_START) / (1f - POP_START);
        return Math.min(FRAME_COUNT - 1, (int) (frac * FRAME_COUNT));
    }

    @Override
    public void render(SpriteBatch batch) {
        if (!started) return;
        float t = age / LIFE;
        float alpha = Math.min(1f, t / FADE_IN_TIME); // "быстро становится видимым"
        float[] screen = FxContext.worldToScreen(wx, wy);
        float riseY = screen[1] + RISE_HEIGHT * t; // "от поверхности вверх поднимается"
        Texture frame = myFrames[currentFrameIdx()];
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(frame, screen[0] - SIZE / 2f, riseY - SIZE / 2f, SIZE, SIZE);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    public void collectLight(float[][] pts, int[] idxRef) {
        if (started && age < LIFE * POP_START) {
            FxContext.writeLight(pts, idxRef, wx, wy, FxContext.LIGHT_COLOR_ICE);
        }
    }
}
