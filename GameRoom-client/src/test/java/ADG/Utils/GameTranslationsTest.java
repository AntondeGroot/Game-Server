package ADG.Utils;

import ADG.Lobby.GameOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTranslationsTest {

    private static GameOption option(String labelKey, String label) {
        GameOption option = new GameOption();
        option.setKey(labelKey);
        option.setLabelKey(labelKey);
        option.setLabel(label);
        return option;
    }

    // Keezen translates its option labels itself (/game-options?locale=) and serves no
    // i18n/{lang}.json; asking for one anyway cost two 404s (the language, then English) on every
    // options fetch. When every option already carries its label there is nothing to look up.
    @Test
    void isNotNeeded_whenEveryOptionCarriesItsOwnLabel() {
        List<GameOption> options = List.of(
                option("gameOption.exactMoveRequired.label", "Exact uitkomen"),
                option("gameOption.mustPlayIfPossible.label", "Verplicht spelen als het kan"));

        assertFalse(GameTranslations.isNeededFor(options));
    }

    // Qwixx sends only a labelKey and keeps the words in its own i18n/{lang}.json. A single option
    // without a label is enough to need that file, or it would show up as its raw key.
    @Test
    void isNeeded_whenAnOptionHasOnlyALabelKey() {
        List<GameOption> options = List.of(
                option("gameOption.bigPoints", "Big Points"),
                option("gameOption.randomOrder", null));

        assertTrue(GameTranslations.isNeededFor(options));
    }

    // A game without options has nothing to translate, so nothing is fetched for it.
    @Test
    void isNotNeeded_whenTheGameHasNoOptions() {
        assertFalse(GameTranslations.isNeededFor(List.of()));
    }
}
