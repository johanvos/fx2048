package game2048;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * @author bruno.borges@oracle.com
 */
public class Game2048 extends Application {

    private GameManager gameManager;
    private Bounds gameBounds;

    @Override
    public void start(Stage primaryStage) {
        gameManager = new GameManager();
        gameBounds = gameManager.getLayoutBounds();

        StackPane root = new StackPane(gameManager);
        root.setPrefSize(gameBounds.getWidth(), gameBounds.getHeight());
        ChangeListener<Number> resize = (ov, v, v1) -> {
            gameManager.setLayoutX((root.getWidth() - gameBounds.getWidth()) / 2d);
            gameManager.setLayoutY((root.getHeight() - gameBounds.getHeight()) / 2d);
        };
        root.widthProperty().addListener(resize);
        root.heightProperty().addListener(resize);

        Scene scene = new Scene(root, 600, 700);
        scene.getStylesheets().add("game2048/game.css");
        addKeyHandler(scene);
        addSwipeHandlers(scene);

        primaryStage.setTitle("2048FX");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(gameBounds.getWidth());
        primaryStage.setMinHeight(gameBounds.getHeight());
        System.out.println("SCREENBOUNDS = "+Screen.getPrimary().getBounds());
        System.out.println("SCREEN = "+Screen.getPrimary());
        System.out.println("minwidt = "+gameBounds.getWidth()+", minheight = "+gameBounds.getHeight());
        primaryStage.show();
    }

    private void addKeyHandler(Scene scene) {
        scene.addEventHandler(KeyEvent.KEY_PRESSED, ke -> {
            KeyCode keyCode = ke.getCode();
            if (keyCode.isArrowKey() == false) {
                return;
            }
            Direction direction = Direction.valueFor(keyCode);
            gameManager.move(direction);
        });
    }

    private void addSwipeHandlers(Scene scene) {
        scene.setOnSwipeUp(e -> gameManager.move(Direction.UP));
        scene.setOnSwipeRight(e -> gameManager.move(Direction.RIGHT));
        scene.setOnSwipeLeft(e -> gameManager.move(Direction.LEFT));
        scene.setOnSwipeDown(e -> gameManager.move(Direction.DOWN));
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

}
