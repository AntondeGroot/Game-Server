package ADG.services;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.Random;
import org.springframework.web.server.ResponseStatusException;

/** Room passwords: generated ones are easy to read aloud, chosen ones are normalized. */
final class RoomPasswords {

    // Password generation: CVC + two digits (e.g. "CAD47") — a pronounceable
    // syllable plus two numbers, easy to read aloud or dictate across the room.
    // Excluded vowels   : I (→1/L), O (→0)
    // Excluded consonants: B (→8), G (→6/9), L (→1/I), Q (rare/O-like), S (→5), V (→U), Z (→2)
    // Excluded digits    : 0 (→O), 1 (→I/L)
    private static final char[] VOWELS     = "AEU".toCharArray();
    private static final char[] CONSONANTS = "CDFHJKMNPRTWXY".toCharArray();
    private static final char[] DIGITS     = "23456789".toCharArray();
    private static final int    MAX_LENGTH = 20;
    private static final Random RANDOM     = new Random();

    private RoomPasswords() {}

    /** A CVC + two-digit password (e.g. "CAD47") using unambiguous characters. */
    static String generate() {
        return new String(new char[] {
                pick(CONSONANTS), pick(VOWELS), pick(CONSONANTS), pick(DIGITS), pick(DIGITS)});
    }

    /**
     * The password as stored: without whitespace, upper-cased (entry is case-insensitive) and
     * capped in length.
     */
    static String normalize(String password) {
        String normalized = password == null ? "" : password.replaceAll("\\s+", "").toUpperCase();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Room password cannot be empty");
        }
        return normalized.length() > MAX_LENGTH ? normalized.substring(0, MAX_LENGTH) : normalized;
    }

    private static char pick(char[] chars) {
        return chars[RANDOM.nextInt(chars.length)];
    }
}
