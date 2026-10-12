package com.tac5studios.elementsclaims.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A forgiving reader for FTB Library's SNBT files (FTB Chunks / FTB Teams). Unlike vanilla SNBT they put one entry
 * per line with no commas, allow # comments and bare true/false. Returns Map / List / String / Number / Boolean.
 */
final class Snbt {

    private final String s;
    private int i;

    private Snbt(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Snbt p = new Snbt(text);
        p.skip();
        return p.value();
    }

    private void skip() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '#') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
            } else if (Character.isWhitespace(c) || c == ',') {
                i++;
            } else {
                break;
            }
        }
    }

    private Object value() {
        skip();
        if (i >= s.length()) return null;
        char c = s.charAt(i);
        if (c == '{') return compound();
        if (c == '[') return list();
        if (c == '"' || c == '\'') return string();
        return scalar(bare());
    }

    private Map<String, Object> compound() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        while (true) {
            skip();
            if (i >= s.length()) return m;
            if (s.charAt(i) == '}') {
                i++;
                return m;
            }
            String key;
            if (s.charAt(i) == '"' || s.charAt(i) == '\'') {
                key = string();
                skip();
                if (i < s.length() && s.charAt(i) == ':') i++;
            } else {
                key = bareKey();
            }
            m.put(key, value());
        }
    }

    private List<Object> list() {
        List<Object> l = new ArrayList<>();
        i++; // [
        skip();
        // typed arrays: [I; 1, 2] [L; ...] [B; ...]
        if (i + 1 < s.length() && s.charAt(i + 1) == ';' && "BIL".indexOf(s.charAt(i)) >= 0) i += 2;
        while (true) {
            skip();
            if (i >= s.length()) return l;
            if (s.charAt(i) == ']') {
                i++;
                return l;
            }
            l.add(value());
        }
    }

    private String string() {
        char q = s.charAt(i++);
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '\\' && i < s.length()) {
                char n = s.charAt(i++);
                b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else if (c == q) {
                break;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /** An unquoted key: may itself contain ':' (e.g. minecraft:overworld). The last ':' ends it. */
    private String bareKey() {
        int start = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == ',' || c == '}' || c == ']' || c == '{' || c == '[' || c == '"') break;
            i++;
        }
        String tok = s.substring(start, i);
        if (tok.endsWith(":")) return tok.substring(0, tok.length() - 1);
        int colon = tok.lastIndexOf(':');
        if (colon > 0) {
            i = start + colon + 1; // value follows straight after the colon
            return tok.substring(0, colon);
        }
        skip();
        if (i < s.length() && s.charAt(i) == ':') i++;
        return tok;
    }

    private String bare() {
        int start = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == ',' || c == ':' || c == '}' || c == ']' || c == '{' || c == '[') break;
            i++;
        }
        return s.substring(start, i);
    }

    private static Object scalar(String t) {
        if (t.equals("true")) return Boolean.TRUE;
        if (t.equals("false")) return Boolean.FALSE;
        String num = t;
        char last = t.isEmpty() ? ' ' : Character.toLowerCase(t.charAt(t.length() - 1));
        if ("bslfd".indexOf(last) >= 0 && t.length() > 1) num = t.substring(0, t.length() - 1);
        try {
            if (num.contains(".") || last == 'f' || last == 'd') return Double.parseDouble(num);
            return Long.parseLong(num);
        } catch (NumberFormatException e) {
            return t;
        }
    }
}
