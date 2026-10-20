import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

/**
 * Render the four SVG concepts to PNG and compose a review sheet.
 * JSVG is bundled with Android Studio, so this needs no network dependency.
 */
public final class LogoRender {
    private static final int ICON_SIZE = 1024;
    private static final String[] NAMES = {
            "option-a-solo-point",
            "option-b-s-stone",
            "option-c-seal",
            "option-d-star-ai"
    };
    private static final String[] LABELS = {
            "OPTION A  ·  独子 / Solo Point",
            "OPTION B  ·  双石 / S-Stone",
            "OPTION C  ·  棋印 / Study Seal",
            "OPTION D  ·  星位 / Star AI"
    };
    private static final String[] DESCRIPTIONS = {
            "暖木棋盘、单颗黑棋与青绿色轨迹",
            "黑白双石构成 S 形路径，现代直接",
            "翡翠底、象牙棋子与朱红印章",
            "发光星位与黑棋，突出本地 AI 分析"
    };

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        BufferedImage[] icons = new BufferedImage[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            File svg = new File(dir, NAMES[i] + ".svg");
            SVGDocument document = new SVGLoader().load(svg.toURI().toURL());
            BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            configure(g);
            document.render(new JPanel(), g, new ViewBox(0, 0, ICON_SIZE, ICON_SIZE));
            g.dispose();
            ImageIO.write(image, "png", new File(dir, NAMES[i] + ".png"));
            icons[i] = image;
        }
        composeSheet(dir, icons);
    }

    private static void composeSheet(File dir, BufferedImage[] icons) throws Exception {
        int width = 1440;
        int height = 1500;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        configure(g);

        g.setPaint(new LinearGradientPaint(
                0, 0, width, height,
                new float[]{0f, 1f},
                new Color[]{new Color(10, 15, 22), new Color(7, 12, 19)}
        ));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(35, 176, 166, 22));
        g.fill(new Ellipse2D.Double(-170, -210, 720, 720));
        g.setColor(new Color(45, 125, 255, 18));
        g.fill(new Ellipse2D.Double(1010, 1040, 720, 720));

        g.setColor(new Color(113, 231, 220));
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g.drawString("SOLO GO  ·  BRAND DIRECTION", 54, 52);
        g.setColor(new Color(245, 247, 250));
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 46));
        g.drawString("App Icon Concepts", 50, 110);
        g.setColor(new Color(156, 170, 184));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 18));
        g.drawString("四个方向围绕单机围棋与学习分析设计，当前仅展示，不替换应用图标。", 54, 151);

        int cardW = 650;
        int cardH = 492;
        int gap = 24;
        int startX = 50;
        int startY = 190;
        for (int i = 0; i < icons.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int x = startX + col * (cardW + gap);
            int y = startY + row * (cardH + gap);
            drawCard(g, x, y, cardW, cardH, icons[i], LABELS[i], DESCRIPTIONS[i]);
        }

        g.setColor(new Color(145, 160, 174));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        g.drawString("建议：A 最通用，B 最现代，C 最有棋文化辨识度，D 最突出 AI。选定后再制作 Adaptive Icon 和商店素材。", 54, 1442);
        g.dispose();
        ImageIO.write(sheet, "png", new File(dir, "solo-go-logo-options.png"));
    }

    private static void drawCard(Graphics2D g, int x, int y, int w, int h,
                                 BufferedImage icon, String label, String description) {
        g.setColor(new Color(24, 33, 44));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 28, 28));
        g.setColor(new Color(43, 58, 73));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 28, 28));

        int iconSize = 286;
        int ix = x + 24;
        int iy = y + 103;
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(ix + 3, iy + 9, iconSize, iconSize, 58, 58));
        g.drawImage(icon, ix, iy, iconSize, iconSize, null);

        int tx = x + 342;
        g.setColor(new Color(111, 226, 215));
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.drawString(label, tx, y + 48);

        g.setColor(new Color(248, 250, 252));
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 27));
        g.drawString(label.substring(label.indexOf('/') + 1).trim(), tx, y + 91);

        g.setColor(new Color(166, 181, 194));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 15));
        g.drawString(description, tx, y + 124);

        g.setColor(new Color(245, 249, 251));
        g.setFont(new Font("Segoe UI", Font.BOLD, 31));
        g.drawString("Solo", tx, y + 178);
        g.setColor(new Color(93, 228, 216));
        g.drawString("Go", tx + 68, y + 178);

        g.drawImage(icon, tx, y + 208, 48, 48, null);
        g.drawImage(icon, tx + 66, y + 216, 32, 32, null);
        g.setColor(new Color(135, 151, 165));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g.drawString("48 px", tx + 112, y + 238);
        g.drawString("32 px", tx + 172, y + 238);

        g.setColor(new Color(38, 55, 69));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(tx, y + 288, x + w - 24, y + 288);
        g.setColor(new Color(129, 146, 161));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        g.drawString("单一视觉焦点 · 小尺寸可识别 · 可扩展为自适应图标", tx, y + 322);
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}
