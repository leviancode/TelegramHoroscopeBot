
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class HoroscopeBot extends TelegramLongPollingBot {
    private static final String TOKEN = "1217201362:AAGX3kOYrbPoXPbUhsshwtJROme4IKNUutM";
    private static final String USERNAME = "dailyhoroscopes_bot";

    private static final String WELCOME = "Приветствую! Напиши свой знак или выбери из списка.";
    private static final String OOPS = "Не выдумывай. Дай мне существующий знак.";

    private static final Map<User, Map<Integer, Zodiac>> REQUESTS = new HashMap<>();  // key: user from, value: request history: message id + zodiac
    private static final Logger LOGGER = Logger.getLogger(HoroscopeBot.class.getSimpleName());
    private static final String LOG_PATH = "src/main/resources/horoscope_bot.log";
    private long requestCount = 0;

    public HoroscopeBot() {
        try {
            FileHandler fh = new FileHandler(LOG_PATH, 10000, 1, true);
            fh.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        // We check if the update has a message and the message has text
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            User user = message.getFrom();

            String request = message.getText().toLowerCase().trim();

            SendMessage sendMessage = new SendMessage().setChatId(message.getChatId());

            if (request.equals("/start")){
                sendMessage.setReplyMarkup(getReplyKeyboardMarkUp());
                sendMessage.setText(WELCOME);
            } else if (request.equals("/hide")) {
                ReplyKeyboardRemove keyboardMarkup = new ReplyKeyboardRemove();
                sendMessage.setReplyMarkup(keyboardMarkup);
            } else {
                try {
                    Zodiac zodiac = Zodiac.valueOfLabel(request);

                    sendMessage.setText(getHoroscope(zodiac, Category.GENERAL));
                    sendMessage.setReplyMarkup(getInlineKeyboardMarkup(Category.GENERAL));
                    sendMessage.setParseMode("markdown");

                    addRequest(user, message.getMessageId(), zodiac);
                }catch (IllegalArgumentException e){
                    sendMessage.setText(OOPS);
                }
            }

            try {
                execute(sendMessage); // Call method to send the message
            } catch (TelegramApiException e) {
                LOGGER.log(Level.WARNING,e.getMessage());
            }
            logging(user, request);

        // if update has not message, check maybe it has a callbackQuery (tap on inlineButton)
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackquery = update.getCallbackQuery();
            User user = callbackquery.getFrom();
            String data = callbackquery.getData();
            int messageId = callbackquery.getMessage().getMessageId();

            // check if callbackQuery is not current
            if (!data.contains("_")){
                Category category = Category.valueOfLabel(data);
                String horoscope = getHoroscope(REQUESTS.get(user).get(messageId), category);

                EditMessageText editMessageText = new EditMessageText();
                editMessageText.setChatId(callbackquery.getMessage().getChatId());
                editMessageText.setMessageId(messageId);
                editMessageText.setInlineMessageId(callbackquery.getInlineMessageId());
                editMessageText.setText(horoscope);
                editMessageText.setParseMode("markdown");
                editMessageText.setReplyMarkup(getInlineKeyboardMarkup(category));

                try {
                    execute(editMessageText);
                } catch (TelegramApiException e) {
                    LOGGER.log(Level.WARNING,e.getMessage());
                }
                logging(user, data);
            }
        }
    }

    /**
     * Add user's request to map of requests history to be able to edit messages in the future.
     * @param user who send a message
     * @param messageId unique id of the sent message
     * @param zodiac the user was interested in
     */
    private void addRequest(User user, int messageId, Zodiac zodiac){
        Map<Integer, Zodiac> map = REQUESTS.get(user);
        if (map == null){
            map = new HashMap<>();
        }
        map.put(messageId+1, zodiac);
        REQUESTS.put(user, map);
    }

    /**
     * Log user's messages
     * @param user who send a message
     * @param request is text of user's message
     */
    private void logging (User user, String request){
        requestCount++;
        LOGGER.info(String.format("New request '%s' from user '%s'. Request count: %d. User count: %d",
                request,
                (user.getUserName() != null ? user.getUserName() : user.getFirstName()),
                requestCount,
                REQUESTS.size()));
    }

    /**
     * Get InlineKeyboardMarkUp with 4 category-buttons
     * @param selectedCategory is category chosen by user
     * @return InlineKeyboardMarkup
     */
    private InlineKeyboardMarkup getInlineKeyboardMarkup(Category selectedCategory) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List <List<InlineKeyboardButton>> allInlineButtons = new ArrayList <>();
        List <InlineKeyboardButton> rowInlineButtons = new ArrayList <>();

        for (Category category : Category.values()) {
            String text = category.label;
            if (category == selectedCategory){
                text = "_" + "\n" + text;
            }
            rowInlineButtons.add(new InlineKeyboardButton()
                    .setText(text)
                    .setCallbackData(text));
        }

        allInlineButtons.add(rowInlineButtons);
        keyboardMarkup.setKeyboard(allInlineButtons);

        return keyboardMarkup;
    }

    /**
     * Get ReplyKeyboardMarkUp with 12 zodiac-sign buttons
     * @return ReplyKeyboardMarkup
     */
    private ReplyKeyboardMarkup getReplyKeyboardMarkUp(){
        // Create ReplyKeyboardMarkup object
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        // Create a keyboard rows
        KeyboardRow row = new KeyboardRow();
        Zodiac[] zodiacs = Zodiac.values();

        for (int i = 0; i < zodiacs.length; i++) {
            row.add(zodiacs[i].label);

            // after each three added elements create a new row
            if ((i + 1) % 3 == 0){
                keyboard.add(row);
                row = new KeyboardRow();
            }
        }
        // Set the keyboard to the markup
        replyKeyboardMarkup.setKeyboard(keyboard);

        return replyKeyboardMarkup;

    }

    /**
     *
     * @param zodiac is one of 12 zodiacs
     * @param category is category of horoscope
     * @return text of horoscope
     */
    private String getHoroscope(Zodiac zodiac, Category category){
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append(zodiac.title)
                .append("\n")
                .append("\n");

        String url = String.format(zodiac.url, category.id);

        try {
            Document doc = Jsoup.connect(url).get();
            Elements elements = doc.select("li.multicol_item > p");
            responseBuilder.append(elements.first().text());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,e.getMessage());
        }

        return responseBuilder.toString();
    }


    @Override
    public String getBotUsername() {
        return USERNAME;
    }

    @Override
    public String getBotToken() {
        return TOKEN;
    }

}
