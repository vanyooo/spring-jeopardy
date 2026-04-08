package ru.vanyooo.jeopardy.model;

import java.util.List;

public class Category {

    private final Long id;
    private final String title;
    private final String description;
    private final List<QuestionCard> questions;

    public Category(Long id, String title, String description, List<QuestionCard> questions) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.questions = questions;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<QuestionCard> getQuestions() {
        return questions;
    }
}
