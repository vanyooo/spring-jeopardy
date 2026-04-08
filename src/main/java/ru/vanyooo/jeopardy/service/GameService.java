package ru.vanyooo.jeopardy.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import ru.vanyooo.jeopardy.model.Category;
import ru.vanyooo.jeopardy.model.GameBoard;
import ru.vanyooo.jeopardy.model.GameRound;
import ru.vanyooo.jeopardy.model.Player;
import ru.vanyooo.jeopardy.model.PlayerResult;
import ru.vanyooo.jeopardy.model.QuestionCard;
import ru.vanyooo.jeopardy.model.QuestionStatus;

@Service
public class GameService {

    private final List<Player> players;
    private final Set<Long> removedFinalCategoryIds;
    private GameBoard currentBoard;
    private GameRound currentRound;
    private boolean gameConfigured;

    public GameService() {
        // По умолчанию создаём стартовый набор игроков для тестов и внутренних расчётов.
        this.players = new ArrayList<>(createDefaultPlayers());
        this.removedFinalCategoryIds = new HashSet<>();
        this.currentRound = GameRound.ROUND_ONE;
        this.currentBoard = createFirstRoundBoard();
        this.gameConfigured = false;
    }

    public GameBoard getBoard() {
        return currentBoard;
    }

    public boolean isGameConfigured() {
        return gameConfigured;
    }

    public void configurePlayers(int playerCount, List<String> playerNames) {
        int normalizedCount = playerCount == 4 ? 4 : 3;
        List<Player> configuredPlayers = new ArrayList<>();

        for (int i = 0; i < normalizedCount; i++) {
            String rawName = i < playerNames.size() ? playerNames.get(i) : "";
            String normalizedName = rawName == null ? "" : rawName.trim();

            if (normalizedName.isBlank()) {
                normalizedName = "Игрок " + (i + 1);
            }

            configuredPlayers.add(new Player((long) (i + 1), normalizedName, 0));
        }

        players.clear();
        players.addAll(configuredPlayers);
        gameConfigured = true;
        resetGame();
    }

    public void resetGame() {
        removedFinalCategoryIds.clear();
        currentRound = GameRound.ROUND_ONE;
        currentBoard = createFirstRoundBoard();
    }

    public List<Player> getPlayers() {
        return players;
    }

    public boolean isFirstRoundActive() {
        return currentRound == GameRound.ROUND_ONE;
    }

    public boolean isSecondRoundActive() {
        return currentRound == GameRound.ROUND_TWO;
    }

    public boolean isFinalRoundActive() {
        return currentRound == GameRound.FINAL;
    }

    public Optional<QuestionCard> findQuestionById(Long questionId) {
        return currentBoard.getCategories().stream()
                .flatMap(category -> category.getQuestions().stream())
                .filter(question -> question.getId().equals(questionId))
                .findFirst();
    }

    public void openQuestion(Long questionId) {
        findQuestionById(questionId).ifPresent(question -> question.setStatus(QuestionStatus.OPENED));
    }

    public void answerQuestion(Long questionId) {
        findQuestionById(questionId).ifPresent(question -> question.setStatus(QuestionStatus.ANSWERED));
    }

    public void addScoreToPlayer(Long playerId, int points) {
        if (points <= 0) {
            return;
        }

        findPlayerById(playerId).ifPresent(player -> player.addScore(points));
    }

    public void subtractScoreFromPlayer(Long playerId, int points) {
        if (points <= 0) {
            return;
        }

        findPlayerById(playerId).ifPresent(player -> player.subtractScore(points));
    }

    public void startSecondRound() {
        if (currentRound != GameRound.ROUND_ONE) {
            return;
        }

        currentRound = GameRound.ROUND_TWO;
        currentBoard = createSecondRoundBoard();
    }

    public void startFinalRound() {
        if (currentRound != GameRound.ROUND_TWO) {
            return;
        }

        currentRound = GameRound.FINAL;
        removedFinalCategoryIds.clear();
        currentBoard = createFinalRoundBoard();
    }

    public List<Category> getActiveFinalCategories() {
        if (!isFinalRoundActive()) {
            return List.of();
        }

        return currentBoard.getCategories().stream()
                .filter(category -> !removedFinalCategoryIds.contains(category.getId()))
                .collect(Collectors.toList());
    }

    public List<Category> getRemovedFinalCategories() {
        if (!isFinalRoundActive()) {
            return List.of();
        }

        return currentBoard.getCategories().stream()
                .filter(category -> removedFinalCategoryIds.contains(category.getId()))
                .collect(Collectors.toList());
    }

    public int getRemainingFinalCategoryCount() {
        return getActiveFinalCategories().size();
    }

