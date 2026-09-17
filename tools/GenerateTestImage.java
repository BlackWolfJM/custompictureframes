import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Synthetic photograph substitute for the manual QA workflow; no personal files required. */
class GenerateTestImage {
    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(1000, 1600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(18, 77, 130), 1000, 1600, new Color(243, 194, 79)));
        g.fillRect(0, 0, 1000, 1600);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 54));
        for (int y = 0; y < 8; y++) for (int x = 0; x < 5; x++) {
            g.setColor(new Color(255, 255, 255, 120)); g.drawRect(x * 200, y * 200, 199, 199);
            g.setColor(Color.WHITE); g.drawString((x + 1) + "," + (y + 1), x * 200 + 45, y * 200 + 110);
        }
        g.setColor(Color.RED); g.fillRect(10, 10, 80, 80);
        g.setColor(Color.GREEN); g.fillRect(110, 10, 80, 80);
        g.setColor(Color.BLUE); g.fillRect(210, 10, 80, 80);
        g.dispose();
        Path path = Path.of("build", "qa", "test-photo.png");
        Files.createDirectories(path.getParent()); ImageIO.write(image, "PNG", path.toFile());
        System.out.println(path.toAbsolutePath());
    }
}
