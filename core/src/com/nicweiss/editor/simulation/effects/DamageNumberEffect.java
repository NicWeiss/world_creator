package com.nicweiss.editor.simulation.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

/**
 * Всплывающая цифра урона над существом (см. CombatSystem.applyDamage) — поднимается на RISE_PX
 * пикселей и постепенно "растворяется в воздухе" (альфа 1→0) за RISE_DURATION секунд. Мировые
 * координаты (не экранные) — фиксированная точка удара/цель, переводится в экранные каждый кадр
 * через FxContext.worldToScreen, как и остальные мировые эффекты (см. GroundFireEffect).
 *
 * Шрифт — один общий на все цифры урона за всё время работы (лениво создаётся один раз, как
 * FxContext.pixel() — не плодить BitmapFont на каждое попадание).
 */
public class DamageNumberEffect extends SkillEffect {
    private static final float RISE_DURATION = 0.9f;
    private static final float RISE_PX = 42f;

    private static BitmapFont font;
    private static final GlyphLayout LAYOUT = new GlyphLayout();

    private static BitmapFont font() {
        if (font == null) {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("Fonts/Roboto-Medium.ttf"));
            FreeTypeFontGenerator.FreeTypeFontParameter param = new FreeTypeFontGenerator.FreeTypeFontParameter();
            param.size = 22;
            param.color = Color.WHITE;
            param.borderWidth = 1.5f;
            param.borderColor = new Color(0, 0, 0, 0.8f);
            font = generator.generateFont(param);
            generator.dispose();
        }
        return font;
    }

    private final float wx, wy;
    private final String text;

    public DamageNumberEffect(float wx, float wy, int amount) {
        this.wx = wx;
        this.wy = wy;
        this.text = String.valueOf(amount);
    }

    @Override
    public boolean update(float dt) {
        age += dt;
        return age < RISE_DURATION;
    }

    @Override
    public void render(SpriteBatch batch) {
        float t = age / RISE_DURATION;
        float[] screen = FxContext.worldToScreen(wx, wy);
        float y = screen[1] + RISE_PX * t;
        float alpha = 1f - t;

        BitmapFont f = font();
        LAYOUT.setText(f, text);
        f.setColor(1f, 0.92f, 0.75f, alpha);
        f.draw(batch, text, screen[0] - LAYOUT.width / 2f, y + LAYOUT.height / 2f);
        f.setColor(Color.WHITE);
    }
}
