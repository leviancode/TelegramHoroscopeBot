
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
    private static final String[] DOMAIN_NAME = {"Общий", "Любовь", "Здоровье", "Бизнес"};
    private static final String[] DOMAIN_ID = {"c", "l", "h", "b"};

    private static final String OOPS = "Не выдумывай. Дай мне существующий знак.";
    private static final String WELCOME = "Приветствую! Напиши свой знак или выбери из списка.";

    private static final Map<Zodiac, String> ZODIACS_URL = new HashMap<>();      // key: zodiac, value: url
    private static final Map<Zodiac, String> TITLE = new HashMap<>();            // key: zodiac, value: emoji + zodiac
    private static final Map<User, Map<Integer, Zodiac>> REQUESTS = new HashMap<>();  // key: user from, value: request history: message id + zodiac
    private static final String TOKEN = "1217201362:AAGX3kOYrbPoXPbUhsshwtJROme4IKNUutM";
    private static final String USERNAME = "dailyhoroscopes_bot";
    private static final Logger LOGGER = Logger.getLogger(HoroscopeBot.class.getSimpleName());
    private static final String LOG_PATH = "src/main/resources/horoscope_bot.log";
    private long requestCount = 0;

    public HoroscopeBot() {

        ZODIACS_URL.put(Zodiac.ОВЕН, "https://goroskop.i.ua/aries/%s/");
        ZODIACS_URL.put(Zodiac.ТЕЛЕЦ, "https://goroskop.i.ua/taurus/%s/");
        ZODIACS_URL.put(Zodiac.БЛИЗНЕЦЫ, "https://goroskop.i.ua/gemini/%s/");
        ZODIACS_URL.put(Zodiac.РАК, "https://goroskop.i.ua/cancer/%s/");
        ZODIACS_URL.put(Zodiac.ЛЕВ, "https://goroskop.i.ua/leo/%s/");
        ZODIACS_URL.put(Zodiac.ДЕВА, "https://goroskop.i.ua/virgo/%s/");
        ZODIACS_URL.put(Zodiac.ВЕСЫ, "https://goroskop.i.ua/libra/%s/");
        ZODIACS_URL.put(Zodiac.СКОРПИОН, "https://goroskop.i.ua/scorpio/%s/");
        ZODIACS_URL.put(Zodiac.СТРЕЛЕЦ, "https://goroskop.i.ua/sagittarius/%s/");
        ZODIACS_URL.put(Zodiac.КОЗЕРОГ, "https://goroskop.i.ua/capricorn/%s/");
        ZODIACS_URL.put(Zodiac.ВОДОЛЕЙ, "https://goroskop.i.ua/aquarius/%s/");
        ZODIACS_URL.put(Zodiac.РЫБЫ, "https://goroskop.i.ua/pisces/%s/");

        TITLE.put(Zodiac.ВОДОЛЕЙ, "♒ *Водолей* (21.01 — 20.02)");
        TITLE.put(Zodiac.РЫБЫ, "♓ *Рыбы* (21.02 — 20.03)");
        TITLE.put(Zodiac.ОВЕН, "♈️ *Овен* (21 марта — 20 апреля)");
        TITLE.put(Zodiac.ТЕЛЕЦ, "♉️ *Телец* (21 апреля — 20 мая)");
        TITLE.put(Zodiac.БЛИЗНЕЦЫ, "♊ *Близнецы* (21.05 — 21.06)");
        TITLE.put(Zodiac.РАК, "♋️ *Рак* (22.06 — 22.07)");
        TITLE.put(Zodiac.ЛЕВ, "♌️ *Лев* (23.07 — 23.08)");
        TITLE.put(Zodiac.ДЕВА, "♍ *Дева* (24.08 — 23.09)");
        TITLE.put(Zodiac.ВЕСЫ, "♎ *Весы* (24.09 — 23.10)");
        TITLE.put(Zodiac.СКОРПИОН, "♏ *Скорпион* (24.10 — 22.11)");
        TITLE.put(Zodiac.СТРЕЛЕЦ, "♐ *Стрелец* (23.11 — 21.12)");
        TITLE.put(Zodiac.КОЗЕРОГ, "♑ *Козерог* (22.12 — 20.01)");

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
                    Zodiac zodiac = Zodiac.valueOf(request.toUpperCase());

                    sendMessage.setText(getHoroscope(zodiac, DOMAIN_ID[0]));
                    sendMessage.setReplyMarkup(getInlineKeyboardMarkup(DOMAIN_ID[0]));
                    sendMessage.setParseMode("markdown");

                    addRequest(user, message.getMessageId(), zodiac);
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

        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackquery = update.getCallbackQuery();
            User user = callbackquery.getFrom();
            String domainId = callbackquery.getData();
            int messageId = callbackquery.getMessage().getMessageId();
            System.out.println("callbackMessageId: " + messageId);

            // check if callbackQuery is not current
            if (!domainId.contains("$")){
                String horoscope = getHoroscope(REQUESTS.get(user).get(messageId), domainId);

                EditMessageText editMessageText = new EditMessageText();
                editMessageText.setChatId(callbackquery.getMessage().getChatId());
                editMessageText.setMessageId(messageId);
                editMessageText.setInlineMessageId(callbackquery.getInlineMessageId());
                editMessageText.setText(horoscope);
                editMessageText.setParseMode("markdown");
                editMessageText.setReplyMarkup(getInlineKeyboardMarkup(domainId));

                try {
                    execute(editMessageText);
                } catch (TelegramApiException e) {
                    LOGGER.log(Level.WARNING,"TelegramApiException: execute AnswerCallbackQuery", e);
                }
                logging(user, domainId);
            }
        }
    }

    private void addRequest(User user, int messageId, Zodiac zodiac){
        Map<Integer, Zodiac> map = REQUESTS.get(user);
        if (map == null){
            map = new HashMap<>();
        }
        map.put(messageId+1, zodiac);
        REQUESTS.put(user, map);
        System.out.println("messageId: " + messageId);
    }

    private void logging (User user, String request){
        requestCount++;
        LOGGER.info(String.format("New request '%s' from user '%s'. Request count: %d. User count: %d",
                request,
                (user.getUserName() != null ? user.getUserName() : user.getFirstName()),
                requestCount,
                REQUESTS.size()));
    }

    private InlineKeyboardMarkup getInlineKeyboardMarkup(String currentDomainId) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List <List<InlineKeyboardButton>> allInlineButtons = new ArrayList <>();
        List <InlineKeyboardButton> rowInlineButtons = new ArrayList <>();

        for (int i = 0; i < DOMAIN_NAME.length; i++) {
            String text = DOMAIN_NAME[i];
            String data = DOMAIN_ID[i];
            if (data.equals(currentDomainId)){
                text = "_" + "\n" + DOMAIN_NAME[i];
                data = DOMAIN_ID[i] + "$";              // sign current callbackData with $
            }
            rowInlineButtons.add(new InlineKeyboardButton().setText(text).setCallbackData(data));
        }

        allInlineButtons.add(rowInlineButtons);
        keyboardMarkup.setKeyboard(allInlineButtons);

        return keyboardMarkup;
    }

    private ReplyKeyboardMarkup getReplyKeyboardMarkUp(){
        // Create ReplyKeyboardMarkup object
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        // Create a keyboard rows
        KeyboardRow row = new KeyboardRow();
        Zodiac[] zodiacs = Zodiac.values();

        for (int i = 0; i < zodiacs.length; i++) {
            String zodiacStr = zodiacs[i].toString().substring(0,1)
                    + zodiacs[i].toString().substring(1).toLowerCase();
            row.add(zodiacStr);

            if ((i + 1) % 3 == 0){
                keyboard.add(row);
                row = new KeyboardRow();
            }
        }
        // Set the keyboard to the markup
        replyKeyboardMarkup.setKeyboard(keyboard);

        return replyKeyboardMarkup;

    }

    @Override
    public String getBotUsername() {
        return USERNAME;
    }

    @Override
    public String getBotToken() {
        return TOKEN;
    }

    /**
     *
     * @param zodiac is one of 12 zodiacs
     * @param domainId is domain id of horoscope from DOMAIN_ID array
     * @return text of horoscope
     */
    private String getHoroscope(Zodiac zodiac, String domainId){
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append(TITLE.get(zodiac))
                .append("\n")
                .append("\n");

        String url = String.format(ZODIACS_URL.get(zodiac), domainId);

        try {
            Document doc = Jsoup.connect(url).get();
            Elements elements = doc.select("li.multicol_item > p");
            responseBuilder.append(elements.first().text());
        } catch (IOException e) {
                LOGGER.log(Level.WARNING,"IOException: getHoroscope", e);
        }

        return responseBuilder.toString();
    }
}
