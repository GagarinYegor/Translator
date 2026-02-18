package com.nequma.translator;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Translator {
    static boolean hadError = false;
    static boolean hadRuntimeError = false;
    static boolean scanningOutExpected;
    static boolean parsingOutExpected;
    static boolean viewStagesExpected;

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 4) {
            System.out.println("Usage: java Translator <path> [-VS] [-SO] [-PO]");
            System.exit(64);
        }

        scanningOutExpected = false;
        parsingOutExpected = false;
        viewStagesExpected = false;

        for (int i = 1; i < args.length; i++) {
            System.out.println(args[i]);
            switch (args[i]) {
                case "-VS":
                    viewStagesExpected = true;
                    break;
                case "-SO":
                    scanningOutExpected = true;
                    break;
                case "-PO":
                    parsingOutExpected = true;
                    break;
                default:
                    System.err.println("Unknown flag: " + args[i]);
            }
        }
        runFile(args[0]);
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        String source = new String(bytes, Charset.defaultCharset());

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
        if (viewStagesExpected){
            System.out.println("Source language:");
            System.out.println(source);
        }

        List<Token> tokens = scanner.scanTokens();

        if (viewStagesExpected || scanningOutExpected) {
            StringBuilder outString = new StringBuilder();
            if (viewStagesExpected) System.out.println("\nScanning output:");
            for (Token token : tokens) {
                if (viewStagesExpected) System.out.println(token.toString());
                outString.append(token.toString()).append("\n");
            }
            if (scanningOutExpected) {
                try {
                    try (PrintWriter fileWriter = new PrintWriter("ScannerOutput.txt")) {
                        fileWriter.write(outString.toString());
                    }
                } catch (FileNotFoundException ex) {
                    System.out.println(ex.getMessage());
                }
            }
        }

        if (hadError) {
            System.out.println("Scanning failed. Exiting.");
            return;
        }

        //System.out.println("Scanning successful.");

        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        if (viewStagesExpected || parsingOutExpected) {
            AstPrinter printer = new AstPrinter();
            StringBuilder outString = new StringBuilder();
            if (viewStagesExpected) System.out.println("\nParsing output:");
            for (Stmt stmt : statements) {
                if (viewStagesExpected) System.out.println(printer.print(stmt));
                outString.append(printer.print(stmt)).append("\n");
            }
            if (parsingOutExpected) {
                try {
                    try (PrintWriter fileWriter = new PrintWriter("ParserOutput.txt")) {
                        fileWriter.write(outString.toString());
                    }
                } catch (FileNotFoundException ex) {
                    System.out.println(ex.getMessage());
                }
            }
        }

        if (parser.hadError() || hadError) {
            System.out.println("Parsing failed.");
            return;
        }

        //System.out.println("Parsing successful.");
        //System.out.println("Number of statements: " + statements.size());

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