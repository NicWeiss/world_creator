package com.nicweiss.editor.simulation;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nicweiss.editor.Generic.BaseObject;
import com.nicweiss.editor.utils.Font;

import java.util.LinkedHashMap;
import java.util.Random;

/**
 * Предмет или кучка золота, лежащие на земле (после убийства врага / открытия сундука).
 * Появляется с анимацией броска по дуге (как будто подкинули вверх и в сторону),
 * затем лежит на тайле до подбора. Спавнится через {@link DropManager}.
 *
 * Сам Drop умеет только рисовать свою иконку и (по запросу) одну плашку подписи в заданной
 * точке — решение "показывать ли подпись, где и с каким фокусом" принимает DropManager
 * (нужен общий проход по всем дропам для раскладки "кирпичиками" без перекрытия).
 */
public class Drop extends BaseObject {

    // Тайл, на который предмет в итоге падает (1-based, та же конвенция, что у Creation.mapCellX/Y).
    public int mapCellX, mapCellY;

    public LinkedHashMap itemData; // null = это кучка золота, не предмет
    public int goldAmount;

    // ── Анимация броска по дуге ──────────────────────────────────────────────
    private static final float THROW_DURATION = 0.3f; // сек
    private static final float ARC_HEIGHT = 60f;        // пикселей вверх в пике дуги
    private float startIsoX, startIsoY, endIsoX, endIsoY;
    private float elapsed = 0f;
    public boolean isLanded = false;

    // ── Случайный ракурс при отрисовке лёжа ──────────────────────────────────
    // Для разнообразия: 33% — как есть, 33% — +90°, 33% — -90° (см. drawIsoFlat).
    private static final Random ROTATION_RANDOM = new Random();
    private int rotationStep = 0; // сдвиг UV по часовой стрелке: 0, 1 (+90°) или 3 (-90°, т.е. -1 mod 4)

    // ── Подпись над предметом ──────────────────────────────────────────────────
    private static Font labelFont;
    private static Texture plaqueTexture;
    private static final float PAD_X = 10, PAD_Y = 6;
    private static final float OUTLINE_THICKNESS = 2f;

    private String labelText;
    private float[] labelBgColor = {1f, 1f, 1f};
    private float labelBgAlpha = 1f;
    private float[] labelTextColor = {0f, 0f, 0f};

    /** Подпрыгивает на месте (без бокового смещения) — когда в инвентаре нет места для подбора. */
    public void bounce() {
        initThrow(x, y, x, y);
    }

    /** Запускает анимацию броска от (startIsoX,startIsoY) к (endIsoX,endIsoY) — изометрические мировые пиксели. */
    public void initThrow(float startIsoX, float startIsoY, float endIsoX, float endIsoY) {
        this.startIsoX = startIsoX;
        this.startIsoY = startIsoY;
        this.endIsoX = endIsoX;
        this.endIsoY = endIsoY;
        this.elapsed = 0f;
        this.isLanded = false;
        x = startIsoX;
        y = startIsoY;

        float roll = ROTATION_RANDOM.nextFloat();
        rotationStep = roll < 0.33f ? 1 : roll < 0.66f ? 3 : 0;
    }

    /** Продвигает анимацию броска на dt секунд. Вызывается из CreationThread (DropManager.update). */
    public void updateThrow(float dt) {
        if (isLanded) return;
        elapsed += dt;
        float t = Math.min(1f, elapsed / THROW_DURATION);
        x = startIsoX + (endIsoX - startIsoX) * t;
        float arc = 4f * ARC_HEIGHT * t * (1f - t); // парабола: 0 в начале и в конце, пик в середине
        y = startIsoY + (endIsoY - startIsoY) * t + arc;
        if (t >= 1f) {
            isLanded = true;
            x = endIsoX;
            y = endIsoY;
        }
    }

    /** Цвет/прозрачность фона плашки и цвет текста (RGB, 0..1) — см. DropManager. */
    public void setLabel(String text, float[] bgColor, float bgAlpha, float[] textColor) {
        labelText = text;
        labelBgColor = bgColor;
        labelBgAlpha = bgAlpha;
        labelTextColor = textColor;
    }

    public boolean hasLabel() {
        return labelText != null;
    }

    // Золото, амулеты, артефакты и чармы физически мелкие предметы — на полный тайл смотрятся
    // непропорционально крупно, поэтому уменьшаем их дополнительно (в 6 раз от "размера в тайл").
    private float smallItemScaleFactor() {
        if (itemData == null) return 1f / 6f; // золото
        String typeKey = (String) itemData.get("__type__");
        if ("amulet".equals(typeKey) || "artifact".equals(typeKey) || "charm".equals(typeKey)) {
            return 1f / 6f;
        }
        return 1f;
    }

    private float[] computeFootprint() {
        if (img == null) return new float[]{0f, 0f};
        float scale = Math.min(store.tileSizeWidth / img.getWidth(), store.tileSizeHeight / img.getHeight());
        scale *= smallItemScaleFactor();
        return new float[]{img.getWidth() * scale, img.getHeight() * scale};
    }

    // ── Геометрия иконки в экранных координатах (для DropManager: видимость/наводка/раскладка) ──

    public float getWorldIsoX() { return x; }
    public float getWorldIsoY() { return y; }

    public float getIconScreenCenterX() {
        float[] fp = computeFootprint();
        return x + store.shiftX + fp[0] / 2f;
    }

    public float getIconScreenCenterY() {
        float[] fp = computeFootprint();
        return y + store.shiftY + fp[1] / 2f;
    }

    public float getIconFootprint() {
        float[] fp = computeFootprint();
        return Math.max(fp[0], fp[1]);
    }

