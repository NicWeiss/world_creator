package com.nicweiss.editor.components.windows;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Generic.Window;
import com.nicweiss.editor.Interfaces.BaseCallBack.CallBack;

/**
 * Модальное окно прогресса загрузки.
 * Нельзя закрыть вручную — только через кнопку «Готово!» после завершения.
 *
 * Всё содержимое рисуется вручную (не через ButtonCommon/renderItemsList),
 * чтобы обеспечить красивый центрированный макет.
 *
 * Поля прогресса volatile — обновляются из фонового потока,
 * читаются на GL-потоке во время render().
 */
public class LoadingWindow extends Window implements CallBack {
    public static Store store;

    Texture white;

    volatile String archiveName = "";
    volatile int    step        = 0;
    volatile int    totalSteps  = 9;
    volatile String currentFile = "";
    volatile int    percent     = 0;
    volatile boolean isComplete = false;

    // Границы кнопки «Готово!» — вычисляются в render(), проверяются в checkTouch()
    private int okBtnX, okBtnY, okBtnW = 160, okBtnH = 34;

    public LoadingWindow() {
        super();
        windowName   = "Загрузка мира";
        windowWidth  = 560;
        windowHeight = 270;
        closeEnabled = false;

        // Текстуры — в конструкторе (GL-поток через new UserInterface())
        white = new Texture("white.png");
    }

    public void buildWindow() {
        isScrollHidden = true;
        super.buildWindow();
    }

    // ── Управление прогрессом (безопасно из фонового потока) ─────────────────

    public void startLoading(String filename) {
        archiveName  = filename;
        step         = 0;
        currentFile  = "";
        percent      = 0;
        isComplete   = false;
        closeEnabled = false;
        repositionToCenter();
    }

    public void setStep(int s, int total, String file, int pct) {
        step        = s;
        totalSteps  = total;
        currentFile = file;
        percent     = pct;
    }

    public void complete() {
        step         = totalSteps;
        percent      = 100;
        currentFile  = "Загрузка завершена";
        isComplete   = true;
        closeEnabled = true;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);   // рисует рамку, заголовок «Загрузка мира», фон
        if (!isShowWindow) return;

        int cx  = x + width / 2;
        int top = y + windowOperationalHeight; // верх контентной зоны

        // ─── Равномерное распределение по вертикали ──────────────────────────
        // top → archive → step → file → bar → [ok]
        int line1 = top - 32;   // Архив
        int line2 = top - 70;   // Этап N из M
        int line3 = top - 105;  // Операция / файл

        drawCentered(batch, "Архив:  " + archiveName,         cx, line1);
        drawCentered(batch, "Этап " + step + " из " + totalSteps, cx, line2);
        drawCentered(batch, currentFile,                       cx, line3);

        // ─── Прогресс-бар ────────────────────────────────────────────────────
        int barMargin = 55;
        int pctW      = 46;
        int barX = x + barMargin;
        int barW = width - barMargin * 2 - pctW;
        int barY = top - 145;
        int barH = 18;

        // Фон бара
        batch.setColor(0.18f, 0.18f, 0.18f, 1f);
        batch.draw(white, barX, barY, barW, barH);

        // Заполнение
        float fill = Math.max(0f, Math.min(1f, percent / 100f));
        if (isComplete) {
            batch.setColor(0.20f, 0.72f, 0.35f, 1f);  // зелёный когда готово
        } else {
            batch.setColor(0.18f, 0.52f, 0.88f, 1f);  // синий в процессе
        }
        if (fill > 0f) batch.draw(white, barX, barY, (int)(barW * fill), barH);

        // Рамка бара
        batch.setColor(0.42f, 0.42f, 0.42f, 1f);
        batch.draw(white, barX,        barY,       barW, 1);
        batch.draw(white, barX,        barY + barH, barW, 1);
        batch.draw(white, barX,        barY,       1,    barH);
        batch.draw(white, barX + barW, barY,       1,    barH + 1);

        // Процент справа от бара — вертикально по центру бара
        batch.setColor(1f, 1f, 1f, 1f);
        float pctTextH = font.getHeight("0");
        font.draw(batch, percent + "%", barX + barW + 8, barY + barH / 2f - pctTextH / 2f);

        // ─── Кнопка «Готово!» ─────────────────────────────────────────────
        batch.setColor(1f, 1f, 1f, 1f);
        if (isComplete) {
            drawOkButton(batch, cx);
        }

        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawCentered(SpriteBatch batch, String text, int cx, int y) {
        float tw = font.getWidth(text);
        font.draw(batch, text, cx - tw / 2f, y);
    }

    private void drawOkButton(SpriteBatch batch, int cx) {
        okBtnX = cx - okBtnW / 2;
        okBtnY = y + 18;

        boolean hovered = store.mouseX >= okBtnX && store.mouseX <= okBtnX + okBtnW
                       && store.mouseY >= okBtnY && store.mouseY <= okBtnY + okBtnH;

        // Тело кнопки
        batch.setColor(hovered ? 0.30f : 0.20f,
                       hovered ? 0.72f : 0.58f,
                       hovered ? 0.45f : 0.32f, 1f);
        batch.draw(white, okBtnX, okBtnY, okBtnW, okBtnH);

        // Рамка кнопки
        batch.setColor(0.25f, 0.55f, 0.28f, 1f);
        batch.draw(white, okBtnX,           okBtnY,           okBtnW, 1);
        batch.draw(white, okBtnX,           okBtnY + okBtnH,  okBtnW, 1);
        batch.draw(white, okBtnX,           okBtnY,           1, okBtnH);
        batch.draw(white, okBtnX + okBtnW,  okBtnY,           1, okBtnH + 1);

        // Текст кнопки — вертикально по центру кнопки
        batch.setColor(1f, 1f, 1f, 1f);
        String label = "Готово!";
        float lw      = font.getWidth(label);
        float labelH  = font.getHeight(label);
        font.draw(batch, label, cx - lw / 2f, okBtnY + okBtnH / 2f - labelH / 2f);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean checkTouch(boolean isDragged, boolean isTouchUp) {
        if (!isShowWindow) return false;
        super.checkTouch(isDragged, isTouchUp);   // обрабатывает drag окна
        if (isComplete && isTouchUp && !isDragged) {
            if (store.mouseX >= okBtnX && store.mouseX <= okBtnX + okBtnW
             && store.mouseY >= okBtnY && store.mouseY <= okBtnY + okBtnH) {
                hide();
            }
        }
        return isShowWindow;
    }
}
