package shaif.htmlgenerator.htmlform;

/** Типы HTML-полей. {@link #AUTO} — вывести по Java-типу. */
public enum FieldType {
    AUTO, TEXT, TEXTAREA, NUMBER, EMAIL, PASSWORD, CHECKBOX, RADIO, SELECT,
    DATE, TIME, DATETIME, HIDDEN, TEL, URL;

    /** Значение атрибута {@code type} для {@code <input>}. */
    public String inputType() {
        switch (this) {
            case NUMBER:return "number"; case EMAIL:return "email"; case PASSWORD:return "password";
            case CHECKBOX:return "checkbox"; case DATE:return "date"; case TIME:return "time";
            case DATETIME:return "datetime-local"; case HIDDEN:return "hidden";
            case TEL:return "tel"; case URL:return "url"; default:return "text";
        }
    }
}
