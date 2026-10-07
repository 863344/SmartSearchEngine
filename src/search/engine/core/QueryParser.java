package search.engine.core;

import java.util.*;

public class QueryParser {

    private enum TokenType { TERM, PHRASE, AND, OR, NOT, LPAREN, RPAREN }

    private record Token(TokenType type, String value) {}

    public static QueryNode parse(String queryString, InvertedIndex index) {
        if (queryString == null || queryString.isBlank()) {
            throw new IllegalArgumentException("Enter a search term or expression.");
        }
        List<Token> tokens = withImplicitAnd(tokenize(queryString));
        return buildAST(tokens, index);
    }

    private static List<Token> withImplicitAnd(List<Token> tokens) {
        List<Token> result = new ArrayList<>();
        TokenType previous = null;
        for (Token token : tokens) {
            boolean endsOperand = previous == TokenType.TERM || previous == TokenType.PHRASE || previous == TokenType.RPAREN;
            boolean startsOperand = token.type == TokenType.TERM || token.type == TokenType.PHRASE
                || token.type == TokenType.LPAREN || token.type == TokenType.NOT;
            if (endsOperand && startsOperand) result.add(new Token(TokenType.AND, "AND"));
            result.add(token);
            previous = token.type;
        }
        return result;
    }

    private static List<Token> tokenize(String query) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = query.length();

        while (i < len) {
            char c = query.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (c == '"') {
                // Quoted Phrase Extraction
                int start = ++i;
                while (i < len && query.charAt(i) != '"') {
                    i++;
                }
                String phrase = query.substring(start, Math.min(i, len)).trim();
                if (i == len) throw new IllegalArgumentException("Close the quoted phrase with a double quote.");
                if (phrase.isEmpty()) throw new IllegalArgumentException("A quoted phrase cannot be empty.");
                if (!phrase.isEmpty()) {
                    tokens.add(new Token(TokenType.PHRASE, phrase));
                }
                if (i < len && query.charAt(i) == '"') {
                    i++;
                }
            } else if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "("));
                i++;
            } else if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")"));
                i++;
            } else {
                StringBuilder sb = new StringBuilder();
                while (i < len && !Character.isWhitespace(query.charAt(i)) 
                       && query.charAt(i) != '(' && query.charAt(i) != ')' && query.charAt(i) != '"') {
                    sb.append(query.charAt(i));
                    i++;
                }
                String word = sb.toString();
                String upper = word.toUpperCase();
                switch (upper) {
                    case "AND" -> tokens.add(new Token(TokenType.AND, "AND"));
                    case "OR" -> tokens.add(new Token(TokenType.OR, "OR"));
                    case "NOT" -> tokens.add(new Token(TokenType.NOT, "NOT"));
                    default -> tokens.add(new Token(TokenType.TERM, word));
                }
            }
        }
        return tokens;
    }

    private static int precedence(TokenType type) {
        return switch (type) {
            case NOT -> 3;
            case AND -> 2;
            case OR -> 1;
            default -> 0;
        };
    }

    private static QueryNode buildAST(List<Token> tokens, InvertedIndex index) {
        Deque<QueryNode> nodeStack = new ArrayDeque<>();
        Deque<Token> operatorStack = new ArrayDeque<>();
        boolean expectOperand = true;

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            switch (token.type) {
                case TERM -> {
                    if (!expectOperand) throw new IllegalArgumentException("Missing operator.");
                    nodeStack.push(new TermNode(token.value));
                    expectOperand = false;
                }
                case PHRASE -> {
                    List<Tokenizer.Token> parsedTokens = Tokenizer.tokenize(token.value);
                    List<String> words = new ArrayList<>();
                    for (Tokenizer.Token t : parsedTokens) {
                        words.add(t.term());
                    }
                    if (words.isEmpty()) throw new IllegalArgumentException("The phrase must contain a word.");
                    nodeStack.push(new PhraseNode(words));
                    expectOperand = false;
                }
                case LPAREN -> {
                    operatorStack.push(token);
                    expectOperand = true;
                }
                case RPAREN -> {
                    if (expectOperand) throw new IllegalArgumentException("A parenthesized expression needs an operand.");
                    while (!operatorStack.isEmpty() && operatorStack.peek().type != TokenType.LPAREN) {
                        applyOperator(operatorStack.pop(), nodeStack, index);
                    }
                    if (operatorStack.isEmpty()) throw new IllegalArgumentException("Unmatched closing parenthesis.");
                    operatorStack.pop();
                    expectOperand = false;
                }
                case NOT -> {
                    if (!expectOperand) throw new IllegalArgumentException("Missing operator before NOT.");
                    operatorStack.push(token);
                }
                case AND, OR -> {
                    if (expectOperand) throw new IllegalArgumentException("Operator " + token.value + " needs a left operand.");
                    while (!operatorStack.isEmpty() && operatorStack.peek().type != TokenType.LPAREN
                            && precedence(operatorStack.peek().type) >= precedence(token.type)) {
                        applyOperator(operatorStack.pop(), nodeStack, index);
                    }
                    operatorStack.push(token);
                    expectOperand = true;
                }
            }
        }

        if (expectOperand) throw new IllegalArgumentException("The query needs a final operand.");
        while (!operatorStack.isEmpty()) {
            if (operatorStack.peek().type == TokenType.LPAREN) throw new IllegalArgumentException("Unmatched opening parenthesis.");
            applyOperator(operatorStack.pop(), nodeStack, index);
        }

        if (nodeStack.size() != 1) throw new IllegalArgumentException("Invalid search expression.");
        return nodeStack.pop();
    }

    private static void applyOperator(Token op, Deque<QueryNode> nodeStack, InvertedIndex index) {
        if (op.type == TokenType.NOT) {
            if (nodeStack.isEmpty()) throw new IllegalArgumentException("NOT needs an operand.");
            nodeStack.push(new NotNode(buildUniverseNode(index), nodeStack.pop()));
            return;
        }

        if (nodeStack.size() < 2) {
            throw new IllegalArgumentException("Operator " + op.value + " needs two operands.");
        }

        QueryNode right = nodeStack.pop();
        QueryNode left = nodeStack.pop();

        switch (op.type) {
            case AND -> nodeStack.push(new AndNode(left, right));
            case OR -> nodeStack.push(new OrNode(left, right));
            default -> throw new IllegalStateException("Unknown operator: " + op.value);
        }
    }

    private static QueryNode buildUniverseNode(InvertedIndex index) {
        return InvertedIndex::getDocumentIds;
    }
}
