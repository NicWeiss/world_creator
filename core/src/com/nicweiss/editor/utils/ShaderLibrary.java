package com.nicweiss.editor.utils;

import com.badlogic.gdx.Gdx;
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
}
