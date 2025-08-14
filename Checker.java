package VC.Checker;
import VC.ASTs.*;
import VC.ErrorReporter;
import VC.Scanner.SourcePosition;
import VC.StdEnvironment;
import java.util.Objects;
import java.util.Optional;
public final class Checker implements Visitor {
    
    private enum ErrorMessage {
        MISSING_MAIN("*0: main function is missing"),
        
        MAIN_RETURN_TYPE_NOT_INT("*1: return type of main is not int"),
        IDENTIFIER_REDECLARED("*2: identifier redeclared"),
        IDENTIFIER_DECLARED_VOID("*3: identifier declared void"),
        IDENTIFIER_DECLARED_VOID_ARRAY("*4: identifier declared void[]"),
        
        IDENTIFIER_UNDECLARED("*5: identifier undeclared"),
        
        INCOMPATIBLE_TYPE_FOR_ASSIGNMENT("*6: incompatible type for ="),
        INVALID_LVALUE_IN_ASSIGNMENT("*7: invalid lvalue in assignment"),
        
        INCOMPATIBLE_TYPE_FOR_RETURN("*8: incompatible type for return"),
        INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR("*9: incompatible type for this binary operator"),
        INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR("*10: incompatible type for this unary operator"),
        ARRAY_FUNCTION_AS_SCALAR("*11: attempt to use an array/function as a scalar"),
        SCALAR_FUNCTION_AS_ARRAY("*12: attempt to use a scalar/function as an array"),
        WRONG_TYPE_FOR_ARRAY_INITIALISER("*13: wrong type for element in array initialiser"),
        INVALID_INITIALISER_ARRAY_FOR_SCALAR("*14: invalid initialiser: array initialiser for scalar"),
        INVALID_INITIALISER_SCALAR_FOR_ARRAY("*15: invalid initialiser: scalar initialiser for array"),
        EXCESS_ELEMENTS_IN_ARRAY_INITIALISER("*16: excess elements in array initialiser"),
        ARRAY_SUBSCRIPT_NOT_INTEGER("*17: array subscript is not an integer"),
        ARRAY_SIZE_MISSING("*18: array size missing"),
        SCALAR_ARRAY_AS_FUNCTION("*19: attempt to reference a scalar/array as a function"),
        
        IF_CONDITIONAL_NOT_BOOLEAN("*20: if conditional is not boolean"),
        FOR_CONDITIONAL_NOT_BOOLEAN("*21: for conditional is not boolean"),
        WHILE_CONDITIONAL_NOT_BOOLEAN("*22: while conditional is not boolean"),
        
        BREAK_NOT_IN_LOOP("*23: break must be in a while/for"),
        CONTINUE_NOT_IN_LOOP("*24: continue must be in a while/for"),
        TOO_MANY_ACTUAL_PARAMETERS("*25: too many actual parameters"),
        TOO_FEW_ACTUAL_PARAMETERS("*26: too few actual parameters"),
        WRONG_TYPE_FOR_ACTUAL_PARAMETER("*27: wrong type for actual parameter"),
        
        MISC_1("*28: misc 1"),
        MISC_2("*29: misc 2"),
        
        STATEMENTS_NOT_REACHED("*30: statement(s) not reached"),
        MISSING_RETURN_STATEMENT("*31: missing return statement");
        private final String message;
        ErrorMessage(String message) {
            this.message = message;
        }
        public String getMessage() {
            return message;
        }
    }
    private final SymbolTable idTable;
    private static final SourcePosition dummyPos = new SourcePosition();
    private final ErrorReporter reporter;
    private boolean isMain = false;
    private int nestedLoopDegree = 0;
    public Checker(ErrorReporter reporter) {
        this.reporter = Objects.requireNonNull(reporter, "ErrorReporter must not be null");
        this.idTable = new SymbolTable();
        establishStdEnvironment();
    }
    /* Auxiliary Methods */
    /* 
    * Declares a variable in the symbol table and checks for redeclaration errors.
    */
    private void declareVariable(Ident ident, Decl decl) {
        idTable.retrieveOneLevel(ident.spelling).ifPresent(entry -> 
            
            reporter.reportError(ErrorMessage.IDENTIFIER_REDECLARED.getMessage(), "", dummyPos)
        );
        idTable.insert(ident.spelling, decl);
        ident.visit(this, null);
    }
    //**************************************************************************************************  
    
