package VC.Parser;
import VC.ASTs.*;
import VC.ErrorReporter;
import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import java.util.ArrayList;
public class Parser {
  private Scanner scanner;
  private ErrorReporter errorReporter;
  private Token currentToken;
  private SourcePosition previousTokenPosition;
  private SourcePosition dummyPos = new SourcePosition();
  //****************************************************************************
  public Parser(Scanner lexer, ErrorReporter reporter) {
    scanner = lexer;
    errorReporter = reporter;
    currentToken = scanner.getToken();
  }
  //****************************************************************************
  private void match(int expectedToken) throws SyntaxError {
    if (currentToken.kind == expectedToken) {
      currentToken = scanner.getToken();
    } else {
      syntacticError("\"%\" expected here", Token.spell(expectedToken));
    }
  }
  //****************************************************************************
  private void accept() {
    currentToken = scanner.getToken();
  }
  //****************************************************************************
  private void syntacticError(String message, String tokenText) throws SyntaxError {
    errorReporter.reportError(message, tokenText, null);
    throw new SyntaxError();
  }
  void start(SourcePosition position) {
    position.lineStart = currentToken.position.lineStart;
    position.charStart = currentToken.position.charStart;
  }
  void finish(SourcePosition position) {
    position.lineFinish = previousTokenPosition.lineFinish;
    position.charFinish = previousTokenPosition.charFinish;
  }  
  void copyStart(SourcePosition from, SourcePosition to) {
    to.lineStart = from.lineStart;
    to.charStart = from.charStart;
  }
  public Program parseProgram() {
    try {
      List decls = parseDecls(false);
      Program programAST = new Program(decls, null);
      return programAST;
    } catch (SyntaxError error) {
      return null;
    }
  }
  //**************************************************************************** 
  private List parseDecls(boolean VarDec) throws SyntaxError {
    if (currentToken.kind != Token.EOF) {
      Type tAST = parseType();
      Ident identAST = parseIdent();
      List declList; 
      if (currentToken.kind == Token.LPAREN) {
        declList = createDeclList(parseFuncDecl(tAST, identAST), tAST, VarDec);
        return declList;
      } else {
        declList = createDeclList(parseVarDecl(tAST, identAST, VarDec), tAST, VarDec);
        return declList;
      } 
    }
    return new EmptyDeclList(null);
  }
  //****************************************************************************
  private ParaDecl parseParam() throws SyntaxError {
    //System.out.println("Entering parseParam");
    Type tAST = parseType();
    Ident identAST = parseIdent();
    if (currentToken.kind == Token.LBRACKET) {
      match(Token.LBRACKET);
      if (currentToken.kind == Token.INTLITERAL) {
        IntLiteral idx = parseIntLiteral();
        tAST = new ArrayType(tAST, new IntExpr(idx, null), null);
      }
      match(Token.RBRACKET);
    }
    return new ParaDecl(tAST, identAST, null);
  }
  //****************************************************************************
  private List parseParaList() throws SyntaxError {
    //System.out.println("Entering parseParaList");
    List paraList = new EmptyParaList(null);
    while (currentToken.kind != Token.RPAREN) {
        ParaDecl param = parseParam();
        paraList = new ParaList(param, paraList, null); 
        if (currentToken.kind != Token.RPAREN) {
            match(Token.COMMA); 
        }
    }
    return paraList;
  }
  //***************************************************************************
  private Stmt parseCompoundStmt() throws SyntaxError {
    List varList = new EmptyDeclList(null);
    List StmtList = new EmptyStmtList(null);
    match(Token.LCURLY);
    //System.out.println("konni");
    while (currentToken.kind != Token.RCURLY) {
      if (isTokenType()) {
        varList = parseDecls(true);
      } else {
        StmtList = parseStmtList();
      }
    }
    match(Token.RCURLY);
    Stmt cStmt;
    cStmt = new CompoundStmt(varList, StmtList, null);
    return cStmt;
  }
  //****************************************************************************
  private FuncDecl parseFuncDecl(Type tAST, Ident identAST) throws SyntaxError {
    match(Token.LPAREN);
    List params = parseParaList();
    match(Token.RPAREN);
    
    Stmt bdy = parseCompoundStmt();
    return new FuncDecl(tAST, identAST, params, bdy, null);
  }
  //****************************************************************************
  private Decl parseVarDecl(Type tAST, Ident identAST, boolean VarDec) throws SyntaxError {
      Type Ft = tAST;  
      if (currentToken.kind == Token.LBRACKET) { 
        match(Token.LBRACKET);
        Expr idxExpr = new EmptyExpr(null);
        //System.out.println(currentToken);
        if (currentToken.kind == Token.INTLITERAL) {
            idxExpr = new IntExpr(parseIntLiteral(), null);
        }
        Ft = new ArrayType(tAST, idxExpr, null);
        match(Token.RBRACKET); 
      } 
      if (VarDec) {
        //System.out.println("oi");
          return new LocalVarDecl(Ft, identAST, parseVarInit(identAST), null);
      } else {
          return new GlobalVarDecl(Ft, identAST, parseVarInit(identAST), null);
      }
  }
  //****************************************************************************
  
