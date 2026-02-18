package com.nequma.translator;

import java.util.ArrayList;
import java.util.Collections;
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
        // Если программа начинается с 'begin', парсим её как один блок
        if (check(BST)) {
            Stmt block = blockStatement();
            return List.of(block);
        }
        
        List<Stmt> stmts = new ArrayList<>();
        try {
            while (!isAtEnd()) {
                List<Stmt> decls = declaration();
                stmts.addAll(decls);
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
        // Представляем программу как один блок (begin ... end)
        if (stmts.isEmpty()) {
            return List.of(new Stmt.Block(new ArrayList<>()));
        }
        return List.of(new Stmt.Block(stmts));
    }

    private List<Stmt> declaration() {
        try {
            // Комментарии игнорируются парсером (лексема COMMENT)
            if (check(COMMENT)) {
                advance();
                return Collections.emptyList();
            }
            
            // 'end' не является declaration - это терминатор для блоков/циклов
            if (check(EST)) {
                return Collections.emptyList();
            }

            // Проверка на описание переменной: идентификатор { "," идентификатор } ":" тип
            if (check(IDENTIFIER)) {
                if (checkNext(COMMA) || (checkNext(COLON) && (checkNext(INTEGER, 2) || checkNext(REAL, 2) || checkNext(VECTOR, 2)))) {
                    return varDeclaration();
                }
            }

            // Проверка на метку: идентификатор, за которым следует ':' (не объявление переменной)
            if (check(IDENTIFIER) && checkNext(COLON)) {
                Token labelName = advance(); // IDENTIFIER
                advance(); // COLON
                return List.of(new Stmt.Label(labelName));
            }

            Stmt stmt = statement();
            return stmt != null ? List.of(stmt) : Collections.emptyList();
        } catch (ParseError error) {
            synchronize();
            return Collections.emptyList();
        }
    }
    
    private boolean checkNext(TokenType type, int offset) {
        if (current + offset >= tokens.size()) return false;
        return tokens.get(current + offset).type == type;
    }


    private List<Stmt> varDeclaration() {
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

        // Создаем объявление для каждого идентификатора (EBNF: идентификатор { "," идентификатор } ":"
        List<Stmt> declarations = new ArrayList<>();
        for (Token name : identifiers) {
            declarations.add(new Stmt.Var(name, null, isVector, size, type));
        }
        return declarations;
    }

    private Stmt statement() {
        // 'end' не является statement - это терминатор для блоков/циклов
        if (check(EST)) {
            return null;
        }
        
        if (match(BST)) return blockStatement();
        if (match(IF)) return ifStatement();
        if (match(LOOP)) return loopStatement();
        if (match(GOTO)) return gotoStatement();
        if (match(READ)) return readStatement();
        if (match(WRITE)) return writeStatement();

        // Проверка на присваивание
        if (check(IDENTIFIER)) {
            return assignmentStatement();
        }

        // Пустой оператор (может быть просто ';')
        return new Stmt.Empty();
    }

    private Stmt blockStatement() {
        List<Stmt> stmts = new ArrayList<>();
        int nestingLevel = 0; // Отслеживаем уровень вложенности begin/end и loop/end

        // Если блок вызван из parse() и начинается с 'begin', потребляем его (один 'end' закроет весь блок)
        if (check(BST)) {
            advance();
            nestingLevel = 1;
        }

        while (!isAtEnd()) {
            if (check(EST)) {
                if (nestingLevel == 0) {
                    // Это 'end' для этого блока
                    break;
                } else {
                    // Это 'end' для вложенной конструкции (блок или цикл)
                    nestingLevel--;
                    stmts.addAll(declaration());
                    continue;
                }
            }
            
            if (check(BST)) {
                nestingLevel++;
            } else if (check(LOOP)) {
                nestingLevel++;
                advance(); // Потребляем токен LOOP
                // Парсим цикл (он остановится когда увидит 'end' на уровне вложенности 0)
                Stmt stmt = loopStatement();
                if (stmt != null) {
                    stmts.add(stmt);
                }
                // После парсинга цикла проверяем, не является ли следующий токен 'end'
                // (циклы не имеют собственного 'end', они бесконечные)
                // Цикл должен был остановиться на 'end', поэтому он должен быть следующим токеном
                if (check(EST)) {
                    if (nestingLevel > 0) nestingLevel--;
                    // Выходим - следующий 'end' закроет этот блок
                    break;
                }
                // Если после цикла нет 'end', продолжаем парсинг других statements
                // (это не должно происходить, но на всякий случай)
            }
            
            stmts.addAll(declaration());
            // Пропускаем ';' если есть
            if (check(EOP)) {
                advance();
            }
        }

        if (!check(EST)) {
            throw error(peek(), "Expect 'end' after block.");
        }
        consume(EST, "Expect 'end' after block.");
        return new Stmt.Block(stmts);
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
        List<Stmt> stmts = new ArrayList<>();
        int nestingLevel = 0; // Отслеживаем уровень вложенности begin/end

        // Циклы не требуют 'end' - они бесконечные, выход только через goto к метке
        // Парсим операторы до тех пор, пока не встретим 'end' на уровне вложенности 0
        // (который закроет внешний блок, а не цикл)
        while (!isAtEnd()) {
            // Проверяем 'end' в начале каждой итерации
            if (check(EST)) {
                if (nestingLevel == 0) {
                    // Это 'end' для внешнего блока, не для цикла
                    // Цикл не имеет собственного 'end', поэтому просто останавливаемся
                    // НЕ потребляем токен 'end' - пусть blockStatement обработает его
                    break;
                } else {
                    // Это 'end' для вложенного блока внутри цикла
                    nestingLevel--;
                    advance(); // Потребляем токен 'end' для вложенного блока
                    // Если после этого уровень 0 — тело цикла закончилось, не парсим дальше
                    if (nestingLevel == 0) break;
                    continue;
                }
            }
            
            if (check(BST)) {
                nestingLevel++;
            }
            
            // Перед вызовом declaration() еще раз проверяем 'end'
            // (на случай, если предыдущая итерация пропустила его)
            if (check(EST) && nestingLevel == 0) {
                break;
            }
            
            stmts.addAll(declaration());
            
            // Если declaration() вернул пустой список, это может быть потому что текущий токен - 'end'
            // Проверяем это и останавливаемся если nestingLevel == 0
            if (check(EST) && nestingLevel == 0) {
                break;
            }
            
            // Пропускаем ';' если есть
            if (check(EOP)) {
                advance();
            }
            
            // После ';' также проверяем 'end'
            if (check(EST) && nestingLevel == 0) {
                break;
            }
        }

        // Тело цикла представляется как блок операторов
        // Цикл бесконечный - выход только через goto к метке
        Stmt.Block body = new Stmt.Block(stmts);
        return new Stmt.Loop(body);
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

        // В учебном языке нет операторов из одиночных выражений без присваивания
        throw error(previous(), "Expect ':=' after variable in assignment statement.");
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
                case COMMENT:
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