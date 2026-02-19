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

    // EBNF: программа = { ( описание | оператор ) ";" } конец_файла.
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
                List<Stmt> decls = declarationOrStatement();
                if (decls != null && !decls.isEmpty()) {
                    stmts.addAll(decls);
                }

                // After each declaration or statement, there must be ';'
                if (!isAtEnd() && !check(EOF)) {
                    if (!check(EOP)) {
                        throw error(peek(), "Expected ';' after declaration or statement");
                    }
                    advance(); // consume ';'
                }
            }
        } catch (ParseError error) {
            // Error already logged
        }

        return stmts;
    }

    // EBNF: описание | оператор
    private List<Stmt> declarationOrStatement() {
        // Skip comments
        while (check(COMMENT)) {
            advance();
        }

        if (isAtEnd()) return null;

        // Check for variable declaration - pattern: identifier { "," identifier } ":" type
        // Must distinguish from assignment: identifier ":=" expression
        if (check(IDENTIFIER) && !isKeyword(peek().lexeme)) {
            // Simple heuristic: if identifier is followed by comma, it's a declaration
            // (assignments use := not comma)
            int checkPos = current + 1;
            // Skip comments
            while (checkPos < tokens.size() && tokens.get(checkPos).type == COMMENT) {
                checkPos++;
            }
            
            // If next token after comments is comma, it's definitely a declaration
            if (checkPos < tokens.size() && tokens.get(checkPos).type == COMMA) {
                return varDeclaration();
            }
            
            // Otherwise, use full lookahead to check pattern: identifier { "," identifier } ":" type
            int lookaheadPos = current;
            boolean isDeclaration = false;
            
            if (lookaheadPos < tokens.size() && tokens.get(lookaheadPos).type == IDENTIFIER) {
                lookaheadPos++; // skip first identifier
                
                // Skip identifiers and commas: identifier { "," identifier }
                while (lookaheadPos < tokens.size()) {
                    // Skip comments
                    while (lookaheadPos < tokens.size() && tokens.get(lookaheadPos).type == COMMENT) {
                        lookaheadPos++;
                    }
                    
                    if (lookaheadPos >= tokens.size()) break;
                    
                    // Check if next is comma
                    if (tokens.get(lookaheadPos).type == COMMA) {
                        lookaheadPos++; // skip comma
                        // Skip comments after comma
                        while (lookaheadPos < tokens.size() && tokens.get(lookaheadPos).type == COMMENT) {
                            lookaheadPos++;
                        }
                        // Should be identifier after comma
                        if (lookaheadPos >= tokens.size() || tokens.get(lookaheadPos).type != IDENTIFIER) {
                            break; // Not a declaration pattern
                        }
                        lookaheadPos++; // skip identifier
                    } else {
                        break; // No more commas
                    }
                }
                
                // Skip comments before colon
                while (lookaheadPos < tokens.size() && tokens.get(lookaheadPos).type == COMMENT) {
                    lookaheadPos++;
                }
                
                // Now check if we have COLON followed by type keyword
                if (lookaheadPos < tokens.size() && tokens.get(lookaheadPos).type == COLON) {
                    lookaheadPos++; // skip colon
                    
                    // Skip comments after colon
                    while (lookaheadPos < tokens.size() && tokens.get(lookaheadPos).type == COMMENT) {
                        lookaheadPos++;
                    }
                    
                    // Check if next token is a type keyword
                    if (lookaheadPos < tokens.size()) {
                        TokenType nextType = tokens.get(lookaheadPos).type;
                        if (nextType == INTEGER || nextType == REAL) {
                            isDeclaration = true;
                        } else if (nextType == VECTOR) {
                            // Could be vector declaration
                            lookaheadPos++;
                            while (lookaheadPos < tokens.size() && tokens.get(lookaheadPos).type == COMMENT) {
                                lookaheadPos++;
                            }
                            if (lookaheadPos < tokens.size() && tokens.get(lookaheadPos).type == LBRACKET) {
                                isDeclaration = true;
                            }
                        }
                    }
                }
            }
            
            // If confirmed as declaration pattern, parse as declaration
            if (isDeclaration) {
                return varDeclaration();
            }
        }

        // Otherwise, parse as statement
        Stmt stmt = statement();
        return stmt != null ? Collections.singletonList(stmt) : null;
    }

    // EBNF: оператор = [ метка ] непомеченный.
    private Stmt statement() {
        // Skip comments
        while (check(COMMENT)) {
            advance();
        }

        if (isAtEnd()) return null;

        // Check for label: имя_метки ":"
        // Must check if identifier is followed by colon (skipping comments)
        Token label = null;
        if (check(IDENTIFIER)) {
            // Look ahead to see if colon follows (skipping comments)
            int checkPos = current + 1;
            while (checkPos < tokens.size() && tokens.get(checkPos).type == COMMENT) {
                checkPos++;
            }
            if (checkPos < tokens.size() && tokens.get(checkPos).type == COLON) {
                label = advance();
                advance(); // consume ':'
                // Skip any comments after colon
                while (check(COMMENT)) {
                    advance();
                }
            }
        }

        // Parse unlabeled statement
        Stmt unlabeled = unlabeledStatement();
        
        if (unlabeled == null) {
            return null;
        }

        // Wrap in label if present
        if (label != null) {
            return new Stmt.Label(label, unlabeled);
        }

        return unlabeled;
    }

    // EBNF: непомеченный = составной | присваивание | перехода | условный | цикла | пустой | ввода | вывода.
    private Stmt unlabeledStatement() {
        // Skip comments
        while (check(COMMENT)) {
            advance();
        }

        if (isAtEnd()) return null;

        if (match(BST)) return compoundStatement(); // составной
        if (match(GOTO)) return gotoStatement();     // перехода
        if (match(IF)) return ifStatement();         // условный
        if (match(LOOP)) return loopStatement();     // цикла
        if (match(READ)) return readStatement();     // ввода
        if (match(WRITE)) return writeStatement();   // вывода

        // Check for assignment
        if (check(IDENTIFIER)) {
            return assignmentStatement(); // присваивание
        }

        // Empty statement
        return new Stmt.Empty(); // пустой
    }

    // EBNF: составной = BST { оператор ";" } EST.
    private Stmt compoundStatement() {
        List<Stmt> stmts = new ArrayList<>();

        // Parse statements inside block: { оператор ";" }
        // Note: оператор can be declaration or statement according to EBNF
        while (!isAtEnd() && !check(EST)) {
            // Skip comments
            while (check(COMMENT)) {
                advance();
            }

            if (check(EST)) break;

            // Parse a declaration or statement
            List<Stmt> decls = declarationOrStatement();
            if (decls != null && !decls.isEmpty()) {
                stmts.addAll(decls);
            } else {
                break;
            }

            // After each statement, there must be ';'
            if (!check(EST)) {
                if (!check(EOP)) {
                    throw error(peek(), "Expected ';' after statement in compound statement");
                }
                advance(); // consume ';'
            }
        }

        // Must have EST (end)
        if (!check(EST)) {
            throw error(peek(), "Expected 'end' to close compound statement");
        }

        consume(EST, "Expected 'end' to close compound statement");
        // Note: No ';' after EST according to EBNF

        return new Stmt.Block(stmts);
    }

    // EBNF: цикла = loop { оператор ";" } end.
    private Stmt loopStatement() {
        List<Stmt> stmts = new ArrayList<>();

        // Parse loop body: { оператор ";" }
        // Note: оператор can be declaration or statement according to EBNF
        while (!isAtEnd() && !check(EST)) {
            // Skip comments
            while (check(COMMENT)) {
                advance();
            }

            if (check(EST)) break;

            // Parse a declaration or statement (which can be a compound statement/block)
            List<Stmt> decls = declarationOrStatement();
            if (decls != null && !decls.isEmpty()) {
                stmts.addAll(decls);
            } else {
                break;
            }

            // After each statement, there must be ';'
            if (!check(EST)) {
                if (!check(EOP)) {
                    throw error(peek(), "Expected ';' after statement in loop body");
                }
                advance(); // consume ';'
            }
        }

        // Must have EST (end)
        if (!check(EST)) {
            throw error(peek(), "Expected 'end' to close loop");
        }

        consume(EST, "Expected 'end' to close loop");
        // Note: No ';' after EST according to EBNF

        // EBNF: цикла = loop { оператор ";" } end.
        // Loop body is a sequence of statements. If there's only one statement and it's a Block,
        // use it directly to avoid unnecessary nesting. Otherwise, wrap in a Block.
        Stmt.Block loopBody;
        if (stmts.size() == 1 && stmts.get(0) instanceof Stmt.Block) {
            // Single compound statement - use it directly
            loopBody = (Stmt.Block) stmts.get(0);
        } else {
            // Multiple statements or non-block statement - wrap in Block
            loopBody = new Stmt.Block(stmts);
        }
        
        return new Stmt.Loop(loopBody);
    }

    // EBNF: условный = if выражение then непомеченный [ else непомеченный ].
    private Stmt ifStatement() {
        Expr condition = expression();
        consume(THEN, "Expected 'then' after condition");
        Stmt thenBranch = unlabeledStatement();
        
        if (thenBranch == null) {
            throw error(peek(), "Expected statement after 'then'");
        }

        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = unlabeledStatement();
            if (elseBranch == null) {
                throw error(peek(), "Expected statement after 'else'");
            }
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    // EBNF: перехода = goto имя_метки.
    private Stmt gotoStatement() {
        Token label = consume(IDENTIFIER, "Expected label name after 'goto'");
        return new Stmt.Goto(label);
    }

    // EBNF: ввода = read переменная { "," переменная }.
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

    // EBNF: вывода = write ( выражение | спецификатор ) { "," ( выражение | спецификатор ) }.
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

    // EBNF: присваивание = переменная ASS выражение.
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

    // EBNF: описание = идентификатор { "," идентификатор } ":" [ vector "[" целое "]" of ] тип.
    private List<Stmt> varDeclaration() {
        // Skip comments
        while (check(COMMENT)) {
            advance();
        }
        
        List<Token> identifiers = new ArrayList<>();
        identifiers.add(consume(IDENTIFIER, "Expected variable name"));

        while (match(COMMA)) {
            // Skip comments after comma
            while (check(COMMENT)) {
                advance();
            }
            
            if (!check(IDENTIFIER)) {
                throw error(peek(), "Expected variable name after ','");
            }
            if (isKeyword(peek().lexeme)) {
                throw error(peek(), "Keyword '" + peek().lexeme + "' cannot be used as variable name");
            }
            identifiers.add(consume(IDENTIFIER, "Expected variable name after ','"));
        }

        // Skip comments before colon
        while (check(COMMENT)) {
            advance();
        }

        // Must have colon before type
        consume(COLON, "Expected ':' after variable names");
        
        // Skip comments after colon
        while (check(COMMENT)) {
            advance();
        }

        // Check for vector declaration
        boolean isVector = false;
        Expr size = null;

        if (match(VECTOR)) {
            isVector = true;
            consume(LBRACKET, "Expected '[' after 'vector'");
            size = expression(); // EBNF says целое, but we'll parse as expression
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

    // EBNF: переменная = идентификатор [ "[" индекс "]" ].
    // EBNF: индекс = идентификатор | целое.
    private Expr variable() {
        Token name = consume(IDENTIFIER, "Expected variable name");

        // Handle array indexing if present
        if (match(LBRACKET)) {
            Expr index = expression(); // Can be identifier or integer
            consume(RBRACKET, "Expected ']' after index");
            // For now, just return the variable name
            // In a full implementation, you'd create an ArrayAccess expression
        }

        return new Expr.Variable(name);
    }

    // Expression parsing methods following EBNF
    // EBNF: выражение = слагаемое { (EQ | NE | LT | GT | LE | GE) слагаемое }.
    private Expr expression() {
        Expr expr = addition();

        while (match(EQ, NE, LT, GT, LE, GE)) {
            Token operator = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // EBNF: слагаемое = множитель { (PLUS | MIN) множитель }.
    private Expr addition() {
        Expr expr = multiplication();

        while (match(PLUS, MIN)) {
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // EBNF: множитель = унарное { (MULT | DIV | MOD) унарное }.
    private Expr multiplication() {
        Expr expr = unary();

        while (match(MULT, DIV, MOD)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // EBNF: унарное = [ MIN ] терм.
    private Expr unary() {
        if (match(MIN)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return term();
    }

    // EBNF: терм = переменная | число | "(" выражение ")".
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
        return checkNext(type, 0);
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

    boolean hadError() {
        return hadError;
    }
}
