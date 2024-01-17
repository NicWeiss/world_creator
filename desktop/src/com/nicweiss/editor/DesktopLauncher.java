package com.nicweiss.editor;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

// Please note that on macOS your application needs to be started with the -XstartOnFirstThread JVM argument
public class DesktopLauncher {
	public static void main (String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setForegroundFPS(60);
		config.setWindowedMode(1366, 768);
		config.setMaximized(true);
//		config.setWindowSizeLimits(1366, 768, 1920, 1080);
		config.useVsync(true);
		config.setTitle("World Creator");
		config.setWindowIcon("icon.png");
		new Lwjgl3Application(new Main(), config);
	}
}