    public void check(AST ast) {
    ast.visit(this, null);
    }
    //**************************************************************************************************       

    @Override
    public Object visitProgram(Program ast, Object o) {
        ast.FL.visit(this, null);
        if (!isMain) {
            reporter.reportError(ErrorMessage.MISSING_MAIN.getMessage(), "", dummyPos);
        }
        return null;
    }
    //**************************************************************************************************   
    @Override
    public Object visitCompoundStmt(CompoundStmt ast, Object o) {
        boolean openedScope = false;
        if (!(ast.parent instanceof FuncDecl)) {
            idTable.openScope();
            openedScope = true;
        }
        ast.DL.visit(this, o);
        Object retFound = ast.SL.visit(this, o);
        if (openedScope) {
            idTable.closeScope();
        }
        return retFound;
    }
    //**************************************************************************************************        

    @Override
    public Object visitStmtList(StmtList ast, Object o) {
        Object first = ast.S.visit(this, o);
        Object second = ast.SL.visit(this, o);
        if (Boolean.TRUE.equals(first) && ast.SL instanceof StmtList) {
            reporter.reportError(ErrorMessage.STATEMENTS_NOT_REACHED.getMessage(), "", dummyPos);
        }
        if (Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second)) {
            return true;
        }
        return null;
    }
    @Override
    public Object visitExprStmt(ExprStmt ast, Object o) {
        ast.E.visit(this, o);
        return null;
    }
    @Override
    public Object visitEmptyStmt(EmptyStmt ast, Object o) {
        return null;
    }
    @Override
    public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
        return null;
    }
    //**************************************************************************************************@Override
    @Override
    public Object visitEmptyExpr(EmptyExpr ast, Object o) {
        ast.type = (ast.parent instanceof ReturnStmt) ? StdEnvironment.voidType : StdEnvironment.errorType;
        return ast.type;
    }
    @Override
    public Object visitBooleanExpr(BooleanExpr ast, Object o) {
        ast.type = StdEnvironment.booleanType;
        return ast.type;
    }
    @Override
    public Object visitIntExpr(IntExpr ast, Object o) {
        ast.type = StdEnvironment.intType;
        return ast.type;
    }
    @Override
    public Object visitFloatExpr(FloatExpr ast, Object o) {
        ast.type = StdEnvironment.floatType;
        return ast.type;
    }
    @Override
    public Object visitVarExpr(VarExpr ast, Object o) {
        ast.type = (Type) ast.V.visit(this, o);
        return ast.type;
    }
    @Override
    public Object visitStringExpr(StringExpr ast, Object o) {
        ast.type = StdEnvironment.stringType;
        return ast.type;
    }
    //***********************************************************************************************************
    @Override
    public Object visitFuncDecl(FuncDecl ast, Object o) {
        declareVariable(ast.I, ast);
        idTable.openScope();
        ast.PL.visit(this, ast);
        Object retFound = ast.S.visit(this, ast);     
        
        if ("main".equals(ast.I.spelling)) {
            isMain = true;
            if (!ast.T.isIntType()) {
                reporter.reportError(ErrorMessage.MAIN_RETURN_TYPE_NOT_INT.getMessage(), "", dummyPos);
            }
        } else {
            if (!ast.T.isVoidType() &&
                !(retFound instanceof Boolean && (Boolean) retFound)) {
                reporter.reportError(ErrorMessage.MISSING_RETURN_STATEMENT.getMessage(), "", dummyPos);
            }
        }
        idTable.closeScope();
        return null;
    }
    //***********************************************************************************************************
    @Override
    public Object visitDeclList(DeclList ast, Object o) {
        ast.D.visit(this, null);
        ast.DL.visit(this, null);
        return null;
    }
    @Override
    public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
        return null;
    }
    //***********************************************************************************************************
    @Override
    public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
        declareVariable(ast.I, ast);
        Expr varExp = ast.E;
        Ident varIdent = ast.I;
        
        varIdent.visit(this, null);
        varExp.visit(this, ast);
        
        if (ast.T.isVoidType()) {
            reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID.getMessage(), "", dummyPos);
        } 
        else if (ast.T.isArrayType() && ((ArrayType) ast.T).T.isVoidType()) {
            reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(), "", dummyPos);
        }
        else if (!ast.T.isArrayType() && !(varExp instanceof EmptyExpr)) {
            if (!ast.T.assignable(varExp.type)) {
                reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT.getMessage(), "", dummyPos);
            }
        }
        else if (ast.T.isArrayType()) {
            ArrayType arrT = (ArrayType) ast.T;
            
            if (!(varExp instanceof ArrayInitExpr || varExp instanceof EmptyExpr)) {
                reporter.reportError(ErrorMessage.INVALID_INITIALISER_SCALAR_FOR_ARRAY.getMessage(), "", dummyPos);
            } 
            else if (varExp instanceof EmptyExpr && arrT.E.isEmptyExpr()) {
                reporter.reportError(ErrorMessage.ARRAY_SIZE_MISSING.getMessage(), "", dummyPos);
            }
        }
 
        return null;
    }
