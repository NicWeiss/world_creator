package com.nicweiss.editor;

import static com.badlogic.gdx.Application.LOG_INFO;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Views.Logo;


public class Main extends ApplicationAdapter {
	SpriteBatch batch, uiBatch;

	public static ExtendViewport viewport, uiViewport;
	public static View view;
	public static Stage stage;
	public static Store store;
	public static OrthographicCamera camera, uiCamera;

	public static float width = 0, uiWidthOriginal = 0;
	public static float height = 0, uiHeightOriginal = 0;

	
	@Override
	public void create () {
		store = new Store();

		Gdx.app.setLogLevel(LOG_INFO);
		setCamera(1920, 1080);
		stage = new Stage(uiViewport);

		changeView(new Logo());
		batch = new SpriteBatch();
		uiBatch = new SpriteBatch();
	}

	public void setCamera(float cameraWidth, float cameraHeight){
		updateSize(cameraWidth, cameraHeight);

		camera = new OrthographicCamera();
		viewport = new ExtendViewport(width, height, camera);
		viewport.apply();
		viewport.update((int)width, (int)height);
		camera.position.set(width/2, height/2, 0);

		uiCamera = new OrthographicCamera();
		uiViewport = new ExtendViewport(width, 100, uiCamera);
		uiViewport.apply();
		uiViewport.update((int)width, (int)height);
		uiCamera.position.set(width/2, height/2, 0);
	}

	public void updateCamera(float cameraWidth, float cameraHeight) {
		updateSize(cameraWidth, cameraHeight);

		camera.viewportWidth = width;
		camera.viewportHeight = height;
		camera.update();
		camera.position.set(width/2, height/2, 0);

		uiCamera.viewportWidth = uiWidthOriginal;
		uiCamera.viewportHeight = uiHeightOriginal;
		uiCamera.update();
		uiCamera.position.set(uiWidthOriginal/2, uiHeightOriginal/2, 0);
	}

	@Override
	public void resize(int r_width, int r_height) {
		uiWidthOriginal= r_width;
		uiHeightOriginal = r_height;

		Store.uiWidthOriginal = uiWidthOriginal;
		Store.uiHeightOriginal = uiHeightOriginal;

		store.scale = store.scaleTotal;
		store.isNeedToChangeScale = true;

		super.resize(r_width, r_height);
		viewport.update(r_width, r_height);
		uiViewport.update(r_width, r_height);
		updateSize((float) r_width, (float) r_height);
	}

	public void updateSize(float sizeX, float sizeY){
		width = sizeX;
		height = sizeY;

		Store.display.put("width", width);
		Store.display.put("height", height);
	}

	@Override
	public void render() {
		if (store.isNeedToChangeScale){
			store.isNeedToChangeScale = false;
			float ar =  height / width;
			int minScale = 90;
			int scalex = store.scale;
			int scaley = (int) (scalex * ar);

			if (width + scalex < 0) {scalex = (int)(minScale - width);}
			if (height + scaley < 0) {scaley = (int)(minScale * ar) - (int) height;}

			store.scaledWidth = (int)width + scalex;
			store.scaledHeight = (int)height + scaley;

			updateCamera(store.scaledWidth, store.scaledHeight);
		}

		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.begin();
		view.render(batch);
		batch.end();


		uiCamera.update();
		uiBatch.setProjectionMatrix(uiCamera.combined);
		uiBatch.begin();
		view.renderUI(uiBatch);
		uiBatch.end();
	}

	public static void changeView(View newView) {
		if (view != null) {
			view.destruct();
			view = null;
		}

		System.gc();
		view = newView;

		InputMultiplexer multiplexer = new InputMultiplexer();
		multiplexer.addProcessor(stage);
		multiplexer.addProcessor(view);

		Gdx.input.setInputProcessor(multiplexer);
	}

	@Override
	public void dispose () {
		batch.dispose();
	}

}
