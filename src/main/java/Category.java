public enum Category {
    GENERAL("Общий","c"),
    LOVE("Любовь", "l"),
    HEALTH("Здоровье","h"),
    BUSINESS("Бизнес","b");

    public final String label;
    public final String id;

    Category(String label, String id) {
        this.label = label;
        this.id = id;
    }

    public static Category valueOfLabel(String label){
        for (Category a : values()) {
            if (a.label.equalsIgnoreCase(label)) {
                return a;
            }
        }
        throw new IllegalArgumentException();
    }
}
