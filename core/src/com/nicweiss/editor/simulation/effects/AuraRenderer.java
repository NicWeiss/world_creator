package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.nicweiss.editor.utils.ShaderLibrary;

import java.util.HashMap;
import java.util.Map;

/**
 * Персистентные ауры/стойки/тумблер (см. Player.activeAuras, переключается в SkillCaster.cast()
 * для SkillKind.AURA/SUSTAINED/STANCE) — НЕ вписываются в модель "разовый эффект из списка"
 * (SkillEffect): это состояние, привязанное к множеству активных умений игрока, а не к списку
 * заспавненных инстансов, поэтому рендерится отдельным путём — SkillEffectRenderer вызывает
 * render(batch) каждый кадр независимо от общего списка эффектов.
 */
public class AuraRenderer {
    // ── Цвета персистентных ауры/стойки/тумблера — фолбэк-кольцо, пока для умения нет спрайта ────
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

    // ── Настоящие спрайты аур (см. assets/skills/auras/) — там, где текстура уже есть, рисуем её
    // ВМЕСТО пунктирного кольца-заглушки, с анимацией через шейдер (см. ShaderLibrary.aura(),
    // assets/shaders/aura.vert|frag — один шейдер, режим анимации выбирается юниформом u_mode).
    // Умения без записи тут — просто ещё не готова картинка, откатываются на дефолтное кольцо.
    private enum Mode { PULSE, BREATHE, SPIN, SPIN_STEP_FADE, RIPPLE, BURST }

    private static final class AuraAnim {
        final String file;
        final Mode mode;
        float pulseSpeed = 2.2f, pulseAmp = 0.12f, brightAmp = 0.35f, alphaAmp = 0.8f;
        float rotSpeed = 0.8f;
        // SPIN_STEP_FADE: треугольный цикл — 3 ступени роста по stepDuration сек каждая (растут на
        // stepGrow за раз), потом те же 3 ступени обратно вниз до минимума.
        float stepDuration = 0.5f, stepGrow = 0.12f;
        float burstPeriod = 4.5f, burstScale = 1.9f;

        AuraAnim(String file, Mode mode) { this.file = file; this.mode = mode; }
    }

