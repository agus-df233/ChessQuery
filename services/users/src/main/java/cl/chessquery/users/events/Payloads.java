package cl.chessquery.users.events;

import java.time.LocalDate;
import java.util.Map;

/** Lecturas tolerantes de los payloads de eventos (números y fechas llegan como JSON genérico). */
public final class Payloads {

    private Payloads() {}

    public static String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public static Integer integer(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long lng(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v == null) return null;
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate date(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) return null;
        try {
            return LocalDate.parse(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
