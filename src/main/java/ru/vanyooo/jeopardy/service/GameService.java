package ru.vanyooo.jeopardy.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import ru.vanyooo.jeopardy.model.Category;
import ru.vanyooo.jeopardy.model.GameBoard;
import ru.vanyooo.jeopardy.model.Player;
import ru.vanyooo.jeopardy.model.QuestionCard;
import ru.vanyooo.jeopardy.model.QuestionStatus;

@Service
public class GameService {

    private final GameBoard gameBoard;

    public GameService() {
        // Демо-данные создаются один раз при старте приложения.
        this.gameBoard = createDemoBoard();
    }

    public GameBoard getBoard() {
        return gameBoard;
    }

    public Optional<QuestionCard> findQuestionById(Long questionId) {
        return gameBoard.getCategories().stream()
                .flatMap(category -> category.getQuestions().stream())
                .filter(question -> question.getId().equals(questionId))
                .findFirst();
    }

    public void openQuestion(Long questionId) {
        findQuestionById(questionId).ifPresent(question -> question.setStatus(QuestionStatus.OPENED));
    }

    private GameBoard createDemoBoard() {
        List<Category> categories = List.of(
                new Category(
                        1L,
                        "Java",
                        "Базовые вопросы по Java и JVM",
                        List.of(
                                new QuestionCard(101L, 100, "Как называется механизм автоматического освобождения памяти?", "Garbage Collector", "Подсказка: GC", QuestionStatus.AVAILABLE),
                                new QuestionCard(102L, 200, "Какой интерфейс представляет неизменяемую последовательность символов?", "CharSequence", "Его реализует String", QuestionStatus.AVAILABLE),
                                new QuestionCard(103L, 300, "Какой модификатор запрещает наследование класса?", "final", "Это ключевое слово", QuestionStatus.AVAILABLE)
                        )),
                new Category(
                        2L,
                        "Spring",
                        "Вопросы по Spring Boot и MVC",
                        List.of(
                                new QuestionCard(201L, 100, "Какая аннотация отмечает главный класс Spring Boot приложения?", "@SpringBootApplication", "Комбинирует три аннотации", QuestionStatus.AVAILABLE),
                                new QuestionCard(202L, 200, "Какой шаблонизатор часто используется вместе со Spring MVC?", "Thymeleaf", "Начинается на T", QuestionStatus.AVAILABLE),
                                new QuestionCard(203L, 300, "Как называется контейнер, управляющий бинами Spring?", "ApplicationContext", "Это центральный интерфейс контекста", QuestionStatus.AVAILABLE)
                        )),
                new Category(
                        3L,
                        "Web",
                        "HTTP, браузер и клиентская часть",
                        List.of(
                                new QuestionCard(301L, 100, "Какой HTTP-статус означает успешный ответ?", "200 OK", "Двухсотый диапазон", QuestionStatus.AVAILABLE),
                                new QuestionCard(302L, 200, "Как называется язык разметки веб-страниц?", "HTML", "Не CSS и не JS", QuestionStatus.AVAILABLE),
                                new QuestionCard(303L, 300, "Какой тег используется для таблицы в HTML?", "<table>", "Уголки обязательны", QuestionStatus.AVAILABLE)
                        ))
        );

        List<Player> players = List.of(
                new Player(1L, "Игрок 1", 0),
                new Player(2L, "Игрок 2", 0),
                new Player(3L, "Игрок 3", 0)
        );

        return new GameBoard(
                "Своя игра",
                "Каркас Spring Boot приложения без базы данных",
                categories,
                players
        );
    }
}
