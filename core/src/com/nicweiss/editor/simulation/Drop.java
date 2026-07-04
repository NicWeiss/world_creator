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
 * Предмет, кучка золота или сфера опыта, появившиеся после убийства врага / открытия сундука.
 * Появляется с анимацией броска по дуге (как будто подкинули вверх и в сторону). Предметы/золото
 * затем лежат на тайле до подбора; сферы опыта не укладываются — парят над тайлом до подбора
 * игроком (см. expAmount, draw/updateThrow). Спавнится через {@link DropManager}.
 *
 * Сам Drop умеет только рисовать свою иконку и (по запросу) одну плашку подписи в заданной
 * точке — решение "показывать ли подпись, где и с каким фокусом" принимает DropManager
 * (нужен общий проход по всем дропам для раскладки "кирпичиками" без перекрытия).
 */
public class Drop extends BaseObject {

    // Тайл, на который предмет в итоге падает (1-based, та же конвенция, что у Creation.mapCellX/Y).
    public int mapCellX, mapCellY;

    public LinkedHashMap itemData; // null = это кучка золота или сфера опыта, не предмет
    public int goldAmount;
    public int expAmount; // >0 = это сфера опыта, а не предмет/золото (см. draw/updateThrow)

    // ── Анимация броска по дуге ──────────────────────────────────────────────
    private static final float THROW_DURATION = 0.3f; // сек
    private static final float ARC_HEIGHT = 60f;        // пикселей вверх в пике дуги
    private float startIsoX, startIsoY, endIsoX, endIsoY;
    private float elapsed = 0f;
    public boolean isLanded = false;

    // ── Парение сфер опыта (не укладываются на землю, в отличие от предметов/золота) ─────────
    private static final float BOB_AMPLITUDE = 8f;  // пикселей вверх-вниз
    private static final float BOB_SPEED     = 2.2f; // рад/сек
    private float bobTime = 0f;

    // ── Случайный ракурс при отрисовке лёжа ──────────────────────────────────
    // Для разнообразия: 33% — как есть, 33% — +90°, 33% — -90° (см. drawIsoFlat).
    private static final Random ROTATION_RANDOM = new Random();
    private int rotationStep = 0; // сдвиг UV по часовой стрелке: 0, 1 (+90°) или 3 (-90°, т.е. -1 mod 4)

    // ── Подпись над предметом ──────────────────────────────────────────────────
    private static Font labelFont;
    private static Texture plaqueTexture;
    private static final float PAD_X = 7.5f, PAD_Y = 4.5f;
    private static final float OUTLINE_THICKNESS = 1.5f;

    private String labelText;
    private float[] labelBgColor = {1f, 1f, 1f};
    private float labelBgAlpha = 1f;
    private float[] labelTextColor = {0f, 0f, 0f};
    // Маленькая иконка-маркер слева от текста (сердце/искра/звезда у зелий) — см. DropManager.
    private Texture labelIcon;
    private static final float LABEL_ICON_GAP = 3f;

    // Постоянное свечение лейбла (Rare/Unique, см. DropManager.setLabelGlow) — в отличие от
    // focused-обводки ниже, включено ВСЕГДА, не только при наведении курсора. Два вида:
    // OUTLINE — тонкая пульсирующая рамка вокруг плашки (Rare, дёшево и достаточно заметно);
    // HALO — мягкое аддитивное свечение большим радиусом позади плашки (Unique, эффектнее).
    public enum LabelGlow { NONE, OUTLINE, HALO }
    private LabelGlow labelGlow = LabelGlow.NONE;

    private static final long  GLOW_PERIOD_MS = 1400L;  // период пульсации OUTLINE (Rare)
    private static final float GLOW_MIN_ALPHA = 0.35f, GLOW_MAX_ALPHA = 1f;

    // HALO (Unique) — медленнее и шире, чем OUTLINE: "дыхание", а не быстрое мерцание.
    private static Texture haloTexture;
    private static final int   HALO_TEX_SIZE   = 64;   // разрешение процедурной радиальной текстуры
    private static final long  HALO_PERIOD_MS  = 2600L;
    private static final float HALO_ALPHA_MIN  = 0.35f, HALO_ALPHA_MAX = 0.85f;
    private static final float HALO_SIZE_MIN   = 1.8f, HALO_SIZE_MAX = 2.6f; // множитель от большей стороны плашки

