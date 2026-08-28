package client;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class Theme {
    public static final Color BG = new Color(241, 245, 249);
    public static final Color PANEL = new Color(255, 255, 255);
    public static final Color PANEL_ELEVATED = new Color(250, 252, 255);
    public static final Color SIDEBAR = new Color(248, 250, 252);
    public static final Color SIDEBAR_DARK = new Color(31, 35, 42);
    public static final Color BORDER = new Color(218, 224, 232);
    public static final Color INK = new Color(24, 28, 35);
    public static final Color MUTED = new Color(105, 116, 130);
    public static final Color MUTED_LIGHT = new Color(148, 158, 171);
    public static final Color ACCENT = new Color(37, 99, 235);
    public static final Color ACCENT_DARK = new Color(29, 78, 216);
    public static final Color ACCENT_SOFT = new Color(225, 236, 255);
    public static final Color SUCCESS = new Color(22, 163, 74);
    public static final Color WARN = new Color(217, 119, 6);

    public static final Color BUBBLE_IN = new Color(255, 255, 255);
    public static final Color BUBBLE_OUT = new Color(37, 99, 235);
    public static final Color BUBBLE_SYSTEM = new Color(240, 247, 255);

    public static final Font UI_FONT = new Font("Inter", Font.PLAIN, 13);
    public static final Font UI_FONT_BOLD = new Font("Inter", Font.BOLD, 13);
    public static final Font SMALL_FONT = new Font("Inter", Font.PLAIN, 12);
    public static final Font HEADER_FONT = new Font("Inter", Font.BOLD, 18);
    public static final Font TITLE_FONT = new Font("Inter", Font.BOLD, 22);

    private Theme() {}

    public static JButton primaryButton(String label) {
        JButton button = new JButton(label);
        button.setFont(UI_FONT_BOLD);
        button.setForeground(Color.WHITE);
        button.setBackground(ACCENT);
        button.setBorder(new EmptyBorder(9, 14, 9, 14));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        return button;
    }

    public static JButton secondaryButton(String label) {
        JButton button = new JButton(label);
        button.setFont(UI_FONT);
        button.setForeground(INK);
        button.setBackground(PANEL_ELEVATED);
        button.setBorder(new EmptyBorder(9, 12, 9, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        return button;
    }

    public static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UI_FONT_BOLD);
        label.setForeground(MUTED);
        return label;
    }

    public static void stylePanel(JComponent panel) {
        panel.setBackground(PANEL);
        panel.setForeground(INK);
    }

    public static void styleCard(JComponent panel) {
        panel.setBackground(PANEL_ELEVATED);
        panel.setForeground(INK);
        panel.setBorder(BorderFactory.createLineBorder(BORDER));
    }

    public static JPanel gradientHeader() {
        return new GradientPanel();
    }

    public static void applyToUI() {
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.messageForeground", INK);
        UIManager.put("OptionPane.messageFont", UI_FONT);
        UIManager.put("OptionPane.buttonFont", UI_FONT_BOLD);
        UIManager.put("Button.font", UI_FONT);
        UIManager.put("Label.font", UI_FONT);
        UIManager.put("Label.foreground", INK);
        UIManager.put("TextField.font", UI_FONT);
        UIManager.put("TextField.background", PANEL_ELEVATED);
        UIManager.put("TextField.foreground", INK);
        UIManager.put("ComboBox.font", UI_FONT);
        UIManager.put("ComboBox.background", PANEL_ELEVATED);
        UIManager.put("ComboBox.foreground", INK);
        UIManager.put("Table.background", PANEL_ELEVATED);
        UIManager.put("Table.foreground", INK);
        UIManager.put("Table.selectionBackground", ACCENT_SOFT);
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("TableHeader.background", PANEL_ELEVATED);
        UIManager.put("TableHeader.foreground", INK);
        UIManager.put("List.background", PANEL_ELEVATED);
        UIManager.put("List.foreground", INK);
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("TabbedPane.font", UI_FONT_BOLD);
        UIManager.put("TabbedPane.background", PANEL);
        UIManager.put("TabbedPane.foreground", INK);
        UIManager.put("TabbedPane.selected", PANEL_ELEVATED);
    }

    private static class GradientPanel extends JPanel {
        GradientPanel() {
            setBorder(new EmptyBorder(18, 22, 18, 22));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            GradientPaint gp = new GradientPaint(0, 0, new Color(28, 36, 48), w, h, new Color(22, 26, 34));
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);
            g2.dispose();
        }
    }
}
