package game2048;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 *
 * @author bruno
 */
public class GameManager extends Group {

    private static final int SCALE =1;
    private static final int FINAL_VALUE_TO_WIN = 2048;
    public static final int CELL_SIZE = 128/SCALE;
    private static final int DEFAULT_GRID_SIZE = 4;
    private static final int BORDER_WIDTH = (14 + 2) / (2* SCALE);
    // grid_width=4*cell_size + 2*cell_stroke/2d (14px css)+2*grid_stroke/2d (2 px css)
    private static final int GRID_WIDTH = CELL_SIZE * DEFAULT_GRID_SIZE + BORDER_WIDTH * 2;
    private static final int TOP_HEIGHT = 92;

    private final int gridSize;
    private final List<Location> locations = new ArrayList<>();
    private final Map<Location, Tile> gameGrid;
    private final BooleanProperty gameWonProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty gameOverProperty = new SimpleBooleanProperty(false);
    private volatile boolean movingTiles = false;
    private final IntegerProperty gameScoreProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty gameMovePoints = new SimpleIntegerProperty(0);

    private final VBox vGame = new VBox(50/SCALE);
    private final HBox hTop = new HBox(0);
    private final Label lblScore = new Label("0");
    private final Label lblPoints = new Label();
    private final HBox hBottom = new HBox();
    private final Group grid = new Group();
    private final HBox hOvrLabel = new HBox();
    private final HBox hOvrButton = new HBox();
    private final List<Integer> traversalX;
    private final List<Integer> traversalY;
    final Set<Tile> mergedToBeRemoved = new HashSet<>();
    ParallelTransition parallelTransition = new ParallelTransition();

    public GameManager() {
        this(DEFAULT_GRID_SIZE);
    }

    public GameManager(int gridSize) {
        this.gameGrid = new HashMap<>();
        this.gridSize = gridSize;

     //   traversalX = IntStream.range(0, gridSize).boxed().collect(Collectors.toList());
     //   traversalY = IntStream.range(0, gridSize).boxed().collect(Collectors.toList());
        traversalX = new LinkedList<Integer>();
        traversalY = new LinkedList<Integer>();
        for (int i = 0; i < gridSize; i++) {
            traversalX.add(i);
            traversalY.add(i);
        }
        createScore();
        createGrid();
        initializeGrid();

        this.setManaged(false);
    }

    public void move(Direction direction) {
        synchronized (gameGrid) {
            if (movingTiles) {
                return;
            }
        }

        gameMovePoints.set(0);
MyIntBinaryOperator mibo = new MyIntBinaryOperator() {

            @Override
            public int applyAsInt(int x, int y) {
                Location thisloc = new Location(x, y);
            Tile tile = gameGrid.get(thisloc);
            if (tile == null) {
                return 0;
            }

            Location farthestLocation = findFarthestLocation(thisloc, direction); // farthest available location
            Location nextLocation = farthestLocation.offset(direction); // calculates to a possible merge
            Tile tileToBeMerged = nextLocation.isValidFor(gridSize) ? gameGrid.get(nextLocation) : null;

            if (tileToBeMerged != null && tileToBeMerged.getValue().equals(tile.getValue()) && !tileToBeMerged.isMerged()) {
                tileToBeMerged.merge(tile);

                gameGrid.put(nextLocation, tileToBeMerged);
                if (gameGrid.get(tile.getLocation()) != null) {
                    gameGrid.put(tile.getLocation(), null);
                }
//                gameGrid.replace(tile.getLocation(), null);

                parallelTransition.getChildren().add(animateTile(tile, tileToBeMerged.getLocation()));
                parallelTransition.getChildren().add(hideTileToBeMerged(tile));
                mergedToBeRemoved.add(tile);

                gameMovePoints.set(gameMovePoints.get() + tileToBeMerged.getValue());
                gameScoreProperty.set(gameScoreProperty.get() + tileToBeMerged.getValue());

                if (tileToBeMerged.getValue() == FINAL_VALUE_TO_WIN) {
                    gameWonProperty.set(true);
                }
                return 1;
            } else if (farthestLocation.equals(tile.getLocation()) == false) {
                parallelTransition.getChildren().add(animateTile(tile, farthestLocation));

                gameGrid.put(farthestLocation, tile);
                if (gameGrid.containsKey(tile.getLocation())) {
                    gameGrid.put(tile.getLocation(), null);
                }
//                gameGrid.replace(tile.getLocation(), null);
                tile.setLocation(farthestLocation);

                return 1;
            }

            return 0;
            }
        };
        int tilesMoved = sortAndTraverseGrid(direction, mibo);

        if (gameMovePoints.get() > 0) {
            animateScore(gameMovePoints.getValue().toString()).play();
        }

        // parallelTransition.getChildren().add(animateRandomTileAdded());
        parallelTransition.setOnFinished(e -> {
            synchronized (gameGrid) {
                movingTiles = false;
            }

            grid.getChildren().removeAll(mergedToBeRemoved); // better code
            // below a code to demonstrate use of lambda and stream API
            // mergedToBeRemoved.forEach(grid.getChildren()::remove); 
            if (tilesMoved > 0) {
                animateRandomTileAdded();
            }
            mergedToBeRemoved.clear();

            // reset merged after each movement
            for(Tile tile: gameGrid.values()) {
                if (tile != null) {
                    tile.clearMerge();
                }
            }
         //   gameGrid.values().stream().filter(t -> t != null).forEach(Tile::clearMerge);
        });

        synchronized (gameGrid) {
            movingTiles = true;
        }

        parallelTransition.play();
        parallelTransition.getChildren().clear();
    }

