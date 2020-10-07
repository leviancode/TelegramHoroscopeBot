package com.leviancode;

public enum Zodiac {
    ARIES("Овен", "♈️ *Овен* (21 марта — 20 апреля)", "https://goroskop.i.ua/aries/%s/"),
    TAURUS ("Телец", "♉️ *Телец* (21 апреля — 20 мая)", "https://goroskop.i.ua/taurus/%s/"),
    GEMINI ("Близнецы","♊ *Близнецы* (21.05 — 21.06)", "https://goroskop.i.ua/gemini/%s/"),
    CANCER ("Рак", "♋️ *Рак* (22.06 — 22.07)", "https://goroskop.i.ua/cancer/%s/"),
    LEO ("Лев", "♌️ *Лев* (23.07 — 23.08)", "https://goroskop.i.ua/leo/%s/"),
    VIRGO ("Дева", "♍ *Дева* (24.08 — 23.09)", "https://goroskop.i.ua/virgo/%s/"),
    LIBRA ("Весы", "♎ *Весы* (24.09 — 23.10)", "https://goroskop.i.ua/libra/%s/"),
    SCORPIO ("Скорпион", "♏ *Скорпион* (24.10 — 22.11)", "https://goroskop.i.ua/scorpio/%s/"),
    SAGITTARIUS ("Стрелец", "♐ *Стрелец* (23.11 — 21.12)", "https://goroskop.i.ua/sagittarius/%s/"),
    CAPRICORN ("Козерог", "♑ *Козерог* (22.12 — 20.01)", "https://goroskop.i.ua/capricorn/%s/"),
    AQUARIUS ("Водолей", "♒ *Водолей* (21.01 — 20.02)", "https://goroskop.i.ua/aquarius/%s/"),
    PISCES ("Рыбы", "♓ *Рыбы* (21.02 — 20.03)", "https://goroskop.i.ua/pisces/%s/");

    public final String label;
    public final String title;
    public final String url;

    Zodiac(String label, String title, String url) {
        this.label = label;
        this.title = title;
        this.url = url;
    }

    public static Zodiac valueOfLabel(String label) {
        for (Zodiac z : values()) {
            if (z.label.equalsIgnoreCase(label)) {
                return z;
            }
        }
        throw new IllegalArgumentException();
    }
}
