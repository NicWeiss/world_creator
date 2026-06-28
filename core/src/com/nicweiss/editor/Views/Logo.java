package com.nicweiss.editor.Views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Main;
import com.nicweiss.editor.utils.Font;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

// ── Прогресс загрузки ────────────────────────────────────────────────────────
// Volatile-поля: обновляются из фонового потока ResourceLoader,
// читаются на GL-потоке в Logo.render().
class LoadProgress {
    static volatile int    step       = 0;
    static volatile int    totalSteps = 3;
    static volatile String stepLabel  = "Инициализация";
    static volatile String detail     = "";
    static volatile int    percent    = 0;
}

// ── Фоновый загрузчик ────────────────────────────────────────────────────────
class ResourceLoader implements Callable<String> {
    Editor localEditorClass;
    public static Store store;

    public ResourceLoader(Editor editorClass) {
        localEditorClass = editorClass;
    }

    @Override
    public String call() throws Exception {
        LoadProgress.step      = 1;
        LoadProgress.stepLabel = "Генерация мира";
        LoadProgress.detail    = "terrain_generator";
        LoadProgress.percent   = 5;

        localEditorClass.defineMap();

        LoadProgress.step      = 2;
        LoadProgress.stepLabel = "Загрузка модулей";
        LoadProgress.detail    = "ui_modules";
        LoadProgress.percent   = 60;

        localEditorClass.defineUI();

        LoadProgress.step      = 3;
        LoadProgress.stepLabel = "Готово";
        LoadProgress.detail    = "";
        LoadProgress.percent   = 100;

        store.isEditorLoadComplete = true;
        return null;
    }
}

// ── Экран-заставка ───────────────────────────────────────────────────────────
public class Logo extends View {

    private static final String APP_NAME   = "World Creator 1.0";
    private static final int    STRIP_H    = 54;   // высота нижней плашки, px
    private static final float  DELAY_DONE = 0.6f; // пауза после загрузки, с

    Texture logoImg;
    Texture white;
    Font    font;
    Editor  editorClass;

    private float completionTimer = 0f;

    public Logo() {
        logoImg    = new Texture("logo.png");
        white      = new Texture("white.png");
        font       = new Font(8, Color.WHITE);
        editorClass = new Editor();

        ResourceLoader resourceLoader = new ResourceLoader(editorClass);
        FutureTask<String> task = new FutureTask<>(resourceLoader);
        new Thread(task).start();
    }

    private void openMenu() {
        Main.changeView(editorClass);
    }

    @Override
    public void render(SpriteBatch batch) {
        float W = store.display.get("width");
        float H = store.display.get("height");

        // ── Фон ─────────────────────────────────────────────────────────────
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // ── Логотип ──────────────────────────────────────────────────────────
        // Занимает центр области над плашкой, масштаб сохраняет пропорции
        float areaH = H - STRIP_H - 60;  // область для логотипа (оставляем отступ)
        float areaW = W * 0.6f;

        float imgRatio = logoImg.getHeight() / (float) logoImg.getWidth();
        float logoW = areaW;
        float logoH = logoW * imgRatio;
        if (logoH > areaH) {
            logoH = areaH;
            logoW = logoH / imgRatio;
        }

        float logoX = W / 2f - logoW / 2f;
        float logoY = STRIP_H + (H - STRIP_H) / 2f - logoH / 2f;

        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(logoImg, logoX, logoY, logoW, logoH);

        // ── Версия (левый нижний угол над плашкой) ───────────────────────────
        batch.setColor(0.55f, 0.55f, 0.62f, 1f);
        font.draw(batch, APP_NAME, 14, STRIP_H + 10);

        // ── Нижняя тёмная плашка ─────────────────────────────────────────────
        batch.setColor(0.10f, 0.10f, 0.13f, 1f);
        batch.draw(white, 0, 0, W, STRIP_H);

        // Граница между логотипом и плашкой
        batch.setColor(0.20f, 0.20f, 0.25f, 1f);
        batch.draw(white, 0, STRIP_H - 1, W, 1);

        // ── Прогресс-бар (верхние 5px плашки, на всю ширину) ─────────────────
        int pct     = LoadProgress.percent;
        int step    = LoadProgress.step;
        int total   = LoadProgress.totalSteps;
        String lbl  = LoadProgress.stepLabel;
        String det  = LoadProgress.detail;

        // Фон бара (вся ширина)
        batch.setColor(0.16f, 0.16f, 0.20f, 1f);
        batch.draw(white, 0, STRIP_H - 5, W, 5);

        // Заполнение
        float fill = Math.max(0f, Math.min(1f, pct / 100f));
        if (pct >= 100) {
            batch.setColor(0.20f, 0.72f, 0.38f, 1f);  // зелёный — готово
        } else {
            batch.setColor(0.20f, 0.50f, 0.90f, 1f);  // синий — в процессе
        }
        batch.draw(white, 0, STRIP_H - 5, W * fill, 5);

        // ── Текст внутри плашки ───────────────────────────────────────────────
        batch.setColor(0.72f, 0.72f, 0.78f, 1f);

        // Левая часть: "Этап X/Y (detail) • Label"
        String leftTxt;
        if (!det.isEmpty()) {
            leftTxt = "Этап " + step + "/" + total
                    + "  (" + det + ")  •  " + lbl;
        } else {
            leftTxt = "Этап " + step + "/" + total + "  •  " + lbl;
        }
        font.draw(batch, leftTxt, 14, (STRIP_H - 5) / 2f);

        // Правая часть: процент
        String pctTxt = pct + "%";
        float pctTxtW = font.getWidth(pctTxt);
        font.draw(batch, pctTxt, W - pctTxtW - 14, (STRIP_H - 5) / 2f);

        batch.setColor(1f, 1f, 1f, 1f);

        // ── Переход на Editor ─────────────────────────────────────────────────
        if (store.isEditorLoadComplete) {
            completionTimer += Gdx.graphics.getDeltaTime();
            if (completionTimer >= DELAY_DONE) {
                openMenu();
            }
        }
    }
}
