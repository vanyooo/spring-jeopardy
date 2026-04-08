package ru.vanyooo.jeopardy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.vanyooo.jeopardy.service.GameService;

@Controller
public class WebController {

    private final GameService gameService;

    public WebController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("board", gameService.getBoard());
        return "index";
    }

    @GetMapping("/questions/{id}")
    public String question(@PathVariable Long id, Model model) {
        var question = gameService.findQuestionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + id));

        gameService.openQuestion(id);
        model.addAttribute("question", question);
        model.addAttribute("board", gameService.getBoard());
        return "question";
    }

    @GetMapping("/questions/{id}/answer")
    public String answer(@PathVariable Long id, Model model) {
        var question = gameService.findQuestionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + id));

        // После перехода на страницу ответа считаем вопрос отвеченным.
        gameService.answerQuestion(id);
        model.addAttribute("question", question);
        model.addAttribute("board", gameService.getBoard());
        return "answer";
    }
}
