package ru.vanyooo.jeopardy.model;

import java.util.List;

public class GameBoard {

    private final String title;
    private final String subtitle;
    private final List<Category> categories;
    private final List<Player> players;

    public GameBoard(String title, String subtitle, List<Category> categories, List<Player> players) {
        this.title = title;
        this.subtitle = subtitle;
        this.categories = categories;
        this.players = players;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public List<Player> getPlayers() {
        return players;
    }
}
