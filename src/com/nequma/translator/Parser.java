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
        List<Stmt> stmts = new ArrayList<>();

        try {
            while (!isAtEnd()) {
                // Skip comments
                while (check(COMMENT)) {
                    advance();
                }

                if (isAtEnd()) break;

                // Parse a declaration or statement
                List<Stmt> decls = declaration();
                if (decls != null && !decls.isEmpty()) {
                    stmts.addAll(decls);
                }

                // После каждого оператора должна быть ';' согласно EBNF
                if (!isAtEnd() && !check(EOF)) {
                    // Проверяем, не находимся ли мы в конце программы
                    if (check(EST)) {
                        // Если мы видим 'end', это значит, что мы в конце блока
                        // Точка с запятой будет требоваться после выхода из блока
                        break;
                    }

                    if (!check(EOP)) {
                        throw error(peek(), "Expected ';' after statement");
                    }
                    advance(); // consume ';'
                }
            }
        } catch (ParseError error) {
            // Error already logged
        }

        // Check for extra tokens after program end
        if (!isAtEnd() && !check(EOF) && !check(EST)) {
            error(peek(), "Unexpected token after program end");
        }

        // Wrap the entire program in a Block statement
        return Collections.singletonList(new Stmt.Block(stmts));
    }

    private List<Stmt> declaration() {
        try {
            // Skip comments
            while (check(COMMENT)) {
                advance();
            }

            if (check(EST)) {
                return null;
            }

            // Handle labels: identifier followed by colon
            if (check(IDENTIFIER) && checkNext(COLON)) {
                Token labelName = advance();
                advance(); // consume ':'
                List<Stmt> bodyList = declaration(); // Parse the statement that follows the label
                Stmt body = (bodyList != null && !bodyList.isEmpty()) ? bodyList.get(0) : new Stmt.Empty();
                return Collections.singletonList(new Stmt.Label(labelName, body));
            }

            // Check for variable declaration - pattern: identifier { "," identifier } ":" type
            if (check(IDENTIFIER)) {
                // Check if this identifier is actually a keyword
                if (isKeyword(peek().lexeme)) {
                    // It's a keyword, so it must be a statement, not a declaration
                    Stmt stmt = statement();
                    return stmt != null ? Collections.singletonList(stmt) : null;
                }

                // Check if next token indicates a variable declaration
                if (checkNext(COMMA) || checkNext(COLON)) {
                    return varDeclaration();
                }
            }

            // If we get here, it should be a statement
            if (canStartStatement()) {
                Stmt stmt = statement();
                return stmt != null ? Collections.singletonList(stmt) : null;
            }

            // If we can't recognize anything, skip this token
            if (!isAtEnd()) {
                advance();
            }
            return null;
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private List<Stmt> varDeclaration() {
        // Make sure we're actually at a variable declaration
        if (!check(IDENTIFIER)) {
            throw error(peek(), "Expected identifier for variable declaration");
        }

        // Double-check that this identifier is not a keyword
        if (isKeyword(peek().lexeme)) {
            throw error(peek(), "Keyword '" + peek().lexeme + "' cannot be used as variable name");
        }

        List<Token> identifiers = new ArrayList<>();
        identifiers.add(consume(IDENTIFIER, "Expected variable name"));

        while (match(COMMA)) {
            // After comma, we must have another identifier
            if (!check(IDENTIFIER)) {
                throw error(peek(), "Expected variable name after ','");
            }
            // Check that it's not a keyword
            if (isKeyword(peek().lexeme)) {
                throw error(peek(), "Keyword '" + peek().lexeme + "' cannot be used as variable name");
            }
            identifiers.add(consume(IDENTIFIER, "Expected variable name after ','"));
        }

        // Must have colon before type
        consume(COLON, "Expected ':' after variable names");

        // Check for vector declaration
        boolean isVector = false;
        Expr size = null;

        if (match(VECTOR)) {
            isVector = true;
            consume(LBRACKET, "Expected '[' after 'vector'");
            size = expression();
            consume(RBRACKET, "Expected ']' after vector size");
            consume(OF, "Expected 'of' after vector size");
        }

        // Parse type - must be integer or real
        String type;
        if (match(INTEGER)) {
            type = "integer";
        } else if (match(REAL)) {
            type = "real";
        } else {
            throw error(peek(), "Expected type 'integer' or 'real' in variable declaration");
        }

        // Create Var statements for each identifier
        List<Stmt> declarations = new ArrayList<>();
        for (Token name : identifiers) {
            declarations.add(new Stmt.Var(name, null, isVector, size, type));
        }

        return declarations;
    }

    private boolean isKeyword(String lexeme) {
        String lower = lexeme.toLowerCase();
        return lower.equals("if") ||
                lower.equals("loop") ||
                lower.equals("goto") ||
                lower.equals("read") ||
                lower.equals("write") ||
                lower.equals("begin") ||
                lower.equals("end") ||
                lower.equals("integer") ||
                lower.equals("real") ||
                lower.equals("vector") ||
                lower.equals("of") ||
                lower.equals("then") ||
                lower.equals("else") ||
                lower.equals("skip") ||
                lower.equals("space") ||
                lower.equals("tab") ||
                lower.equals("mod");
    }

    /** Tokens that can start a statement */
    private boolean canStartStatement() {
        if (isAtEnd()) return false;

        TokenType type = peek().type;
        switch (type) {
            case BST:      // begin
            case IF:
            case LOOP:
            case GOTO:
            case READ:
            case WRITE:
            case IDENTIFIER:
                return true;
            default:
                return false;
        }
    }

    private Stmt statement() {
        if (match(BST)) return blockStatement();
        if (match(IF)) return ifStatement();
        if (match(LOOP)) return loopStatement();
        if (match(GOTO)) return gotoStatement();
        if (match(READ)) return readStatement();
        if (match(WRITE)) return writeStatement();

        // Check for assignment statement
        if (check(IDENTIFIER)) {
            return assignmentStatement();
        }

        // Empty statement
        return new Stmt.Empty();
    }

    private Stmt blockStatement() {
        List<Stmt> stmts = new ArrayList<>();

        // Parse statements inside block until matching 'end'
        while (!isAtEnd() && !check(EST)) {
            // Skip comments
            while (check(COMMENT)) {
                advance();
            }

            if (check(EST)) break;

            // Parse a declaration or statement
            List<Stmt> decls = declaration();
            if (decls != null) {
                stmts.addAll(decls);
            }

            // После каждого оператора внутри блока должна быть ';'
            // Единственное исключение - если мы дошли до конца блока
            if (!check(EST)) {
                if (!check(EOP)) {
                    throw error(peek(), "Expected ';' after statement in block");
                }
                advance(); // consume ';'
            }
        }

        // Check for missing 'end'
        if (!check(EST)) {
            throw error(peek(), "Expected 'end' to close block");
        }

        consume(EST, "Expected 'end' to close block");

        // ВАЖНО: После закрытия блока мы НЕ требуем ';' здесь,
        // потому что blockStatement() вызывается из statement(),
        // и ';' будет требоваться в вызывающем контексте

        return new Stmt.Block(stmts);
    }

    private Stmt ifStatement() {
        Expr condition = expression();
        consume(THEN, "Expected 'then' after condition");
        Stmt thenBranch = statement();
        Stmt elseBranch = null;

        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt loopStatement() {
        List<Stmt> stmts = new ArrayList<>();

        // Parse loop body: { оператор ";" } end
        while (!isAtEnd() && !check(EST)) {
            // Skip comments
            while (check(COMMENT)) {
                advance();
            }

            if (check(EST)) break;

            // Parse a single statement
            List<Stmt> decls = declaration();
            if (decls != null) {
                stmts.addAll(decls);
            }

            // После каждого оператора в теле цикла должна быть ';'
            // Единственное исключение - если мы дошли до конца цикла
            if (!check(EST)) {
                if (!check(EOP)) {
                    throw error(peek(), "Expected ';' after statement in loop body");
                }
                advance(); // consume ';'
            }
        }

        // Check for missing 'end'
        if (!check(EST)) {
            throw error(peek(), "Expected 'end' to close loop");
        }

        consume(EST, "Expected 'end' to close loop");

        // ВАЖНО: После закрытия цикла мы НЕ требуем ';' здесь,
        // потому что loopStatement() вызывается из statement(),
        // и ';' будет требоваться в вызывающем контексте

        return new Stmt.Loop(new Stmt.Block(stmts));
    }

    private Stmt gotoStatement() {
        Token label = consume(IDENTIFIER, "Expected label name after 'goto'");
        return new Stmt.Goto(label);
    }

    private Stmt readStatement() {
        List<Expr> variables = new ArrayList<>();

        // First variable
        if (!check(IDENTIFIER)) {
            throw error(peek(), "Expected variable name after 'read'");
        }
        variables.add(variable());

        while (match(COMMA)) {
            if (!check(IDENTIFIER)) {
                throw error(peek(), "Expected variable name after ','");
            }
            variables.add(variable());
        }

        return new Stmt.Read(variables);
    }

    private Stmt writeStatement() {
        List<Object> arguments = new ArrayList<>();

        // First argument
        if (check(SKIP) || check(SPACE) || check(TAB)) {
            arguments.add(advance().type);
        } else {
            arguments.add(expression());
        }

        while (match(COMMA)) {
            if (check(SKIP) || check(SPACE) || check(TAB)) {
                arguments.add(advance().type);
            } else {
                arguments.add(expression());
            }
        }

        return new Stmt.Write(arguments);
    }

    private Stmt assignmentStatement() {
        Expr expr = variable();

        if (match(ASS)) {
            if (!(expr instanceof Expr.Variable)) {
                throw error(previous(), "Invalid assignment target");
            }
            Expr.Variable var = (Expr.Variable) expr;
            Expr value = expression();
            return new Stmt.Expression(new Expr.Assign(var.name, value));
        }

        throw error(peek(), "Expected ':=' in assignment statement");
    }

    private Expr variable() {
        Token name = consume(IDENTIFIER, "Expected variable name");

        // Handle array indexing if present
        if (match(LBRACKET)) {
            Expr index = expression();
            consume(RBRACKET, "Expected ']' after index");
            // For now, just return the variable name
            // In a full implementation, you'd create an ArrayAccess expression
        }

        return new Expr.Variable(name);
    }

    // Expression parsing methods
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
            consume(RPAREN, "Expected ')' after expression");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expected expression");
    }

    // Helper methods
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

    private boolean checkNext(TokenType type, int offset) {
        if (current + offset >= tokens.size()) return false;
        return tokens.get(current + offset).type == type;
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
                case COMMENT:
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