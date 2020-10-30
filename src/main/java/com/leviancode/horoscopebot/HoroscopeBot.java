package com.leviancode.horoscopebot;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
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
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Telegram Horoscope Bot
 *
 * The bot receives the zodiac sign from the user
 * and returns the horoscope for today from https://goroskop.i.ua/
 *
 * @author Dmitry Levian
 */

public class HoroscopeBot extends TelegramLongPollingBot {
    private final DatabaseManager dbManager;
    private final static String PROPERTIES_PATH = "/botconfig.properties";

    private final String TOKEN;
    private final String USERNAME;

    private final String WELCOME = "Приветствую! Напиши свой знак или выбери из списка.";
    private final String OOPS = "Не выдумывай. Дай мне существующий знак.";

    // Map to store last user's request. Key: userId, value: map: message id + zodiac
    private Map<Integer, Map<Integer, Zodiac>> REQUESTS = new HashMap<>();
    private MongoDatabase database;

    private long requestCount = 0;

    /**
     * HoroscopeBot constructor
     * @param token unique string which BotFather gave to you when you created a bot
     * @param username bot username
     * @param mongoUri uri to mongoDB Atlas connection
     */
    public HoroscopeBot(String token, String username, String mongoUri) {
        this.TOKEN = token;
        this.USERNAME = username;

        dbManager = new DatabaseManager(mongoUri);
        new Thread(dbManager).start();
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
                    sendMessage.setParseMode(ParseMode.MARKDOWN);

                    addRequest(user.getId(), message.getMessageId(), zodiac);

                    dbManager.insertData(user);
                }catch (IllegalArgumentException e){
                    sendMessage.setText(OOPS);
                }
            }

            try {
                execute(sendMessage); // Call method to send the message
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            logging(user, request);

        // if update has not message, check maybe it has a callbackQuery (tap on inlineButton)
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackquery = update.getCallbackQuery();
            String data = callbackquery.getData();

            // check if callbackQuery is not current
            if (!data.contains("_")){
                User user = callbackquery.getFrom();
                int messageId = callbackquery.getMessage().getMessageId();
                Category category = Category.valueOfLabel(data);
                EditMessageText editMessageText = new EditMessageText();
                editMessageText.setChatId(callbackquery.getMessage().getChatId());
                editMessageText.setMessageId(messageId);
                editMessageText.setInlineMessageId(callbackquery.getInlineMessageId());
                editMessageText.setParseMode(ParseMode.MARKDOWN);

                String response = "Неа. Отправь новый запрос.";
                Zodiac zodiac = REQUESTS.get(user.getId()).get(messageId);
                if (zodiac != null){
                    response = getHoroscope(zodiac, category);
                    editMessageText.setReplyMarkup(getInlineKeyboardMarkup(category));
                }

                editMessageText.setText(response);
                try {
                    execute(editMessageText);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                logging(user, data);
            }
        }
    }

    /**
     * Add user's request to map of requests history to be able to edit messages in the future.
     * @param userId unique id of message sender
     * @param messageId unique message id
     * @param zodiac the user was interested in
     */
    private void addRequest(int userId, int messageId, Zodiac zodiac){
        Map<Integer, Zodiac> map = REQUESTS.get(userId);
        if (map == null || map.size() > 5){
            map = new HashMap<>();
        }
        map.put(messageId+1, zodiac);
        REQUESTS.put(userId, map);
    }

    /**
     * Log user's messages
     * @param user who send a message
     * @param request text of user's message
     */
    private void logging (User user, String request){
        requestCount++;
        System.out.println(String.format("New request '%s' from user '%s'. Request count: %d. User count: %d",
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
     * @param zodiac one of 12 zodiacs
     * @param category category of horoscope
     * @return text of horoscope
     */
    private String getHoroscope(Zodiac zodiac, Category category){
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append(zodiac.title)
                .append("\n")
                .append("\n");

        String url = String.format(zodiac.url, category.id);

        try {
            org.jsoup.nodes.Document doc = Jsoup.connect(url).get();
            Elements elements = doc.select("li.multicol_item > p");
            responseBuilder.append(elements.first().text());
        } catch (IOException e) {
            e.printStackTrace();
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

    public static void main(String[] args) {
        Properties props = new Properties();
        InputStream stream = HoroscopeBot.class.getResourceAsStream(PROPERTIES_PATH);
        try {
            props.load(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String token = props.getProperty("token");
        String username = props.getProperty("username");
        String mongoUri = props.getProperty("mongoUri");

        System.out.println("Telegram Horoscope Bot is initialization...");

        ApiContextInitializer.init();

        TelegramBotsApi botsApi = new TelegramBotsApi();
        try {
            botsApi.registerBot(new HoroscopeBot(token, username, mongoUri));
        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
        }
        System.out.println("Telegram Horoscope Bot was successfully initialized.");
        System.out.println("Bot is running...");
    }
}
