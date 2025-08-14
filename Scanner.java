/*
* Scanner.java                        
*
* Sun 09 Feb 2025 13:31:52 AEDT
*
* The starter code here is provided as a high-level guide for implementation.
*
* You may completely disregard the starter code and develop your own solution, 
* provided that it maintains the same public interface.
*
*/

package VC.Scanner;
import VC.ErrorReporter;

public final class Scanner {

    private SourceFile sourceFile; 
    private ErrorReporter errorReporter;
    private boolean debug;
    private StringBuilder currentSpelling;

    //**********************************************************
    private char currentChar;
    private SourcePosition sourcePos;

    // MINE
    //private static final char[] escapeChars = {'b', 't', 'n', 'f', 'r', '\\', '"', '\''};
    private int currLinePoss;
    private int currColPoss;
    
    //**********************************************************
    public Scanner(SourceFile source, ErrorReporter reporter) {
        sourceFile = source;
        errorReporter = reporter;
        debug = false;
        
        // Initiaise currentChar for the starter code. 
        // Change it if necessary for your full implementation
        currentChar = sourceFile.getNextChar();
        currLinePoss = 1; 
        currColPoss = 1; 
    }
    
    //**********************************************************
    public void enableDebugging() {
        debug = true;
    }

    //**********************************************************
    private void accept() {
        
        switch(currentChar) {
            case '\n': 
                currLinePoss++;
                // System.out.println(currLinePoss);
                currColPoss = 1;
                break;
            case '\t':
                //A tab size of 8 characters is assumed. However, this does not mean that the cursor always moves by
                // 8 characters each time you hit the tab key. See the test case tab.vc and its solution tab.sol.
                int rem = (currColPoss - 1) % 8;
                currColPoss += 8 - rem;
                // System.out.println(currColPoss);
                break;
            default:
                currColPoss++;
                // System.out.println(currColPoss);
                break;
        }
        currentChar = sourceFile.getNextChar();

        // You may save the lexeme of the current token incrementally here
        // You may also increment your line and column counters here
    }

    //**********************************************************
 
    // inspectChar returns the n-th character after currentChar in the input stream. 
    // If there are fewer than nthChar characters between currentChar 
    // and the end of file marker, SourceFile.eof is returned.
    // 
    // Both currentChar and the current position in the input stream
    // are *not* changed. Therefore, a subsequent call to accept()
    // will always return the next char after currentChar.

    // That is, inspectChar does not change 

    private char inspectChar(int nthChar) {
        return sourceFile.inspectChar(nthChar);
    }