    public boolean canRemoveFinalCategory(Long categoryId) {
        return isFinalRoundActive()
                && getRemainingFinalCategoryCount() > 1
                && !removedFinalCategoryIds.contains(categoryId)
                && currentBoard.getCategories().stream().anyMatch(category -> category.getId().equals(categoryId));
    }

    public boolean canSelectFinalCategory(Long categoryId) {
        return isFinalRoundActive()
                && getRemainingFinalCategoryCount() == 1
                && !removedFinalCategoryIds.contains(categoryId);
    }

    public void removeFinalCategory(Long categoryId) {
        if (!canRemoveFinalCategory(categoryId)) {
            return;
        }

        removedFinalCategoryIds.add(categoryId);
    }

    public Optional<Category> findFinalCategoryById(Long categoryId) {
        if (!isFinalRoundActive()) {
            return Optional.empty();
        }

        return currentBoard.getCategories().stream()
                .filter(category -> category.getId().equals(categoryId))
                .findFirst();
    }

    public Player getCurrentFinalEliminationPlayer() {
        int playerIndex = removedFinalCategoryIds.size() % players.size();
        return players.get(playerIndex);
    }

    public boolean isFinalRoundCompleted() {
        return isFinalRoundActive()
                && currentBoard.getCategories().stream()
                .flatMap(category -> category.getQuestions().stream())
                .anyMatch(question -> question.getStatus() == QuestionStatus.ANSWERED);
    }

    public List<PlayerResult> getFinalResults() {
        List<Player> sortedPlayers = players.stream()
                .sorted((left, right) -> Integer.compare(right.getScore(), left.getScore()))
                .toList();

        List<PlayerResult> results = new ArrayList<>();
        int previousScore = Integer.MIN_VALUE;
        int currentPosition = 0;

        // Если очки одинаковые, игроки получают одинаковое место.
        for (int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            if (player.getScore() != previousScore) {
                currentPosition = i + 1;
                previousScore = player.getScore();
            }

            results.add(new PlayerResult(currentPosition, player.getName(), player.getScore()));
        }

        return results;
    }

    private Optional<Player> findPlayerById(Long playerId) {
        return players.stream()
                .filter(player -> player.getId().equals(playerId))
                .findFirst();
    }

    private List<Player> createDefaultPlayers() {
        return List.of(
                new Player(1L, "Игрок 1", 0),
                new Player(2L, "Игрок 2", 0),
                new Player(3L, "Игрок 3", 0)
        );
    }

    private GameBoard createFirstRoundBoard() {
        List<Category> categories = List.of(
                category(1L, "Java", "Базовые вопросы по Java и JVM",
                        question(101L, 100, "Как называется механизм автоматического освобождения памяти?", "Garbage Collector", "GC"),
                        question(102L, 200, "Какой интерфейс представляет неизменяемую последовательность символов?", "CharSequence", "Его реализует String"),
                        question(103L, 300, "Какой модификатор запрещает наследование класса?", "final", "Ключевое слово из пяти букв"),
                        question(104L, 400, "С какого метода обычно начинается выполнение Java-программы?", "main", "Он принимает массив строк"),
                        question(105L, 500, "Какая коллекция не допускает дубликаты элементов?", "Set", "Это не List")
                ),
                category(2L, "Spring", "Spring Boot и MVC",
                        question(201L, 100, "Какая аннотация отмечает главный класс Spring Boot приложения?", "@SpringBootApplication", "Собирает несколько аннотаций вместе"),
                        question(202L, 200, "Какой шаблонизатор часто используют со Spring MVC?", "Thymeleaf", "Начинается на T"),
                        question(203L, 300, "Как называется контейнер, управляющий бинами Spring?", "ApplicationContext", "Центральный интерфейс контекста"),
                        question(204L, 400, "Какой HTTP-метод чаще всего используют для создания ресурса в REST?", "POST", "Не GET и не PUT"),
                        question(205L, 500, "Какая аннотация связывает метод контроллера с GET-запросом?", "@GetMapping", "Короткая форма маппинга")
                ),
                category(3L, "Web", "HTTP и браузер",
                        question(301L, 100, "Какой HTTP-статус означает успешный ответ?", "200 OK", "Из диапазона 2xx"),
                        question(302L, 200, "Как называется язык разметки веб-страниц?", "HTML", "Не CSS и не JavaScript"),
                        question(303L, 300, "Какой тег используют для таблицы в HTML?", "<table>", "Уголки обязательны"),
                        question(304L, 400, "Какой заголовок сообщает браузеру тип содержимого ответа?", "Content-Type", "Слово type уже есть в названии"),
                        question(305L, 500, "Как называется технология для оформления страниц?", "CSS", "Это не язык программирования")
                ),
                category(4L, "SQL", "Базы данных и запросы",
                        question(401L, 100, "Какой оператор выбирает данные из таблицы?", "SELECT", "Самый частый SQL-запрос"),
                        question(402L, 200, "Каким словом добавляют новую строку в таблицу?", "INSERT", "Не UPDATE"),
                        question(403L, 300, "Как называется ключ, который уникально определяет строку?", "PRIMARY KEY", "Главный ключ таблицы"),
                        question(404L, 400, "Какая команда изменяет уже существующие данные?", "UPDATE", "Не DELETE"),
                        question(405L, 500, "Какая часть запроса отвечает за фильтрацию строк?", "WHERE", "Обычно идёт после FROM")
                ),
                category(5L, "Git", "Коммиты и ветки",
                        question(501L, 100, "Какая команда показывает текущее состояние файлов?", "git status", "Часто запускают перед коммитом"),
                        question(502L, 200, "Как называется отдельная линия разработки в Git?", "branch", "По-русски это ветка"),
                        question(503L, 300, "Какая команда сохраняет изменения в истории?", "git commit", "Обычно после git add"),
                        question(504L, 400, "Какая команда скачивает изменения и пытается сразу их влить?", "git pull", "Это fetch и merge в одном действии"),
                        question(505L, 500, "Как называется механизм объединения двух веток?", "merge", "Его можно сделать и через pull request")
                ),
                category(6L, "Linux", "Консоль и команды",
                        question(601L, 100, "Какая команда показывает содержимое текущей директории?", "ls", "В Windows аналогом часто служит dir"),
                        question(602L, 200, "Какая команда выводит путь к текущей директории?", "pwd", "Сокращение от print working directory"),
                        question(603L, 300, "Какая команда создаёт новую директорию?", "mkdir", "Сокращение от make directory"),
                        question(604L, 400, "Какая команда ищет текст по файлам быстрее классического grep?", "rg", "ripgrep"),
                        question(605L, 500, "Какая команда перемещает или переименовывает файл?", "mv", "Две буквы")
                )
        );

        return new GameBoard("Своя игра", "Раунд 1", categories, players);
    }