//***********************************************************************************************************
    @Override
    public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
        declareVariable(ast.I, ast);
        Expr varExp = ast.E;
        Ident varIdent = ast.I;
        
        varIdent.visit(this, null);
        varExp.visit(this, ast);
        
        if (ast.T.isVoidType()) {
            reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID.getMessage(), "", dummyPos);
        } 
        else if (ast.T.isArrayType() && ((ArrayType) ast.T).T.isVoidType()) {
            reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(), "", dummyPos);
        }
        else if (!ast.T.isArrayType() && !(varExp instanceof EmptyExpr)) {
            if (!ast.T.assignable(varExp.type)) {
                reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT.getMessage(), "", dummyPos);
            }
        }
        else if (ast.T.isArrayType()) {
            ArrayType arrT = (ArrayType) ast.T;
            
            if (!(varExp instanceof ArrayInitExpr || varExp instanceof EmptyExpr)) {
                reporter.reportError(ErrorMessage.INVALID_INITIALISER_SCALAR_FOR_ARRAY.getMessage(), "", dummyPos);
            } 
            else if (varExp instanceof EmptyExpr && arrT.E.isEmptyExpr()) {
                reporter.reportError(ErrorMessage.ARRAY_SIZE_MISSING.getMessage(), "", dummyPos);
            }
        }

        return null;
    }
//**************************************************************************************************@Override
    @Override
    public Object visitParaList(ParaList ast, Object o) {
        ast.P.visit(this, null);
        ast.PL.visit(this, null);
        return null;
    }
    @Override
    public Object visitParaDecl(ParaDecl ast, Object o) {
        declareVariable(ast.I, ast);
        if (ast.T.isVoidType()) {
            reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID.getMessage(), "", dummyPos);
        } else if (ast.T.isArrayType()) {
            if (((ArrayType) ast.T).T.isVoidType()) {
                reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(), "", dummyPos);
            }
        }
        return null;
    }
    @Override
    public Object visitEmptyParaList(EmptyParaList ast, Object o) {
        return null;
    }
//**************************************************************************************************//**************************************************************************************************@Override
    @Override
    public Object visitErrorType(ErrorType ast, Object o) {
        return StdEnvironment.errorType;
    }
    @Override
    public Object visitBooleanType(BooleanType ast, Object o) {
        return StdEnvironment.booleanType;
    }
    @Override
    public Object visitIntType(IntType ast, Object o) {
        return StdEnvironment.intType;
    }
    @Override
    public Object visitFloatType(FloatType ast, Object o) {
        return StdEnvironment.floatType;
    }
    @Override
    public Object visitStringType(StringType ast, Object o) {
        return StdEnvironment.stringType;
    }
    @Override
    public Object visitVoidType(VoidType ast, Object o) {
        return StdEnvironment.voidType;
    }
    @Override
    public Object visitArrayType(ArrayType ast, Object o) {
        Type type = (Type) ast.T.visit(this, o);
        ast.E.visit(this, o);
        return type;
    }
