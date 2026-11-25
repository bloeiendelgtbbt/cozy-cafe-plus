package io.github.chakyl.cozycafe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces Forge's ForgeConfigSpec. It reads and writes the same flat TOML layout that Forge produced
 * (config/cozyrestaurant-common.toml), so an existing Forge config file can be copied over unchanged.
 * Values are read once at startup; restart the game or server after editing the file.
 */
public class CozyConfig {

    public abstract static class ConfigValue<T> {
        final String key;
        final String comment;
        final T defaultValue;
        T value;

        ConfigValue(String key, String comment, T defaultValue) {
            this.key = key;
            this.comment = comment;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public T get() {
            return this.value;
        }

        abstract T parse(String raw);

        abstract String serialize(T value);

        String rangeComment() {
            return null;
        }
    }

    public static class IntValue extends ConfigValue<Integer> {
        final int min, max;

        IntValue(String key, String comment, int def, int min, int max) {
            super(key, comment, def);
            this.min = min;
            this.max = max;
        }

        @Override
        Integer parse(String raw) {
            int v = Integer.parseInt(raw.trim());
            if (v < min || v > max) throw new IllegalArgumentException("out of range");
            return v;
        }

        @Override
        String serialize(Integer value) {
            return String.valueOf(value);
        }

        @Override
        String rangeComment() {
            return "Range: " + (max == Integer.MAX_VALUE ? "> " + min : min + " ~ " + max);
        }
    }

    public static class DoubleValue extends ConfigValue<Double> {
        final double min, max;

        DoubleValue(String key, String comment, double def, double min, double max) {
            super(key, comment, def);
            this.min = min;
            this.max = max;
        }

        @Override
        Double parse(String raw) {
            double v = Double.parseDouble(raw.trim());
            if (v < min || v > max) throw new IllegalArgumentException("out of range");
            return v;
        }

        @Override
        String serialize(Double value) {
            return String.valueOf(value);
        }

        @Override
        String rangeComment() {
            return "Range: " + min + " ~ " + max;
        }
    }

    public static class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(String key, String comment, boolean def) {
            super(key, comment, def);
        }

        @Override
        Boolean parse(String raw) {
            String s = raw.trim();
            if (s.equals("true")) return true;
            if (s.equals("false")) return false;
            throw new IllegalArgumentException("not a boolean");
        }

        @Override
        String serialize(Boolean value) {
            return String.valueOf(value);
        }
    }

    public static class StringValue extends ConfigValue<String> {
        StringValue(String key, String comment, String def) {
            super(key, comment, def);
        }

        @Override
        String parse(String raw) {
            return parseString(raw.trim());
        }

        @Override
        String serialize(String value) {
            return quote(value);
        }
    }

    public static class StringListValue extends ConfigValue<List<? extends String>> {
        StringListValue(String key, String comment, List<String> def) {
            super(key, comment, def);
        }

        @Override
        List<? extends String> parse(String raw) {
            String s = raw.trim();
            if (!s.startsWith("[") || !s.endsWith("]")) throw new IllegalArgumentException("not a list");
            List<String> out = new ArrayList<>();
            int i = 1;
            while (i < s.length() - 1) {
                char c = s.charAt(i);
                if (c == '"' || c == '\'') {
                    int end = findStringEnd(s, i);
                    out.add(parseString(s.substring(i, end + 1)));
                    i = end + 1;
                } else if (c == ',' || Character.isWhitespace(c)) {
                    i++;
                } else {
                    throw new IllegalArgumentException("unexpected character in list");
                }
            }
            return out;
        }

