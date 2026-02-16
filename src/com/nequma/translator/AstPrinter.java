package com.nequma.translator;

class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {
    String print(Expr expr) {
        return expr.accept(this);
    }

    String print(Stmt stmt) {
        return stmt.accept(this);
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return parenthesize("= " + expr.name.lexeme, expr.value);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return expr.name.lexeme;
    }

    @Override
    public String visitBlockStmt(Stmt.Block stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append("(block");
        for (Stmt s : stmt.statements) {
            builder.append("\n  ");
            builder.append(s.accept(this).replace("\n", "\n  "));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitExpressionStmt(Stmt.Expression stmt) {
        return parenthesize("expr", stmt.expression);
    }

    @Override
    public String visitIfStmt(Stmt.If stmt) {
        if (stmt.elseBranch == null) {
            return parenthesize("if", stmt.condition, stmt.thenBranch);
        }
        return parenthesize("if-else", stmt.condition, stmt.thenBranch, stmt.elseBranch);
    }

    @Override
    public String visitWriteStmt(Stmt.Write stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append("(write");
        for (Object arg : stmt.arguments) {
            builder.append(" ");
            if (arg instanceof Expr) {
                builder.append(((Expr) arg).accept(this));
            } else {
                builder.append(arg.toString());
            }
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitReadStmt(Stmt.Read stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append("(read");
        for (Expr var : stmt.variables) {
            builder.append(" ");
            builder.append(var.accept(this));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitVarStmt(Stmt.Var stmt) {
        if (stmt.initializer == null) {
            return parenthesize("var", stmt.name.lexeme + ":" + stmt.type);
        }
        return parenthesize("var", stmt.name.lexeme + ":" + stmt.type, stmt.initializer);
    }

    @Override
    public String visitLoopStmt(Stmt.Loop stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append("(loop");
        for (Stmt s : stmt.statements) {
            builder.append("\n  ");
            builder.append(s.accept(this).replace("\n", "\n  "));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitGotoStmt(Stmt.Goto stmt) {
        return parenthesize("goto", stmt.label.lexeme);
    }

    @Override
    public String visitLabelStmt(Stmt.Label stmt) {
        return parenthesize("label", stmt.name.lexeme);
    }

    @Override
    public String visitEmptyStmt(Stmt.Empty stmt) {
        return "(empty)";
    }

    private String parenthesize(String name, Expr... exprs) {
        StringBuilder builder = new StringBuilder();
        builder.append("(").append(name);
        for (Expr expr : exprs) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");
        return builder.toString();
    }

    private String parenthesize(String name, String... strings) {
        StringBuilder builder = new StringBuilder();
        builder.append("(").append(name);
        for (String s : strings) {
            builder.append(" ");
            builder.append(s);
        }
        builder.append(")");
        return builder.toString();
    }

    private String parenthesize(String name, Object... parts) {
        StringBuilder builder = new StringBuilder();
        builder.append("(").append(name);
        for (Object part : parts) {
            builder.append(" ");
            if (part instanceof Expr) {
                builder.append(((Expr) part).accept(this));
            } else if (part instanceof Stmt) {
                builder.append(((Stmt) part).accept(this));
            } else {
                builder.append(part.toString());
            }
        }
        builder.append(")");
        return builder.toString();
    }
}