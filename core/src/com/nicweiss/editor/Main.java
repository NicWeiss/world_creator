package com.nicweiss.editor;

import static com.badlogic.gdx.Application.LOG_INFO;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import com.nicweiss.editor.Generic.View;
import com.nicweiss.editor.Generic.Store;
import com.nicweiss.editor.Views.Logo;


public class Main extends ApplicationAdapter {
	SpriteBatch batch;

	public static ExtendViewport viewport;
	public static View view;
	public static Store store;
	public static OrthographicCamera camera;

	public static float width = 0;
	public static float height = 0;

	
	@Override
	public void create () {
		store = new Store();

		Gdx.app.setLogLevel(LOG_INFO);
		setCamera(1920, 1080);

		changeView(new Logo());
		batch = new SpriteBatch();
	}

	public void setCamera(float cameraWidth, float cameraHeight){
		updateSize(cameraWidth, cameraHeight);

		camera = new OrthographicCamera();
		viewport = new ExtendViewport(width, height, camera);
		viewport.apply();
		viewport.update((int)width, (int)height);
		camera.position.set(width/2, height/2, 0);
	}

	public void updateCamera(float cameraWidth, float cameraHeight) {
		updateSize(cameraWidth, cameraHeight);

		camera.viewportWidth = width;
		camera.viewportHeight = height;
		camera.update();
	}

	@Override
	public void resize(int r_width, int r_height) {
		store.scale = store.scaleTotal;
		store.isNeedToChangeScale = true;

		super.resize(r_width, r_height);
		viewport.update(r_width, r_height);
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
			int scalex = store.scale;
			int scaley = (int) (scalex * ar);

			if (width + scalex < 0) {scalex = 50 - (int) width;}
			if (height + scaley < 0) {scaley = (int)(50*ar) - (int) height;}

			updateCamera(width +scalex, height + scaley);
			batch = new SpriteBatch();
		}

		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.begin();
		view.render(batch);
		batch.end();
	}

	public static void changeView(View newView) {
		if (view != null) {
			view.destruct();
			view = null;
		}

		System.gc();
		view = newView;
		Gdx.input.setInputProcessor(view);
	}

	@Override
	public void dispose () {
		batch.dispose();
	}

}
