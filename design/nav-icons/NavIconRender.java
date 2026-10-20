import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
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

public final class NavIconRender {
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        BufferedImage review = renderSvg(new File(dir, "nav-review.svg"));
        BufferedImage settings = renderSvg(new File(dir, "nav-settings.svg"));
        ImageIO.write(review, "png", new File(dir, "nav-review.png"));
        ImageIO.write(settings, "png", new File(dir, "nav-settings.png"));
        composeSheet(dir, review, settings);
    }

    private static BufferedImage renderSvg(File svg) throws Exception {
        SVGDocument document = new SVGLoader().load(svg.toURI().toURL());
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);
        document.render(new JPanel(), g, new ViewBox(0, 0, 512, 512));
        g.dispose();
        return image;
    }

    private static void composeSheet(File dir, BufferedImage review, BufferedImage settings) throws Exception {
        int width = 1400;
        int height = 920;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        configure(g);

        g.setPaint(new LinearGradientPaint(
                0, 0, width, height,
                new float[]{0f, 1f},
                new Color[]{new Color(8, 16, 24), new Color(5, 10, 16)}
        ));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(35, 176, 166, 20));
        g.fill(new Ellipse2D.Double(-180, -220, 680, 680));
        g.setColor(new Color(45, 125, 255, 16));
        g.fill(new Ellipse2D.Double(980, 620, 600, 600));

        g.setColor(new Color(113, 231, 220));
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g.drawString("DUYI WEIQI  ·  NAVIGATION ICONS", 52, 50);
        g.setColor(new Color(245, 247, 250));
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 42));
        g.drawString("复盘与设置图标方案", 48, 105);
        g.setColor(new Color(156, 170, 184));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 17));
        g.drawString("沿用底部导航的单色线性风格，先展示设计稿，不替换资源。", 52, 145);

        drawCard(g, 50, 190, 620, 560, review,
                "复盘", "REVIEW",
                "棋盘放大镜 + 回看播放三角",
                "对应逐手浏览和当前手 AI 推荐");
        drawCard(g, 730, 190, 620, 560, settings,
                "设置", "SETTINGS",
                "棋石旋钮 + 调节滑杆",
                "对应对局参数与本地偏好设置");

        g.setColor(new Color(148, 162, 176));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        g.drawString("图标采用 512×512 透明矢量源，后续可无损转换为 Android 24dp 单色导航图标。", 52, 842);
        g.dispose();
        ImageIO.write(sheet, "png", new File(dir, "nav-icons-preview.png"));
    }

    private static void drawCard(Graphics2D g, int x, int y, int w, int h,
                                 BufferedImage icon, String title, String english,
                                 String description, String detail) {
        g.setColor(new Color(24, 33, 44));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 28, 28));
        g.setColor(new Color(43, 58, 73));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 28, 28));
        g.drawImage(icon, x + 42, y + 60, 300, 300, null);

        g.setColor(new Color(111, 226, 215));
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.drawString(english, x + 366, y + 67);
        g.setColor(new Color(247, 250, 252));
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 35));
        g.drawString(title, x + 362, y + 113);
        g.setColor(new Color(170, 184, 197));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        g.drawString(description, x + 364, y + 153);
        g.setColor(new Color(135, 151, 165));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        g.drawString(detail, x + 364, y + 183);

        g.drawImage(icon, x + 364, y + 224, 64, 64, null);
        g.drawImage(icon, x + 446, y + 240, 32, 32, null);
        g.setColor(new Color(137, 153, 167));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g.drawString("64 px", x + 364, y + 314);
        g.drawString("32 px", x + 446, y + 314);

        g.setColor(new Color(38, 55, 69));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(x + 42, y + 395, x + w - 42, y + 395);
        g.setColor(new Color(143, 158, 172));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        g.drawString("24dp 时可保持清晰轮廓 · 选中时由主题色统一着色", x + 42, y + 437);
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}
