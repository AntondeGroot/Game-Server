package ADG.services;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The Angular client lives at /next/ until it replaces the GWT client. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NextClientControllerTest {

  @Autowired MockMvc mockMvc;

  @Test
  void theNextFolderServesTheAngularPage() throws Exception {
    mockMvc.perform(get("/next/"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/next/index.html"));
  }
}
