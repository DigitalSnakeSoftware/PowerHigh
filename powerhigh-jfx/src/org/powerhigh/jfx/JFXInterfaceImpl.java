package org.powerhigh.jfx;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.powerhigh.graphics.Interface;
import org.powerhigh.graphics.TextureLoader;
import org.powerhigh.input.Input;
import org.powerhigh.jfx.input.JFXKeyboard;
import org.powerhigh.jfx.input.JFXMouse;
import org.powerhigh.jfx.input.JFXTexturePlugin;
import org.powerhigh.utils.Area;
import org.powerhigh.utils.Color;
import org.powerhigh.utils.Point;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

// POSSIBILITY: use com.sun.media.jfxmediaimpl.AudioClipProvider.create for sound
public class JFXInterfaceImpl extends Interface {

	public static Stage stage;
	protected static boolean closeRequested;
	private static boolean platformStarted;
	private Object answer;
	public static Canvas gameCanvas;
	private boolean inited;
	private GCDrawer drawer;
	private boolean visible;
	private Color background = Color.BLUE;
	static JFXInterfaceImpl instance;
	private JFXMouse mouse;
	private JFXKeyboard keyboard;
	
	private Object platformLock = new Object();
	
	public static class JFXApp extends Application {

		private BorderPane pane;
		private Scene scene;
		
		public JFXApp() {
			
		}
		
		@Override
		public void start(Stage primaryStage) throws Exception {
			pane = new BorderPane();
			scene = new Scene(pane, 620, 480);
			gameCanvas = new Canvas();
			pane.setTop(gameCanvas);
			ExecutorService service = Executors.newSingleThreadExecutor();
			
			// Mouse events
			gameCanvas.setOnMouseMoved((e) -> {
				service.submit(() -> {
					instance.mouse.mouseMoved((int) e.getX(), (int) e.getY(), (int) e.getScreenX(), (int) e.getScreenY());
				});
			});
			gameCanvas.setOnMouseDragged((e) -> {
				service.submit(() -> {
					instance.mouse.mouseDragged(e.getButton().ordinal() - 1, (int) e.getX(), (int) e.getY(), (int) e.getScreenX(), (int) e.getScreenY());
				});
			});
			gameCanvas.setOnMousePressed((e) -> {
				service.submit(() -> {
					instance.mouse.mousePressed(e.getButton().ordinal() - 1, (int) e.getX(), (int) e.getY());
				});
			});
			gameCanvas.setOnMouseReleased((e) -> {
				service.submit(() -> {
					instance.mouse.mouseReleased(e.getButton().ordinal() - 1, (int) e.getX(), (int) e.getY());
				});
			});
			
			// Keyboard events
			scene.setOnKeyPressed((e) -> {
				service.submit(() -> {
					instance.keyboard.keyPressed(e.getCharacter().charAt(0), e.getCode().getCode());
				});
			});
			scene.setOnKeyReleased((e) -> {
				service.submit(() -> {
					instance.keyboard.keyReleased(e.getCharacter().charAt(0), e.getCode().getCode());
				});
			});
			scene.setOnKeyTyped((e) -> {
				service.submit(() -> {
					instance.keyboard.keyTyped(e.getCharacter().charAt(0), e.getCode().getCode());
				});
			});
			
			stage = primaryStage;
			stage.setScene(scene);
			stage.setOnCloseRequest((event) -> {
				closeRequested = true;
			});
		}
		
	}
	
	public JFXInterfaceImpl() {
		instance = this;
		TextureLoader.setPlugin(new JFXTexturePlugin());
		Thread t = new Thread(() -> {
			if (!platformStarted) {
				Application.launch(JFXApp.class);
				platformStarted = true;
			} else {
				JFXApp app = new JFXApp();
				Platform.runLater(() -> {
					try {
						app.init();
						app.start(new Stage());
					} catch (Exception e) {
						System.out.println("JavaFX error:");
						e.printStackTrace();
					}
				});
				Platform.requestNextPulse();
			}
		});
		t.start();
		try {
			t.join(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		drawer = new GCDrawer();
		init();
	}
	
	@Override
	public void setBackground(Color color) {
		background = color;
	}

	@Override
	public Color getBackground() {
		return background;
	}
	
	public void runOnPlatform(Runnable runnable) {
		if (Platform.isFxApplicationThread()) {
			runnable.run();
		} else {
			Platform.runLater(runnable);
		}
	}
	
	public Object resultOnPlatform(Supplier<Object> func) {
		if (Platform.isFxApplicationThread()) {
			return func.get();
		} else {
			synchronized (platformLock) {
				Platform.runLater(() -> {
					answer = func.get();
					synchronized (platformLock) {
						platformLock.notifyAll();
					}
				});
				try {
					platformLock.wait(0);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			return answer;
		}
	}

	@Override
	public void show() {
		if (!inited) {
			inited = true;
			instance.mouse = new JFXMouse(0, 0, instance);
			instance.keyboard = new JFXKeyboard(this);
			Input.setMouseImpl(mouse);
			Input.setKeyboardImpl(instance.keyboard);
		}
		runOnPlatform(() -> {
			if (stage != null)
				stage.show();
		});
		visible = true;
	}

	@Override
	public void hide() {
		runOnPlatform(() -> {
			stage.hide();
		});
	}

	@Override
	public boolean isCloseRequested() {
		return closeRequested;
	}

	@Override
	public boolean isVisible() {
		return (Boolean) resultOnPlatform(() -> {
			return stage.isShowing();
		});
	}

	@Override
	public void setSize(int width, int height) {
		runOnPlatform(() -> {
			stage.setWidth(width);
			stage.setHeight(height);
		});
	}
	
	
	@Override
	public void update() {
		if (visible) {
			super.update();
			Platform.runLater(() -> {
				gameCanvas.setTranslateX(getViewport().getX());
				gameCanvas.setTranslateY(getViewport().getY());
				gameCanvas.setWidth(getViewport().getWidth());
				gameCanvas.setHeight(getViewport().getHeight());
				GraphicsContext gc = gameCanvas.getGraphicsContext2D();
				drawer.setGC(gc);
				JFXInterfaceImpl.getRenderer().render(JFXInterfaceImpl.this, drawer);
			});
		}
	}

	@Override
	public Area getSize() {
		return (Area) resultOnPlatform(() -> {
			return new Area((int) stage.getWidth(), (int) stage.getHeight());
		});
	}

	@Override
	public int getWidth() {
		return getSize().getWidth();
	}

	@Override
	public int getHeight() {
		return getSize().getHeight();
	}
	
	@Override
	public void setResizable(boolean resizable) {
		Platform.runLater(() -> {
			stage.setResizable(resizable);
		});
	}
	
	@Override
	public void setTitle(String title) {
		Platform.runLater(() -> {
			stage.setTitle(title);
		});
	}
	
	@Override
	public void setPosition(int x, int y) {
		Platform.runLater(() -> {
			stage.setX(x);
			stage.setY(y);
		});
	}
	
	@Override
	public Point getPosition() {
		return (Point) resultOnPlatform(() -> {
			return new Point((int) stage.getX(), (int) stage.getY());
		});
	}

	@Override
	public void unregister() {
		hide();
		Platform.exit();
	}

}