    private Location findFarthestLocation(Location location, Direction direction) {
        Location farthest;

        do {
            farthest = location;
            location = farthest.offset(direction);
        } while (location.isValidFor(gridSize) && gameGrid.get(location) == null);

        return farthest;
    }

    private int funcResult = 0; // lambda expressions don't accept manipulate local variables

    private int sortAndTraverseGrid(Direction d, MyIntBinaryOperator func) {
        Collections.sort(traversalX, d.getX() == 1 ? Collections.reverseOrder() : Integer::compareTo);
        Collections.sort(traversalY, d.getY() == 1 ? Collections.reverseOrder() : Integer::compareTo);

        // lambda expressions can't manipulate non-final local variables
        // non-final field (instance) variables are fine
        // we could use a final SimpleIntegerProperty instead
        // final SimpleIntegerProperty intProperty = new SimpleIntegerProperty();
        funcResult = 0;
        for (int t_x : traversalX){
            for (int t_y : traversalY) {
                                funcResult += func.applyAsInt(t_x, t_y);

            }
        }
//        traversalX.forEach(t_x -> {
//            traversalY.forEach(t_y -> {
//                // intProperty.add(func.applyAsInt(t_x, t_y));
//                funcResult += func.applyAsInt(t_x, t_y);
//            });
//        });

        // return intProperty.get();
        return funcResult;
    }

    private volatile int numberOfMergeableTiles = 0;

    private boolean findMoreMovements() {
        for (Tile tile: gameGrid.values()) {
            if (tile == null) return true;
        }
//        if (gameGrid.values().parallelStream().anyMatch(Objects::isNull)) {
//            // there are empty "null" cells
//            return true;
//        }

        numberOfMergeableTiles = 0;

        for (Direction direction :Direction.values()) {
            MyIntBinaryOperator mibo = new MyIntBinaryOperator(){

                @Override
                public int applyAsInt(int x, int y) {
                      Location thisloc = new Location(x, y);
                Tile tile = gameGrid.get(thisloc);
                if (tile != null) {
                    Location nextLocation = thisloc.offset(direction); // calculates to a possible merge
                    if (nextLocation.isValidFor(gridSize)) {
                        Tile tileToBeMerged = gameGrid.get(nextLocation);
                        if (tileToBeMerged != null && tileToBeMerged.getValue().equals(tile.getValue())) {
                            // Found two tiles that can be merged
                            // this could be a HINT: System.out.println("tiles: "+tileToBeMerged+" "+tile);
                            return 1;
                        }
                    }
                }
                return 0;
                }
            };
            
           int mergeableFound = sortAndTraverseGrid(direction, mibo);

            synchronized (gameGrid) {
                numberOfMergeableTiles += mergeableFound;
            }
        }
        
//        Stream.of(Direction.values()).parallel().forEach(direction -> {
//            int mergeableFound = sortAndTraverseGrid(direction, (x, y) -> {
//                Location thisloc = new Location(x, y);
//                Tile tile = gameGrid.get(thisloc);
//                if (tile != null) {
//                    Location nextLocation = thisloc.offset(direction); // calculates to a possible merge
//                    if (nextLocation.isValidFor(gridSize)) {
//                        Tile tileToBeMerged = gameGrid.get(nextLocation);
//                        if (tileToBeMerged != null && tileToBeMerged.getValue().equals(tile.getValue())) {
//                            // Found two tiles that can be merged
//                            // this could be a HINT: System.out.println("tiles: "+tileToBeMerged+" "+tile);
//                            return 1;
//                        }
//                    }
//                }
//                return 0;
//            });
//
//            synchronized (gameGrid) {
//                numberOfMergeableTiles += mergeableFound;
//            }
//        });
        return numberOfMergeableTiles > 0;
    }

