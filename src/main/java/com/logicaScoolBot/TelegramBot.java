package com.logicaScoolBot;

import com.logicaScoolBot.config.BotConfig;
import com.logicaScoolBot.entity.Payment;
import com.logicaScoolBot.entity.Student;
import com.logicaScoolBot.entity.TelegramUser;
import com.logicaScoolBot.repository.PaymentRepository;
import com.logicaScoolBot.repository.StudentRepository;
import com.logicaScoolBot.repository.UserRepository;
import com.logicaScoolBot.service.ReadEmails;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private static final String KRISTINA = "Kristina";
    private static final String ALEX = "Alex";
    private static final String VERONIKA = "VERONIKA";
    private final UserRepository userRepository;
    private final BotConfig config;
    private final StudentRepository studentRepository;
    private final ReadEmails readEmails;
    private final PaymentRepository paymentRepository;
    private final Set<Long> chatIds = Set.of(1466178855L, 397009920L);
    private final Map<String, Long> mapChatId = Map.of(
            KRISTINA, 397009920L,
            ALEX, 1466178855L,
            VERONIKA, 5090638968L
    );

    static final String HELP_TEXT = "This bot is created to demonstrate Spring capabilities.\n\n" +
            "You can execute commands from the main menu on the left or by typing a command:\n\n" +
            "Type /start to see a welcome message\n\n" +
            "Type /mydata to see data stored about yourself\n\n" +
            "Type /help to see this message again";

    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";

    static final String ERROR_TEXT = "Error occurred: ";

    public TelegramBot(BotConfig config,
                       UserRepository userRepository,
                       StudentRepository studentRepository,
                       ReadEmails readEmails,
                       PaymentRepository paymentRepository) {
        this.config = config;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.readEmails = readEmails;
        this.paymentRepository = paymentRepository;

        List<BotCommand> listofCommands = new ArrayList<>();
        listofCommands.add(new BotCommand("/start", "get a welcome message"));
        listofCommands.add(new BotCommand("/mydata", "get your data stored"));
        listofCommands.add(new BotCommand("/deletedata", "delete my data"));
        listofCommands.add(new BotCommand("/help", "info how to use this bot"));
        listofCommands.add(new BotCommand("/settings", "set your preferences"));
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("Yes");
        yesButton.setCallbackData(YES_BUTTON);
        try {
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.contains("/send") && config.getOwnerId() == chatId) {
                var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                var users = userRepository.findAll();
                for (TelegramUser telegramUser : users) {
                    prepareAndSendMessage(telegramUser.getChatId(), textToSend);
                }
            } else {
                if (messageText.contains("Поступил платёжhhh")) {
                    String name = update.getMessage().getFrom().getFirstName();
                    System.out.println(name + " " + LocalTime.now().format(new DateTimeFormatterBuilder()
                            .appendValue(HOUR_OF_DAY, 2)
                            .appendLiteral(':')
                            .appendValue(MINUTE_OF_HOUR, 2)
                            .optionalStart().toFormatter()));

                    String phone4LastNumber = messageText.substring(messageText.length() - 5).replace("-", "");
                    List<Student> students = studentRepository.findAll().stream()
                            .filter(student -> student.getPhone() != null &&
                                    Arrays.stream(student.getPhone().split(", "))
//                                            .peek(System.out::println)
                                            .anyMatch(x -> x.length() >= 4 && x.substring(x.length() - 4).equals(phone4LastNumber)))
                            .collect(Collectors.toList());

                    if (students.isEmpty()) {
                        prepareAndSendMessage(chatId, "Ничего не найдено по этому номеру телефона");
                        return;
                    } else {
                        students.stream()
                                .map(Student::toString)
                                .forEach(s -> prepareAndSendMessage(chatId, s));
                    }
                } else if (messageText.startsWith("Добавление нового ученика")) {
                    String[] split = messageText.split("\n");
                    Student student = Student.builder()
                            .id(studentRepository.findAll().stream()
                                    .map(Student::getId)
                                    .max(Comparator.naturalOrder())
                                    .orElse(0L) + 1)
                            .fullNameChild(split[1])
                            .fullNameParent(split[2])
                            .city(split[3])
                            .phone(split[4])
                            .course(split[5])
                            .build();
                    studentRepository.save(student);
                    prepareAndSendMessage(chatId, "Ученик добавлен.");
                    System.out.println("Ученик добавлен.");
                }

                // Answer
//                String replyToMessage = update.getMessage().getReplyToMessage().getText();

                switch (messageText) {
                    case "/start":

                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "/start@LogicaScoolBot":

                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "/help":

                        prepareAndSendMessage(chatId, HELP_TEXT);
                        break;

                    case "/register":

                        register(chatId);
                        break;
                    case "Добавить нового ученика":

                        prepareAndSendMessage(chatId, "Для добавления нового ученика," +
                                " необходимо отправить сообщение по шаблону:\n\n" +
                                "Добавление нового ученика\n" +
                                "Имя фамилия ребенка\n" +
                                "Имя фамилия родителя\n" +
                                "город\n" +
                                "телефон без 8 и слитно\n" +
                                "название курса");
                        break;

//                    default:
//
//                        prepareAndSendMessage(chatId, "Sorry, command was not recognized");

                }
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals(YES_BUTTON)) {
                String text = "You pressed YES button";
                executeEditMessageText(text, chatId, messageId);
            } else if (callbackData.equals(NO_BUTTON)) {
                String text = "You pressed NO button";
                executeEditMessageText(text, chatId, messageId);
            }
        }
    }

    private void register(long chatId) {

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Do you really want to register?");

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("Yes");
        yesButton.setCallbackData(YES_BUTTON);

        var noButton = new InlineKeyboardButton();

        noButton.setText("No");
        noButton.setCallbackData(NO_BUTTON);

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rowsInLine.add(rowInLine);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);

        executeMessage(message);
    }

    private void registerUser(Message msg) {

        if (userRepository.findById(msg.getChatId()).isEmpty()) {

            var chatId = msg.getChatId();
            var chat = msg.getChat();

            TelegramUser telegramUser = new TelegramUser();

            telegramUser.setChatId(chatId);
            telegramUser.setFirstName(chat.getFirstName());
            telegramUser.setLastName(chat.getLastName());
            telegramUser.setUserName(chat.getUserName());
            telegramUser.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(telegramUser);
            log.info("user saved: " + telegramUser);
        }
    }

    private void startCommandReceived(long chatId, String name) {


        String answer = EmojiParser.parseToUnicode("Hi, " + name + ", nice to meet you!" + " :blush:");
        log.info("Replied to user " + name);


        sendMessage(chatId, answer);
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();

        row.add("weather");
        row.add("Добавить нового ученика");

        keyboardRows.add(row);

//        row = new KeyboardRow();
//
//        row.add("register");
//        row.add("check my data");
//        row.add("delete my data");
//
//        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    private void executeEditMessageText(String text, long chatId, long messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int) messageId);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void prepareAndSendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }

    @Scheduled(cron = "${cron.scheduler}")
    public void sendAds() {
        System.out.println(LocalDateTime.now());
        Map<LocalDateTime, String> main = readEmails.main();
        main.forEach((k, v) -> {
            String[] split = v.substring(29).split(" ");
            Integer integer = null;
            try {
                integer = Integer.valueOf(split[1]);
            } catch (NumberFormatException e) {
                System.out.println(e.getMessage());
            }
            String phone4LastNumber = v.substring(v.length() - 5).replace("-", "");
            Payment build = Payment.builder()
                    .createDate(k)
                    .phone(phone4LastNumber)
                    .amount(split[0] + (integer != null ? split[1] : ""))
                    .build();
            paymentRepository.save(build);
            List<Student> students = getListStudent(v);

            if (students.isEmpty()) {
                userRepository.findAll().stream()
                        .filter(u -> !u.getChatId().equals(mapChatId.get(KRISTINA)))
                        .peek(u -> System.out.println(u.getFirstName()))
                        .forEach(u -> prepareAndSendMessage(u.getChatId(), v + "\n\nНичего не найдено по этому номеру телефона\n\n"));
            } else {
                userRepository.findAll().stream()
                        .filter(u -> !u.getChatId().equals(mapChatId.get(KRISTINA)))
                        .peek(u -> System.out.println(u.getFirstName()))
                        .forEach(u -> students.stream()
                                .map(Student::toString)
                                .forEach(s -> prepareAndSendMessage(u.getChatId(), v + "\n\n" + s + "\n\n")));
            }
        });
    }

    private List<Student> getListStudent(String messageText) {
            String phone4LastNumber = messageText.substring(messageText.length() - 5).replace("-", "");
            return studentRepository.findAll().stream()
                    .filter(student -> student.getPhone() != null &&
                            Arrays.stream(student.getPhone().split(", "))
                                    .anyMatch(x -> x.length() >= 4 && x.substring(x.length() - 4).equals(phone4LastNumber)))
                    .collect(Collectors.toList());
    }

    @Scheduled(cron = "${cron.job.statisticEveryDay}")
    public void invoke() {
        int dayOfMonth = LocalDateTime.now().getDayOfMonth() - 1;

        List<Payment> all = paymentRepository.findAll();

        Integer sumMonth = all.stream()
                .filter(payment -> payment.getCreateDate().isAfter(LocalDateTime.now().minusDays(dayOfMonth)))
                .map(Payment::getAmount)
                .map(Integer::valueOf)
                .reduce(0, Integer::sum);

        String amountMonth = "Сумма оплат за текущий месяц по СБП " + getFormatNumber(sumMonth);

        Integer sumDay = all.stream()
                .filter(payment -> payment.getCreateDate().isAfter(LocalDateTime.now().minusHours(24)))
                .map(Payment::getAmount)
                .map(Integer::valueOf)
                .reduce(0, Integer::sum);

        String amountDay = "Сумма оплат за текущий день по СБП " + getFormatNumber(sumDay);

        mapChatId.forEach((k, v) -> {
            prepareAndSendMessage(v, amountMonth);
            prepareAndSendMessage(v, amountDay);
        });
    }

    private String getFormatNumber(int number) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        // вот тут устанавливаем разделитель он и так по умолчанию пробел,
        // но в этом примере я решил это сделать явно
        symbols.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat();
        df.setDecimalFormatSymbols(symbols);
        // указываем сколько символов в группе
        df.setGroupingSize(3);
        return df.format(number);
    }
 }
