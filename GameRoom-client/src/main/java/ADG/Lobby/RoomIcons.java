package ADG.Lobby;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;

/** Inline stroke icons for the room view; they take the text colour of their button. */
final class RoomIcons {

    private static final String OPEN =
        "<svg class='room-icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2.2' "
        + "stroke-linecap='round' stroke-linejoin='round' aria-hidden='true'>";
    private static final String CLOSE = "</svg>";

    static final String BACK = OPEN + "<path d='M15 18l-6-6 6-6'/>" + CLOSE;
    static final String MORE =
        "<svg class='room-icon' viewBox='0 0 24 24' fill='currentColor' aria-hidden='true'>"
        + "<circle cx='5' cy='12' r='2'/><circle cx='12' cy='12' r='2'/><circle cx='19' cy='12' r='2'/>" + CLOSE;
    static final String OPTIONS = OPEN
        + "<path d='M4 7h10M18 7h2M4 17h4M12 17h8'/><circle cx='16' cy='7' r='2'/><circle cx='10' cy='17' r='2'/>" + CLOSE;
    static final String LOCK = OPEN
        + "<rect x='5' y='11' width='14' height='10' rx='2'/><path d='M8 11V8a4 4 0 0 1 8 0v3'/>" + CLOSE;
    static final String CARET = OPEN + "<path d='M6 9l6 6 6-6'/>" + CLOSE;
    static final String REGENERATE = OPEN + "<path d='M20 11a8 8 0 1 0-2.3 5.7'/><path d='M20 5v6h-6'/>" + CLOSE;
    static final String CHAT = OPEN + "<path d='M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z'/>" + CLOSE;
    static final String CROSS = OPEN + "<path d='M6 6l12 12M18 6L6 18'/>" + CLOSE;
    static final String SEND = OPEN + "<path d='M5 12h14M13 6l6 6-6 6'/>" + CLOSE;
    static final String LEAVE = OPEN
        + "<path d='M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4'/><path d='M10 17l-5-5 5-5M5 12h11'/>" + CLOSE;
    static final String STOP = OPEN + "<rect x='6' y='6' width='12' height='12' rx='2'/>" + CLOSE;
    static final String TRASH = OPEN + "<path d='M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3'/>" + CLOSE;

    private RoomIcons() {}

    /** An icon followed by a text label, as button HTML. The label is escaped. */
    static String withLabel(String icon, String label) {
        return icon + "<span>" + SafeHtmlUtils.htmlEscape(label) + "</span>";
    }
}
