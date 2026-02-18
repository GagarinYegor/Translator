package com.nequma.translator;

enum TokenType {
    // Операторы
    PLUS,    // +
    MIN,     // -
    MULT,    // *
    DIV,     // /
    MOD,     // mod

    // Отношения
    EQ,      // =
    NE,      // <>
    LT,      // <
    GT,      // >
    LE,      // <=
    GE,      // >=

    // Присваивание
    ASS,     // (variable) := (expression)

    // Разделители
    EOP,     // ;
    COMMENT, // { ... }

    // Ключевые слова
    BST,     // begin
    EST,     // end
    VECTOR,  // vector
    OF,      // of
    GOTO,    // goto
    READ,    // read
    WRITE,   // write
    SKIP,    // skip
    SPACE,   // space
    TAB,     // tab
    IF,      // if
    THEN,    // then
    ELSE,    // else
    LOOP,    // loop
    INTEGER, // integer
    REAL,    // real

    // Литералы
    IDENTIFIER, // идентификатор
    NUMBER,     // число (целое или действительное)
    LABEL,      // метка

    // Специальные символы
    LPAREN,    // (
    RPAREN,    // )
    LBRACKET,  // [
    RBRACKET,  // ]
    COMMA,     // ,
    COLON,     // :

    EOF      // End Of File
}