package com.tac5studios.elementsclaims.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Turns &-color text (&a, &l, &#FFD700) into Minecraft text. */
public final class Text {

    private Text() {}

    public static MutableComponent color(String raw) {
        MutableComponent out = Component.empty();
        if (raw == null || raw.isEmpty()) return out;

        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                // Hex color: &#RRGGBB
                if (raw.charAt(i + 1) == '#' && i + 8 <= raw.length()) {
                    String hex = raw.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        flush(out, buf, style);
                        style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(hex, 16)));
                        i += 8;
                        continue;
                    }
                }
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(raw.charAt(i + 1)));
                if (f != null) {
                    flush(out, buf, style);
                    if (f == ChatFormatting.RESET) style = Style.EMPTY;
                    else if (f.isColor()) style = Style.EMPTY.withColor(f); // a new color clears bold etc. (same as vanilla)
                    else style = style.applyFormat(f);
                    i += 2;
                    continue;
                }
            }
            buf.append(c);
            i++;
        }
        flush(out, buf, style);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder buf, Style style) {
        if (buf.length() == 0) return;
        out.append(Component.literal(buf.toString()).setStyle(style));
        buf.setLength(0);
    }
}
