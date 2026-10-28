package ADG.services;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/** Who is calling: the clients keep the player's id in the {@code playerid} cookie. */
final class PlayerCookie {

    private PlayerCookie() {}

    /** The caller's player id, or null when the request has no playerid cookie. */
    static String idOf(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if ("playerid".equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
