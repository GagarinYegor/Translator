package com.nequma.translator;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Translator {
    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: translator [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        String source = new String(bytes, Charset.defaultCharset());

        System.out.println("\n+Source language program:");
        System.out.println(source);

        System.out.println("\n+Scanning output:");
        run(source);

        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
            hadError = false;
            hadRuntimeError = false;
        }
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        // Вывод токенов
        StringBuilder outString = new StringBuilder();
        for (Token token : tokens) {
            System.out.println(token);
            outString.append(token.toString()).append("\n");
        }

        // Сохранение вывода сканера в файл
        try {
            File outFile = new File("ScannerOutput.txt");
            try (PrintWriter fileWriter = new PrintWriter(outFile)) {
                fileWriter.write(outString.toString());
            }
            System.out.println("\nScanner output saved to ScannerOutput.txt");
        } catch (FileNotFoundException ex) {
            System.out.println(ex.getMessage());
        }

        if (hadError) {
            System.out.println("Scanning failed. Exiting.");
            return;
        }

        System.out.println("\nParsing output:");
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        if (parser.hadError() || hadError) {
            System.out.println("Parsing failed.");
            return;
        }

        System.out.println("Parsing successful!");
        System.out.println("Number of statements: " + statements.size());

        // Вывод структуры программы (для отладки)
        AstPrinter printer = new AstPrinter();
        for (Stmt stmt : statements) {
            System.out.println(printer.print(stmt));
        }

        System.out.println("\nInterpreter output:");
        Interpreter interpreter = new Interpreter();
        interpreter.interpret(statements);
    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

    static void runtimeError(RuntimeError error) {
        System.err.println("[line " + error.token.line + "] Runtime Error: " + error.getMessage());
        hadRuntimeError = true;
    }
}