//**************************************************************************************************@Override
    @Override
    public Object visitIdent(Ident I, Object o) {
        Optional<IdEntry> binding = idTable.retrieve(I.spelling);
        if (binding.isEmpty()) {
            reporter.reportError(ErrorMessage.IDENTIFIER_UNDECLARED.getMessage(), "", dummyPos);
            return null;
        }
        I.decl = binding.get().attr;  
        return binding.get().attr;
    }
    @Override
    public Object visitBooleanLiteral(BooleanLiteral SL, Object o) {
        return StdEnvironment.booleanType;
    }
    @Override
    public Object visitIntLiteral(IntLiteral IL, Object o) {
        return StdEnvironment.intType;
    }
    @Override
    public Object visitFloatLiteral(FloatLiteral IL, Object o) {
        return StdEnvironment.floatType;
    }
    @Override
    public Object visitStringLiteral(StringLiteral IL, Object o) {
        return StdEnvironment.stringType;
    }
    @Override
    public Object visitOperator(Operator O, Object o) {
        return null;
    }
    private FuncDecl declareStdFunc(Type resultType, String id, VC.ASTs.List pl) {
        var binding = new FuncDecl(resultType, new Ident(id, dummyPos), pl,
                new EmptyStmt(dummyPos), dummyPos);
        idTable.insert(id, binding);
        return binding;
    }
    private final static Ident dummyI = new Ident("x", dummyPos);
