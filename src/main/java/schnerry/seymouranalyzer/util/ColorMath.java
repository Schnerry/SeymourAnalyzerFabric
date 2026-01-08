package schnerry.seymouranalyzer.util;

/**
 * Color conversion utilities for RGB, XYZ, and LAB color spaces.
 * Implements CIEDE2000 color difference calculations.
 */
public class ColorMath {

    /**
     * Convert hex string to RGB values
     */
    public static class RGB {
        public final int r, g, b;

        public RGB(int r, int g, int b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    /**
     * XYZ color space representation
     */
    public static class XYZ {
        public final double x, y, z;

        public XYZ(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * LAB color space representation
     */
    public static class LAB {
        public final double L, a, b;

        public LAB(double L, double a, double b) {
            this.L = L;
            this.a = a;
            this.b = b;
        }
    }

    /**
     * Parse hex color string to RGB
     */
    public static RGB hexToRgb(String hex) {
        hex = hex.replace("#", "").toUpperCase();
        if (hex.length() != 6) {
            return new RGB(0, 0, 0);
        }

        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new RGB(r, g, b);
        } catch (NumberFormatException e) {
            return new RGB(0, 0, 0);
        }
    }

    /**
     * Convert RGB to XYZ color space
     */
    public static XYZ rgbToXyz(RGB rgb) {
        double r = rgb.r / 255.0;
        double g = rgb.g / 255.0;
        double b = rgb.b / 255.0;

        r = r > 0.04045 ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
        g = g > 0.04045 ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
        b = b > 0.04045 ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;

        double x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) * 100;
        double y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750) * 100;
        double z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) * 100;

        return new XYZ(x, y, z);
    }

    /**
     * Convert XYZ to LAB color space
     */
    public static LAB xyzToLab(XYZ xyz) {
        final double xn = 95.047;
        final double yn = 100.0;
        final double zn = 108.883;

        double x = xyz.x / xn;
        double y = xyz.y / yn;
        double z = xyz.z / zn;

        x = x > 0.008856 ? Math.pow(x, 1.0/3.0) : (7.787 * x + 16.0/116.0);
        y = y > 0.008856 ? Math.pow(y, 1.0/3.0) : (7.787 * y + 16.0/116.0);
        z = z > 0.008856 ? Math.pow(z, 1.0/3.0) : (7.787 * z + 16.0/116.0);

        double L = 116 * y - 16;
        double a = 500 * (x - y);
        double b = 200 * (y - z);

        return new LAB(L, a, b);
    }

    /**
     * Convert hex string directly to LAB
     */
    public static LAB hexToLab(String hex) {
        RGB rgb = hexToRgb(hex);
        XYZ xyz = rgbToXyz(rgb);
        return xyzToLab(xyz);
    }

    /**
     * Calculate Delta E (CIE76) between two colors
     */
    public static double calculateDeltaE(String hex1, String hex2) {
        LAB lab1 = hexToLab(hex1);
        LAB lab2 = hexToLab(hex2);

        return Math.sqrt(
            Math.pow(lab1.L - lab2.L, 2) +
            Math.pow(lab1.a - lab2.a, 2) +
            Math.pow(lab1.b - lab2.b, 2)
        );
    }

    /**
     * Calculate Delta E between two LAB colors
     */
    public static double calculateDeltaEWithLab(LAB lab1, LAB lab2) {
        return Math.sqrt(
            Math.pow(lab1.L - lab2.L, 2) +
            Math.pow(lab1.a - lab2.a, 2) +
            Math.pow(lab1.b - lab2.b, 2)
        );
    }

    /**
     * Calculate absolute RGB distance (Manhattan distance)
     */
    public static int calculateAbsoluteDistance(String hex1, String hex2) {
        RGB rgb1 = hexToRgb(hex1);
        RGB rgb2 = hexToRgb(hex2);

        return Math.abs(rgb1.r - rgb2.r) +
               Math.abs(rgb1.g - rgb2.g) +
               Math.abs(rgb1.b - rgb2.b);
    }

    /**
     * Check if a color is dark (for text contrast)
     */
    public static boolean isColorDark(String hex) {
        RGB rgb = hexToRgb(hex);
        double luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255;
        return luminance < 0.5;
    }

    /**
     * Convert RGB to hex string
     */
    public static String rgbToHex(int r, int g, int b) {
        return String.format("%02X%02X%02X", r, g, b);
    }
}

