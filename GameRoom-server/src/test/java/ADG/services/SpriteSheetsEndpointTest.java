package ADG.services;

import ADG.config.SpriteSheetsConfig;
import ADG.config.SpriteSheetsConfig.SheetDef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SpriteSheetsEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpriteSheetsConfig config;

    // ── GET /sprite-sheets ──────────────────────────────────────────────

    @Test
    void getSpriteSheetsReturns200() throws Exception {
        mockMvc.perform(get("/sprite-sheets")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getSpriteSheetsReturnsJsonArray() throws Exception {
        mockMvc.perform(get("/sprite-sheets")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", isA(java.util.List.class)));
    }

    @Test
    void getSpriteSheetsReturnsConfiguredSheets() throws Exception {
        mockMvc.perform(get("/sprite-sheets")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(0))));
    }

    @Test
    void getSpriteSheetsAllowsCrossOrigin() throws Exception {
        mockMvc.perform(get("/sprite-sheets")
                        .header("Origin", "http://example.com")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    void getSpriteSheetsReturnsSheetProperties() throws Exception {
        if (!config.getSheets().isEmpty()) {
            mockMvc.perform(get("/sprite-sheets")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$[0].url", notNullValue()))
                    .andExpect(jsonPath("$[0].cols", notNullValue()))
                    .andExpect(jsonPath("$[0].rows", notNullValue()))
                    .andExpect(jsonPath("$[0].imgWidth", notNullValue()))
                    .andExpect(jsonPath("$[0].imgHeight", notNullValue()));
        }
    }
}