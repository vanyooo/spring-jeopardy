package ru.vanyooo.jeopardy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.vanyooo.jeopardy.service.GameService;

@SpringBootTest
class JeopardyApplicationTests {

    @Autowired
    private GameService gameService;

    @BeforeEach
    void resetGame() {
        gameService.resetGame();
    }

    @Test
    void contextLoads() {
        assertThat(gameService).isNotNull();
    }

    @Test
    void demoBoardContainsCategoriesAndPlayers() {
        var board = gameService.getBoard();

        assertThat(board.getCategories()).hasSize(6);
        assertThat(board.getPlayers()).hasSize(3);
        assertThat(board.getSubtitle()).isEqualTo("Раунд 1");
    }

    @Test
    void playersCanBeConfiguredDynamically() {
        gameService.configurePlayers(4, java.util.List.of("Аня", "Борис", "Вика", "Глеб"));

        var board = gameService.getBoard();

        assertThat(gameService.isGameConfigured()).isTrue();
        assertThat(board.getPlayers()).hasSize(4);
        assertThat(board.getPlayers().get(3).getName()).isEqualTo("Глеб");
    }

    @Test
    void playerScoreCanBeAddedAndSubtracted() {
        gameService.addScoreToPlayer(1L, 200);
        gameService.subtractScoreFromPlayer(1L, 100);

        assertThat(gameService.getBoard().getPlayers().get(0).getScore()).isEqualTo(100);
    }

    @Test
    void secondRoundCanBeStartedBeforeAllQuestionsAreAnswered() {
        gameService.startSecondRound();

        var board = gameService.getBoard();

        assertThat(gameService.isFirstRoundActive()).isFalse();
        assertThat(gameService.isSecondRoundActive()).isTrue();
        assertThat(board.getSubtitle()).isEqualTo("Раунд 2");
        assertThat(board.getCategories()).hasSize(6);
    }

    @Test
    void finalRoundCanBeStartedFromSecondRound() {
        gameService.startSecondRound();
        gameService.startFinalRound();

        var board = gameService.getBoard();

        assertThat(gameService.isFinalRoundActive()).isTrue();
        assertThat(board.getSubtitle()).isEqualTo("Финальный раунд");
        assertThat(board.getCategories()).hasSize(7);
        assertThat(gameService.getActiveFinalCategories()).hasSize(7);
    }

    @Test
    void finalRoundLeavesOnlyOneThemeAfterSixRemovals() {
        gameService.startSecondRound();
        gameService.startFinalRound();

        gameService.removeFinalCategory(21L);
        gameService.removeFinalCategory(22L);
        gameService.removeFinalCategory(23L);
        gameService.removeFinalCategory(24L);
        gameService.removeFinalCategory(25L);
        gameService.removeFinalCategory(26L);

        assertThat(gameService.getActiveFinalCategories()).hasSize(1);
        assertThat(gameService.canSelectFinalCategory(27L)).isTrue();
        assertThat(gameService.canRemoveFinalCategory(27L)).isFalse();
    }

    @Test
    void finalResultsBecomeAvailableAfterFinalQuestionIsAnswered() {
        gameService.startSecondRound();
        gameService.startFinalRound();

        gameService.removeFinalCategory(21L);
        gameService.removeFinalCategory(22L);
        gameService.removeFinalCategory(23L);
        gameService.removeFinalCategory(24L);
        gameService.removeFinalCategory(25L);
        gameService.removeFinalCategory(26L);

        gameService.answerQuestion(2701L);

        assertThat(gameService.isFinalRoundCompleted()).isTrue();
        assertThat(gameService.getFinalResults()).hasSize(3);
        assertThat(gameService.getFinalResults().get(0).getPosition()).isEqualTo(1);
    }
}