  private Expr parseVarInit(Ident identAST) throws SyntaxError {
    //System.out.println("Entering parseVarInit");
    if (currentToken.kind == Token.EQ) {
        acceptOperator();
        if (currentToken.kind == Token.LCURLY) {
            match(Token.LCURLY);
            List initList = new EmptyArrayExprList(null);
            while (currentToken.kind != Token.RCURLY) {
                Expr exprAST = parseExpr(); 
                //System.out.println(currentToken);
                if (currentToken.kind != Token.RCURLY) {
                    match(Token.COMMA); 
                }
                initList = new ArrayExprList(exprAST, initList, null); 
            }
            match(Token.RCURLY);
            return new ArrayInitExpr(initList, null);
        }
        return parseExpr();
    }      
    
    if (currentToken.kind == Token.LBRACKET) {
        match(Token.LBRACKET);
        if (currentToken.kind == Token.INTLITERAL) {
            match(Token.INTLITERAL);
        }
        match(Token.RBRACKET);
    }
    return new EmptyExpr(null);
  }
 //****************************************************************************
  private List createDeclList(Decl firstDecl, Type currT, boolean VarDec) throws SyntaxError {
      //System.out.println("Entering createDeclList");
      java.util.List<Decl> decls = new ArrayList<>();
      decls.add(firstDecl);
      boolean isCom = false;
      while (!isCom) {
          if (currentToken.kind == Token.COMMA) {
              match(Token.COMMA);
              //System.out.println(currentToken);
              Ident identAST = parseIdent();
              Decl NDecl;
              if (currentToken.kind != Token.LPAREN) { 
                NDecl = parseVarDecl(currT, identAST, VarDec);
              } else {
                NDecl = parseFuncDecl(currT, identAST);
              }
              decls.add(NDecl);
          } 
          else if (currentToken.kind == Token.SEMICOLON) {
             //System.out.println("HOLAA");
              match(Token.SEMICOLON);
              if (isTokenType()) {
                  currT = parseType();
                  Ident identAST = parseIdent();
                  Decl NDecl;
                  if (currentToken.kind == Token.LPAREN) {
                      NDecl = parseFuncDecl(currT, identAST);
                  } else {
                      NDecl = parseVarDecl(currT, identAST, VarDec);
                  }
                  //System.out.println("oi");
                  decls.add(NDecl);
              }
          } 
          else if (isTokenType()) {
            //System.out.println("hii");
              currT = parseType();
              Ident identAST = parseIdent();
              Decl NDecl;
              if (currentToken.kind == Token.LPAREN) {
                  NDecl = parseFuncDecl(currT, identAST);
              } else {
                  NDecl = parseVarDecl(currT, identAST, VarDec);
              }
              decls.add(NDecl);
          } 
          else {
              //System.out.println("oi");
              isCom = true;
          }
      }
      List fl = new EmptyDeclList(null);
      for (int i = decls.size() - 1; i >= 0; i--) {
          fl = new DeclList(decls.get(i), fl, null);
      }
      return fl;
  }
  //****************************************************************************
  private Type parseType() throws SyntaxError {
    Type tAST = null;
    //System.out.println(currentToken);
    switch (currentToken.kind) {
      case Token.INT -> {
          accept();

          tAST = new IntType(null);
      }
      case Token.FLOAT -> {
          accept();
          tAST = new FloatType(null);
      }
      case Token.BOOLEAN -> {
          accept();
          tAST = new BooleanType(null);
      }
      case Token.VOID -> {
          accept();
          tAST = new VoidType(null);
      }

      default -> {
        syntacticError("Incorrect parse t", "");
      }
    }
    return tAST;
  }
  //****************************************************************************
  private List parseStmtList() throws SyntaxError {
    java.util.List<Stmt> stmtArr = new ArrayList<>();
    while (currentToken.kind != Token.RCURLY) {
      Stmt stmt = parseStmt();
      stmtArr.add(stmt);
    }

    List fl = new EmptyStmtList(null);
    for (int i = stmtArr.size() - 1; i >= 0; i--) {
      fl = new StmtList(stmtArr.get(i), fl, null);
    }
    return fl;
  }
  //****************************************************************************
  private Stmt parseStmt() throws SyntaxError {
      switch (currentToken.kind) {
          case Token.IF:
              return parseIfStmt();
          case Token.WHILE:
              return parseWhileStmt();
          case Token.CONTINUE:
              return parseContinueStmt();
          case Token.FOR:
            return parseForStmt();
          case Token.BREAK:
              return parseBreakStmt();
          case Token.RETURN:
              return parseReturnStmt();
          default:

            if (currentToken.kind == Token.LCURLY) {
                return parseCompoundStmt();
            } else {
                return parseExprStmt();
            }
      }
  }
  //****************************************************************************
  private Stmt parseIfStmt() throws SyntaxError {
    match(Token.IF);
    match(Token.LPAREN);
    Expr cond = parseExpr();
    match(Token.RPAREN);
    Stmt thenC = parseStmt();
    
    if (currentToken.kind == Token.ELSE) {
      match(Token.ELSE);

      Stmt elseC;
      if (currentToken.kind == Token.IF) {
        elseC = parseIfStmt();
      } else {
        elseC = parseStmt();
      }


      return new IfStmt(cond, thenC, elseC, null);
    }

    return new IfStmt(cond, thenC, null);
  }
  //****************************************************************************
  private Stmt parseForStmt() throws SyntaxError {
    match(Token.FOR);
    match(Token.LPAREN);
    Expr[] forC = new Expr[3];
    for (int i = 0; i < 3; i++) {
      Expr partExpr = new EmptyExpr(null);
      if (currentToken.kind != Token.SEMICOLON && currentToken.kind != Token.RPAREN) {
        partExpr = parseAssignmentExpr();
        if (partExpr == null) {
          partExpr = parseRelationalExpr();
          if (partExpr == null) {
            partExpr = new EmptyExpr(null);
          }
        }
      }
      forC[i] = partExpr;
      if (i < 2) {
        match(Token.SEMICOLON);
      }
    }
    
    match(Token.RPAREN);
    Stmt bdy = parseStmt();
    return new ForStmt(forC[0], forC[1], forC[2], bdy, null);
  }
  //****************************************************************************
  