    private void createScore() {
        Label lblTitle = new Label("2048");
        lblTitle.getStyleClass().add("title");
        Label lblSubtitle = new Label("FX");
        lblSubtitle.getStyleClass().add("subtitle");
        HBox hFill = new HBox();
        HBox.setHgrow(hFill, Priority.ALWAYS);
        hFill.setAlignment(Pos.CENTER);
        VBox vScore = new VBox();
        vScore.setAlignment(Pos.CENTER);
        vScore.getStyleClass().add("vbox");
        Label lblTit = new Label("SCORE");
        lblTit.getStyleClass().add("titScore");
        lblScore.getStyleClass().add("score");
        lblScore.textProperty().bind(gameScoreProperty.asString());
        vScore.getChildren().addAll(lblTit, lblScore);

        hTop.getChildren().addAll(lblTitle, lblSubtitle, hFill, vScore);
        hTop.setMinSize(GRID_WIDTH, TOP_HEIGHT);
        hTop.setPrefSize(GRID_WIDTH, TOP_HEIGHT);
        hTop.setMaxSize(GRID_WIDTH, TOP_HEIGHT);

        vGame.getChildren().add(hTop);
        getChildren().add(vGame);

        lblPoints.getStyleClass().add("points");

        getChildren().add(lblPoints);

    }

    private void createGrid() {
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                Location loc = new Location(i, j);
                    locations.add(loc);

                    Rectangle rect2 = new Rectangle(i * CELL_SIZE, j * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                    rect2.setArcHeight(CELL_SIZE / 6d);
                    rect2.setArcWidth(CELL_SIZE / 6d);
                    rect2.getStyleClass().add("grid-cell");
                    grid.getChildren().add(rect2);
            }
        }
//        IntStream.range(0, gridSize)
//                .mapToObj(i -> IntStream.range(0, gridSize).mapToObj(j -> {
//                    Location loc = new Location(i, j);
//                    locations.add(loc);
//
//                    Rectangle rect2 = new Rectangle(i * CELL_SIZE, j * CELL_SIZE, CELL_SIZE, CELL_SIZE);
//                    rect2.setArcHeight(CELL_SIZE / 6d);
//                    rect2.setArcWidth(CELL_SIZE / 6d);
//                    rect2.getStyleClass().add("grid-cell");
//                    return rect2;
//                }))
//                .flatMap(s -> s)
//                .forEach(grid.getChildren()::add);

        grid.getStyleClass().add("grid");
        grid.setManaged(false);
        grid.setLayoutX(BORDER_WIDTH);
        grid.setLayoutY(BORDER_WIDTH);
        hBottom.getStyleClass().add("backGrid");
        hBottom.setMinSize(GRID_WIDTH, GRID_WIDTH);
        hBottom.setPrefSize(GRID_WIDTH, GRID_WIDTH);
        hBottom.setMaxSize(GRID_WIDTH, GRID_WIDTH);

        hBottom.getChildren().add(grid);
        vGame.getChildren().add(hBottom);

