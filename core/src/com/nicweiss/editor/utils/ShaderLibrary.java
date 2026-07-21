package com.nicweiss.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * Первая система шейдеров в проекте — единая точка загрузки/хранения ShaderProgram'ов для
 * рендера карты (Editor.java переключает batch.setShader(...) точечно, только вокруг отрисовки
 * тайлов нужного типа — остальной рендер идёт стандартным шейдером SpriteBatch, см.
 * Editor.renderMapTiles). Первичная цель — тайлы воды (мерцание/искажение поверхности), см.
 * assets/shaders/water.vert|frag. Дальше сюда же добавлять новые эффекты по мере необходимости.
 */
public class ShaderLibrary {
    private static ShaderProgram water;
    private static boolean waterLoadAttempted = false;

    private static ShaderProgram shore;
    private static boolean shoreLoadAttempted = false;

    private static Texture waterPattern;
    private static boolean waterPatternLoadAttempted = false;

    private static ShaderProgram aura;
    private static boolean auraLoadAttempted = false;

    private static ShaderProgram mist;
    private static boolean mistLoadAttempted = false;

    private ShaderLibrary() {}

    /** Шейдер искажения поверхности воды — грузится/компилируется один раз, лениво. */
    public static ShaderProgram water() {
        if (!waterLoadAttempted) {
            waterLoadAttempted = true;
            // pedantic по умолчанию требует, чтобы шейдер использовал ровно те атрибуты/юниформы,
            // что шлёт SpriteBatch — у нас так и есть, но на всякий случай (см. типичный совет
            // для кастомных SpriteBatch-шейдеров в libGDX) отключаем, чтобы линковка не падала
            // на некритичных предупреждениях.
            ShaderProgram.pedantic = false;
            ShaderProgram program = new ShaderProgram(
                Gdx.files.internal("shaders/water.vert"),
                Gdx.files.internal("shaders/water.frag")
            );
            if (program.isCompiled()) {
                water = program;
            } else {
                Gdx.app.error("ShaderLibrary", "Не удалось скомпилировать water-шейдер: " + program.getLog());
                program.dispose();
                water = null; // рендер тайлов воды просто откатится на обычный шейдер (см. Editor)
            }
        }
        return water;
    }

    /** Шейдер лёгкой анимированной подсветки берега (см. Editor.markShores/drawTile). */
    public static ShaderProgram shore() {
        if (!shoreLoadAttempted) {
            shoreLoadAttempted = true;
            ShaderProgram.pedantic = false;
            ShaderProgram program = new ShaderProgram(
                Gdx.files.internal("shaders/water.vert"), // тот же passthrough-вертексник, что и у воды
                Gdx.files.internal("shaders/shore.frag")
            );
            if (program.isCompiled()) {
                shore = program;
            } else {
                Gdx.app.error("ShaderLibrary", "Не удалось скомпилировать shore-шейдер: " + program.getLog());
                program.dispose();
                shore = null; // рендер тайлов берега просто откатится на обычный шейдер (см. Editor)
            }
        }
        return shore;
    }

    /** Шейдер пульсации умений (размер + яркость, см. aura.vert/frag) — используется для спрайтов
     *  аур/эффектов умений (см. SkillEffectRenderer.drawAuraSprite), НЕ для тайлов карты. */
    public static ShaderProgram aura() {
        if (!auraLoadAttempted) {
            auraLoadAttempted = true;
            ShaderProgram.pedantic = false;
            ShaderProgram program = new ShaderProgram(
                Gdx.files.internal("shaders/aura.vert"),
                Gdx.files.internal("shaders/aura.frag")
            );
            if (program.isCompiled()) {
                aura = program;
            } else {
                Gdx.app.error("ShaderLibrary", "Не удалось скомпилировать aura-шейдер: " + program.getLog());
                program.dispose();
                aura = null; // рендер ауры просто откатится на обычный шейдер (см. SkillEffectRenderer)
            }
        }
        return aura;
    }

    /** Шейдер тумана (лёгкое покачивание UV + альфа-огибающая, см. mist.frag) — используется для
     *  клочков Ледяного Тумана (см. SkillEffectRenderer.updateAndDrawMist). Вертексник — тот же
     *  passthrough, что у воды/берега (см. water() выше), своей вершинной логики тут не нужно. */
    public static ShaderProgram mist() {
        if (!mistLoadAttempted) {
            mistLoadAttempted = true;
            ShaderProgram.pedantic = false;
            ShaderProgram program = new ShaderProgram(
                Gdx.files.internal("shaders/water.vert"),
                Gdx.files.internal("shaders/mist.frag")
            );
            if (program.isCompiled()) {
                mist = program;
            } else {
                Gdx.app.error("ShaderLibrary", "Не удалось скомпилировать mist-шейдер: " + program.getLog());
                program.dispose();
                mist = null; // рендер тумана просто откатится на обычный шейдер (см. SkillEffectRenderer)
            }
        }
        return mist;
    }

    /**
     * Реальная фотография поверхности воды (assets/shaders/water_pattern.jpg) — источник цвета
     * для настоящей бесшовной рефракции в water.frag. Repeat-wrap по обеим осям: читается в
     * НЕПРЕРЫВНЫХ мировых координатах карты, а не в локальных 0..1 UV конкретного тайла-спрайта —
     * поэтому у неё в принципе нет края, о который могло бы "запнуться" искажение (в отличие от
     * маленького изолированного ромба gp_10.png, из-за которого раньше и были все проблемы со
     * швами/зазорами/деформацией).
     */
    public static Texture waterPattern() {
        if (!waterPatternLoadAttempted) {
            waterPatternLoadAttempted = true;
            try {
                Texture t = new Texture(Gdx.files.internal("shaders/water_pattern.jpg"));
                t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                waterPattern = t;
            } catch (Exception e) {
                Gdx.app.error("ShaderLibrary", "Не удалось загрузить water_pattern.jpg: " + e.getMessage());
                waterPattern = null;
            }
        }
        return waterPattern;
    }
}
