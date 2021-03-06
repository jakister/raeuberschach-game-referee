package Controller;

import GUIView.AnimationThread;
import GameModel.Players.HumanPlayer;
import assets.IO;
import GUIView.ActivePiece;
import GUIView.ComponentCreation.BoardPanel;
import GUIView.GameGUI;
import GameModel.Game;
import GameModel.Move;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

import java.util.Observable;
import java.util.Observer;

public class BoardEventHandler implements EventHandler<MouseEvent>, Observer {
    private Game game;
    private GameGUI gameGUI;

    private BoardPanel boardPanel;

    public BoardEventHandler(Game game, GameGUI gameGUI) {
        this.game = game;
        this.gameGUI = gameGUI;
        boardPanel = gameGUI.getBoardPanel();
        game.addObserver(this);
    }

    @Override
    public synchronized void handle(MouseEvent event) {
        if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
            actionOnMousePressed(event);
        } else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
            actionOnMouseDragged(event);
        } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED) {
            actionOnMouseReleased(event);
        }
    }

    public synchronized void actionOnMousePressed(MouseEvent event) {
        IO.println(event.getX() + " getX");
        IO.println(event.getY() + " getY");
        IO.println(calcIndex(event.getX()));
        IO.println(calcIndex(event.getY()));
        if (mouseIsInBoardRange(event)) {
            if (game.getBoard().getFieldAtIndex(calcIndex(event.getY()), calcIndex(event.getX())).getContentPiece() != null) {
                ActivePiece activePiece = new ActivePiece(event.getX(), event.getY());
                activePiece.setSrcField(game.getBoard().getFieldAtIndex(calcIndex(event.getY()),
                                                                        calcIndex(event.getX())));
                boardPanel.setActivePiece(activePiece);
            } else {
                boardPanel.setActivePiece(null);
            }
        } else {
            boardPanel.setActivePiece(null);
        }
        boardPanel.update();
    }

    public synchronized void actionOnMouseDragged(MouseEvent event) {
        if (mouseIsInBoardRange(event) && boardPanel.getActivePiece() != null) {
            boardPanel.getActivePiece().setX(event.getX());
            boardPanel.getActivePiece().setY(event.getY());
            IO.println(boardPanel.getActivePiece().getX() + " Dragged Piece X value");
            IO.println(boardPanel.getActivePiece().getY() + " Dragged Piece Y value");
        }
        boardPanel.update();
    }

    public void actionOnMouseReleased(MouseEvent event) {
        if (game.getGameThread() != null) {
            synchronized (game.getGameThread()) {
                if (mouseIsInBoardRange(event) && boardPanel.getActivePiece() != null) {
                    Move move = new Move(boardPanel.getActivePiece().getSrcField().getColumnDesignation() + 'a',
                                         boardPanel.getActivePiece().getSrcField().getRowDesignation(),
                                         calcIndex(boardPanel.getActivePiece().getX()) + 'a',
                                         calcIndex(boardPanel.getActivePiece().getY()));
                    IO.print(move.getSourceColumn() + "\n" + move.getSourceRow() + "\n" + move.getDestColumn() + "\n" + move.getDestRow() + "\n");
                    IO.println("Move erstellt");
                    boardPanel.setActivePiece(null);
                    if (game.numberOfHumanPlayers() > 0) {
                        if (!game.getCurrentPlayer().isAI()) {
                            HumanPlayer currentHumanPlayer = (HumanPlayer) game.getCurrentPlayer();
                            currentHumanPlayer.setNextMove(move);
                            if (game.getGameThread() != null)
                                boardPanel.update();
                                game.getGameThread().notify();
                        }
                    }
                    boardPanel.update();
                }
            }
        }
    }

    private boolean mouseIsInBoardRange(MouseEvent event) {
        return (event.getX() <= boardPanel.CELL_SIZE * 9 && event.getX() >= boardPanel.CELL_SIZE && event.getY() <= boardPanel.CELL_SIZE * 9 && event.getY() >= boardPanel.CELL_SIZE);
    }

    private void validateAndExecuteMove(Move move) {
        if (game.getReferee().checkMove(move, game.getCurrentPlayer())) {
            IO.println("checkMove() == true");
            if ((game.getCurrentPlayer().canStrikeEnemy(game.getBoard()) && game.getBoard().getFieldAtIndex(
                    move.getDestRow(), move.getDestColumn()).getContentPiece() == null)) {
                Platform.runLater(() -> gameGUI.setLabelText((game.getCurrentPlayer().isWhite() ? "Weiß ist am Zug!" :
                        "Schwarz ist am Zug!") + " Du kannst eine gegnerische Figur schlagen!"));
            } else {
                game.getReferee().doMove(move);
                game.setCurrentPlayer(game.getCurrentPlayer().isWhite() ? game.getBlack() : game.getWhite());
                Platform.runLater(() -> gameGUI.setLabelText(game.getCurrentPlayer().isWhite() ? "Weiß ist am Zug!" : "Schwarz ist am Zug!"));
            }
        } else {
            IO.println("Log: Move ist nicht gültig");
            Platform.runLater(() -> gameGUI.setLabelText((game.getCurrentPlayer().isWhite() ? "Weiß ist am Zug!" :
                    "Schwarz ist am Zug!") + " Der Zug ist so nicht gültig!"));
        }
        if (game.checkEndingByPieces(game.getBoard().getFields())) {
            game.getBoard().setPiecesInitial();
            Platform.runLater(() -> gameGUI.setLabelText((game.getCurrentPlayer().isWhite() ? "Schwarz hat gewonnen!" :
                    "Weiß hat gewonnen!") + " Weiß ist wieder am Zug!"));
        }
        if (game.getGameThread() != null) {
            if (game.getGameThread().isInterrupted()) {
                game.getBoard().setPiecesInitial();
            }
        }
    }

    /**
     * Berechnet aus einer übergebenen Koordinate einen gültigen Array-Index.
     *
     * @param x Die übergebene Koordinate, ganz egal ob x oder y.
     * @return Der berechnete Index.
     */
    private int calcIndex(Double x) {
        return ((int) ((x / boardPanel.CELL_SIZE) - 1));
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg != null) {
            Game x = (Game) o;
            if (!(game.getCurrentPlayer() instanceof HumanPlayer)) {
                Thread animationThread = new AnimationThread(boardPanel, game, (Move) arg, 10); // muss an die erste
                // Stelle geschrieben werden, da sonst der timeout von gameThread
                // ausläuft und somit die Ausführung der Animation durch aufrufen von notifyObervers() in GameThread niemals erreicht wird
                animationThread.start();
                try {
                    animationThread.join();
                } catch (InterruptedException e) {
                    game.getGameThread().interrupt();
                }
            }
            this.validateAndExecuteMove((Move) arg);
            boardPanel.update();
            try {
                x.getGameThread().sleep(1000);
            } catch (InterruptedException e) {
                x.getGameThread().interrupt();
            }
        }
    }
}