    private GameBoard createSecondRoundBoard() {
        List<Category> categories = List.of(
                category(11L, "Алгоритмы", "Структуры данных и сложность",
                        question(1101L, 200, "Как называется сложность, при которой время растёт линейно от числа элементов?", "O(n)", "Большое O с одной переменной"),
                        question(1102L, 400, "Какая структура данных работает по принципу LIFO?", "Stack", "Последний пришёл, первый вышел"),
                        question(1103L, 600, "Как называется поиск по отсортированному массиву с делением диапазона пополам?", "Бинарный поиск", "Нужно отсортировать заранее"),
                        question(1104L, 800, "Какая структура обычно используется для обхода графа в ширину?", "Queue", "Это не stack"),
                        question(1105L, 1000, "Как называется сортировка, которая на каждом шаге ставит минимум на своё место?", "Сортировка выбором", "По-английски selection sort")
                ),
                category(12L, "Docker", "Контейнеры и образы",
                        question(1201L, 200, "Как называется файл с инструкциями для сборки Docker-образа?", "Dockerfile", "Имя файла без расширения"),
                        question(1202L, 400, "Какая команда запускает новый контейнер из образа?", "docker run", "Слово run обязательно"),
                        question(1203L, 600, "Как называется механизм описания нескольких контейнеров в одном файле?", "Docker Compose", "Часто хранится в yaml"),
                        question(1204L, 800, "Какая команда показывает список запущенных контейнеров?", "docker ps", "Две короткие буквы после docker"),
                        question(1205L, 1000, "Как называется слой, куда контейнер пишет изменения поверх образа?", "Writable layer", "Слой для записи")
                ),
                category(13L, "Kotlin", "Типы и функции",
                        question(1301L, 200, "Как в Kotlin объявляется неизменяемая переменная?", "val", "Не var"),
                        question(1302L, 400, "Какой символ используют для безопасного обращения к nullable-объекту?", "?.", "Точка с дополнительным знаком"),
                        question(1303L, 600, "Как называется специальный класс, для которого генерируются equals, hashCode и toString?", "data class", "Это сочетание из двух слов"),
                        question(1304L, 800, "Каким словом объявляется функция в Kotlin?", "fun", "Три буквы"),
                        question(1305L, 1000, "Какой оператор принудительно утверждает, что значение не null?", "!!", "Два одинаковых знака")
                ),
                category(14L, "Сети", "Протоколы и адреса",
                        question(1401L, 200, "Как называется защищённая версия HTTP?", "HTTPS", "HTTP плюс безопасность"),
                        question(1402L, 400, "Какой протокол обычно преобразует доменное имя в IP-адрес?", "DNS", "Три буквы"),
                        question(1403L, 600, "Как называется адрес устройства на канальном уровне в локальной сети?", "MAC-адрес", "Не IP"),
                        question(1404L, 800, "Какой транспортный протокол гарантирует доставку и порядок пакетов?", "TCP", "Альтернатива UDP"),
                        question(1405L, 1000, "Как называется диапазон адресов, который не маршрутизируется в публичный интернет?", "Приватная сеть", "Private address space")
                ),
                category(15L, "Тестирование", "JUnit и виды тестов",
                        question(1501L, 200, "Как называется тест, который проверяет небольшую часть логики изолированно?", "Unit-тест", "Самый локальный вид тестов"),
                        question(1502L, 400, "Какая аннотация в JUnit 5 отмечает тестовый метод?", "@Test", "Очень короткая"),
                        question(1503L, 600, "Как называется объект-заглушка, который позволяет проверять вызовы методов?", "Mock", "Популярен вместе с Mockito"),
                        question(1504L, 800, "Как называется тест, который проверяет работу нескольких компонентов вместе?", "Интеграционный тест", "Он шире unit-теста"),
                        question(1505L, 1000, "Как называется подход, где тесты пишутся раньше кода?", "TDD", "Сокращение из трёх букв")
                ),
                category(16L, "Архитектура", "Слои и проектирование",
                        question(1601L, 200, "Как называется шаблон, разделяющий приложение на Model, View и Controller?", "MVC", "Три буквы"),
                        question(1602L, 400, "Как называется принцип, по которому верхние модули не зависят от нижних напрямую?", "Инверсия зависимостей", "Один из принципов SOLID"),
                        question(1603L, 600, "Как называется слой, где обычно находится бизнес-логика приложения?", "Service", "Между controller и repository"),
                        question(1604L, 800, "Как называется объект передачи данных между слоями без бизнес-логики?", "DTO", "Сокращение из трёх букв"),
                        question(1605L, 1000, "Как называется подход, где система делится на независимые маленькие сервисы?", "Микросервисная архитектура", "Противопоставляется монолиту")
                )
        );

        return new GameBoard("Своя игра", "Раунд 2", categories, players);
    }

