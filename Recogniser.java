
/*
 * Recogniser.java            
 *
 * Wed 26 Feb 2025 14:06:17 AEDT
 */

/* This recogniser accepts a subset of VC defined by the following CFG: 

	program       -> func-decl
	
	// declaration
	
	func-decl     -> void identifier "(" ")" compound-stmt
	
	identifier    -> ID
	
	// statements 
	compound-stmt -> "{" stmt* "}" 
	stmt          -> continue-stmt
	    	      |  expr-stmt
	continue-stmt -> continue ";"
	expr-stmt     -> expr? ";"
	
	// expressions 
	expr                -> assignment-expr
	assignment-expr     -> additive-expr
	additive-expr       -> multiplicative-expr
	                    |  additive-expr "+" multiplicative-expr
	multiplicative-expr -> unary-expr
		            |  multiplicative-expr "*" unary-expr
	unary-expr          -> "-" unary-expr
			    |  primary-expr
	
	primary-expr        -> identifier
	 		    |  INTLITERAL
			    | "(" expr ")"
 
It serves as a good starting point for implementing your own VC recogniser. 
You can modify the existing parsing methods (if necessary) and add any missing ones 
to build a complete recogniser for VC.

Alternatively, you are free to disregard the starter code entirely and develop 
your own solution, as long as it adheres to the same public interface.

*/  

package VC.Recogniser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;

public class Recogniser {
    private Scanner scanner;
    private ErrorReporter errorReporter;
    private Token currentToken;
    //****************************************************************************
    public Recogniser(Scanner lexer, ErrorReporter reporter) {
        scanner = lexer;
        errorReporter = reporter;
        currentToken = scanner.getToken();
    }

    // match checks to see if the current token matches tokenExpected.
    // If so, fetches the next token.
    // If not, reports a syntactic error.
    void match(int tokenExpected) throws SyntaxError {
        if (currentToken.kind == tokenExpected) {
            currentToken = scanner.getToken();
        } else {
            syntacticError("\"%\" expected here", Token.spell(tokenExpected));
        }
    }
    // accepts the current token and fetches the next
    void accept() {
        currentToken = scanner.getToken();
    }

