package com.nequma.translator;

import java.util.List;

abstract class Stmt {
    interface Visitor<R> {
        R visitBlockStmt(Block stmt); //Составной
        R visitVarStmt(Var stmt); //Присваивания
        R visitGotoStmt(Goto stmt); //Перехода
        R visitIfStmt(If stmt); //Условный
        R visitLoopStmt(Loop stmt); //Цикла
        R visitEmptyStmt(Empty stmt); //Пустой
        R visitReadStmt(Read stmt); //Ввода
        R visitWriteStmt(Write stmt); //Вывода
        R visitExpressionStmt(Expression stmt);
        R visitLabelStmt(Label stmt);
    }

    static class Block extends Stmt {
        Block(List<Stmt> stmts) {
            this.stmts = stmts;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }

        final List<Stmt> stmts;
    }

    static class Expression extends Stmt {
        Expression(Expr expr) {
            this.expr = expr;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }

        final Expr expr;
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
        Loop(Block body) {
            this.body = body;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitLoopStmt(this);
        }

        final Block body;
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
        /** EBNF: оператор = [ метка ] непомеченный — метка может сопровождать следующий оператор */
        Label(Token name, Stmt body) {
            this.name = name;
            this.body = body;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitLabelStmt(this);
        }

        final Token name;
        /** Непомеченный оператор после метки (null только при ошибке восстановления). */
        final Stmt body;
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