  private Stmt parseWhileStmt() throws SyntaxError {
    match(Token.WHILE);

    match(Token.LPAREN);
    Expr cond = parseExpr();
    match(Token.RPAREN);
    Stmt bdy = parseStmt();
    return new WhileStmt(cond, bdy, null);
  }

  //****************************************************************************
  
  private Stmt parseBreakStmt() throws SyntaxError {
    match(Token.BREAK);
    match(Token.SEMICOLON);
    return new BreakStmt(null);
  }
  //****************************************************************************
  
  private Stmt parseContinueStmt() throws SyntaxError {
    match(Token.CONTINUE);
    match(Token.SEMICOLON);
    return new ContinueStmt(null);

  }
  //****************************************************************************
  
  private Stmt parseReturnStmt() throws SyntaxError {
    match(Token.RETURN);
    if (currentToken.kind != Token.SEMICOLON) {
      Expr returnExpr = parseExpr();
    }
    match(Token.SEMICOLON);
    return new ReturnStmt(new EmptyExpr(null), null);
  }
  //********************************** ******************************************
  
  private Stmt parseExprStmt() throws SyntaxError {
    if (currentToken.kind != Token.SEMICOLON) {
      Expr exp = parseExpr();
      match(Token.SEMICOLON);
      return new ExprStmt(exp, null);
    } 
    match(Token.SEMICOLON);
    return new ExprStmt(new EmptyExpr(null), null);
    

  }
  //****************************************************************************
  private List parseArgs() throws SyntaxError {
    List argList = new EmptyArgList(null);
    match(Token.LPAREN);
    if (currentToken.kind == Token.RPAREN) {
      return new EmptyArgList(null);
    }
    Arg arg = parseArg();
    argList = new ArgList(arg, argList, null);
    while (currentToken.kind == Token.COMMA) {
      accept();
      arg = parseArg();
      argList = new ArgList(arg, argList, null);
    }
    return argList;

  }
  //****************************************************************************
  private List parseArgsIt() throws SyntaxError {
    java.util.List<Arg> args = new ArrayList<>();
    //System.out.println("namestee");
    if (currentToken.kind == Token.RPAREN) {
      return new EmptyArgList(null);
    }

    args.add(parseArg());
    while (currentToken.kind == Token.COMMA) {
      match(Token.COMMA);
      args.add(parseArg());
    }
    return createArgList(args);
  }
  //****************************************************************************
  private List createArgList(java.util.List<Arg> args) {
    List fl = new EmptyArgList(null);
    for (int i = args.size() - 1; i >= 0; i--) {
        fl = new ArgList(args.get(i), fl, null);
    }
    return fl;
  }