    /** Подпрыгивает на месте (без бокового смещения) — когда в инвентаре нет места для подбора. */
    public void bounce() {
        initThrow(x, y, x, y);
    }

    /** Сдвигает позицию на (dx,dy) в изометрических мировых пикселях — магнит сферы опыта к игроку (см. DropManager). */
    public void moveBy(float dx, float dy) {
        x += dx;
        y += dy;
    }

    /** Запускает анимацию броска от (startIsoX,startIsoY) к (endIsoX,endIsoY) — изометрические мировые пиксели. */
    public void initThrow(float startIsoX, float startIsoY, float endIsoX, float endIsoY) {
        this.startIsoX = startIsoX;
        this.startIsoY = startIsoY;
        this.endIsoX = endIsoX;
        this.endIsoY = endIsoY;
        this.elapsed = 0f;
        this.isLanded = false;
        this.bobTime = 0f;
        x = startIsoX;
        y = startIsoY;

        float roll = ROTATION_RANDOM.nextFloat();
        rotationStep = roll < 0.33f ? 1 : roll < 0.66f ? 3 : 0;
    }

    /**
     * Продвигает анимацию броска на dt секунд. Вызывается из CreationThread (DropManager.update).
     * Сферы опыта не останавливаются на "приземлении" — им нужен dt и после isLanded, чтобы парить.
     */
    public void updateThrow(float dt) {
        if (isLanded) {
            if (expAmount > 0) bobTime += dt;
            return;
        }
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
        setLabel(text, bgColor, bgAlpha, textColor, null);
    }

    /** Вариант с иконкой-маркером слева от текста (сердце/искра/звезда у зелий) — см. DropManager. */
    public void setLabel(String text, float[] bgColor, float bgAlpha, float[] textColor, Texture icon) {
        labelText = text;
        labelBgColor = bgColor;
        labelBgAlpha = bgAlpha;
        labelTextColor = textColor;
        labelIcon = icon;
    }

    /** Постоянное свечение лейбла — NONE/OUTLINE/HALO, см. константы GLOW_ и HALO_, drawLabelAt. */
    public void setLabelGlow(LabelGlow glow) {
        labelGlow = glow;
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

    // Кэш последнего расчёта позиции источника света — cartesianToIsometric не бесплатен,
    // а getLightSourceIsoX/Y дважды за кадр (X и Y) вызывают одно и то же преобразование.
    private int litForCellX = Integer.MIN_VALUE, litForCellY = Integer.MIN_VALUE;
    private final float[] lightSourcePos = new float[2];

    private void ensureLightSourcePos() {
        if (litForCellX == mapCellX && litForCellY == mapCellY) return;
        // Единая логика — см. Lighting.tileLightAnchor (используется и Creation, чтобы не дублировать).
        com.nicweiss.editor.utils.Lighting.tileLightAnchor(mapCellX, mapCellY, lightSourcePos);
        litForCellX = mapCellX;
        litForCellY = mapCellY;
    }

    /** Позиция источника света дропа — см. ensureLightSourcePos(). Тот же формат, что store.lightPoints[i][1]/[2]. */
    public float getLightSourceIsoX() { ensureLightSourcePos(); return lightSourcePos[0]; }
    public float getLightSourceIsoY() { ensureLightSourcePos(); return lightSourcePos[1]; }

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
        if (labelText == null) return 0f;
        float w = labelFont.getWidth(labelText) + PAD_X * 2;
        if (labelIcon != null) w += labelIconSize() + LABEL_ICON_GAP;
        return w;
    }

    public float getLabelHeight() {
        ensureLabelAssets();
        return labelText == null ? 0f : labelFont.getHeight(labelText) + PAD_Y * 2;
    }

    // Иконка-маркер — квадрат со стороной в высоту строки текста, чтобы вписаться в плашку без
    // лишнего вертикального паддинга.
    private float labelIconSize() {
        return labelText == null ? 0f : labelFont.getHeight(labelText);
    }

