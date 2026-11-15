package ADG.services;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Angular client (../client) at /next/ until it replaces the GWT client. Its files are
 * static resources under public/next; Spring only serves a folder's index.html at "/", so this
 * forwards the folder itself.
 */
@Controller
public class NextClientController {

  @GetMapping({"/next", "/next/"})
  public String page() {
    return "forward:/next/index.html";
  }
}
