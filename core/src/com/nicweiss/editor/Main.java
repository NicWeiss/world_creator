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

	float width = 0;
	float height = 0;

	
	@Override
	public void create () {
		store = new Store();

		Gdx.app.setLogLevel(LOG_INFO);

		width = Gdx.graphics.getWidth();
		height = Gdx.graphics.getHeight();

		Store.display.put("width", width);
		Store.display.put("height", height);

		camera = new OrthographicCamera();
		viewport = new ExtendViewport(width, height, camera);
		viewport.apply();
		camera.position.set(width/2, height/2, 0);
		changeView(new Logo());
		batch = new SpriteBatch();
	}
	
	@Override
	public void dispose () {
		batch.dispose();
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		viewport.update(width, height);
	}

	@Override
	public void render() {
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
}
