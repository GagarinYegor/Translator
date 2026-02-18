package com.nequma.translator;

import java.util.ArrayList;
import java.util.List;

import static com.nequma.translator.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;
    private boolean hadError = false;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        try {
            while (!isAtEnd()) {
                Stmt stmt = declaration();
                if (stmt != null) {
                    statements.add(stmt);
                }
                // Ожидаем ';' между операторами, но не в конце и не перед end
                if (!isAtEnd() && !check(EST) && !check(EOF)) {
                    if (check(EOP)) {
                        advance(); // пропускаем ;
                    }
                }
            }
        } catch (ParseError error) {
            // Ошибка уже залогирована
        }
        return statements;
    }

    private Stmt declaration() {
        try {
            // Проверка на метку
            if (check(LABEL)) {
                return labelStatement();
            }

            // Проверка на описание переменной (идентификатор, за которым может быть , или :)
            if (check(IDENTIFIER)) {
                if (checkNext(COLON) || checkNext(COMMA)) {
                    return varDeclaration();
                }
            }

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt labelStatement() {
        Token label = advance(); // LABEL
        return new Stmt.Label(label);
    }

    private Stmt varDeclaration() {
        List<Token> identifiers = new ArrayList<>();
        identifiers.add(consume(IDENTIFIER, "Expect variable name."));

        while (match(COMMA)) {
            identifiers.add(consume(IDENTIFIER, "Expect variable name after ','."));
        }

        consume(COLON, "Expect ':' after variable name(s).");

        boolean isVector = false;
        Expr size = null;

        if (match(VECTOR)) {
            isVector = true;
            consume(LBRACKET, "Expect '[' after 'vector'.");
            size = expression();
            consume(RBRACKET, "Expect ']' after vector size.");
            consume(OF, "Expect 'of' after vector size.");
        }

        String type;
        if (match(INTEGER)) {
            type = "integer";
        } else if (match(REAL)) {
            type = "real";
        } else {
            throw error(peek(), "Expect type 'integer' or 'real'.");
        }

        // Создаем объявление для первого идентификатора
        // В реальном проекте нужно создать несколько объявлений
        Token name = identifiers.get(0);
        return new Stmt.Var(name, null, isVector, size, type);
    }

    private Stmt statement() {
        if (match(BST)) return blockStatement();
        if (match(IF)) return ifStatement();
        if (match(LOOP)) return loopStatement();
        if (match(GOTO)) return gotoStatement();
        if (match(READ)) return readStatement();
        if (match(WRITE)) return writeStatement();
        if (match(SKIP, SPACE, TAB)) {
            Token spec = previous();
            List<Object> args = new ArrayList<>();
            args.add(spec.type);
            return new Stmt.Write(args);
        }

        // Проверка на присваивание
        if (check(IDENTIFIER)) {
            return assignmentStatement();
        }

        // Пустой оператор (может быть просто ';')
        return new Stmt.Empty();
    }

    private Stmt blockStatement() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(EST) && !isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) {
                statements.add(stmt);
            }
            // Пропускаем ';' если есть
            if (check(EOP)) {
                advance();
            }
        }

        consume(EST, "Expect 'end' after block.");
        return new Stmt.Block(statements);
    }

    private Stmt ifStatement() {
        Expr condition = expression();
        consume(THEN, "Expect 'then' after condition.");
        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt loopStatement() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(EST) && !isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) {
                statements.add(stmt);
            }
            // Пропускаем ';' если есть
            if (check(EOP)) {
                advance();
            }
        }

        consume(EST, "Expect 'end' after loop.");
        return new Stmt.Loop(statements);
    }

    private Stmt gotoStatement() {
        Token label = consume(IDENTIFIER, "Expect label name after 'goto'.");
        return new Stmt.Goto(label);
    }

    private Stmt readStatement() {
        List<Expr> variables = new ArrayList<>();
        variables.add(variable());

        while (match(COMMA)) {
            variables.add(variable());
        }

        return new Stmt.Read(variables);
    }

    private Stmt writeStatement() {
        List<Object> arguments = new ArrayList<>();

        do {
            if (check(SKIP) || check(SPACE) || check(TAB)) {
                arguments.add(advance().type);
            } else {
                arguments.add(expression());
            }
        } while (match(COMMA));

        return new Stmt.Write(arguments);
    }

    private Stmt assignmentStatement() {
        Expr expr = variable();

        if (match(ASS)) {
            if (expr instanceof Expr.Variable) {
                Expr.Variable var = (Expr.Variable) expr;
                Expr value = expression();
                return new Stmt.Expression(new Expr.Assign(var.name, value));
            }
            throw error(peek(), "Invalid assignment target.");
        }

        // Если нет присваивания, это просто выражение
        return new Stmt.Expression(expr);
    }

    private Expr variable() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        if (match(LBRACKET)) {
            Expr index = expression();
            consume(RBRACKET, "Expect ']' after index.");
            // Для упрощения возвращаем просто переменную
            // В реальном проекте нужно создать отдельный тип
        }

        return new Expr.Variable(name);
    }

    private Expr expression() {
        return comparison();
    }

    private Expr comparison() {
        Expr expr = addition();

        while (match(EQ, NE, LT, GT, LE, GE)) {
            Token operator = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr addition() {
        Expr expr = multiplication();

        while (match(PLUS, MIN)) {
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr multiplication() {
        Expr expr = unary();

        while (match(MULT, DIV, MOD)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(MIN)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return term();
    }

    private Expr term() {
        if (match(NUMBER)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LPAREN)) {
            Expr expr = expression();
            consume(RPAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private boolean checkNext(TokenType type) {
        if (current + 1 >= tokens.size()) return false;
        return tokens.get(current + 1).type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Translator.error(token, message);
        hadError = true;
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == EOP) return;

            switch (peek().type) {
                case BST:
                case EST:
                case IF:
                case LOOP:
                case GOTO:
                case READ:
                case WRITE:
                case LABEL:
                    return;
                default:
                    advance();
            }
        }
    }

    boolean hadError() {
        return hadError;
    }
}