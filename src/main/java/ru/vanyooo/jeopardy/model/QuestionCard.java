package ru.vanyooo.jeopardy.model;

public class QuestionCard {

    private final Long id;
    private final int cost;
    private final String question;
    private final String imageUrl;
    private final String answer;
    private final String hint;
    private QuestionStatus status;

    public QuestionCard(Long id, int cost, String question, String imageUrl, String answer, String hint, QuestionStatus status) {
        this.id = id;
        this.cost = cost;
        this.question = question;
        this.imageUrl = imageUrl;
        this.answer = answer;
        this.hint = hint;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public int getCost() {
        return cost;
    }

    public String getQuestion() {
        return question;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getAnswer() {
        return answer;
    }

    public String getHint() {
        return hint;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public void setStatus(QuestionStatus status) {
        this.status = status;
    }
}
