package ADG.services;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * What the clients keep in cookies: the player's id, the UI language, and a hint that this
 * browser has logged in as admin. The games and the GWT client rely on these names, so they
 * must not change.
 */
public final class ClientCookies {

    static final String PLAYER_ID = "playerid";
    static final String LANGUAGE = "language";
    /** Not a credential: it only makes the lobby show the admin a "Login" shortcut. */
    public static final String ADMIN_HINT = "admin_hint";
    static final String DEFAULT_LANGUAGE = "en";

    /** As long as browsers allow; the GWT client uses the same lifetime. */
    private static final int MAX_AGE_SECONDS = 400 * 24 * 60 * 60;

    private ClientCookies() {}

    /** The caller's player id, or null when the request has no playerid cookie. */
    static String playerId(HttpServletRequest request) {
        return value(request, PLAYER_ID);
    }

    /** The GameRoom UI language in lower case, or English when the cookie is missing or blank. */
    static String language(HttpServletRequest request) {
        String language = value(request, LANGUAGE);
        return language == null || language.isBlank() ? DEFAULT_LANGUAGE : language.toLowerCase();
    }

    /**
     * A cookie for the whole site that lasts 400 days, Secure when the request came over https.
     * Not HttpOnly: the GWT client reads these cookies.
     */
    public static Cookie longLived(String name, String value, HttpServletRequest request) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(MAX_AGE_SECONDS);
        cookie.setSecure(request.isSecure());
        cookie.setHttpOnly(false);
        return cookie;
    }

    /** The cookie's value, or null without a request or such a cookie. */
    private static String value(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
