package com.nequma.translator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private Environment environment = new Environment();
    private Map<String, Integer> labels = new HashMap<>();
    private int currentStatementIndex = 0;
    private List<Stmt> statements;
    private boolean gotoJump = false;
    private Scanner inputScanner = new Scanner(System.in);

    void interpret(List<Stmt> statements) {
        // Если есть только один Block на верхнем уровне, используем его statements
        if (statements.size() == 1 && statements.get(0) instanceof Stmt.Block) {
            this.statements = ((Stmt.Block) statements.get(0)).statements;
        } else {
            this.statements = statements;
        }

        // Первый проход: сбор меток
        for (int i = 0; i < this.statements.size(); i++) {
            Stmt stmt = this.statements.get(i);
            if (stmt instanceof Stmt.Label) {
                Stmt.Label labelStmt = (Stmt.Label) stmt;
                labels.put(labelStmt.name.lexeme, i);
            }
        }

        // Второй проход: выполнение
        try {
            while (currentStatementIndex < this.statements.size()) {
                Stmt statement = this.statements.get(currentStatementIndex);
                gotoJump = false;
                execute(statement);
                if (!gotoJump) {
                    currentStatementIndex++;
                }
            }
        } catch (RuntimeError error) {
            Translator.runtimeError(error);
        }
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        return null;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        if (expr.operator.type == TokenType.MIN) {
            checkNumberOperand(expr.operator, right);
            if (right instanceof Integer) {
                return -(int) right;
            } else if (right instanceof Double) {
                return -(double) right;
            }
        }

        return right;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case PLUS:
                if (left instanceof Number && right instanceof Number) {
                    if (left instanceof Double || right instanceof Double) {
                        return ((Number) left).doubleValue() + ((Number) right).doubleValue();
                    }
                    return ((Number) left).intValue() + ((Number) right).intValue();
                }
                throw new RuntimeError(expr.operator, "Operands must be numbers.");

            case MIN:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Double || right instanceof Double) {
                    return ((Number) left).doubleValue() - ((Number) right).doubleValue();
                }
                return ((Number) left).intValue() - ((Number) right).intValue();

            case MULT:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Double || right instanceof Double) {
                    return ((Number) left).doubleValue() * ((Number) right).doubleValue();
                }
                return ((Number) left).intValue() * ((Number) right).intValue();

            case DIV:
                checkNumberOperands(expr.operator, left, right);
                if (((Number) right).doubleValue() == 0) {
                    throw new RuntimeError(expr.operator, "Division by zero.");
                }
                if (left instanceof Double || right instanceof Double) {
                    return ((Number) left).doubleValue() / ((Number) right).doubleValue();
                }
                return ((Number) left).intValue() / ((Number) right).intValue();

            case MOD:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left % (int) right;
                }
                throw new RuntimeError(expr.operator, "Modulo requires integer operands.");

            case EQ:
                return isEqual(left, right);

            case NE:
                return !isEqual(left, right);

            case LT:
                checkNumberOperands(expr.operator, left, right);
                return ((Number) left).doubleValue() < ((Number) right).doubleValue();

            case GT:
                checkNumberOperands(expr.operator, left, right);
                return ((Number) left).doubleValue() > ((Number) right).doubleValue();

            case LE:
                checkNumberOperands(expr.operator, left, right);
                return ((Number) left).doubleValue() <= ((Number) right).doubleValue();

            case GE:
                checkNumberOperands(expr.operator, left, right);
                return ((Number) left).doubleValue() >= ((Number) right).doubleValue();

            default:
                return null;
        }
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Number) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Number && right instanceof Number) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean) object;
        if (object instanceof Number) {
            if (object instanceof Integer) return (int) object != 0;
            if (object instanceof Double) return (double) object != 0.0;
        }
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "nil";
        return object.toString();
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;

        try {
            this.environment = environment;
            for (Stmt statement : statements) {
                if (gotoJump) break;
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        boolean condition = isTruthy(evaluate(stmt.condition));
        if (condition) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWriteStmt(Stmt.Write stmt) {
        for (Object arg : stmt.arguments) {
            if (arg instanceof TokenType) {
                TokenType type = (TokenType) arg;
                switch (type) {
                    case SPACE:
                        System.out.print(" ");
                        break;
                    case TAB:
                        System.out.print("\t");
                        break;
                    case SKIP:
                        // ничего не выводим
                        break;
                    default:
                        break;
                }
            } else {
                Expr expr = (Expr) arg;
                Object value = evaluate(expr);
                System.out.print(stringify(value));
            }
        }
        System.out.println();
        return null;
    }

    @Override
    public Void visitReadStmt(Stmt.Read stmt) {
        for (Expr var : stmt.variables) {
            if (var instanceof Expr.Variable) {
                Expr.Variable variable = (Expr.Variable) var;
                System.out.print("Enter value for " + variable.name.lexeme + ": ");
                String input = inputScanner.nextLine();
                try {
                    if (input.contains(".")) {
                        environment.assign(variable.name, Double.parseDouble(input));
                    } else {
                        environment.assign(variable.name, Integer.parseInt(input));
                    }
                } catch (NumberFormatException e) {
                    environment.assign(variable.name, input);
                }
            }
        }
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        } else {
            if ("integer".equals(stmt.type)) {
                value = 0;
            } else if ("real".equals(stmt.type)) {
                value = 0.0;
            }
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitLoopStmt(Stmt.Loop stmt) {
        while (true) {
            // Выполняем тело цикла
            for (Stmt statement : stmt.statements) {
                if (gotoJump) {
                    // Goto прыгнул за пределы цикла
                    return null;
                }
                execute(statement);
            }

            if (gotoJump) {
                // Goto прыгнул за пределы цикла
                return null;
            }

            // Продолжаем цикл
        }
    }

    @Override
    public Void visitGotoStmt(Stmt.Goto stmt) {
        Integer targetIndex = labels.get(stmt.label.lexeme);
        if (targetIndex == null) {
            throw new RuntimeError(stmt.label, "Undefined label: " + stmt.label.lexeme);
        }
        currentStatementIndex = targetIndex;
        gotoJump = true;
        return null;
    }

    @Override
    public Void visitLabelStmt(Stmt.Label stmt) {
        // Метки просто пропускаем (ничего не делаем)
        return null;
    }

    @Override
    public Void visitEmptyStmt(Stmt.Empty stmt) {
        // Пустой оператор ничего не делает
        return null;
    }
}