//************************************************************************************************** 
private void establishStdEnvironment() {
    
    
    StdEnvironment.booleanType = new BooleanType(dummyPos);
    StdEnvironment.intType = new IntType(dummyPos);
    StdEnvironment.floatType = new FloatType(dummyPos);
    StdEnvironment.stringType = new StringType(dummyPos);
    StdEnvironment.voidType = new VoidType(dummyPos);
    StdEnvironment.errorType = new ErrorType(dummyPos);
    
    StdEnvironment.getIntDecl = declareStdFunc(StdEnvironment.intType,
            "getInt", new EmptyParaList(dummyPos));
    StdEnvironment.putIntDecl = declareStdFunc(StdEnvironment.voidType,
            "putInt", new ParaList(
                    new ParaDecl(StdEnvironment.intType, dummyI, dummyPos),
                    new EmptyParaList(dummyPos), dummyPos));
    StdEnvironment.putIntLnDecl = declareStdFunc(StdEnvironment.voidType,
            "putIntLn", new ParaList(
                    new ParaDecl(StdEnvironment.intType, dummyI, dummyPos),
                    new EmptyParaList(dummyPos), dummyPos));
    StdEnvironment.getFloatDecl = declareStdFunc(StdEnvironment.floatType,
            "getFloat", new EmptyParaList(dummyPos));
    StdEnvironment.putFloatDecl = declareStdFunc(StdEnvironment.voidType,
            "putFloat", new ParaList(
                    new ParaDecl(StdEnvironment.floatType, dummyI, dummyPos),
                    new EmptyParaList(dummyPos), dummyPos));
    StdEnvironment.putFloatLnDecl = declareStdFunc(StdEnvironment.voidType,
            "putFloatLn", new ParaList(
                    new ParaDecl(StdEnvironment.floatType, dummyI, dummyPos),
                    new EmptyParaList(dummyPos), dummyPos));
    StdEnvironment.putBoolDecl = declareStdFunc(StdEnvironment.voidType,
            "putBool", new ParaList(
                    new ParaDecl(StdEnvironment.booleanType, dummyI, dummyPos),
                    new EmptyParaList(dummyPos), dummyPos));
    StdEnvironment.putBoolLnDecl = declareStdFunc(StdEnvironment.voidType,
            "putBoolLn", new ParaList(
                    new ParaDecl(StdEnvironment.booleanType, dummyI, dummyPos),
                    new EmptyParaList(dummyPos), dummyPos));
    StdEnvironment.putStringLnDecl = declareStdFunc(StdEnvironment.voidType,
            "putStringLn", new ParaList(
                    new ParaDecl(StdEnvironment.stringType, dummyI, dummyPos),
                    new EmptyParaList(dummyPos), dummyPos));
    StdEnvironment.putStringDecl = declareStdFunc(StdEnvironment.voidType,
            "putString", new ParaList(
                    new ParaDecl(StdEnvironment.stringType, dummyI, dummyPos),
                    new EmptyParaList(dummyPos), dummyPos));
    StdEnvironment.putLnDecl = declareStdFunc(StdEnvironment.voidType,
            "putLn", new EmptyParaList(dummyPos));
}
//************************************************************************************************** 
@Override
public Object visitEmptyArrayExprList(EmptyArrayExprList ast, Object o) {
    return null; 
}
//************************************************************************************************** 
@Override
public Object visitEmptyArgList(EmptyArgList ast, Object o) {
    return null;
}
//************************************************************************************************** 
@Override
public Object visitIfStmt(IfStmt ast, Object o) {
    if (!((Type) ast.E.visit(this, o)).isBooleanType()) {
        reporter.reportError(ErrorMessage.IF_CONDITIONAL_NOT_BOOLEAN.getMessage(), "", dummyPos);
    }
    Object r1 = ast.S1.visit(this, o);
    Object r2 = ast.S2.visit(this, o);
    return r1 instanceof Boolean && (Boolean) r1 &&
            r2 instanceof Boolean && (Boolean) r2;
}
//************************************************************************************************** 
@Override
public Object visitWhileStmt(WhileStmt ast, Object o) {
    if (!((Type) ast.E.visit(this, o)).isBooleanType()) {
        reporter.reportError(ErrorMessage.WHILE_CONDITIONAL_NOT_BOOLEAN.getMessage(), "", dummyPos);
    }
    nestedLoopDegree++;
    ast.S.visit(this, o);
    nestedLoopDegree--;
    return false;
}
//************************************************************************************************** 
@Override
public Object visitForStmt(ForStmt ast, Object o) {
    ast.E1.visit(this, o);
    if (!ast.E2.isEmptyExpr()) {
        if (!((Type) ast.E2.visit(this, o)).isBooleanType()) {
            reporter.reportError(ErrorMessage.FOR_CONDITIONAL_NOT_BOOLEAN.getMessage(), "", dummyPos);
        }
    }
    ast.E3.visit(this, o);
    nestedLoopDegree++;
    ast.S.visit(this, o);
    nestedLoopDegree--;
    return false;
}
//************************************************************************************************** 
@Override
public Object visitBreakStmt(BreakStmt ast, Object o) {
    if (nestedLoopDegree <= 0) {
        reporter.reportError(ErrorMessage.BREAK_NOT_IN_LOOP.getMessage(), "", dummyPos);
    }
    return null;
}
@Override
public Object visitContinueStmt(ContinueStmt ast, Object o) {
    if (nestedLoopDegree <= 0) {
        reporter.reportError(ErrorMessage.CONTINUE_NOT_IN_LOOP.getMessage(), "", dummyPos);
    }
    return null;
}
//************************************************************************************************** 
@Override
public Object visitReturnStmt(ReturnStmt ast, Object o) {
    Type retT = (Type) ast.E.visit(this, o);
    FuncDecl currFunc = (FuncDecl) o; 
    Type currFuncRT = currFunc.T;
    
    if (currFuncRT.isVoidType()) {
        if (!(ast.E instanceof EmptyExpr)) {
            reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_RETURN.getMessage(), "", ast.position);
        }
    } else {
        if (ast.E instanceof EmptyExpr) {
            reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_RETURN.getMessage(), "", ast.position);
        } else if (currFuncRT.isFloatType() && retT.isIntType()) {
            
            ast.E = coerceIntToFloat(ast.E);
        } else if (!currFuncRT.assignable(retT)) {
            reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_RETURN.getMessage(), "", ast.position);
        }
    }
    return true;  
}
//************************************************************************************************** 
private Expr coerceIntToFloat(Expr expr) {
    UnaryExpr newNode = new UnaryExpr(new Operator("coerceIntToFloat", dummyPos), expr, dummyPos);
    newNode.type = StdEnvironment.floatType;
    return newNode;
}
//************************************************************************************************** 
@Override
public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
    return false; 
}
//************************************************************************************************** 
@Override
public Object visitUnaryExpr(UnaryExpr ast, Object o) {
    Type exprT = (Type) ast.E.visit(this, o);
    scalarCheck(ast.E);
    switch (ast.O.spelling) {
        case "+", "-" -> {
            if (exprT.isIntType()) {
                ast.type = StdEnvironment.intType;
            } else if (exprT.isFloatType()) {
                ast.type = StdEnvironment.floatType;
            } else {
                reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR.getMessage(), "", dummyPos);
                ast.type = StdEnvironment.errorType;
            }
            }
        case "!" -> {
            if (exprT.isBooleanType()) {
                ast.type = StdEnvironment.booleanType;
            } else {
                reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR.getMessage(), "", dummyPos);
                ast.type = StdEnvironment.errorType;
            }
            }
        default -> {
            reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR.getMessage(), "", dummyPos);
            ast.type = StdEnvironment.errorType;
            }
    }
    return ast.type;
}
//************************************************************************************************** 
@Override
public Object visitBinaryExpr(BinaryExpr ast, Object o) {
    Type leftT = (Type) ast.E1.visit(this, o);
    Type rightT = (Type) ast.E2.visit(this, o);
    
    if(leftT != null && rightT != null) {
        if (leftT.isErrorType() && rightT.isErrorType()) {
            ast.type = StdEnvironment.errorType;
            reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", dummyPos);
            return ast.type;
        }
    } else {
        ast.type = StdEnvironment.errorType;
        return ast.type;
    }
    scalarCheck(ast.E1);
    scalarCheck(ast.E2);
    
    if (leftT.isIntType() && rightT.isFloatType()) {
        ast.E1 = coerceIntToFloat(ast.E1);
        leftT = StdEnvironment.floatType;
    } 
    else if (leftT.isFloatType() && rightT.isIntType()) {
        ast.E2 = coerceIntToFloat(ast.E2);
        rightT = StdEnvironment.floatType;
    }
    
    switch (ast.O.spelling) {
        case "+", "-", "*", "/" -> {
            if (leftT.equals(rightT) && (leftT.isIntType() || leftT.isFloatType())) {
                ast.type = leftT;
            } else {
                reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", dummyPos);
            }
            }
        case "==", "!=" -> {
            if (leftT.equals(rightT) &&
                    (leftT.isIntType() || leftT.isFloatType() || leftT.isBooleanType())) {
                ast.type = StdEnvironment.booleanType;
            } else {
                reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", dummyPos);
            }
            }
        case "<", ">", "<=", ">=" -> {
            if (leftT.equals(rightT) && (leftT.isIntType() || leftT.isFloatType())) {
                ast.type = StdEnvironment.booleanType;
            } else {
                reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", dummyPos);
            }
            }
        case "&&", "||" -> {
            if (leftT.isBooleanType() && rightT.isBooleanType()) {
                ast.type = StdEnvironment.booleanType;
            } else {
                reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", dummyPos);
            }
            }
       
        default -> reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", dummyPos);
    }
    return ast.type;
}
//************************************************************************************************** 
    @Override
    public Object visitArrayExprList(ArrayExprList ast, Object o) {
        
        Type expT = ((ArrayType) o).T;
        int count = 1;
        
        if (!expT.assignable((Type) ast.E.visit(this, o))) {
            reporter.reportError(ErrorMessage.WRONG_TYPE_FOR_ARRAY_INITIALISER.getMessage(), "", dummyPos);
        }
        if (ast.EL != null) {
            Object cObj = ast.EL.visit(this, o);
            if (cObj instanceof Integer integer) {
                count += integer;
            }
        }
        
        ((ArrayType) o).E = new IntExpr(new IntLiteral(String.valueOf(count), dummyPos), dummyPos);
        return expT;
    }