    public void draw(SpriteBatch batch) {
        if (img == null) return;
        float drawX = x + store.shiftX;
        float drawY = y + store.shiftY;

        float[] fp = computeFootprint();
        width = Math.round(fp[0]);
        height = Math.round(fp[1]);

        if (expAmount > 0) {
            // Сферы опыта сами светятся — не темнеют от ночи/дождя/тени (см. calcLitColor в MapObject:
            // они сами являются источником света для тайлов и других дропов, а не его получателем).
            batch.setColor(1, 1, 1, 1);
            // Никогда не укладываются на землю — парят анфас, покачиваясь по синусоиде после
            // приземления (см. updateThrow). В полёте (бросок по дуге) — без покачивания.
            float bob = isLanded ? (float) Math.sin(bobTime * BOB_SPEED) * BOB_AMPLITUDE : 0f;
            batch.draw(img, drawX, drawY + bob, width, height);
        } else if (isLanded) {
            // Предметы/золото освещаются как обычные тайлы карты — см. computeLitColor().
            float[] lit = computeLitColor();
            batch.setColor(lit[0], lit[1], lit[2], 1f);
            // Лежащий предмет проецируем как изометрический ромб 2:1 (как сами тайлы),
            // а не плоский прямоугольник — иначе нет ощущения, что он лежит на земле.
            float centerX = drawX + width / 2f;
            float centerY = drawY + height / 2f;
            float diamondWidth = Math.max(width, height);
            drawIsoFlat(batch, img, centerX, centerY, diamondWidth);
        } else {
            // В полёте (бросок по дуге) предмет рисуется обычным спрайтом "анфас", но освещается так же.
            float[] lit = computeLitColor();
            batch.setColor(lit[0], lit[1], lit[2], 1f);
            batch.draw(img, drawX, drawY, width, height);
        }
    }

    // ── Освещение (день/ночь, дождь, источники света) ───────────────────────────
    // Reusable-буфер результата — избегаем new float[3] на каждый кадр для каждого дропа.
    private final float[] litColorBuf = new float[3];

    /**
     * Освещённость предмета/золота в текущей точке — единая логика, см. Lighting.computeLitColor
     * (используется и Creation, чтобы не дублировать день/ночь/дождь/источники света дважды).
     * this передаётся как excludeDrop — сфера опыта не подсвечивает сама себя.
     */
    private float[] computeLitColor() {
        return com.nicweiss.editor.utils.Lighting.computeLitColor(
            getLightSourceIsoX(), getLightSourceIsoY(), this, litColorBuf);
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

        if (labelGlow == LabelGlow.HALO) {
            // Unique: гало + пульсирующая обводка на ОДНОЙ фазе ("в такт") — общий pulse на оба.
            float pulse = pulseValue(HALO_PERIOD_MS);
            drawHalo(batch, plaqueX, plaqueY, plaqueW, plaqueH, pulse);
            if (!focused) drawGlowOutline(batch, plaqueX, plaqueY, plaqueW, plaqueH, pulse);
        } else if (!focused && labelGlow == LabelGlow.OUTLINE) {
            // Rare: только пульсирующая обводка, свой (более быстрый) период.
            drawGlowOutline(batch, plaqueX, plaqueY, plaqueW, plaqueH, pulseValue(GLOW_PERIOD_MS));
        }

        if (focused) {
            // Обводка в цвет текста — увеличенный прямоугольник позади основной плашки.
            batch.setColor(labelTextColor[0], labelTextColor[1], labelTextColor[2], 1f);
            batch.draw(plaqueTexture,
                plaqueX - OUTLINE_THICKNESS, plaqueY - OUTLINE_THICKNESS,
                plaqueW + OUTLINE_THICKNESS * 2, plaqueH + OUTLINE_THICKNESS * 2);
        }

        batch.setColor(labelBgColor[0], labelBgColor[1], labelBgColor[2], labelBgAlpha);
        batch.draw(plaqueTexture, plaqueX, plaqueY, plaqueW, plaqueH);

        float textX = plaqueX + PAD_X;
        if (labelIcon != null) {
            float iconSize = labelIconSize();
            batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(labelIcon, plaqueX + PAD_X, plaqueY + PAD_Y, iconSize, iconSize);
            textX += iconSize + LABEL_ICON_GAP;
        }

        // BitmapFont печёт цвет вершин из своего внутреннего поля в момент draw() — тинт батча
        // на сам текст не действует, поэтому красим шрифт напрямую (см. Font.setColor).
        batch.setColor(1f, 1f, 1f, 1f);
        labelFont.setColor(new Color(labelTextColor[0], labelTextColor[1], labelTextColor[2], 1f));
        labelFont.draw(batch, labelText, textX, plaqueY + PAD_Y);
        batch.setColor(1, 1, 1, 1);
    }

