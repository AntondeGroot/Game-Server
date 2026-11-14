package ADG.services;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The games send players back through /rejoin, which restores their playerid cookie. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RejoinControllerTest {

    private static final int FOUR_HUNDRED_DAYS_IN_SECONDS = 400 * 24 * 60 * 60;

    @Autowired MockMvc mockMvc;

    @Test
    void rejoinRestoresThePlayerIdCookieForTheWholeSite() throws Exception {
        mockMvc.perform(get("/rejoin").param("pid", "player-42").secure(true))
                .andExpect(cookie().value("playerid", "player-42"))
                .andExpect(cookie().path("playerid", "/"))
                .andExpect(cookie().maxAge("playerid", FOUR_HUNDRED_DAYS_IN_SECONDS))
                .andExpect(cookie().secure("playerid", true))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void rejoinOverPlainHttpSetsACookieThatIsNotSecure() throws Exception {
        mockMvc.perform(get("/rejoin").param("pid", "player-42"))
                .andExpect(cookie().value("playerid", "player-42"))
                .andExpect(cookie().secure("playerid", false));
    }
}
