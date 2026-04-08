package ru.vanyooo.jeopardy.model;

public class PlayerResult {

    private final int position;
    private final String playerName;
    private final int score;

    public PlayerResult(int position, String playerName, int score) {
        this.position = position;
        this.playerName = playerName;
        this.score = score;
    }

    public int getPosition() {
        return position;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getScore() {
        return score;
    }
}
