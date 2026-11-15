package ADG.services;

import java.util.HashMap;
import java.util.Map;

/**
 * The room keeps every option value as text; the games expect JSON booleans and numbers. This
 * turns "true"/"false" into booleans and whole numbers into integers, and keeps the rest as text.
 */
final class GameOptionValues {

  private GameOptionValues() {}

  static Map<String, Object> parse(Map<String, String> values) {
    Map<String, Object> parsed = new HashMap<>();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      parsed.put(entry.getKey(), parse(entry.getValue()));
    }
    return parsed;
  }

  private static Object parse(String value) {
    if ("true".equalsIgnoreCase(value)) return true;
    if ("false".equalsIgnoreCase(value)) return false;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return value;
    }
  }
}
