package search.engine.core;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {

    public record Token(String term, int position) {}

    /**
     * Splits text on non-alphanumeric boundaries, lowercases characters,
     * and maps each token to its 0-indexed word position.
     */
    public static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }

        StringBuilder buffer = new StringBuilder();
        int currentWordPosition = 0;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            if (Character.isLetterOrDigit(c)) {
                buffer.append(Character.toLowerCase(c));
            } else {
                if (!buffer.isEmpty()) {
                    tokens.add(new Token(buffer.toString(), currentWordPosition++));
                    buffer.setLength(0);
                }
            }
        }

        if (!buffer.isEmpty()) {
            tokens.add(new Token(buffer.toString(), currentWordPosition));
        }

        return tokens;
    }
}