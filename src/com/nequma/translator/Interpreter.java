package com.nequma.translator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private static final class Frame {
        final List<Stmt> stmts;
        int index;
        final boolean isLoop;

        Frame(List<Stmt> stmts, int index, boolean isLoop) {
            this.stmts = stmts;
            this.index = index;
            this.isLoop = isLoop;
        }

        Frame copy() {
            return new Frame(stmts, index, isLoop);
        }
    }

    private Environment environment = new Environment();
    /** Labels to full stack (path into nested blocks/loops). */
    private Map<String, List<Frame>> labels = new HashMap<>();
    private List<Frame> stack = new ArrayList<>();
    private boolean gotoJump = false;
    private String gotoTargetLabel = null;
    private Token gotoToken = null;
    private Scanner inputScanner = new Scanner(System.in);

    void interpret(List<Stmt> stmts) {
        if (stmts.size() != 1 || !(stmts.get(0) instanceof Stmt.Block)) {
            throw new RuntimeError(null, "Program must be represented as a single Block statement.");
        }
        Stmt.Block programBlock = (Stmt.Block) stmts.get(0);
        List<Stmt> programStmts = programBlock.stmts;

        // Collect all labels from the entire AST (including nested blocks and loop bodies)
        collectLabels(programStmts, new ArrayList<>(), programStmts, false);

        stack.clear();
        stack.add(new Frame(programStmts, 0, false));

        try {
            while (!stack.isEmpty()) {
                if (gotoJump && gotoTargetLabel != null) {
                    List<Frame> target = labels.get(gotoTargetLabel);
                    if (target == null) {
                        throw new RuntimeError(gotoToken, "Undefined label: " + gotoTargetLabel);
                    }
                    stack.clear();
                    for (Frame f : target) {
                        stack.add(f.copy());
                    }
                    gotoJump = false;
                    gotoTargetLabel = null;
                    continue;
                }

                Frame frame = stack.get(stack.size() - 1);
                if (frame.index >= frame.stmts.size()) {
                    if (frame.isLoop) {
                        frame.index = 0;
                    } else {
                        stack.remove(stack.size() - 1);
                    }
                    continue;
                }

                Stmt stmt = frame.stmts.get(frame.index);
                int stackSizeBefore = stack.size();
                execute(stmt);

                if (stack.size() == stackSizeBefore) {
                    frame.index++;
                }

                // Advance past end of list (pop or loop)
                frame = stack.get(stack.size() - 1);
                while (frame.index >= frame.stmts.size()) {
                    if (frame.isLoop) {
                        frame.index = 0;
                        break;
                    }
                    stack.remove(stack.size() - 1);
                    if (stack.isEmpty()) break;
                    frame = stack.get(stack.size() - 1);
                }
            }
        } catch (RuntimeError error) {
            Translator.runtimeError(error);
        }
    }

    private void collectLabels(List<Stmt> stmts, List<Frame> stackPrefix, List<Stmt> currentList, boolean currentIsLoop) {
        for (int i = 0; i < stmts.size(); i++) {
            Stmt s = stmts.get(i);
            if (s instanceof Stmt.Label) {
                List<Frame> fullStack = new ArrayList<>(stackPrefix);
                fullStack.add(new Frame(currentList, i, currentIsLoop));
                labels.put(((Stmt.Label) s).name.lexeme, fullStack);
            }
            if (s instanceof Stmt.Block) {
                List<Frame> newStack = new ArrayList<>(stackPrefix);
                newStack.add(new Frame(currentList, i, currentIsLoop));
                collectLabels(((Stmt.Block) s).stmts, newStack, ((Stmt.Block) s).stmts, false);
            }
            if (s instanceof Stmt.Loop) {
                List<Frame> newStack = new ArrayList<>(stackPrefix);
                newStack.add(new Frame(currentList, i, currentIsLoop));
                collectLabels(((Stmt.Loop) s).body.stmts, newStack, ((Stmt.Loop) s).body.stmts, true);
            }
        }
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
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
        return evaluate(expr.expr);
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

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        stack.add(new Frame(stmt.stmts, 0, false));
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expr);
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
        stack.add(new Frame(stmt.body.stmts, 0, true));
        return null;
    }

    @Override
    public Void visitGotoStmt(Stmt.Goto stmt) {
        gotoTargetLabel = stmt.label.lexeme;
        gotoToken = stmt.label;
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