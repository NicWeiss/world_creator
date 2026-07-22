package com.nicweiss.editor.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.simulation.effects.AuraRenderer;
import com.nicweiss.editor.simulation.effects.ChainBoltEffect;
import com.nicweiss.editor.simulation.effects.EffectSink;
import com.nicweiss.editor.simulation.effects.FireDoomEffect;
import com.nicweiss.editor.simulation.effects.FireWaveEffect;
import com.nicweiss.editor.simulation.effects.FireballEffect;
import com.nicweiss.editor.simulation.effects.FragilityParticleEffect;
import com.nicweiss.editor.simulation.effects.IceSpikeEffect;
import com.nicweiss.editor.simulation.effects.MeleeStrikeEffects;
import com.nicweiss.editor.simulation.effects.MistPatchEffect;
import com.nicweiss.editor.simulation.effects.SkillEffect;
import com.nicweiss.editor.simulation.effects.StormBoltScheduler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Визуальный слой применения умений (см. SkillCaster.cast) — ЧИСТО визуальные эффекты, без урона и
 * попаданий: боевой системы для NPC пока нет (SpawnManager никому не уменьшает health), поэтому
 * реальное нанесение эффектов — отдельная будущая задача.
 *
 * Сама эта реализация теперь только КООРДИНАТОР: конкретные эффекты (снаряды/огонь/туман/молния/...)
 * живут в пакете simulation/effects/ как отдельные классы, каждый со своим update/render (см.
 * SkillEffect — общий базовый класс жизненного цикла). Этот класс:
 *  - реализует EffectSink (spawn) — общий список эффектов, который заполняют фабрики умений
 *    (FireballEffect.trigger, IceSpikeEffect.trigger, ...) и сами эффекты друг из друга (снаряд
 *    по прибытии спавнит наземный огонь/анимацию раскола);
 *  - раз в кадр обновляет/рисует список и собирает снимок динамического света (см. updateLightSnapshot);
 *  - отдельно держит AuraRenderer (персистентные ауры Вестника — завязаны на Player.activeAuras,
 *    не на список эффектов) и StormBoltScheduler (Гроза — тонкая обёртка над WeatherRenderer).
 */
public class SkillEffectRenderer implements EffectSink {
    public static Store store;

    private final List<SkillEffect> effects = new ArrayList<>();
    // Эффекты сами порождают новые эффекты ИЗ СЕРЕДИНЫ обхода (снаряд по прибитии спавнит огонь,
    // ДУМ каждый тик спавнит очаг) — добавлять их сразу в effects во время итерации по effects
    // ловит ConcurrentModificationException. spawn() копит их тут, render() вливает пачкой ПОСЛЕ
    // обхода (см. ниже) — тот же приём, что "второй проход" во многих игровых циклах.
    private final List<SkillEffect> pendingSpawns = new ArrayList<>();
    private final int[] lightCursor = new int[1];

    private final AuraRenderer auraRenderer = new AuraRenderer();
    private final StormBoltScheduler stormScheduler = new StormBoltScheduler();

    @Override
    public void spawn(SkillEffect effect) {
        pendingSpawns.add(effect);
    }

    /** Ауры/тумблеры (Player.activeAuras) — ЗЕМЛЯНОЙ слой под ногами игрока. Вызывается ДО
     *  Player.draw() (см. Editor.renderMap) — иначе спрайт ауры перекрывал бы персонажа сверху. */
    public void renderGround(SpriteBatch batch) {
        auraRenderer.render(batch, Gdx.graphics.getDeltaTime());
    }

    /** Разовые эффекты (снаряды/росчерки/кольца/огонь/туман/молнии/...) — вызывается ПОСЛЕ
     *  отрисовки карты/игрока, тем же батчем/местом, что и WeatherRenderer.render (это летящие/
     *  бьющие эффекты поверх сцены, а не земляная "подложка" — им, в отличие от ауры, место над
     *  персонажем корректно). */
    public void render(SpriteBatch batch) {
        float dt = Gdx.graphics.getDeltaTime();
        stormScheduler.update(dt);
        for (Iterator<SkillEffect> it = effects.iterator(); it.hasNext(); ) {
            SkillEffect e = it.next();
            if (!e.update(dt)) { it.remove(); continue; }
            e.render(batch);
        }
        if (!pendingSpawns.isEmpty()) {
            effects.addAll(pendingSpawns);
            pendingSpawns.clear();
        }
    }