    // Handles syntactic errors and reports them via the error reporter.
    void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
        SourcePosition pos = currentToken.position;
        errorReporter.reportError(messageTemplate, tokenQuoted, pos);
        throw (new SyntaxError());
    }
    //****************************************************************************
    //****************************************************************************
    // ========================== PROGRAMS ========================
    public void parseProgram() {
        try {
            while (currentToken.kind != Token.EOF) {
                checkFuncOrValDecl();
            }
            if (currentToken.kind != Token.EOF) {
                syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
            }
        } catch (SyntaxError s) {
        }
    }
    // ========================== DECLARATIONS ========================
    //****************************************************************************
    void checkFuncOrValDecl() throws SyntaxError {
        //system.out.println("KONNICHIWA");
        if (isTokenType()) {
            accept();
        }
        else {
            syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
        }
        parseIdent();
        if (currentToken.kind == Token.LPAREN) {
            parseFuncDecl();
        } else {
            parseVariableDecl();
        }
    }
    //****************************************************************************
    void parseFuncDecl() throws SyntaxError {
        //system.out.println("NAMESTE");
        match(Token.LPAREN); 
        
        if (isTokenType()) {    
            accept();
            parseIdent(); 
            
            if (currentToken.kind == Token.LBRACKET) {
                match(Token.LBRACKET);
                if (currentToken.kind == Token.INTLITERAL) {
                    match(Token.INTLITERAL);
                }
                match(Token.RBRACKET);
            }
            
            while (currentToken.kind == Token.COMMA) {
                match(Token.COMMA);
                
                if (isTokenType()) {
                    accept();
                }
                else {
                    //system.out.println("NOOOOOO");
                    syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
                
                }
                parseIdent();
                
                if (currentToken.kind == Token.LBRACKET) {
                    match(Token.LBRACKET);
                    if (currentToken.kind == Token.INTLITERAL) {
                        match(Token.INTLITERAL);
                    }
                    match(Token.RBRACKET);
                }
            }
        }
        match(Token.RPAREN); 
        
        parseCompoundStmt();
    }
    
    //****************************************************************************
    void parseVariableDecl() throws SyntaxError {
        parseSingleVariableDecl(); 
        //system.out.println("HELLO");
        
        while (currentToken.kind == Token.COMMA) {
            match(Token.COMMA);
            //system.out.println("HOLA");
            parseIdent();
            parseSingleVariableDecl();
        }
        match(Token.SEMICOLON);  
    }
    //****************************************************************************
    void parseSingleVariableDecl() throws SyntaxError {
        //boolean isArray = false;
        
        if (currentToken.kind == Token.LBRACKET) {
            // isArray = true;  
            match(Token.LBRACKET);
            //System.out.println(currentToken);
            if (currentToken.kind == Token.INTLITERAL) {
                match(Token.INTLITERAL);
            }
            match(Token.RBRACKET);
        }
        
        if (currentToken.kind == Token.EQ) {
            match(Token.EQ);
            if (/*isArray &&*/ currentToken.kind == Token.LCURLY) {
                match(Token.LCURLY);
                parseExpr();
                //System.out.println(currentToken);
                while (currentToken.kind == Token.COMMA) {
                    match(Token.COMMA);
                    parseExpr();
                }
                match(Token.RCURLY);
            } else {
                parseExpr();
            }
        }
    }
    //****************************************************************************
    //****************************************************************************
    void parseCompoundStmt() throws SyntaxError {
        match(Token.LCURLY);
        //System.out.println("OHAYO");
        while (currentToken.kind != Token.RCURLY) {
            //System.out.println("BOUNJOUR");
            while (isTokenType()) {
                //System.out.println("WORK PLZ");
                checkFuncOrValDecl();
            }
            parseStmtList();
        }
        match(Token.RCURLY);
    }
    //****************************************************************************
    void parseStmtList() throws SyntaxError {
        while (currentToken.kind != Token.RCURLY)
            parseStmt();
    }
    //****************************************************************************
    void parseExprStmt() throws SyntaxError {
        if (exprStartToken()) {
            parseExpr();
            match(Token.SEMICOLON);
        } else {
            match(Token.SEMICOLON);
        }
    }
    //****************************************************************************
    void parseStmt() throws SyntaxError {
        switch (currentToken.kind) {
            case Token.CONTINUE:
                parseContinueStmt();
                break;
            
            case Token.BREAK:
                parseBreakStmt();
                break;
            case Token.IF: 
                parseIfStmt();
                break;
            case Token.WHILE:
                parseWhileStmt();
                break;
            case Token.RETURN:
                parseReturnStmt();
                break;
            
            case Token.FOR:
                parseForStmt();
                break;
           
            default:
                if (currentToken.kind == Token.LCURLY) {
                    parseCompoundStmt();
                    break;
                } 
                parseExprStmt();
                break;
        }
    }
    //****************************************************************************
    void parseIfStmt() throws SyntaxError {
        match(Token.IF);
        match(Token.LPAREN);
        parseExpr();
        match(Token.RPAREN);
        parseStmt();
        if (currentToken.kind == Token.ELSE) {
            match(Token.ELSE);
            parseStmt();
        }
    }
    //****************************************************************************
    void parseWhileStmt() throws SyntaxError {
        match(Token.WHILE);
        match(Token.LPAREN);
        parseExpr();
        match(Token.RPAREN);
        parseStmt();
    } 
    //****************************************************************************
    void parseReturnStmt() throws SyntaxError {
        match(Token.RETURN);
        if (currentToken.kind != Token.SEMICOLON) {
            parseExpr();
        }
        match(Token.SEMICOLON);
    }
    //****************************************************************************
    void parseContinueStmt() throws SyntaxError {
        match(Token.CONTINUE);
        match(Token.SEMICOLON);
    }
    //****************************************************************************
    void parseBreakStmt() throws SyntaxError {
        match(Token.BREAK);
        match(Token.SEMICOLON);
    }
    //****************************************************************************
    void parseForStmt() throws SyntaxError {
        match(Token.FOR);
        match(Token.LPAREN);
        if (currentToken.kind != Token.SEMICOLON) {
            parseExpr();
        }
        match(Token.SEMICOLON);
        if (currentToken.kind != Token.SEMICOLON) {
            parseExpr();
        }
        match(Token.SEMICOLON);
        if (currentToken.kind != Token.RPAREN) {
            parseExpr();
        }
        match(Token.RPAREN);
        parseStmt();
    }
    //****************************************************************************
    //****************************************************************************
    void acceptOperator() throws SyntaxError {
        currentToken = scanner.getToken();
    }

    //****************************************************************************
    void parseExpr() throws SyntaxError {
        parseAssignExpr();
    }

    //****************************************************************************

    void parseAssignExpr() throws SyntaxError {
        parseLogicalOrExpression();
        if (currentToken.kind == Token.EQ) {
            match(Token.EQ);
            //System.out.println(currentToken);
            parseExpr();
        }
    }

    void parseLogicalOrExpression() throws SyntaxError {
        parseLogicalAndExpression();
        //System.out.println(currentToken);
        while (currentToken.kind == Token.OROR) {
            match(Token.OROR);
            parseLogicalAndExpression();
        }
    }

    void parseLogicalAndExpression() throws SyntaxError {
        parseEqualityExpression();
        while (currentToken.kind == Token.ANDAND) {
            match(Token.ANDAND);
            //System.out.println(currentToken);
            parseEqualityExpression();
        }
    }

    void parseEqualityExpression() throws SyntaxError {
        parseRelationalExpression();
        //System.out.println("NANII");
        while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
            match(currentToken.kind);
            parseRelationalExpression();
        }
    }

    void parseRelationalExpression() throws SyntaxError {
        parseAdditiveExpression();
        //System.out.println(currentToken);
        while (currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ 
            || currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ) {
            match(currentToken.kind);
            //System.out.println("HOLAAA");
            parseAdditiveExpression();
        }
    }

    void parseAdditiveExpression() throws SyntaxError {
        parseMultiplicativeExpression();
        //System.out.println("OUUIII");
        while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
            match(currentToken.kind);
            //System.out.println(currentToken);
            parseMultiplicativeExpression();
        }
    }

    void parseMultiplicativeExpression() throws SyntaxError {
        parseUnaryExpression();
        while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV) {
            match(currentToken.kind);
            //System.out.println(currentToken);
            parseUnaryExpression();
        }
    }

    void parseUnaryExpression() throws SyntaxError {
        switch (currentToken.kind) {
            case Token.MINUS:
            case Token.PLUS:
            case Token.NOT:
                match(currentToken.kind);
                parseUnaryExpression();
                break;
            default:
                parsePrimaryExpression();
                break;
        }
    }

    void parsePrimaryExpression() throws SyntaxError {
        switch (currentToken.kind) {
            //System.out.println("BOUNJOUR");
            case Token.ID:
                parseIdentifierPrimaryExpression();
                break;
            case Token.LPAREN:
                match(Token.LPAREN);
                parseExpr();
                match(Token.RPAREN);
                break;
            case Token.INTLITERAL:
                parseIntLiteral();
                break;
            case Token.FLOATLITERAL:
                parseFloatLiteral();
                break;
            case Token.BOOLEANLITERAL:
                parseBooleanLiteral();
                break;
            case Token.STRINGLITERAL:
                parseStringLiteral();
                break;
            case Token.VOID:
                parseVoidLiteral();
                break;
            default:
                syntacticError("illegal primary expression", currentToken.spelling);
        }
    }

    void parseIdentifierPrimaryExpression() throws SyntaxError {
        parseIdent();
        //System.out.println("BOUNJOUR");
        if (currentToken.kind == Token.LBRACKET) {
            match(Token.LBRACKET);
            parseExpr();
            match(Token.RBRACKET);
            return;
        }

        if (currentToken.kind == Token.LPAREN) {
            match(Token.LPAREN);
            //System.out.println("WORKK");
            if (exprStartToken()) {
                parseExpr();
                while (currentToken.kind != Token.RPAREN) {
                    match(Token.COMMA);
                    parseExpr();
                }
            }
            match(Token.RPAREN);
            return;
        }
    }
    //****************************************************************************
    //****************************************************************************
    void parseIntLiteral() throws SyntaxError {
        if (currentToken.kind == Token.INTLITERAL) {
            accept();
        } else
            //System.out.println("OIII");
            syntacticError("integer literal expected here", "");
    }
    
    //****************************************************************************
    void parseFloatLiteral() throws SyntaxError {
        if (currentToken.kind == Token.FLOATLITERAL) {
            accept();
        } else
            syntacticError("float literal expected here", "");
    }

    //****************************************************************************
    void parseBooleanLiteral() throws SyntaxError {
        if (currentToken.kind == Token.BOOLEANLITERAL) {
            accept();
        } else
            syntacticError("boolean literal expected here", "");
    }
    //****************************************************************************
    void parseVoidLiteral() throws SyntaxError {
        if (currentToken.kind == Token.VOID) {
            accept();
        } else
            syntacticError("void literal expected here", "");
    }
    //****************************************************************************
    void parseStringLiteral() throws SyntaxError {
        if (currentToken.kind == Token.STRINGLITERAL) {
            accept();
        } else
            syntacticError("string literal expected here", "");
    }
    //****************************************************************************
    //****************************************************************************
    void parseIdent() throws SyntaxError {
        if (currentToken.kind == Token.ID) {
            accept();
        }
    }
    //****************************************************************************
    //****************************************************************************
    boolean exprStartToken() {
        return isUnaryOperator() || isLiteral() || isIdorParantesis();
    }
    //****************************************************************************
    private boolean isIdorParantesis() {
        return currentToken.kind == Token.ID
                || currentToken.kind == Token.LPAREN;
    }
    //****************************************************************************
    private boolean isUnaryOperator() {
        return currentToken.kind == Token.PLUS
                || currentToken.kind == Token.MINUS
                || currentToken.kind == Token.NOT;
    }
    //****************************************************************************
    private boolean isTokenType() {
        return currentToken.kind == Token.INT
                || currentToken.kind == Token.FLOAT
                || currentToken.kind == Token.BOOLEAN
                || currentToken.kind == Token.VOID;
    }
    //****************************************************************************
    private boolean isLiteral() {
        return currentToken.kind == Token.INTLITERAL
                || currentToken.kind == Token.FLOATLITERAL
                || currentToken.kind == Token.BOOLEANLITERAL
                || currentToken.kind == Token.STRINGLITERAL;
    }
    //****************************************************************************
}