  //****************************************************************************
  private Arg parseArg() throws SyntaxError {
    Expr exp = parseExpr();
    return new Arg(exp, null);
  }
  //****************************************************************************
  private Expr parseExpr() throws SyntaxError {
    Expr exp = parseAssignmentExpr();
    return exp;
  }
  //****************************************************************************
  private Expr parseAssignmentExpr() throws SyntaxError {
    Expr LExpr = parseLogicalOrExpr();
    if (currentToken.kind == Token.EQ) {
      Operator O = acceptOperator();
      Expr RExpr = parseAssignmentExpr();
      return new AssignExpr(LExpr, RExpr, null);
    }
    return LExpr;
  }
  
  //****************************************************************************
  private Expr parseLogicalOrExpr() throws SyntaxError {
    Expr LExpr = parseLogicalAndExpr();
    while (currentToken.kind == Token.OROR) {
      //System.out.println(currentToken);
      Operator O = acceptOperator();
      Expr AAExp = parseLogicalAndExpr();
      LExpr = new BinaryExpr(LExpr, O, AAExp, null);
    }
    return LExpr;
  }
  //****************************************************************************
  private Expr parseLogicalAndExpr() throws SyntaxError {
    Expr LExpr = parseEqualityExpr();
    while (currentToken.kind == Token.ANDAND) {
      //System.out.println(currentToken);
      Operator O = acceptOperator();


      Expr EqExp = parseEqualityExpr();
      LExpr = new BinaryExpr(LExpr, O, EqExp, null);
    }
    return LExpr;
  }


