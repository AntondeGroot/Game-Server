package ADG.services;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** The pool of suggested room names, read once at startup. */
@Service
public class RoomNameService {

    private List<String> names;
    private final Random random = new Random();

    @PostConstruct
    public void loadNames() {
        Path piPath = Path.of("/opt/gameroom/room-names.txt");
        if (Files.exists(piPath)) {
            try {
                names = Files.readAllLines(piPath).stream()
                        .map(String::trim)
                        .filter(s -> !s.isBlank() && !s.startsWith("#"))
                        .collect(Collectors.toList());
                return;
            } catch (IOException ignored) {}
        }
        try (InputStream is = getClass().getResourceAsStream("/room-names.txt")) {
            if (is != null) {
                names = new BufferedReader(new InputStreamReader(is)).lines()
                        .map(String::trim)
                        .filter(s -> !s.isBlank() && !s.startsWith("#"))
                        .collect(Collectors.toList());
            }
        } catch (IOException ignored) {}

        if (names == null || names.isEmpty()) {
            names = List.of("Game Room");
        }
    }

    public String randomName() {
        return names.get(random.nextInt(names.size()));
    }

    /** The full pool, so the client can pick a name that no open room uses yet. */
    public List<String> allNames() {
        return names;
    }
}