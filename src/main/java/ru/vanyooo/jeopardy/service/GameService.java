package ru.vanyooo.jeopardy.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final Map<Long, Integer> finalWagers;
    private GameBoard currentBoard;
    private GameRound currentRound;
    private boolean gameConfigured;

    public GameService() {
        // По умолчанию создаём стартовый набор игроков для тестов и для первого открытия приложения.
        this.players = new ArrayList<>(createDefaultPlayers());
        this.removedFinalCategoryIds = new HashSet<>();
        this.finalWagers = new HashMap<>();
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
        finalWagers.clear();
        resetPlayerScores();
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

    public void saveFinalWagers(List<Integer> wagers) {
        if (!isFinalRoundActive()) {
            return;
        }

        finalWagers.clear();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            int requestedWager = i < wagers.size() && wagers.get(i) != null ? wagers.get(i) : 0;
            int normalizedWager = Math.max(0, requestedWager);
            int maxAllowedWager = Math.max(0, player.getScore());

            // Ставка не может быть больше текущих очков игрока.
            finalWagers.put(player.getId(), Math.min(normalizedWager, maxAllowedWager));
        }
    }

    public int getPlayerFinalWager(Long playerId) {
        return finalWagers.getOrDefault(playerId, 0);
    }

    public boolean areFinalWagersSaved() {
        return isFinalRoundActive() && finalWagers.size() == players.size();
    }

    public int getQuestionPointsForPlayer(Long questionId, Long playerId) {
        Optional<QuestionCard> question = findQuestionById(questionId);
        if (question.isEmpty()) {
            return 0;
        }

        if (isFinalRoundActive()) {
            return getPlayerFinalWager(playerId);
        }

        return question.get().getCost();
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
        finalWagers.clear();
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

    private void resetPlayerScores() {
        List<Player> resetPlayers = players.stream()
                .map(player -> new Player(player.getId(), player.getName(), 0))
                .toList();

        players.clear();
        players.addAll(resetPlayers);
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
                category(1L, "Логика", "Парадоксы, последовательности и наблюдательность",
                        question(101L, 100, "Какое число должно идти следующим в ряду 1, 1, 2, 3, 5, 8, ...?", "13", "Каждое следующее равно сумме двух предыдущих"),
                        question(102L, 200, "Что тяжелее: килограмм свинца или килограмм перьев?", "Одинаково", "Ключевое слово уже есть в условии"),
                        question(103L, 300, "Если у вас есть только одна спичка и тёмная комната, где стоят свеча, лампа и камин, что вы зажжёте первым?", "Спичку", "Без неё остальное не зажечь"),
                        question(104L, 400, "Какое слово всегда пишется неправильно, если отвечать буквально на вопрос?", "«Неправильно»", "Ответ в самой формулировке"),
                        question(105L, 500, "Продолжите закономерность: ПН, ВТ, СР, ЧТ, ... Что дальше?", "ПТ", "Это первые буквы дней недели")
                ),
                category(2L, "История идей", "Люди, открытия и поворотные моменты",
                        question(201L, 100, "Как звали учёного, которого традиционно связывают с законом всемирного тяготения?", "Исаак Ньютон", "Легенда говорит о яблоке"),
                        question(202L, 200, "Как называется эпоха европейской истории, последовавшая за Средневековьем и связанная с обращением к античности?", "Возрождение", "По-итальянски Rinascimento"),
                        question(203L, 300, "Как звали первого человека, совершившего полёт в космос?", "Юрий Гагарин", "Полёт состоялся 12 апреля 1961 года"),
                        question(204L, 400, "Как называется документ 1215 года, ограничивший власть английского короля Иоанна?", "Великая хартия вольностей", "По-латыни Magna Carta"),
                        question(205L, 500, "Какой афинский философ не оставил собственных текстов, но известен по диалогам своего ученика Платона?", "Сократ", "Его имя связано с методом вопросов")
                ),
                category(3L, "Слова и смыслы", "Язык, этимология и точность",
                        question(301L, 100, "Как называется устойчивое выражение, значение которого нельзя понять по отдельным словам?", "Фразеологизм", "Например, «бить баклуши»"),
                        question(302L, 200, "Как называется слово, противоположное по значению другому слову?", "Антоним", "Пример: «холодный» и «горячий»"),
                        question(303L, 300, "Как называется наука о происхождении слов?", "Этимология", "Она изучает историю слов"),
                        question(304L, 400, "Какой термин обозначает слово, одинаково звучащее и пишущееся, но имеющее разные значения?", "Омоним", "Например, «лук» как оружие и овощ"),
                        question(305L, 500, "Как называется логическая ошибка, когда смысл тезиса незаметно подменяют по ходу рассуждения?", "Подмена тезиса", "Ошибка аргументации, а не грамматики")
                ),
                category(4L, "Наука", "Физика, биология и устройство мира",
                        question(401L, 100, "Как называется ближайшая к Земле звезда?", "Солнце", "Она видна днём"),
                        question(402L, 200, "Какой химический элемент имеет обозначение O?", "Кислород", "Он нужен для дыхания"),
                        question(403L, 300, "Как называется процесс, при котором растения используют свет для создания органических веществ?", "Фотосинтез", "Он происходит в листьях"),
                        question(404L, 400, "Какой газ составляет основную часть атмосферы Земли?", "Азот", "Это не кислород"),
                        question(405L, 500, "Как называется мысленный принцип, согласно которому не следует умножать сущности без необходимости?", "Бритва Оккама", "Философский принцип простоты")
                ),
                category(5L, "Литература", "Книги, авторы и персонажи",
                        question(501L, 100, "Кто написал роман «Преступление и наказание»?", "Фёдор Достоевский", "Русский классик XIX века"),
                        question(502L, 200, "Как зовут героя, который сражался с ветряными мельницами?", "Дон Кихот", "Создан Сервантесом"),
                        question(503L, 300, "Как называется поэма Данте, состоящая из частей «Ад», «Чистилище» и «Рай»?", "«Божественная комедия»", "Средневековый шедевр"),
                        question(504L, 400, "Кто является автором пьесы «Гамлет»?", "Уильям Шекспир", "Английский драматург"),
                        question(505L, 500, "Как зовут рассказчика в романе Набокова «Лолита»?", "Гумберт Гумберт", "Его имя повторяется дважды")
                ),
                category(6L, "Культура и искусство", "Музыка, живопись и символы эпох",
                        question(601L, 100, "Какой композитор написал «Лунную сонату»?", "Людвиг ван Бетховен", "Немецкий классик"),
                        question(602L, 200, "Как называется музей в Париже, где хранится «Мона Лиза»?", "Лувр", "Бывший королевский дворец"),
                        question(603L, 300, "Как называется стиль в искусстве, для которого характерны размытые контуры и попытка передать впечатление момента?", "Импрессионизм", "Название связано с впечатлением"),
                        question(604L, 400, "Кто написал картину «Чёрный квадрат»?", "/images/black-square.svg", "Казимир Малевич", "Русский авангардист"),
                        question(605L, 500, "Как называется древнегреческий театр под открытым небом с полукруглым расположением мест?", "Амфитеатр", "Часто ассоциируется и с римской архитектурой")
                )
        );

        return new GameBoard("Своя игра", "Раунд 1", categories, players);
    }

    private GameBoard createSecondRoundBoard() {
        List<Category> categories = List.of(
                category(11L, "Философия и мышление", "Понятия, школы и интеллектуальные повороты",
                        question(1101L, 200, "Как называется философское направление, ставящее в центр ценность личного выбора и ответственности человека?", "Экзистенциализм", "С ним часто связывают Сартра и Камю"),
                        question(1102L, 400, "Какой древнегреческий философ написал «Метафизику» и был учеником Платона?", "Аристотель", "Он также был учителем Александра Македонского"),
                        question(1103L, 600, "Как называется рассуждение от частных наблюдений к общему выводу?", "Индукция", "Противоположность дедукции"),
                        question(1104L, 800, "Какой термин обозначает невозможность опровергнуть гипотезу опытным путём, если она сформулирована слишком расплывчато?", "Нефальсифицируемость", "Термин связан с Карлом Поппером"),
                        question(1105L, 1000, "Какой философ сформулировал категорический императив?", "Иммануил Кант", "Немецкий мыслитель XVIII века")
                ),
                category(12L, "Мировая история", "События, имена, документы",
                        question(1201L, 200, "Как называлась эпоха массового культурного и политического подъёма в Европе XIV-XVI веков?", "Возрождение", "Она обратилась к античности"),
                        question(1202L, 400, "Какой мирный договор завершил Первую мировую войну для Германии?", "Версальский договор", "Подписан в 1919 году"),
                        question(1203L, 600, "Как называлась форма правления в Риме до установления империи?", "Республика", "Ею управляли консулы и сенат"),
                        question(1204L, 800, "Какой город был столицей Византийской империи?", "Константинополь", "Позднее стал Стамбулом"),
                        question(1205L, 1000, "Как называется поход Наполеона 1812 года, завершившийся катастрофическим отступлением?", "Поход в Россию", "Одной из ключевых точек была Москва")
                ),
                category(13L, "Естественные науки", "Глубже школьного уровня",
                        question(1301L, 200, "Как называется наименьшая частица химического элемента, сохраняющая его свойства?", "Атом", "У него есть ядро и электроны"),
                        question(1302L, 400, "Как называется биологическая молекула, которая хранит наследственную информацию?", "ДНК", "Её структуру описывают как двойную спираль"),
                        question(1303L, 600, "Как называется единица измерения силы в системе СИ?", "Ньютон", "Названа в честь учёного"),
                        question(1304L, 800, "Какой раздел математики изучает пределы, производные и интегралы?", "Математический анализ", "На первом курсе это отдельный предмет"),
                        question(1305L, 1000, "Как называется принцип в квантовой механике, утверждающий невозможность одновременно точно определить пару некоторых величин, например координату и импульс?", "Принцип неопределённости Гейзенберга", "Связан с именем немецкого физика")
                ),
                category(14L, "Политика и право", "Институты и понятия",
                        question(1401L, 200, "Как называется основной закон государства?", "Конституция", "На ней строится правовая система"),
                        question(1402L, 400, "Как называется форма правления, при которой глава государства избирается, а не наследует власть?", "Республика", "В противоположность монархии"),
                        question(1403L, 600, "Как называется разделение власти на законодательную, исполнительную и судебную?", "Разделение властей", "Один из базовых политико-правовых принципов"),
                        question(1404L, 800, "Как называется международный суд, рассматривающий споры между государствами в Гааге?", "Международный суд ООН", "Главный судебный орган Организации Объединённых Наций"),
                        question(1405L, 1000, "Как называется принцип, по которому никто не может быть наказан за деяние, которое не было запрещено законом на момент его совершения?", "Нет наказания без закона", "Латинская формула nulla poena sine lege")
                ),
                category(15L, "Литература XX века", "Тексты, смыслы и авторы",
                        question(1501L, 200, "Кто написал роман «1984»?", "Джордж Оруэлл", "Также написал «Скотный двор»"),
                        question(1502L, 400, "Как называется роман Булгакова о Понтии Пилате, дьяволе в Москве и писателе?", "«Мастер и Маргарита»", "Один из самых известных русских романов XX века"),
                        question(1503L, 600, "Кто автор романа «Процесс»?", "Франц Кафка", "Писатель, чьё имя стало прилагательным"),
                        question(1504L, 800, "Как называется модернистский роман Джеймса Джойса, действие которого разворачивается в течение одного дня в Дублине?", "«Улисс»", "Название отсылает к античному герою"),
                        question(1505L, 1000, "Какой аргентинский писатель создал рассказы «Сад расходящихся тропок» и «Вавилонская библиотека»?", "Хорхе Луис Борхес", "Мастер интеллектуальной прозы")
                ),
                category(16L, "Сложная эрудиция", "Неочевидные, но проверяемые факты",
                        question(1601L, 200, "Как называется число, равное отношению длины окружности к её диаметру?", "Пи", "Часто записывается греческой буквой"),
                        question(1602L, 400, "Какой язык является самым распространённым родным языком в мире по числу носителей?", "Китайский", "Точнее, путунхуа/мандаринский"),
                        question(1603L, 600, "Как называется устройство средневековых часов, которое дозирует ход механизма?", "Спусковой механизм", "Без него часы не отбивали бы ритм"),
                        question(1604L, 800, "Какой термин в риторике обозначает намеренное преуменьшение ради выразительности?", "Литота", "Например, «мальчик с пальчик»"),
                        question(1605L, 1000, "Как называется мысленный эксперимент Шрёдингера с животным в ящике?", "Кот Шрёдингера", "Он связан с интерпретациями квантовой механики")
                )
        );

        return new GameBoard("Своя игра", "Раунд 2", categories, players);
    }

    private GameBoard createFinalRoundBoard() {
        List<Category> categories = List.of(
                finalCategory(21L, 2101L, "Античность", "Древний мир и его наследие",
                        "Как назывался греческий полис, известный своей военной системой воспитания и крайне строгим общественным устройством?",
                        "Спарта", "Не Афины"),
                finalCategory(22L, 2201L, "Научные идеи", "Понятия, изменившие картину мира",
                        "Как называется теория, объясняющая происхождение видов через естественный отбор?",
                        "Теория эволюции", "Связана с Чарльзом Дарвином"),
                finalCategory(23L, 2301L, "Мифы и символы", "Образы, которые знают во всём мире",
                        "Как звали титана, похитившего огонь для людей в древнегреческой мифологии?",
                        "Прометей", "За это он был жестоко наказан"),
                finalCategory(24L, 2401L, "Классическая литература", "Книги, которые пережили века",
                        "Какой герой Достоевского убивает старуху-процентщицу, пытаясь доказать себе право на «кровь по совести»?",
                        "Раскольников", "Главный герой «Преступления и наказания»"),
                finalCategory(25L, 2501L, "Государство и общество", "Политическая теория и практика",
                        "Как называется форма правления, при которой верховная власть формально принадлежит монарху, но ограничена конституцией и парламентом?",
                        "Конституционная монархия", "Такова современная Великобритания"),
                finalCategory(26L, 2601L, "Математические идеи", "Не вычисления, а смыслы",
                        "Как называется доказательство от противного, в котором ложность отрицания тезиса приводит к противоречию?",
                        "Редукция к абсурду", "Латинское название reductio ad absurdum"),
                finalCategory(27L, 2701L, "Искусство", "Стили, эпохи и авторы",
                        "Как называется художественное направление, к которому относят Пикассо периода «Авиньонских девиц»?",
                        "Кубизм", "Название связано с геометризацией формы"),
                finalCategory(28L, 2801L, "Мировая история", "Поворотные события",
                        "Как называется период политического противостояния СССР и США после Второй мировой войны без прямого масштабного столкновения между ними?",
                        "Холодная война", "Противостояние двух блоков"),
                finalCategory(29L, 2901L, "Философские тексты", "Фразы, ставшие эпохой",
                        "Какой философ произнёс формулу «Cogito, ergo sum»?",
                        "Рене Декарт", "Фраза переводится как «Мыслю, следовательно, существую»")
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
        return question(id, cost, question, null, answer, hint);
    }

    private QuestionCard question(Long id, int cost, String question, String imageUrl, String answer, String hint) {
        return new QuestionCard(id, cost, question, imageUrl, answer, hint, QuestionStatus.AVAILABLE);
    }
}
