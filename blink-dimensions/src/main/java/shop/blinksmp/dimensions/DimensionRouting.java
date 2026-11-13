package shop.blinksmp.dimensions;

final class DimensionRouting {
    private DimensionRouting() {}

    static String canonical(String input) {
        if (input == null) return null;
        return switch (input.toLowerCase(java.util.Locale.ROOT)) {
            case "overworld" -> "Overworld";
            case "nether" -> "Nether";
            case "end" -> "End";
            default -> null;
        };
    }

    static String environment(String environment) {
        if (environment == null) return null;
        return switch (environment) {
            case "NORMAL" -> "Overworld";
            case "NETHER" -> "Nether";
            case "THE_END" -> "End";
            default -> null;
        };
    }

    static String target(String current, String cause) {
        if (current == null || cause == null) return null;
        if (cause.equals("NETHER_PORTAL")) {
            if (current.equals("Overworld")) return "Nether";
            if (current.equals("Nether")) return "Overworld";
        }
        if (cause.equals("END_PORTAL")) {
            if (current.equals("Overworld")) return "End";
            if (current.equals("End")) return "Overworld";
        }
        return null;
    }
}