        gameOverProperty.addListener((ov, b, b1) -> {
            if (b1) {
                hOvrLabel.getStyleClass().setAll("over");
                hOvrLabel.setMinSize(GRID_WIDTH, GRID_WIDTH);
                Label lblOver = new Label("Game over!");
                lblOver.getStyleClass().add("lblOver");
                hOvrLabel.setAlignment(Pos.CENTER);
                hOvrLabel.getChildren().setAll(lblOver);
                hOvrLabel.setTranslateY(TOP_HEIGHT + vGame.getSpacing());
                this.getChildren().add(hOvrLabel);

                hOvrButton.setMinSize(GRID_WIDTH, GRID_WIDTH / 2);
                Button bTry = new Button("Try again");
                bTry.getStyleClass().setAll("try");
                bTry.setOnAction(e -> resetGame());
                hOvrButton.setAlignment(Pos.CENTER);
                hOvrButton.getChildren().setAll(bTry);
                hOvrButton.setTranslateY(TOP_HEIGHT + vGame.getSpacing() + GRID_WIDTH / 2);
                this.getChildren().add(hOvrButton);
            }
        });

        gameWonProperty.addListener((ov, b, b1) -> {
            if (b1) {
                hOvrLabel.getStyleClass().setAll("won");
                hOvrLabel.setMinSize(GRID_WIDTH, GRID_WIDTH);
                Label lblWin = new Label("You win!");
                lblWin.getStyleClass().add("lblWon");
                hOvrLabel.setAlignment(Pos.CENTER);
                hOvrLabel.getChildren().setAll(lblWin);
                hOvrLabel.setTranslateY(TOP_HEIGHT + vGame.getSpacing());
                this.getChildren().add(hOvrLabel);

                hOvrButton.setMinSize(GRID_WIDTH, GRID_WIDTH / 2);
                hOvrButton.setSpacing(10);
                Button bContinue = new Button("Keep going");
                bContinue.getStyleClass().add("try");
                bContinue.setOnAction(e -> getChildren().removeAll(hOvrLabel, hOvrButton));
                Button bTry = new Button("Try again");
                bTry.getStyleClass().add("try");
                bTry.setOnAction(e -> resetGame());
                hOvrButton.setAlignment(Pos.CENTER);
                hOvrButton.getChildren().setAll(bContinue, bTry);
                hOvrButton.setTranslateY(TOP_HEIGHT + vGame.getSpacing() + GRID_WIDTH / 2);
                this.getChildren().add(hOvrButton);
            }
        });
    }

    private void resetGame() {
        gameGrid.clear();

        List<Node> collect = new LinkedList<>();
        for(Node candidate: grid.getChildren()) {
            if (candidate instanceof Tile) {
                collect.add(candidate);
            }
        }
     //   = grid.getChildren().filtered(c -> c instanceof Tile).stream().collect(Collectors.toList());
        grid.getChildren().removeAll(collect);

        this.getChildren().removeAll(hOvrLabel, hOvrButton);

        gameScoreProperty.set(0);
        gameWonProperty.set(false);
        gameOverProperty.set(false);

        initializeGrid();
    }

    private void redrawTilesInGameGrid() {
        for (Tile t : gameGrid.values()) {
    //    gameGrid.values().stream().filter(Objects::nonNull).forEach(t -> {
            if (t!= null) {
            double layoutX = t.getLocation().getLayoutX(CELL_SIZE) - (t.getMinWidth() / 2);
            double layoutY = t.getLocation().getLayoutY(CELL_SIZE) - (t.getMinHeight() / 2);

            t.setLayoutX(layoutX);
            t.setLayoutY(layoutY);
            grid.getChildren().add(t);
        }
        }
//        });
    }

    private Timeline animateScore(String v1) {
        final Timeline timeline = new Timeline();
        lblPoints.setText("+" + v1);
        lblPoints.setOpacity(1);
        lblPoints.setLayoutX(400);
        lblPoints.setLayoutY(20);
        final KeyValue kvO = new KeyValue(lblPoints.opacityProperty(), 0);
        final KeyValue kvY = new KeyValue(lblPoints.layoutYProperty(), 100);

        Duration animationDuration = Duration.millis(600);
        final KeyFrame kfO = new KeyFrame(animationDuration, kvO);
        final KeyFrame kfY = new KeyFrame(animationDuration, kvY);

        timeline.getKeyFrames().add(kfO);
        timeline.getKeyFrames().add(kfY);

        return timeline;
    }

    interface AddTile {

        void add(int value, int x, int y);
    }

    private void initializeGrid() {
        // initialize all cells in gameGrid map to null
        MyIntBinaryOperator mibo = new MyIntBinaryOperator() {

            @Override
            public int applyAsInt(int x, int y) {
                Location thisloc = new Location(x, y);
                gameGrid.put(thisloc,null);
                return 0;
            }
        };
        sortAndTraverseGrid(Direction.DOWN, mibo);
        
        Tile tile0 = Tile.newRandomTile();
        List<Location> randomLocs = new ArrayList<>(locations);
        Collections.shuffle(randomLocs);
        LinkedList<Location> tempList = new LinkedList();
        tempList.add(randomLocs.get(0));
        tempList.add(randomLocs.get(1));

        Iterator<Location> locs = tempList.iterator();
        tile0.setLocation(locs.next());

        Tile tile1 = null;
        if (new Random().nextFloat() <= 0.8) { // gives 80% chance to add a second tile
            tile1 = Tile.newRandomTile();
            if (tile1.getValue() == 4 && tile0.getValue() == 4) {
                tile1 = Tile.newTile(2);
            }
            tile1.setLocation(locs.next());
        }
if (tile0 != null) gameGrid.put(tile0.getLocation(), tile0);
if (tile1 != null) gameGrid.put(tile1.getLocation(), tile1);
//        Arrays.asList(tile0, tile1).forEach(t -> {
//            if (t == null) {
//                return;
//            }
//            gameGrid.put(t.getLocation(), t);
//        });

        redrawTilesInGameGrid();
    }

    private void animateRandomTileAdded() {
          List<Location> availableLocations = new LinkedList<>();
        for (Location candidate: locations ) {
            if (gameGrid.get(candidate) ==null) {
                availableLocations.add(candidate);
            }
        }
     //   List<Location> availableLocations = locations.stream().filter(l -> gameGrid.get(l) == null).collect(Collectors.toList());
        Collections.shuffle(availableLocations);
        Location randomLocation = availableLocations.get(new Random().nextInt(availableLocations.size()));

        Tile tile = Tile.newRandomTile();
        tile.setLocation(randomLocation);
        double layoutX = tile.getLocation().getLayoutX(CELL_SIZE) - (tile.getMinWidth() / 2);
        double layoutY = tile.getLocation().getLayoutY(CELL_SIZE) - (tile.getMinHeight() / 2);

        tile.setLayoutX(layoutX);
        tile.setLayoutY(layoutY);
        tile.setScaleX(0);
        tile.setScaleY(0);
        gameGrid.put(tile.getLocation(), tile);

        grid.getChildren().add(tile);

        animateNewTile(tile).play();
    }

    private Timeline animateTile(Tile tile, Location newLocation) {
        final Timeline timeline = new Timeline();
        final KeyValue kvX = new KeyValue(tile.layoutXProperty(), newLocation.getLayoutX(CELL_SIZE) - (tile.getMinHeight() / 2));
        final KeyValue kvY = new KeyValue(tile.layoutYProperty(), newLocation.getLayoutY(CELL_SIZE) - (tile.getMinHeight() / 2));

        Duration animationDuration = Duration.millis(200);
        final KeyFrame kfX = new KeyFrame(animationDuration, kvX);
        final KeyFrame kfY = new KeyFrame(animationDuration, kvY);

        timeline.getKeyFrames().add(kfX);
        timeline.getKeyFrames().add(kfY);

        return timeline;
    }

    private Timeline animateNewTile(Tile tile) {
        final Timeline timeline = new Timeline();
        final KeyValue kvX = new KeyValue(tile.scaleXProperty(), 1);
        final KeyValue kvY = new KeyValue(tile.scaleYProperty(), 1);

        Duration animationDuration = Duration.millis(150);
        final KeyFrame kfX = new KeyFrame(animationDuration, kvX);
        final KeyFrame kfY = new KeyFrame(animationDuration, kvY);

        timeline.getKeyFrames().add(kfX);
        timeline.getKeyFrames().add(kfY);
        timeline.setOnFinished(e -> {
            if (!findMoreMovements()) {
                gameOverProperty.set(true);
            }
        });
        return timeline;
    }

    private Timeline hideTileToBeMerged(Tile tile) {
        final Timeline timeline = new Timeline();
        final KeyValue kv = new KeyValue(tile.opacityProperty(), 0);

        Duration animationDuration = Duration.millis(150);
        final KeyFrame kf = new KeyFrame(animationDuration, kv);

        timeline.getKeyFrames().add(kf);

        return timeline;
    }
}