    private static final Map<String, AuraAnim> AURA_ANIM = new HashMap<>();
    static {
        // 1 — Аура Исцеления: пульсация размера+яркости.
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

    /** Покадровая (не шейдерная) анимация тумблера — см. FRAME_AURA/drawFrameSprite: в отличие от
     *  AuraAnim (одна текстура + шейдер сверху), тут готовая последовательность кадров зацикливается
     *  напрямую (тот же приём, что GroundFireEffect). Нужна там, где сама текстура УЖЕ анимация
     *  (см. Электро-Щит — 20 кадров роста/схлопывания сферы), а не статичная картинка. */
    private static final class FrameAuraAnim {
        final String pathTemplate; // напр. "warriors/lightshield/shield_%02d.png"
        final int frameCount;
        final float frameRate;
        final float size; // свой размер, НЕ общий SPRITE_SIZE — у покадровых анимаций пропорции другие
        Texture[] frames; // лениво грузится один раз

        FrameAuraAnim(String pathTemplate, int frameCount, float frameRate, float size) {
            this.pathTemplate = pathTemplate; this.frameCount = frameCount; this.frameRate = frameRate;
            this.size = size;
        }

        Texture[] load() {
            if (frames != null) return frames;
            Texture[] loaded = new Texture[frameCount];
            for (int i = 0; i < frameCount; i++) {
                Texture tex = FxContext.loadSkillTexture(String.format(pathTemplate, i + 1));
                if (tex == null) return null;
                loaded[i] = tex;
            }
            frames = loaded;
            return frames;
        }
    }

    private static final Map<String, FrameAuraAnim> FRAME_AURA = new HashMap<>();
    static {
        // Электро-Щит — 20 кадров (рост купола → полная сфера → схлопывание, см. нарезку
        // shield_01..20.png), зацикливаем по кругу непрерывно, пока щит активен — простое
        // "дыхание" сферы вокруг игрока, без отдельной логики "активация/деактивация". Размер и
        // скорость подобраны по требованию пользователя: размер ×2/3 (было 160, "уменьшить на
        // треть"), скорость ×2 (было 14 fps, "ускорить примерно в 2 раза").
        FRAME_AURA.put("elem_lightning_shield", new FrameAuraAnim("warriors/lightshield/shield_%02d.png", 20, 28f, 106f));
    }

    private static final float SPRITE_SIZE = 160f;
    // Сплюснутость по Y — та же изометрическая "тарелка под ногами", что у drawDottedRing, только
    // применена к самому спрайту: без этого круглая текстура смотрела бы прямо в камеру, как
    // наклейка, а не лежала на изометрическом полу.
    private static final float ISO_SQUASH = 0.55f;
    // Базовая непрозрачность спрайта ауры — по требованию пользователя (шейдер поверх неё ещё
    // добавляет свою пульсацию яркости/альфы в зависимости от режима).
    private static final float ALPHA = 0.5f;

    private final Map<String, Texture> textureCache = new HashMap<>();
    private float pulseT = 0f;

    /** Вызывается ДО Player.draw() (см. Editor.renderMap) — иначе спрайт ауры перекрывал бы
     *  персонажа сверху. */
    public void render(SpriteBatch batch, float dt) {
        if (FxContext.store.player == null || FxContext.store.player.activeAuras.isEmpty()) return;
        pulseT += dt; // только для отката на пунктирное кольцо ниже — у настоящих спрайтов
                      // пульсацию делает шейдер (см. drawSprite/store.cloudTime).
        float pulse = 0.75f + 0.25f * (float) Math.sin(pulseT * 3f);
        float[] center = FxContext.playerScreenPos();
        // Ауры-спрайты садятся под ноги (ниже центра спрайта игрока), а не в грудь — центр игрока
        // визуально приходится примерно на середину тела.
        float feetY = center[1] - FxContext.store.tileSizeHeight * 0.45f;
        int i = 0;
        for (String id : FxContext.store.player.activeAuras) {
            FrameAuraAnim frameAnim = FRAME_AURA.get(id);
            Texture[] frames = frameAnim != null ? frameAnim.load() : null;
            if (frames != null) {
                // Щит — сфера ВОКРУГ ТЕЛА игрока (не плоский магический круг под ногами), поэтому
                // без ISO_SQUASH и без смещения к ногам — центр строго на центре игрока.
                drawFrameSprite(batch, frames, frameAnim.frameRate, frameAnim.size, center[0], center[1]);
                i++;
                continue;
            }
            AuraAnim anim = AURA_ANIM.get(id);
            Texture tex = anim != null ? loadTexture(anim) : null;
            if (tex != null) {
                drawSprite(batch, tex, anim, center[0], feetY);
            } else {
                float[] c = TOGGLE_COLORS.getOrDefault(id, new float[]{0.8f, 0.8f, 0.8f});
                float radius = 46f + i * 8f; // разные ауры — разные вложенные кольца, чтобы не сливались
                FxContext.drawDottedRing(batch, center[0], center[1], radius, c[0], c[1], c[2], 0.5f * pulse);
            }
            i++;
        }
    }

    /** Зацикленная покадровая анимация (см. FrameAuraAnim) — без иso-сплюснутости, без шейдера:
     *  сама последовательность кадров уже содержит нужное движение. */
    private void drawFrameSprite(SpriteBatch batch, Texture[] frames, float frameRate, float size, float cx, float cy) {
        int frameIdx = ((int) (FxContext.store.cloudTime * frameRate)) % frames.length;
        batch.setColor(1f, 1f, 1f, ALPHA);
        batch.draw(frames[frameIdx], cx - size / 2f, cy - size / 2f, size, size);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    /** Настоящий спрайт ауры с анимацией через aura.vert/frag (см. ShaderLibrary.aura()) — тот же
     *  паттерн setShader/uniform/setShader(null), что Editor использует для water/shore-тайлов.
     *  Без шейдера (не скомпилировался) — просто статичный спрайт, без анимации. */
    private void drawSprite(SpriteBatch batch, Texture tex, AuraAnim anim, float cx, float cy) {
        float w = SPRITE_SIZE;
        float h = SPRITE_SIZE * ISO_SQUASH;
        ShaderProgram shader = ShaderLibrary.aura();
        batch.setColor(1f, 1f, 1f, ALPHA);
        if (shader == null) {
            batch.draw(tex, cx - w / 2f, cy - h / 2f, w, h);
            batch.setColor(1f, 1f, 1f, 1f);
            return;
        }
        batch.setShader(shader);
        shader.setUniformf("u_center", cx, cy);
        shader.setUniformf("u_time", FxContext.store.cloudTime);
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
        shader.setUniformf("u_squash", ISO_SQUASH);
        batch.draw(tex, cx - w / 2f, cy - h / 2f, w, h);
        batch.setShader(null);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private Texture loadTexture(AuraAnim anim) {
        Texture cached = textureCache.get(anim.file);
        if (cached != null) return cached;
        Texture tex = FxContext.loadSkillTexture(anim.file);
        if (tex != null) textureCache.put(anim.file, tex);
        return tex;
    }
}
