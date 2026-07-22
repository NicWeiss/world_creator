package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.nicweiss.editor.simulation.CombatSystem;
import com.nicweiss.editor.simulation.SimCreature;
import com.nicweiss.editor.utils.ShaderLibrary;
import com.nicweiss.editor.utils.SkillCatalog;

import java.util.List;

/**
 * Ледяной Туман — MIST_PUFF_COUNT неподвижных клочков ОДНОЙ И ТОЙ ЖЕ текстуры (fog.png, см.
 * ShaderLibrary.mist), разбросанных В МИРОВОЙ точке каста (не следует за игроком, в отличие от
 * ДУМа — это область НА МЕСТНОСТИ) с сильным перекрытием (размер каждого клочка сравним с
 * радиусом всей области, разброс позиций — узкий) — по требованию пользователя туман должен
 * читаться ОДНИМ цельным облаком, а не отдельными вкраплениями. Параметры (позиция/поворот/
 * размер) генерируются ОДИН раз при касте — "не раскладываться" (не двигаются и не
 * переставляются), а вся группа вместе проступает из нулевой прозрачности и обратно гаснет в неё
 * же по истечении жизни.
 */
public class MistPatchEffect extends SkillEffect {
    private static final float LIFE = 5f;              // см. SkillCatalog "elem_cold_mist" — "5 сек"
    private static final float FADE_IN_TIME = 0.6f;    // проступают из нулевой прозрачности за N сек
    private static final float FADE_OUT_TIME = 1.0f;   // и обратно гаснут в неё за N сек в конце жизни
    private static final int PUFF_COUNT = 10;          // было 15, потом 5 — по требованию пользователя
    // Клочки размером СОПОСТАВИМЫМ с радиусом всей области и узким разбросом позиций — гарантирует
    // сильное перекрытие, чтобы копии одной и той же текстуры сливались в единое облако.
    private static final float PUFF_SIZE_MIN = 0.9f, PUFF_SIZE_MAX = 1.3f; // × radiusPx
    private static final float PUFF_SPREAD = 0.5f; // × radiusPx
    private static final float PUFF_ROT_RANGE = 20f; // ± градусов от горизонтали (0/180) — повороты
                                                      // ближе к 90°/270° портят широкий плоский пласт
    private static final float MAX_ALPHA = 0.5f; // туман полупрозрачный даже на пике огибающей

    private static Texture fogTexture;

    private static Texture loadFogTexture() {
        if (fogTexture != null) return fogTexture;
        fogTexture = FxContext.loadSkillTexture("mage/icefog/fog.png");
        return fogTexture;
    }

    /** "В точке каста должен подниматься туман": в отличие от ДУМа область НЕ следует за игроком,
     *  она встаёт неподвижно на месте курсора (это область на местности, см. SkillCatalog
     *  "elem_cold_mist" — "создаёт на местности область тумана"). */
    private static final float TICK_INTERVAL = 0.5f; // см. SkillCatalog "elem_cold_mist" — "каждые 0.5 сек"

    public static void trigger(int level, EffectSink sink) {
        Texture tex = loadFogTexture();
        if (tex == null) return;
        SkillCatalog.SkillDef def = SkillCatalog.SKILLS.get("elem_cold_mist");
        java.util.LinkedHashMap<String, Double> stats = def != null ? def.compute(level) : new java.util.LinkedHashMap<>();
        double radiusM = stats.getOrDefault("radius_m", 4.0);
        double tickDamage = stats.getOrDefault("tick_damage", 0.0);
        double slowPct = stats.getOrDefault("slow_pct", 0.0);
        sink.spawn(new MistPatchEffect(tex, (float) radiusM, tickDamage, slowPct));
    }

    private final Texture tex;
    private final float[] offX, offY, rotDeg, sizeMul;
    private final float wx, wy;
    private final float radiusPx;
    private final double tickDamage, slowPct;
    private float damageTimer;

