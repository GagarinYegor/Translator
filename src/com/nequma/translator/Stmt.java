package com.nequma.translator;

import java.util.List;

abstract class Stmt {
    interface Visitor<R> {
        R visitBlockStmt(Block stmt);
        R visitExpressionStmt(Expression stmt);
        R visitIfStmt(If stmt);
        R visitWriteStmt(Write stmt);
        R visitReadStmt(Read stmt);
        R visitVarStmt(Var stmt);
        R visitLoopStmt(Loop stmt);
        R visitGotoStmt(Goto stmt);
        R visitLabelStmt(Label stmt);
        R visitEmptyStmt(Empty stmt);
    }

    static class Block extends Stmt {
        Block(List<Stmt> statements) {
            this.statements = statements;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }

        final List<Stmt> statements;
    }

    static class Expression extends Stmt {
        Expression(Expr expression) {
            this.expression = expression;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }

        final Expr expression;
    }

    static class If extends Stmt {
        If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStmt(this);
        }

        final Expr condition;
        final Stmt thenBranch;
        final Stmt elseBranch;
    }

    static class Write extends Stmt {
        Write(List<Object> arguments) {
            this.arguments = arguments;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitWriteStmt(this);
        }

        final List<Object> arguments;
    }

    static class Read extends Stmt {
        Read(List<Expr> variables) {
            this.variables = variables;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitReadStmt(this);
        }

        final List<Expr> variables;
    }

    static class Var extends Stmt {
        Var(Token name, Expr initializer, boolean isVector, Expr size, String type) {
            this.name = name;
            this.initializer = initializer;
            this.isVector = isVector;
            this.size = size;
            this.type = type;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitVarStmt(this);
        }

        final Token name;
        final Expr initializer;
        final boolean isVector;
        final Expr size;
        final String type;
    }

    static class Loop extends Stmt {
        Loop(List<Stmt> statements) {
            this.statements = statements;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitLoopStmt(this);
        }

        final List<Stmt> statements;
    }

    static class Goto extends Stmt {
        Goto(Token label) {
            this.label = label;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitGotoStmt(this);
        }

        final Token label;
    }

    static class Label extends Stmt {
        Label(Token name) {
            this.name = name;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitLabelStmt(this);
        }

        final Token name;
    }

    static class Empty extends Stmt {
        Empty() {}

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitEmptyStmt(this);
        }
    }

    abstract <R> R accept(Visitor<R> visitor);
}