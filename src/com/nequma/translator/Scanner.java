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
            case '(': addToken(LPAREN); break;
            case ')': addToken(RPAREN); break;
            case '[': addToken(LBRACKET); break;
            case ']': addToken(RBRACKET); break;
            case ',': addToken(COMMA); break;
            case ':':
                if (match('=')) {
                    addToken(ASS);
                } else {
                    addToken(COLON);
                }
                break;
            case '=': addToken(EQ); break;
            case '<':
                if (match('=')) {
                    addToken(LE);
                } else if (match('>')) {
                    addToken(NE);
                } else {
                    addToken(LT);
                }
                break;
            case '>':
                if (match('=')) {
                    addToken(GE);
                } else {
                    addToken(GT);
                }
                break;
            case '{':
                // Комментарий до закрывающей скобки
                while (peek() != '}' && !isAtEnd()) {
                    if (peek() == '\n') line++;
                    advance();
                }
                if (isAtEnd()) {
                    Translator.error(line, "Unterminated comment.");
                    return;
                }
                advance(); // Пропускаем }
                // Не добавляем токен для комментария
                break;
            case ' ':
            case '\r':
            case '\t':
                // Игнорируем пробелы
                break;
            case '\n':
                line++;
                break;
            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else if (c == '.') {
                    // Может быть началом действительного числа
                    if (isDigit(peek())) {
                        number();
                    } else {
                        // Период может быть терминатором программы - просто игнорируем
                        break;
                    }
                } else {
                    Translator.error(line, "Unexpected character: " + c);
                }
                break;
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);

        // Проверка на ключевое слово mod (регистронезависимо)
        if (text.equalsIgnoreCase("mod")) {
            addToken(MOD);
            return;
        }

        // Проверка на ключевые слова (регистронезависимо)
        TokenType type = keywords.get(text.toLowerCase());
        if (type != null) {
            addToken(type);
            return;
        }

        // Проверка на метку (если после идентификатора идет двоеточие)
        if (peek() == ':') {
            String labelName = text;
            advance(); // Пропускаем :
            // Создаем специальный токен для метки
            tokens.add(new Token(LABEL, labelName, null, line));
        } else {
            addToken(IDENTIFIER, text);
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
        // Проверка на двоичное число (0B или 0b)
        if (source.charAt(start) == '0' && (peek() == 'B' || peek() == 'b')) {
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

        // Проверка на шестнадцатеричное число (0X или 0x)
        if (source.charAt(start) == '0' && (peek() == 'X' || peek() == 'x')) {
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

        // Обработка чисел
        while (isDigit(peek())) advance();

        // Проверка на действительное число с дробной частью
        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // Consume the "."

            while (isDigit(peek())) advance();

            // Проверка на порядок (E или e)
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

        // Проверка на восьмеричное число
        if (source.charAt(start) == '0' && current > start + 1) {
            String octalStr = source.substring(start, current);
            boolean validOctal = true;
            for (int i = start + 1; i < current; i++) {
                char digit = source.charAt(i);
                if (digit < '0' || digit > '7') {
                    validOctal = false;
                    break;
                }
            }
            if (validOctal) {
                addToken(NUMBER, Integer.parseInt(octalStr, 8));
                return;
            }
        }

        // Десятичное целое число
        String numStr = source.substring(start, current);
        try {
            // Пробуем как целое
            addToken(NUMBER, Integer.parseInt(numStr));
        } catch (NumberFormatException e) {
            // Если слишком большое, пробуем как double
            try {
                addToken(NUMBER, Double.parseDouble(numStr));
            } catch (NumberFormatException ex) {
                Translator.error(line, "Invalid number: " + numStr);
            }
        }
    }

    private boolean isBinaryDigit(char c) {
        return c == '0' || c == '1';
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
                (c >= 'A' && c <= 'F') ||
                (c >= 'a' && c <= 'f');
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
        keywords.put("integer", INTEGER);
        keywords.put("real", REAL);
        keywords.put("mod", MOD);
    }
}