    private MistPatchEffect(Texture tex, float radiusM, double tickDamage, double slowPct) {
        this.tex = tex;
        this.wx = FxContext.store.cursorWorldX;
        this.wy = FxContext.store.cursorWorldY;
        this.tickDamage = tickDamage;
        this.slowPct = slowPct;
        radiusPx = radiusM * FxContext.store.tileSizeWidth;

        offX = new float[PUFF_COUNT]; offY = new float[PUFF_COUNT];
        rotDeg = new float[PUFF_COUNT]; sizeMul = new float[PUFF_COUNT];
        for (int i = 0; i < PUFF_COUNT; i++) {
            float ang = (float) (Math.random() * Math.PI * 2);
            float dist = (float) (Math.sqrt(Math.random()) * radiusPx * PUFF_SPREAD); // узкий разброс
            offX[i] = (float) Math.cos(ang) * dist;
            offY[i] = (float) Math.sin(ang) * dist;
            float baseAngle = Math.random() < 0.5 ? 0f : 180f;
            rotDeg[i] = baseAngle + (float) ((Math.random() * 2 - 1) * PUFF_ROT_RANGE);
            sizeMul[i] = radiusPx * (PUFF_SIZE_MIN + (float) Math.random() * (PUFF_SIZE_MAX - PUFF_SIZE_MIN));
        }
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        damageTimer += dt;
        if (damageTimer >= TICK_INTERVAL) {
            damageTimer -= TICK_INTERVAL;
            List<SimCreature> hits = CombatSystem.findAllInRadius(wx, wy, radiusPx);
            for (SimCreature victim : hits) {
                CombatSystem.applyDamage(victim, tickDamage);
                CombatSystem.applySlow(victim, (float) slowPct, TICK_INTERVAL * 1.5f);
            }
        }
        return age < LIFE;
    }

    @Override
    public void render(SpriteBatch batch) {
        // Вся группа проступает из нулевой прозрачности и гаснет обратно в неё же — единая
        // огибающая на все клочки ("не раскладываться").
        float fadeOutStart = LIFE - FADE_OUT_TIME;
        float envelope = age < FADE_IN_TIME ? age / FADE_IN_TIME
            : age <= fadeOutStart ? 1f
            : Math.max(0f, 1f - (age - fadeOutStart) / FADE_OUT_TIME);
        // Туман должен быть полупрозрачным даже "на пике" — envelope достигает 1.0, но итоговая
        // альфа капается MAX_ALPHA, сквозь него видно землю/траву под ним.
        float alphaMul = envelope * MAX_ALPHA;
        if (alphaMul <= 0f) return;

        ShaderProgram shader = ShaderLibrary.mist();
        for (int i = 0; i < PUFF_COUNT; i++) {
            float[] screen = FxContext.worldToScreen(wx + offX[i], wy + offY[i]);
            // sizeMul уже хранит готовую ширину клочка — высота сохраняет исходную пропорцию
            // текстуры (широкое плоское облако, не квадрат).
            float w = sizeMul[i];
            float h = w * ((float) tex.getHeight() / tex.getWidth());

            if (shader == null) {
                batch.setColor(1f, 1f, 1f, alphaMul);
                batch.draw(tex, screen[0] - w / 2f, screen[1] - h / 2f,
                    w / 2f, h / 2f, w, h, 1f, 1f, rotDeg[i],
                    0, 0, tex.getWidth(), tex.getHeight(), false, false);
                continue;
            }
            batch.setShader(shader);
            shader.setUniformf("u_time", FxContext.store.cloudTime + i); // сдвиг фазы — клочки колышутся не в такт
            shader.setUniformf("u_alphaMul", alphaMul);
            batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(tex, screen[0] - w / 2f, screen[1] - h / 2f,
                w / 2f, h / 2f, w, h, 1f, 1f, rotDeg[i],
                0, 0, tex.getWidth(), tex.getHeight(), false, false);
            batch.setShader(null);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }
}
