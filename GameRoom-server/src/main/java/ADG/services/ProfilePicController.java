package ADG.services;

import ADG.config.SpriteSheetsConfig;
import ADG.config.SpriteSheetsConfig.SheetDef;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class ProfilePicController {

    private static final int OUTPUT_SIZE = 100;

    @Autowired
    private SpriteSheetsConfig config;

    // Rendered pictures by index. A game asks for every player's picture at once when it
    // starts, and decoding a whole sprite sheet takes several MB of heap, so each picture is
    // rendered only once and only one sheet is decoded at a time.
    private final Map<Integer, byte[]> renderedPngs = new ConcurrentHashMap<>();
    private final Object renderLock = new Object();

    @GetMapping("/profile-pic/{index}")
    public ResponseEntity<byte[]> getProfilePic(@PathVariable("index") int index) {
        byte[] png = cachedOrRendered(index);
        if (png == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "public, max-age=86400")
                .body(png);
    }

    private byte[] cachedOrRendered(int index) {
        byte[] png = renderedPngs.get(index);
        if (png != null) return png;
        synchronized (renderLock) {
            return renderedPngs.computeIfAbsent(index, this::renderOrNull);
        }
    }

    private byte[] renderOrNull(int index) {
        try {
            return render(index);
        } catch (Exception e) {
            return null;
        }
    }

    /** The picture at {@code index} counted across all sheets, or null when there is none. */
    private byte[] render(int index) throws IOException {
        List<SheetDef> sheets = config.getSheets();

        int offset = 0;
        SheetDef sheet = null;
        for (SheetDef s : sheets) {
            int total = s.getCols() * s.getRows();
            if (index < offset + total) {
                sheet = s;
                break;
            }
            offset += total;
        }
        if (sheet == null || index < 0) {
            return null;
        }

        int local    = index - offset;
        int localCol = local % sheet.getCols();
        int localRow = local / sheet.getCols();

        double cellW = (double) sheet.getImgWidth()  / sheet.getCols();
        double cellH = (double) sheet.getImgHeight() / sheet.getRows();
        int inset    = sheet.getInsetPx();
        int sx = (int) Math.round(cellW * localCol + inset);
        int sy = (int) Math.round(cellH * localRow + inset);
        int sw = (int) Math.round(cellW - 2.0 * inset);
        int sh = (int) Math.round(cellH - 2.0 * inset);

        String resourcePath = "public/" + sheet.getUrl().replaceFirst("^/", "");

        BufferedImage sheetImg;
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            sheetImg = ImageIO.read(is);
        }

        // Copy into a fresh image before scaling. getSubimage() shares the underlying
        // raster, so bilinear interpolation at the crop edge reads the adjacent row's
        // white border pixels from the original sheet and bleeds them into the output.
        BufferedImage cell = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D cellG = cell.createGraphics();
        cellG.drawImage(sheetImg, 0, 0, sw, sh, sx, sy, sx + sw, sy + sh, null);
        cellG.dispose();

        BufferedImage out = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cell, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", baos);
        return baos.toByteArray();
    }
}
