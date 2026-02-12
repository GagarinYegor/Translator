package com.nequma.translator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nequma.translator.TokenType.*;

class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;

    Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '+': addToken(PLUS); break;
            case '-': addToken(MIN); break;
            case '*': addToken(MULT); break;
            case '/': addToken(DIV); break;
            case ';': addToken(EOP); break;
            case '=': addToken(match('=') ? EQ : EQ); // Проверка на ==, но по условию только =
                if (peek() == '=') advance(); break;
            case '<':
                if (match('=')) {
                    addToken(LE);
                } else if (match('>')) {
                    addToken(NE); // <>
                } else {
                    addToken(LT);
                }
                break;
            case '>':
                addToken(match('=') ? GE : GT);
                break;
            case ':':
                addToken(match('=') ? ASS : null);
                if (peekPrevious() == ':' && source.charAt(current - 2) == ':') {
                    // Обработка метки
                }
                break;
            case '{':
                addToken(COMMENT);
                // Пропускаем комментарий до закрывающей скобки
                while (peek() != '}' && !isAtEnd()) {
                    if (peek() == '\n') line++;
                    advance();
                }
                if (isAtEnd()) {
                    Translator.error(line, "Unterminated comment.");
                    return;
                }
                advance(); // Пропускаем }
                break;
            case '}':
                // Конец комментария обрабатывается в блоке case '{'
                break;
            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace
                break;
            case '\n':
                line++;
                break;
            default:
                if (isDigit(c) || (c == '0' && (peek() == 'B' || peek() == 'b' ||
                        peek() == 'X' || peek() == 'x'))) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    Translator.error(line, "Unexpected character: " + c);
                }
                break;
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        // Проверка на метку
        if (peek() == ':') {
            String labelName = source.substring(start, current);
            advance(); // Пропускаем :
            addToken(LABEL, labelName);
        } else {
            String text = source.substring(start, current);
            TokenType type = keywords.get(text);
            if (type == null) type = IDENTIFIER;
            addToken(type);
        }
    }

    private boolean isAlpha(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void number() {
        // Проверка на двоичное число
        if (peekPrevious() == '0' && (peek() == 'B' || peek() == 'b')) {
            advance(); // Пропускаем B/b
            StringBuilder binary = new StringBuilder();
            while (isBinaryDigit(peek())) {
                binary.append(advance());
            }
            if (binary.length() == 0) {
                Translator.error(line, "Invalid binary number.");
                return;
            }
            addToken(NUMBER, Integer.parseInt(binary.toString(), 2));
            return;
        }

        // Проверка на шестнадцатеричное число
        if (peekPrevious() == '0' && (peek() == 'X' || peek() == 'x')) {
            advance(); // Пропускаем X/x
            StringBuilder hex = new StringBuilder();
            while (isHexDigit(peek())) {
                hex.append(advance());
            }
            if (hex.length() == 0) {
                Translator.error(line, "Invalid hexadecimal number.");
                return;
            }
            addToken(NUMBER, Integer.parseInt(hex.toString(), 16));
            return;
        }

        // Обработка восьмеричных и десятичных чисел
        while (isDigit(peek())) advance();

        // Проверка на восьмеричное число (начинается с 0)
        if (source.charAt(start) == '0' && current > start + 1) {
            String octalStr = source.substring(start, current);
            // Проверяем, что все цифры восьмеричные
            boolean validOctal = true;
            for (int i = start + 1; i < current; i++) {
                if (source.charAt(i) < '0' || source.charAt(i) > '7') {
                    validOctal = false;
                    break;
                }
            }
            if (validOctal) {
                addToken(NUMBER, Integer.parseInt(octalStr, 8));
                return;
            }
        }

        // Проверка на действительное число
        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // Consume the "."

            while (isDigit(peek())) advance();

            // Проверка на порядок
            if (peek() == 'E' || peek() == 'e') {
                advance(); // Consume E/e

                if (peek() == '+' || peek() == '-') {
                    advance(); // Consume sign
                }

                if (!isDigit(peek())) {
                    Translator.error(line, "Invalid exponent.");
                    return;
                }

                while (isDigit(peek())) advance();
            }

            addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
            return;
        }

        // Проверка на порядок без дробной части
        if ((peek() == 'E' || peek() == 'e') && current > start) {
            advance(); // Consume E/e

            if (peek() == '+' || peek() == '-') {
                advance(); // Consume sign
            }

            if (!isDigit(peek())) {
                Translator.error(line, "Invalid exponent.");
                return;
            }

            while (isDigit(peek())) advance();

            addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
            return;
        }

        // Десятичное число
        addToken(NUMBER, Integer.parseInt(source.substring(start, current)));
    }

    private boolean isBinaryDigit(char c) {
        return c == '0' || c == '1';
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
                (c >= 'A' && c <= 'F') ||
                (c >= 'a' && c <= 'f');
    }

    private char peekPrevious() {
        if (current - 1 < 0) return '\0';
        return source.charAt(current - 1);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char advance() {
        return source.charAt(current++);
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("begin", BST);
        keywords.put("end", EST);
        keywords.put("vector", VECTOR);
        keywords.put("of", OF);
        keywords.put("goto", GOTO);
        keywords.put("read", READ);
        keywords.put("write", WRITE);
        keywords.put("skip", SKIP);
        keywords.put("space", SPACE);
        keywords.put("tab", TAB);
        keywords.put("if", IF);
        keywords.put("then", THEN);
        keywords.put("else", ELSE);
        keywords.put("loop", LOOP);
    }
}