    public float getLabelWidth() {
        ensureLabelAssets();
        return labelText == null ? 0f : labelFont.getWidth(labelText) + PAD_X * 2;
    }

    public float getLabelHeight() {
        ensureLabelAssets();
        return labelText == null ? 0f : labelFont.getHeight(labelText) + PAD_Y * 2;
    }

    public void draw(SpriteBatch batch) {
        if (img == null) return;
        float drawX = x + store.shiftX;
        float drawY = y + store.shiftY;

        float[] fp = computeFootprint();
        width = Math.round(fp[0]);
        height = Math.round(fp[1]);

        batch.setColor(1, 1, 1, 1);

        if (isLanded) {
            // Лежащий предмет проецируем как изометрический ромб 2:1 (как сами тайлы),
            // а не плоский прямоугольник — иначе нет ощущения, что он лежит на земле.
            float centerX = drawX + width / 2f;
            float centerY = drawY + height / 2f;
            float diamondWidth = Math.max(width, height);
            drawIsoFlat(batch, img, centerX, centerY, diamondWidth);
        } else {
            // В полёте (бросок по дуге) предмет рисуется обычным спрайтом "анфас".
            batch.draw(img, drawX, drawY, width, height);
        }
    }

    // Переиспользуемый буфер вершин — избегаем new float[20] на каждый кадр для каждого дропа.
    private final float[] isoQuad = new float[20];

    // Базовые UV по углам ромба [низ, лево, верх, право] при rotationStep=0.
    // У libGDX v=0 — верх текстуры, v=1 — низ (см. SpriteBatch.draw(texture,x,y,w,h)).
    private static final float[][] BASE_UV = {{0f, 1f}, {0f, 0f}, {1f, 0f}, {1f, 1f}};

    /**
     * Рисует текстуру как ромб 2:1 с центром (centerX,centerY) и диагональю diamondWidth по X —
     * та же проекция, что у изометрических тайлов (см. Transform.cartesianToIsometric: квадрат
     * в декартовых координатах превращается в ромб с горизонтальной диагональю вдвое длиннее вертикальной).
     */
    private void drawIsoFlat(SpriteBatch batch, Texture tex, float centerX, float centerY, float diamondWidth) {
        float halfW = diamondWidth / 2f;
        float halfH = diamondWidth / 4f;
        float color = batch.getPackedColor();

        // Циклический сдвиг UV по углам ромба = поворот картинки на ±90° (rotationStep задан в initThrow).
        float[] uvBottom = BASE_UV[rotationStep % 4];
        float[] uvLeft   = BASE_UV[(rotationStep + 1) % 4];
        float[] uvTop    = BASE_UV[(rotationStep + 2) % 4];
        float[] uvRight  = BASE_UV[(rotationStep + 3) % 4];

        int i = 0;
        // нижняя вершина
        isoQuad[i++] = centerX;         isoQuad[i++] = centerY - halfH; isoQuad[i++] = color; isoQuad[i++] = uvBottom[0]; isoQuad[i++] = uvBottom[1];
        // левая вершина
        isoQuad[i++] = centerX - halfW; isoQuad[i++] = centerY;         isoQuad[i++] = color; isoQuad[i++] = uvLeft[0];   isoQuad[i++] = uvLeft[1];
        // верхняя вершина
        isoQuad[i++] = centerX;         isoQuad[i++] = centerY + halfH; isoQuad[i++] = color; isoQuad[i++] = uvTop[0];    isoQuad[i++] = uvTop[1];
        // правая вершина
        isoQuad[i++] = centerX + halfW; isoQuad[i++] = centerY;         isoQuad[i++] = color; isoQuad[i++] = uvRight[0];  isoQuad[i++] = uvRight[1];

        batch.draw(tex, isoQuad, 0, 20);
    }

    /** Рисует одну плашку подписи в заданной экранной точке (левый-нижний угол). Позицию решает DropManager. */
    public void drawLabelAt(SpriteBatch batch, float plaqueX, float plaqueY, boolean focused) {
        if (labelText == null) return;
        ensureLabelAssets();

        float plaqueW = getLabelWidth();
        float plaqueH = getLabelHeight();

        if (focused) {
            // Обводка в цвет текста — увеличенный прямоугольник позади основной плашки.
            batch.setColor(labelTextColor[0], labelTextColor[1], labelTextColor[2], 1f);
            batch.draw(plaqueTexture,
                plaqueX - OUTLINE_THICKNESS, plaqueY - OUTLINE_THICKNESS,
                plaqueW + OUTLINE_THICKNESS * 2, plaqueH + OUTLINE_THICKNESS * 2);
        }

        batch.setColor(labelBgColor[0], labelBgColor[1], labelBgColor[2], labelBgAlpha);
        batch.draw(plaqueTexture, plaqueX, plaqueY, plaqueW, plaqueH);

        // BitmapFont печёт цвет вершин из своего внутреннего поля в момент draw() — тинт батча
        // на сам текст не действует, поэтому красим шрифт напрямую (см. Font.setColor).
        batch.setColor(1f, 1f, 1f, 1f);
        labelFont.setColor(new Color(labelTextColor[0], labelTextColor[1], labelTextColor[2], 1f));
        labelFont.draw(batch, labelText, plaqueX + PAD_X, plaqueY + PAD_Y);
        batch.setColor(1, 1, 1, 1);
    }

    private static void ensureLabelAssets() {
        if (labelFont == null) {
            // Шрифт запекаем белым — иначе batch.setColor(тинт) не подействует (чёрный*цвет = чёрный).
            labelFont = new Font(8, Color.WHITE);
        }
        if (plaqueTexture == null) {
            Pixmap pmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pmap.setColor(1, 1, 1, 1);
            pmap.fill();
            plaqueTexture = new Texture(pmap);
            pmap.dispose();
        }
    }
}