//************************************************************************************************** 
    @Override
    public Object visitArrayInitExpr(ArrayInitExpr ast, Object o) {
        
        if (!(o instanceof Decl) || !((Decl) o).T.isArrayType()) {
            reporter.reportError(ErrorMessage.INVALID_INITIALISER_ARRAY_FOR_SCALAR.getMessage(), "", dummyPos);
            return StdEnvironment.errorType;
        }
        
        ArrayType arrT = (ArrayType) ((Decl) o).T;
        Expr ogExpr = arrT.E;
        
        arrT.E = new IntExpr(new IntLiteral("0", dummyPos), dummyPos);
        int actSize = arrayActualSize(ast.IL, arrT);
        arrT.E = ogExpr;
        
        if (!ogExpr.isEmptyExpr()) {
            int ogSize = Integer.parseInt(((IntLiteral) ((IntExpr) ogExpr).IL).spelling);
            if (actSize > ogSize) {
                reporter.reportError(ErrorMessage.EXCESS_ELEMENTS_IN_ARRAY_INITIALISER.getMessage(), "", ast.position);
            }
        }
        return ast.type = arrT.T;
    }

    private int arrayActualSize(VC.ASTs.List list, ArrayType arrT) {
        int count = 0;
    
        while (!(list instanceof EmptyArrayExprList)) {

            Type t = (Type) ((ArrayExprList) list).E.visit(this, arrT);
            if (!arrT.T.assignable(t)) {
                reporter.reportError(ErrorMessage.WRONG_TYPE_FOR_ARRAY_INITIALISER.getMessage(), "", dummyPos);
            }
    
            count++;
            list = ((ArrayExprList) list).EL;
        }
    
        return count;
    }    

    //************************************************************************************************** 
    @Override
    public Object visitArrayExpr(ArrayExpr ast, Object o) {
        
        Type indexT = (Type) ast.E.visit(this, o);
        
        Type arrayT = (Type) ast.V.visit(this, o);
        
        if (indexT.isErrorType() || arrayT == null || arrayT.isErrorType()) {
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }
        
        if (!indexT.isIntType()) {
            reporter.reportError(ErrorMessage.ARRAY_SUBSCRIPT_NOT_INTEGER.getMessage(), "", dummyPos);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }
        
        if (!arrayT.isArrayType()) {
            reporter.reportError(ErrorMessage.SCALAR_FUNCTION_AS_ARRAY.getMessage(), "", dummyPos);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }
        
        ast.type = ((ArrayType) arrayT).T;
        return ast.type;
    }
    //************************************************************************************************** 
    private int countArgs(List argList) {
        int count = 0;
        while (!(argList.isEmptyArgList())) {
            count++;
            argList = ((ArgList) argList).AL;
        }
        return count;
    }
    private int countParams(ParaList params) {
        int count = 0;
        ParaList curr = params;
        while (!params.isEmptyParaList()) {
            count++;
            if (curr.PL instanceof ParaList paraList) {
                curr = paraList;
            } else {
                break;
            }
        } 
        return count;
    }

    private void matchArgsToParams(ArgList args, ParaList params) {
        
        ArgList currArgs = args;
        ParaList currParams = params;

        int paramCount = countParams(params);

        for (int i = 0; i < paramCount; i++) {
            ParaList tempParams = currParams;
            for (int j = 0; j < i; j++) {
                tempParams = (ParaList) tempParams.PL;
            }

            ParaDecl param = tempParams.P;
            Arg arg = currArgs.A;

            checkArgToParam(arg, param);

            if (currArgs.AL instanceof ArgList argList) {
                currArgs = argList;
            } else {
                break;
            }
        }
    }

    private void checkArgToParam(Arg arg, ParaDecl param) {
        Type argT = (Type) arg.visit(this, null);
        Type paramT = param.T;

        if (argT.isArrayType() && paramT.isArrayType()) {
            Type argElemT = ((ArrayType) argT).T;
            Type paramElemT = ((ArrayType) paramT).T;

            if (!argElemT.equals(paramElemT)) {
                if (!(argElemT.isIntType() && paramElemT.isFloatType())) {
                    reporter.reportError(ErrorMessage.WRONG_TYPE_FOR_ACTUAL_PARAMETER.getMessage(), "", dummyPos);
                }
            }
        } else if (!paramT.equals(argT)) {
            if (paramT.isFloatType() && argT.isIntType()) {
                arg.E = coerceIntToFloat(arg.E);
            } else {
                reporter.reportError(ErrorMessage.WRONG_TYPE_FOR_ACTUAL_PARAMETER.getMessage(), "", dummyPos);
            }
        }
    }

    @Override
    public Object visitCallExpr(CallExpr ast, Object o) {
        ast.type = StdEnvironment.errorType;
        
        Decl decl = (Decl) ast.I.visit(this, null);
        
        if (decl == null || !(decl instanceof FuncDecl)) {
            reporter.reportError(ErrorMessage.SCALAR_ARRAY_AS_FUNCTION.getMessage(), "", dummyPos);
            return ast.type;
        }
        FuncDecl func = (FuncDecl) decl;
        ast.type = func.T;  
        
        int numArgs = 0;
        int numParams = 0;
        
        if (!(ast.AL instanceof EmptyArgList)) {
            numArgs = countArgs(ast.AL);
        } 
        
        if (!(func.PL instanceof EmptyParaList)) {
            numParams = countParams((ParaList) func.PL);
        } 

        if (numArgs < numParams) {
            reporter.reportError(ErrorMessage.TOO_FEW_ACTUAL_PARAMETERS.getMessage(), "", dummyPos);
        }
        
        else if (numArgs > numParams) {
            reporter.reportError(ErrorMessage.TOO_MANY_ACTUAL_PARAMETERS.getMessage(), "", dummyPos);
        }
        
        if (ast.AL instanceof ArgList && func.PL instanceof ParaList) {
            matchArgsToParams((ArgList) ast.AL, (ParaList) func.PL);
        }
        
        return ast.type;
    }
    //************************************************************************************************** `
    public void scalarCheck(Expr ast) {
        
        if (ast instanceof VarExpr varExpr) {
            Var var = varExpr.V;
        
            if (var instanceof SimpleVar simpleVar) {
                Ident ident = simpleVar.I;
                
                Optional<IdEntry> entry = idTable.retrieve(ident.spelling);
                if (entry.isEmpty()) {
                    return;
                }
                
                Decl decl = (Decl) entry.get().attr;
                Type type = decl.T;
                if (decl instanceof FuncDecl || type.isArrayType()) {
                    reporter.reportError(ErrorMessage.ARRAY_FUNCTION_AS_SCALAR.getMessage(), "", dummyPos);
                }
            }
        }
    }
    private boolean actuallyVar(Expr expr) {
        Var var = null;
        if (expr instanceof VarExpr varExpr) {
            var = varExpr.V;
        } 
        if (expr instanceof ArrayExpr arrayExpr) {
            var = arrayExpr.V;
        }
        if (var instanceof SimpleVar simpleVar) {
            Ident ident = simpleVar.I;
            Optional<IdEntry> entry = idTable.retrieve(ident.spelling);
            if (entry.isPresent()) {
                Decl decl = (Decl) entry.get().attr;
                return decl.isLocalVarDecl() || decl.isGlobalVarDecl() || decl.isParaDecl();
            }
        }
        return false;
    }
    @Override
    public Object visitAssignExpr(AssignExpr ast, Object o) {
        Type ltype = (Type) ast.E1.visit(this, o);
        Type rtype = (Type) ast.E2.visit(this, o);
        
        scalarCheck(ast.E1);
        scalarCheck(ast.E2);
        
        if (ltype == null || rtype == null) {
            ast.type = StdEnvironment.errorType;
            return ast.type;
        } 

        if (!(ast.E1 instanceof VarExpr || ast.E1 instanceof ArrayExpr)) {
            reporter.reportError(ErrorMessage.INVALID_LVALUE_IN_ASSIGNMENT.getMessage(), "", dummyPos);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }
        else if (!actuallyVar(ast.E1)) {
            reporter.reportError(ErrorMessage.INVALID_LVALUE_IN_ASSIGNMENT.getMessage(), "", dummyPos);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }
        else if (ltype.isErrorType() || rtype.isErrorType()) {
            reporter.reportError(ErrorMessage.INVALID_LVALUE_IN_ASSIGNMENT.getMessage(), "", dummyPos);
            ast.type = StdEnvironment.errorType;
            return ast.type; 
        }
        else if (ltype.isFloatType() && rtype.isIntType()) {
            ast.E2 = coerceIntToFloat(ast.E2);
        }
        else if (!ltype.assignable(rtype)) {
            reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT.getMessage(), "", dummyPos);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }
        ast.type = ast.E1.type;
        return ast.type;
    }
    //************************************************************************************************** 
    @Override
    public Object visitArgList(ArgList ast, Object o) {
        ast.A.visit(this, o);
        ast.AL.visit(this, o);
        return null;
    }
    //************************************************************************************************** 
    @Override
    public Object visitArg(Arg ast, Object o) {
        ast.type = (Type) ast.E.visit(this, o);
        if (ast.type == null) {
            ast.type = StdEnvironment.errorType;
        }
        return ast.type;        
    }
    //************************************************************************************************** 
    @Override
    public Object visitSimpleVar(SimpleVar ast, Object o) {
        Decl binding = (Decl) visitIdent(ast.I, o);
        if (binding == null) {
            ast.type = StdEnvironment.errorType;
        } else {
            ast.type = binding.T;
        }
        return ast.type;
    }
    //************************************************************************************************** 
    }