  //****************************************************************************
  private Expr parseEqualityExpr() throws SyntaxError {
    Expr LExpr = parseRelationalExpr();
    while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
      //System.out.println(currentToken);
      Operator O = acceptOperator();
      Expr RelExp = parseRelationalExpr();
      LExpr = new BinaryExpr(LExpr, O, RelExp, null);
    }
    return LExpr;
  }
  //****************************************************************************
  private Expr parseRelationalExpr() throws SyntaxError {
    Expr LExpr = parseAdditiveExpr();
    while (isRelationalOperator()) {
      Operator O = acceptOperator();
      Expr AddExp = parseAdditiveExpr();
      LExpr = new BinaryExpr(LExpr, O, AddExp, null);
    }
    return LExpr;
  }
  //****************************************************************************
  private Expr parseMultiplicativeExpr() throws SyntaxError {
    Expr LExpr = parseUnaryExpr();
    while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV) {
      Operator O = acceptOperator();
      Expr UExp = parseUnaryExpr();
      LExpr = new BinaryExpr(LExpr, O, UExp, null);
    }
    return LExpr;
  }

    //****************************************************************************
  private Expr parseAdditiveExpr() throws SyntaxError {
    Expr LExpr = parseMultiplicativeExpr();
    while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
      Operator O = acceptOperator();
      Expr RExpr = parseMultiplicativeExpr();
      LExpr = new BinaryExpr(LExpr, O, RExpr, null);
    }
    return LExpr;
  }




  //****************************************************************************
  private Expr parseUnaryExpr() throws SyntaxError {
    if(isUnaryOperator()) {
      Operator O = acceptOperator();
      Expr operand = parseUnaryExpr();
      return new UnaryExpr(O, operand, null);
    } 
    return parsePrimaryExpr();
  }
  //****************************************************************************
  private Expr parsePrimaryExpr() throws SyntaxError {
    switch (currentToken.kind) {
      case Token.ID -> {
          return parseIdentifierExpr();
      }
      case Token.LPAREN -> {
          return parseLParenExpr();
      }
      case Token.INTLITERAL -> {
          IntLiteral intLiteral = parseIntLiteral();
          return new IntExpr(intLiteral, null);
      }
      case Token.FLOATLITERAL -> {
          FloatLiteral floatLiteral = parseFloatLiteral();
          return new FloatExpr(floatLiteral, null);
      } 
      case Token.BOOLEANLITERAL -> {
          BooleanLiteral boolLiteral = parseBooleanLiteral();
          return new BooleanExpr(boolLiteral, null);
      }
      case Token.STRINGLITERAL -> {
          StringLiteral strLiteral = parseStringLiteral();
          return new StringExpr(strLiteral, null);
      }
      default -> {
          syntacticError("illegal primary exp", currentToken.spelling);
          return null;
      }
    }
  }
  //****************************************************************************
  private Expr parseIdentifierExpr() throws SyntaxError {
      Ident ident = parseIdent();
      
      switch (currentToken.kind) {
          case Token.LPAREN: {
              match(Token.LPAREN);
              List args = parseArgsIt();
              Expr callExpr = new CallExpr(ident, args, null);
              match(Token.RPAREN);
              return callExpr;
          }
          case Token.LBRACKET: {
              match(Token.LBRACKET);
              Expr idx = parseExpr();
              Var var = new SimpleVar(ident, null);
              Expr arrayExpr = new ArrayExpr(var, idx, null);
              match(Token.RBRACKET);
              return arrayExpr;
          }
          default: {
              return new VarExpr(new SimpleVar(ident, null), null);
          }
      }   
  }
  //****************************************************************************
  private Expr parseLParenExpr() throws SyntaxError {
    accept(); 
    Expr exp = parseExpr();
    match(Token.RPAREN);
    return exp;
  }
  //****************************************************************************
  private StringLiteral parseStringLiteral() throws SyntaxError {
    //System.out.println(currentToken);
    if (currentToken.kind == Token.STRINGLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      StringLiteral SL = new StringLiteral(spelling, null);
      return SL; 
    } else {
      syntacticError("string literal expected here", "");
    }
    return null;
  }
  
  //****************************************************************************
  private Ident parseIdent() throws SyntaxError {
    if (currentToken.kind == Token.ID) {
      String spelling = currentToken.spelling;
      Ident I = new Ident(spelling, null);
      currentToken = scanner.getToken();
      return I;
    } else {
      syntacticError("identifier expected here", " ");
      return null;
    }
  }
  //****************************************************************************
  private Operator acceptOperator() throws SyntaxError {
    String spelling = currentToken.spelling;
    Operator O = new Operator(spelling, null);
    currentToken = scanner.getToken();
    return O;
  }
  //****************************************************************************
  private IntLiteral parseIntLiteral() throws SyntaxError {
    if (currentToken.kind == Token.INTLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      IntLiteral IL = new IntLiteral(spelling, null);
      return IL;
    } else {
      syntacticError("integer literal expected here", "");
      return null;
    }
  }
  //****************************************************************************
  private FloatLiteral parseFloatLiteral() throws SyntaxError {
    if (currentToken.kind == Token.FLOATLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      FloatLiteral FL = new FloatLiteral(spelling, null);
      return FL;
    } else {
      syntacticError("float literal expected here", "");
      return null;
    }
  }
  //****************************************************************************
  private BooleanLiteral parseBooleanLiteral() throws SyntaxError {
    if (currentToken.kind == Token.BOOLEANLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      BooleanLiteral BL = new BooleanLiteral(spelling, null);
      return BL;
    } else {
      syntacticError("boolean literal expected here", "");
      return null;
    }
  }
  //****************************************************************************
  private boolean isTokenType() {
    return currentToken.kind == Token.INT
        || currentToken.kind == Token.FLOAT
        || currentToken.kind == Token.BOOLEAN
        || currentToken.kind == Token.VOID;
  }
  //****************************************************************************
  private boolean isUnaryOperator() {
    return currentToken.kind == Token.PLUS
        || currentToken.kind == Token.MINUS
        || currentToken.kind == Token.NOT;
  }
  //****************************************************************************
  private boolean isLiteral() {
    return currentToken.kind == Token.INTLITERAL
            || currentToken.kind == Token.FLOATLITERAL
            || currentToken.kind == Token.BOOLEANLITERAL
            || currentToken.kind == Token.STRINGLITERAL;
  }
  //****************************************************************************
  private boolean isRelationalOperator() {
    return currentToken.kind == Token.LT ||
            currentToken.kind == Token.LTEQ ||
            currentToken.kind == Token.GT ||
            currentToken.kind == Token.GTEQ;
  }
  //****************************************************************************
}