    /** Снимок текущих источников света от эффектов умений в Store.skillLightPoints (см. её
     *  описание) — читается MapObject.calcLitColor/Lighting.computeLitColor КАЖДЫЙ тайл. Вызывать
     *  РАНЬШЕ отрисовки карты за кадр (см. Editor.renderMap), а не из render()/renderGround() —
     *  иначе освещение отставало бы на кадр от только что подвинутых снарядов/огня. Позиции тут не
     *  меняются (движение — в render()/update()), только читаются "как есть". */
    public void updateLightSnapshot() {
        float[][] pts = store.skillLightPoints;
        lightCursor[0] = 0;
        for (SkillEffect e : effects) {
            if (lightCursor[0] >= pts.length) break;
            e.collectLight(pts, lightCursor);
        }
        for (int idx = lightCursor[0]; idx < pts.length; idx++) pts[idx][0] = 0f;
    }

    /** Точка входа из SkillCaster — по id умения проигрывает соответствующий визуальный эффект.
     *  AURA/SUSTAINED/STANCE (тумблер) сюда не попадают — их включение/выключение обрабатывает сам
     *  SkillCaster через Player.activeAuras, а отрисовка идёт через AuraRenderer. */
    public void trigger(String skillId, int level) {
        switch (skillId) {
            // ── Воитель: ближний бой ────────────────────────────────────────────────────────────
            case "warrior_strike":
                MeleeStrikeEffects.triggerStrike(this, level);
                break;
            case "warrior_blade_dash":
                MeleeStrikeEffects.triggerBladeDash(this, level);
                break;
            case "warrior_shadow_blade":
                MeleeStrikeEffects.triggerShadowBlade(this, level);
                break;

            // ── Стихийник: Огонь — снаряд/конус/горящая земля ──────────────────────────────────
            case "elem_fire_ball":
                if (store.player != null) {
                    FireballEffect.trigger(store.player.worldX, store.player.worldY, store.cursorWorldX, store.cursorWorldY, level, this);
                }
                break;
            case "elem_fire_wave":
                FireWaveEffect.trigger(level, this);
                break;
            case "elem_fire_doom":
                FireDoomEffect.trigger(level, this);
                break;

            // ── Стихийник: Холод — снаряд/область/накопительный дебафф ─────────────────────────
            case "elem_cold_spike":
                if (store.player != null) {
                    IceSpikeEffect.trigger(store.player.worldX, store.player.worldY, store.cursorWorldX, store.cursorWorldY, level, this);
                }
                break;
            case "elem_cold_mist":
                MistPatchEffect.trigger(level, this);
                break;
            case "elem_cold_fragility":
                FragilityParticleEffect.triggerGroup(level, this);
                break;

            // ── Стихийник: Электро ──────────────────────────────────────────────────────────────
            // Цепной Разряд — СВОЙ зигзаг-разряд от игрока к цели, не разряд WeatherRenderer (тот
            // падает с неба в точку — верно для Грозы, но не для удара молнией ИЗ ИГРОКА В ЦЕЛЬ).
            case "elem_lightning_chain":
                if (store.player != null) {
                    ChainBoltEffect.trigger(store.player.worldX, store.player.worldY, store.cursorWorldX, store.cursorWorldY, level, this);
                }
                break;
            case "elem_lightning_storm": // "Гроза" — несколько ударов подряд по области цели
                stormScheduler.trigger(level);
                break;

            default:
                break; // warrior_stun/warrior_crit/warrior_splash — чистые пассивные статы, без анимации на каст (не бинд.)
        }
    }
}