    //**********************************************************
    private int nextToken() {
        // System.out.println(currentChar);
        //**********************************************************
        // reserved characters and words and strings 
        if(Character.isAlphabetic(currentChar)) {
            return alphabeticElement();
        }
        //**********************************************************
        // digital values - int, float, id 
        if(Character.isDigit(currentChar) || currentChar == '.') {
            return numbericElement();
        }

        //**********************************************************
        switch(currentChar) {
            //**********************************************************
            //operators
            case '+':
                currentSpelling.append(Token.spell(Token.PLUS));
                accept();
                return Token.PLUS;
            case '-':
                currentSpelling.append(Token.spell(Token.MINUS));
                accept();
                return Token.MINUS;
            case '*':
                accept();
                currentSpelling.append(Token.spell(Token.MULT));
                return Token.MULT;
            case '/':
                accept();
                currentSpelling.append(Token.spell(Token.DIV));
                return Token.DIV;
            
            case '!':
                if (inspectChar(1) == '=') {
                    currentSpelling.append(Token.spell(Token.NOTEQ));
                    accept();
                    accept();
                    return Token.NOTEQ;
                } else {
                    currentSpelling.append(Token.spell(Token.NOT));
                    accept();
                    return Token.NOT;
                }
            case '=':
                if (inspectChar(1) == '=') {
                    currentSpelling.append(Token.spell(Token.EQEQ));
                    accept();
                    accept();
                    return Token.EQEQ;
                } else {
                    currentSpelling.append(Token.spell(Token.EQ));
                    accept();
                    return Token.EQ;
                }
            case '<':
                if (inspectChar(1) == '=') {
                    currentSpelling.append(Token.spell(Token.LTEQ));
                    accept();
                    accept();
                    return Token.LTEQ;
                } else {
                    currentSpelling.append(Token.spell(Token.LT));
                    accept();
                    return Token.LT;
                }
            case '>':
                if (inspectChar(1) == '=') {
                    currentSpelling.append(Token.spell(Token.GTEQ));
                    accept();
                    accept();
                    return Token.GTEQ;
                } else {
                    currentSpelling.append(Token.spell(Token.GT));
                    accept();
                    return Token.GT;
                }
            case '&':
                if (inspectChar(1) == '&') { 
                    currentSpelling.append(Token.spell(Token.ANDAND));
                    accept(); 
                    
                    accept(); 
                    return Token.ANDAND; 
                } else {
                    currentSpelling.append('&'); 
                    accept(); 
                    return Token.ERROR; 
                }
            case '|':
                if (inspectChar(1) == '|') { 
                    currentSpelling.append(Token.spell(Token.OROR));
                    // System.out.println(currentSpelling);
                    accept(); 
                    accept(); 
                    return Token.OROR; 
                } else {
                    currentSpelling.append('|'); 
                    accept(); 
                    return Token.ERROR; 
                }
           
            //**********************************************************
            // separators
            case '(':
                currentSpelling.append(Token.spell(Token.LPAREN));
                accept();
                return Token.LPAREN;
            case ')':
                currentSpelling.append(Token.spell(Token.RPAREN));
                accept();
                return Token.RPAREN;
            case '{':
                currentSpelling.append(Token.spell(Token.LCURLY));
                accept();
                return Token.LCURLY;
            case '}':
                currentSpelling.append(Token.spell(Token.RCURLY));
                accept();
                return Token.RCURLY;
            case '[':
                currentSpelling.append(Token.spell(Token.LBRACKET));
                accept();
                return Token.LBRACKET;
            case ']':
                currentSpelling.append(Token.spell(Token.RBRACKET));
                accept();
                return Token.RBRACKET;
            case ';':
                currentSpelling.append(Token.spell(Token.SEMICOLON));
                accept();
                return Token.SEMICOLON;
            case ',':
                currentSpelling.append(Token.spell(Token.COMMA));
                accept();
                return Token.COMMA;
           
            //**********************************************************
            // string literals
            case '"':
                return stringLiteralfunc();
                // System.out.println(currentSpelling);
            
            
            //**********************************************************
            // end of file
            case SourceFile.eof:
                    currentSpelling.append(Token.spell(Token.EOF));
                    return Token.EOF;
            //**********************************************************
            default:
                    break;
        } 
        accept();
        return Token.ERROR;
    }
    //**********************************************************
    private int stringLiteralfunc() {

        accept(); 
        boolean isEscChar = false;    
        
        int initColPos = currColPoss;
        int initLinePos = currLinePoss;

        while (true) {
            //**********************************************************
            if (currentChar == SourceFile.eof || currentChar == '\n') {
                // System.out.println(initColPos);
                // System.out.println(initLinePos);
                sourcePos = new SourcePosition(initLinePos, currLinePoss, initColPos, initColPos + currColPoss - 1);
                errorReporter.reportError("ERROR: Unterminated string literal", "STRING_ERROR", sourcePos);
                return Token.ERROR;
            }
            //**********************************************************
            if (!isEscChar) {

                if (currentChar == '\\') { 
                    isEscChar = true; 
                    accept(); 
                    continue; 
                }

                if (currentChar == '"') { 
                    accept(); 
                    return Token.STRINGLITERAL;
                }
                
            } 
            //**********************************************************
            else {
                isEscChar = false; 
                switch(currentChar) {
                    case '\'': 
                        currentSpelling.append('\''); 
                        break;
                    case 'b': 
                        currentSpelling.append('\b'); 
                        break;
                    case 'n': 
                        currentSpelling.append('\n'); 
                        break;
                    case 'f': 
                        currentSpelling.append('\f'); 
                        break;
                    case 'r': 
                        currentSpelling.append('\r'); 
                        break;
                    case 't': 
                        currentSpelling.append('\t'); 
                        break;
                    case '\\': 
                        currentSpelling.append('\\'); 
                        break;
                    case '"': 
                        currentSpelling.append('\"'); 
                        break;
                    default:
                        errorReporter.reportError("ERROR: Invalid escape sequence", "ESCAPE_ERROR", sourcePos);
                        return Token.ERROR;
                }
                accept(); 
                continue; 
            }
           
            currentSpelling.append(currentChar);
            // System.out.println(currentSpelling);
            accept();
        }
    }
    //**********************************************************
    private void skipSpaceAndComments() {
        while (true) {
                if (Character.isWhitespace(currentChar)) {
                    accept();
                    continue;
                }
            if (currentChar == '/') {
                    if (inspectChar(1) == '/') { 
                        
                        while (currentChar != '\n' && currentChar != SourceFile.eof) {
                            // System.err.println(currentChar);
                            accept();
                        }
                        continue;
                    }
                if (inspectChar(1) == '*') { 
                        int initColPos = currColPoss;
                        int initLinePos = currLinePoss;
                        accept(); 
                        accept(); 
                        // System.out.println(inspectChar(1));
                        while (true) {
                            if (currentChar == '*' && inspectChar(1) == '/') {
                                accept();
                                accept();
                                break;
                            }
                            if (currentChar == SourceFile.eof) {
                                // System.out.println(initColPos);
                                // System.out.println(initLinePos);
                                sourcePos = new SourcePosition(initLinePos, currLinePoss, initColPos, initColPos + currColPoss - 1);
                                errorReporter.reportError("ERROR: Unterminated comment.", "COMMENT_ERROR", sourcePos);
                                break;
                            }
                            accept();
                        }
                        continue;
                }
            }
        break;
        }
    }

       
    //**********************************************************
    private int alphabeticElement() {
        
        currentSpelling.append(currentChar);
        accept();
        
        while (Character.isLetterOrDigit(currentChar) || currentChar == '_') {
            currentSpelling.append(currentChar);
            accept();
        }

        // boolean literals
        String spelling = currentSpelling.toString();
        if (spelling.equals("true") || spelling.equals("false")) {
            return Token.BOOLEANLITERAL;
        }
        return Token.ID;
    }
    //**********************************************************
    private int numbericElement() {
        boolean isFloat = false;
        
        
        if (Character.isDigit(currentChar)) {
            while (Character.isDigit(currentChar)) {
                currentSpelling.append(currentChar);
                accept();
                // System.out.println(currentSpelling);
            }
        }
        
        
        if (currentChar == '.') {
            isFloat = true;
            currentSpelling.append(currentChar);
            accept();
            while (Character.isDigit(currentChar)) {
                currentSpelling.append(currentChar);
                accept();
                // System.out.println(currentSpelling);
            }
        }
        
        
        if (currentChar == 'e' || currentChar == 'E') {
            // System.out.println(inspectChar(1));
            if (Character.isDigit(inspectChar(1))) {
                isFloat = true;
                currentSpelling.append(currentChar); 
                accept(); 
            
                while (Character.isDigit(currentChar)) {
                    currentSpelling.append(currentChar);
                    // System.out.println(currentSpelling);
                    accept();
                }
            }
            
            if (inspectChar(1) == '-' || inspectChar(1) == '+') {
                // System.out.println(inspectChar(2));
                if (Character.isDigit(inspectChar(2))) {
                    isFloat = true;
                    
                    currentSpelling.append(currentChar); 
                    accept(); 
                    // System.out.println(currentSpelling);
                    currentSpelling.append(currentChar); 
                    accept(); 
                    // System.out.println(currentSpelling);
                    while (Character.isDigit(currentChar)) {
                        currentSpelling.append(currentChar);
                        accept();
                    }
                }
            } 
            
          
        }
        if (isFloat) {
            return Token.FLOATLITERAL;
        }
        return Token.INTLITERAL;
    }
    //**********************************************************
    public Token getToken() {
        Token token;
        int kind;
       
        skipSpaceAndComments();
        currentSpelling = new StringBuilder();
       
        int initColPos = currColPoss;
        int initLinePos = currLinePoss; 
        // System.out.println(initColPos);
        // System.out.println(initLinePos);
        kind = nextToken();
        // System.out.println(initColPos);
        // System.out.println(initLinePos);
        sourcePos = new SourcePosition(initLinePos, currLinePoss, initColPos, initColPos + (currColPoss - initColPos == 0 ? 1 : currColPoss - initColPos) - 1);
        token = new Token(kind, currentSpelling.toString(), sourcePos);
        // * do not remove these three lines below (for debugging purposes)
        if (debug) {
            System.out.println(token);
        }
        return token;
    }
    //**********************************************************
}
// ERRORS
// illegal character, i.e., a char that cannot begin any token
// Unterminated comment
// Unterminated string
// Illegal escape character