    // Целевой видимый размер — 6. Запекаем в 2 раза крупнее (12) и выводим с scale=0.5 —
    // суперсэмплинг: подписи рисуются в мировом батче, который во время симуляции заметно
    // приближен камерой (см. UserInterface.SIM_ZOOM_TARGET), обычный 1:1 бейк мылится при таком зуме.
    private static final int LABEL_FONT_SIZE = 6;
    private static final int LABEL_FONT_BAKE_MULT = 2;

    private static void ensureLabelAssets() {
        if (labelFont == null) {
            // Шрифт запекаем белым — иначе batch.setColor(тинт) не подействует (чёрный*цвет = чёрный).
            labelFont = new Font(LABEL_FONT_SIZE * LABEL_FONT_BAKE_MULT, Color.WHITE);
            labelFont.setScale(1f / LABEL_FONT_BAKE_MULT);
        }
        if (plaqueTexture == null) {
            Pixmap pmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pmap.setColor(1, 1, 1, 1);
            pmap.fill();
            plaqueTexture = new Texture(pmap);
            pmap.dispose();
        }
        if (haloTexture == null) {
            haloTexture = buildHaloTexture();
        }
    }

    /**
     * Мягкое аддитивное свечение (Unique, см. LabelGlow.HALO) — большой мягкий круг позади
     * плашки, пульсирующий по размеру и alpha. Аддитивный блендинг (в отличие от обычного
     * alpha-blend у плашки/обводки) даёт настоящее "свечение", а не плоский цветной прямоугольник.
     */
    private void drawHalo(SpriteBatch batch, float plaqueX, float plaqueY, float plaqueW, float plaqueH, float pulse) {
        float haloSize = Math.max(plaqueW, plaqueH) * (HALO_SIZE_MIN + (HALO_SIZE_MAX - HALO_SIZE_MIN) * pulse);
        float alpha = HALO_ALPHA_MIN + (HALO_ALPHA_MAX - HALO_ALPHA_MIN) * pulse;

        float cx = plaqueX + plaqueW / 2f;
        float cy = plaqueY + plaqueH / 2f;

        int srcFunc = batch.getBlendSrcFunc();
        int dstFunc = batch.getBlendDstFunc();
        batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE);
        batch.setColor(labelTextColor[0], labelTextColor[1], labelTextColor[2], alpha);
        batch.draw(haloTexture, cx - haloSize / 2f, cy - haloSize / 2f, haloSize, haloSize);
        batch.setBlendFunction(srcFunc, dstFunc);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    /** Пульсирующая обводка плашки (не путать с solid-обводкой focused) — alpha по pulse [0..1]. */
    private void drawGlowOutline(SpriteBatch batch, float plaqueX, float plaqueY, float plaqueW, float plaqueH, float pulse) {
        float alpha = GLOW_MIN_ALPHA + (GLOW_MAX_ALPHA - GLOW_MIN_ALPHA) * pulse;
        batch.setColor(labelTextColor[0], labelTextColor[1], labelTextColor[2], alpha);
        batch.draw(plaqueTexture,
            plaqueX - OUTLINE_THICKNESS, plaqueY - OUTLINE_THICKNESS,
            plaqueW + OUTLINE_THICKNESS * 2, plaqueH + OUTLINE_THICKNESS * 2);
    }

    /** Значение пульсации [0..1] по синусоиде от текущего времени с периодом periodMs. */
    private static float pulseValue(long periodMs) {
        float phase = (System.currentTimeMillis() % periodMs) / (float) periodMs;
        return (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2));
    }

    /** Процедурная мягкая радиальная текстура (белая, альфа затухает от центра к краю) — см. drawHalo. */
    private static Texture buildHaloTexture() {
        Pixmap pmap = new Pixmap(HALO_TEX_SIZE, HALO_TEX_SIZE, Pixmap.Format.RGBA8888);
        float c = HALO_TEX_SIZE / 2f;
        for (int py = 0; py < HALO_TEX_SIZE; py++) {
            for (int px = 0; px < HALO_TEX_SIZE; px++) {
                float dx = px + 0.5f - c, dy = py + 0.5f - c;
                float dist = (float) Math.sqrt(dx * dx + dy * dy) / c;
                float a = dist >= 1f ? 0f : (float) Math.pow(1f - dist, 2f);
                pmap.setColor(1f, 1f, 1f, a);
                pmap.drawPixel(px, py);
            }
        }
        Texture t = new Texture(pmap);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pmap.dispose();
        return t;
    }
}