        @Override
        String serialize(List<? extends String> value) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < value.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(quote(value.get(i)));
            }
            return sb.append(']').toString();
        }
    }

    private final List<ConfigValue<?>> values = new ArrayList<>();

    public final IntValue customerSpawnInterval;
    public final DoubleValue groupCustomerChance;
    public final IntValue customerWaitTime;
    public final IntValue customerWaitTimePerExtraItem;
    public final IntValue customerOrderTime;
    public final IntValue customerEatTime;
    public final IntValue customerBillWaitTime;
    public final IntValue guestAwayLimit;
    public final IntValue servedFoodKeepTime;
    public final StringListValue customerUsernames;

    private CozyConfig() {
        customerSpawnInterval = add(new IntValue("customer_spawn_interval", "Seconds between groups of NPC customers in a 5-star cafe. Every missing star of the cafe's rating adds this interval once more (5 stars: x1, 4 stars: x2 ... 0 stars: x6).", 40, 4, 4096));
        groupCustomerChance = add(new DoubleValue("group_customer_chance", "Chance another NPC joins the group that is coming in. Repeats until a roll fails or 4 customers are coming.", 0.5D, 0D, 1D));
        customerWaitTime = add(new IntValue("customer_wait_time", "How long an NPC customer waits for a one-position order, in ticks (20 ticks = 1 second).", 1800, 20, Integer.MAX_VALUE));
        customerWaitTimePerExtraItem = add(new IntValue("customer_wait_time_per_extra_item", "Extra waiting time for every further position of an NPC order, in ticks.", 600, 0, Integer.MAX_VALUE));
        customerOrderTime = add(new IntValue("customer_order_time", "How long an NPC customer takes to choose an order, in ticks.", 200, 20, Integer.MAX_VALUE));
        customerEatTime = add(new IntValue("customer_eat_time", "How long an NPC customer eats, in ticks.", 300, 20, Integer.MAX_VALUE));
        customerBillWaitTime = add(new IntValue("customer_bill_wait_time", "How long an NPC customer waits for the bill after eating, in ticks. Without a bill it leaves unpaid.", 2400, 20, Integer.MAX_VALUE));
        guestAwayLimit = add(new IntValue("guest_away_limit", "How long a player guest may stay outside the cafe's claim (offline counts as outside) once something was served, in ticks. After that the guest has left: what was served and not paid becomes a debt.", 12000, 200, Integer.MAX_VALUE));
        servedFoodKeepTime = add(new IntValue("served_food_keep_time", "How long food served to a player guest waits on the table, in ticks. Food not taken by then is thrown away; it still has to be paid for.", 12000, 200, Integer.MAX_VALUE));
        customerUsernames = add(new StringListValue("customer_usernames", "List of Minecraft usernames whose skins will be used for spawning customers.", Arrays.asList("Chakyl", "unnecessarymb", "Nitbe", "MHF_Steve", "MHF_Alex", "MHF_Herobrine", "MHF_Chicken", "MHF_Cow", "MHF_Pig", "MHF_Sheep", "MHF_Squid", "MHF_Villager", "MHF_Ocelot", "MHF_Blaze", "MHF_CaveSpider", "MHF_Enderman", "MHF_Ghast", "MHF_Golem", "MHF_LavaSlime", "MHF_PigZombie", "MHF_Skeleton", "MHF_Slime", "MHF_Spider", "MHF_Witch", "MHF_Zombie", "MHF_Cake", "MHF_Chest", "MHF_Melon", "MHF_OakLog", "MHF_Present1", "MHF_Present2", "MHF_Pumpkin", "MHF_TNT", "MHF_TNT2", "MHF_ArrowUp", "MHF_ArrowDown", "MHF_ArrowLeft", "MHF_ArrowRight", "MHF_Exclamation", "MHF_Question")));
    }

    private <V extends ConfigValue<?>> V add(V value) {
        this.values.add(value);
        return value;
    }

    public static CozyConfig load(Path file) {
        CozyConfig config = new CozyConfig();
        Map<String, String> raw = new HashMap<>();
        if (Files.exists(file)) {
            try {
                raw = readFlatToml(Files.readAllLines(file, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                CozyCafe.LOGGER.error("[COZYCAFE] Could not read config {}, using defaults", file, e);
            }
        }
        boolean needsRewrite = !Files.exists(file);
        for (ConfigValue<?> value : config.values) {
            String rawValue = raw.get(value.key);
            if (rawValue == null) {
                needsRewrite = true;
                continue;
            }
            if (!applyValue(value, rawValue)) {
                CozyCafe.LOGGER.warn("[COZYCAFE] Invalid value for '{}' in {}: {} (using default {})", value.key, file, rawValue, value.defaultValue);
                needsRewrite = true;
            }
        }
        if (needsRewrite) {
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, config.serialize(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                CozyCafe.LOGGER.error("[COZYCAFE] Could not write config {}", file, e);
            }
        }
        return config;
    }

    private static <T> boolean applyValue(ConfigValue<T> value, String raw) {
        try {
            value.value = value.parse(raw);
            return true;
        } catch (RuntimeException e) {
            value.value = value.defaultValue;
            return false;
        }
    }

    private List<String> serialize() {
        List<String> lines = new ArrayList<>();
        for (ConfigValue<?> value : this.values) {
            lines.add("#" + value.comment);
            if (value.rangeComment() != null) lines.add("#" + value.rangeComment());
            lines.add(value.key + " = " + serializeValue(value));
        }
        return lines;
    }

    private static <T> String serializeValue(ConfigValue<T> value) {
        return value.serialize(value.value);
    }

    /**
     * Reads the flat key = value TOML that Forge writes for this mod. Supports multi-line arrays and quoted strings.
     */
    private static Map<String, String> readFlatToml(List<String> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        String pendingKey = null;
        StringBuilder pending = null;
        for (String line : lines) {
            String stripped = stripComment(line).trim();
            if (pendingKey != null) {
                pending.append(' ').append(stripped);
                if (stripped.endsWith("]")) {
                    out.put(pendingKey, pending.toString());
                    pendingKey = null;
                }
                continue;
            }
            if (stripped.isEmpty() || stripped.startsWith("[")) continue;
            int eq = stripped.indexOf('=');
            if (eq < 0) continue;
            String key = stripped.substring(0, eq).trim();
            String value = stripped.substring(eq + 1).trim();
            if (value.startsWith("[") && !value.endsWith("]")) {
                pendingKey = key;
                pending = new StringBuilder(value);
            } else {
                out.put(key, value);
            }
        }
        return out;
    }

    private static String stripComment(String line) {
        boolean inDouble = false, inSingle = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && inDouble) {
                i++;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '#' && !inDouble && !inSingle) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static int findStringEnd(String s, int start) {
        char quote = s.charAt(start);
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && quote == '"') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        throw new IllegalArgumentException("unterminated string");
    }

    private static String parseString(String s) {
        if (s.length() < 2) throw new IllegalArgumentException("not a string");
        char quote = s.charAt(0);
        if ((quote != '"' && quote != '\'') || s.charAt(s.length() - 1) != quote || findStringEnd(s, 0) != s.length() - 1) {
            throw new IllegalArgumentException("not a string");
        }
        String body = s.substring(1, s.length() - 1);
        if (quote == '\'') return body;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char n = body.charAt(++i);
                switch (n) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append('\\').append(n);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