    private GameBoard createFinalRoundBoard() {
        List<Category> categories = List.of(
                finalCategory(21L, 2101L, "Микросервисы", "Контракты, взаимодействие и границы сервисов",
                        "Как называется шаблон, который собирает данные из нескольких сервисов в одной точке входа?",
                        "API Gateway", "Часто стоит перед микросервисами"),
                finalCategory(22L, 2201L, "Безопасность", "Аутентификация, авторизация и защита данных",
                        "Как называется тип токена, который часто используют для передачи утверждений о пользователе между клиентом и сервером?",
                        "JWT", "Сокращение из трёх букв"),
                finalCategory(23L, 2301L, "CI/CD", "Сборка, проверка и доставка изменений",
                        "Как называется практика, при которой каждое изменение автоматически проходит сборку и тесты после коммита?",
                        "Continuous Integration", "Сокращённо CI"),
                finalCategory(24L, 2401L, "Производительность", "Метрики, профилирование и узкие места",
                        "Как называется кеш, который хранит часто запрашиваемые данные в памяти приложения или отдельного сервиса?",
                        "In-memory cache", "Противоположность дисковому хранилищу"),
                finalCategory(25L, 2501L, "Облако", "Инфраструктура, масштабирование и managed-сервисы",
                        "Как называется модель, в которой провайдер управляет инфраструктурой и платформой, а разработчик разворачивает только код приложения?",
                        "PaaS", "Модель между IaaS и SaaS"),
                finalCategory(26L, 2601L, "Паттерны", "Шаблоны проектирования и архитектурные решения",
                        "Как называется паттерн, который позволяет менять семейства алгоритмов без изменения клиента?",
                        "Strategy", "Один объект выбирает нужное поведение"),
                finalCategory(27L, 2701L, "Наблюдаемость", "Логи, трассировка и метрики",
                        "Как называется идентификатор, который помогает связать вызовы одного запроса в распределённой системе?",
                        "Trace ID", "Используется в distributed tracing")
        );

        return new GameBoard("Своя игра", "Финальный раунд", categories, players);
    }

    private Category category(Long id, String title, String description, QuestionCard... questions) {
        return new Category(id, title, description, List.of(questions));
    }

    private Category finalCategory(Long categoryId, Long questionId, String title, String description,
                                   String question, String answer, String hint) {
        return category(categoryId, title, description, question(questionId, 0, question, answer, hint));
    }

    private QuestionCard question(Long id, int cost, String question, String answer, String hint) {
        return new QuestionCard(id, cost, question, answer, hint, QuestionStatus.AVAILABLE);
    }
}
