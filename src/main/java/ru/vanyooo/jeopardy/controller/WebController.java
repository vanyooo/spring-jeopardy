package ru.vanyooo.jeopardy.controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.vanyooo.jeopardy.service.GameService;

@Controller
public class WebController {

    private final GameService gameService;

    public WebController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/")
    public String index(Model model) {
        if (!gameService.isGameConfigured()) {
            model.addAttribute("defaultPlayerCount", 3);
            return "welcome";
        }

        if (gameService.isFinalRoundActive()) {
            return finalRound(model);
        }

        model.addAttribute("board", gameService.getBoard());
        model.addAttribute("firstRoundActive", gameService.isFirstRoundActive());
        model.addAttribute("secondRoundActive", gameService.isSecondRoundActive());
        return "index";
    }

    @PostMapping("/setup")
    public String setupGame(@RequestParam(defaultValue = "3") int playerCount,
                            @RequestParam(name = "playerNames") List<String> playerNames) {
        gameService.configurePlayers(playerCount, playerNames);
        return "redirect:/";
    }

    @GetMapping("/questions/{id}")
    public String question(@PathVariable Long id, Model model) {
        if (!gameService.isGameConfigured()) {
            return "redirect:/";
        }

        var question = gameService.findQuestionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + id));

        gameService.openQuestion(id);
        model.addAttribute("question", question);
        model.addAttribute("board", gameService.getBoard());
        model.addAttribute("finalRoundActive", gameService.isFinalRoundActive());
        return "question";
    }

    @GetMapping("/questions/{id}/answer")
    public String answer(@PathVariable Long id, Model model) {
        if (!gameService.isGameConfigured()) {
            return "redirect:/";
        }

        var question = gameService.findQuestionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + id));

        // После перехода на страницу ответа считаем вопрос сыгранным.
        gameService.answerQuestion(id);
        model.addAttribute("question", question);
        model.addAttribute("board", gameService.getBoard());
        model.addAttribute("finalRoundActive", gameService.isFinalRoundActive());
        return "answer";
    }

    @PostMapping("/questions/{id}/score")
    public String updateScore(@PathVariable Long id,
                              @RequestParam Long playerId,
                              @RequestParam String action) {
        if (!gameService.isGameConfigured()) {
            return "redirect:/";
        }

        var question = gameService.findQuestionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + id));

        int points = question.getCost();
        if ("add".equals(action)) {
            gameService.addScoreToPlayer(playerId, points);
        } else if ("subtract".equals(action)) {
            gameService.subtractScoreFromPlayer(playerId, points);
        }

        return "redirect:/questions/" + id + "/answer";
    }

    @GetMapping("/rounds/2/start")
    public String startSecondRound() {
        if (!gameService.isGameConfigured()) {
            return "redirect:/";
        }

        gameService.startSecondRound();
        return "redirect:/";
    }

    @GetMapping("/rounds/final/start")
    public String startFinalRound() {
        if (!gameService.isGameConfigured()) {
            return "redirect:/";
        }

        gameService.startFinalRound();
        return "redirect:/";
    }

    @GetMapping("/final")
    public String finalRound(Model model) {
        if (!gameService.isGameConfigured()) {
            return "redirect:/";
        }

        if (!gameService.isFinalRoundActive()) {
            return "redirect:/";
        }

        model.addAttribute("board", gameService.getBoard());
        model.addAttribute("activeCategories", gameService.getActiveFinalCategories());
        model.addAttribute("removedCategories", gameService.getRemovedFinalCategories());
        model.addAttribute("remainingCategories", gameService.getRemainingFinalCategoryCount());
        model.addAttribute("currentPlayer", gameService.getCurrentFinalEliminationPlayer());
        return "final-round";
    }

    @GetMapping("/final/categories/{id}/remove")
    public String removeFinalCategory(@PathVariable Long id) {
        if (!gameService.isGameConfigured()) {
            return "redirect:/";
        }

        gameService.removeFinalCategory(id);
        return "redirect:/final";
    }

    @GetMapping("/final/categories/{id}/question")
    public String openFinalCategoryQuestion(@PathVariable Long id) {
        if (!gameService.isGameConfigured()) {
            return "redirect:/";
        }

        var category = gameService.findFinalCategoryById(id)
                .orElseThrow(() -> new IllegalArgumentException("Final category not found: " + id));

        if (!gameService.canSelectFinalCategory(id)) {
            return "redirect:/final";
        }

        Long questionId = category.getQuestions().get(0).getId();
        return "redirect:/questions/" + questionId;
    }

    @GetMapping("/results")
    public String results(Model model) {
        if (!gameService.isGameConfigured()) {
            return "redirect:/";
        }

        if (!gameService.isFinalRoundCompleted()) {
            return "redirect:/";
        }

        model.addAttribute("results", gameService.getFinalResults());
        return "results";
    }
}
