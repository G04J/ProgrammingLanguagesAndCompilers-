# VC Language Compiler

A full-featured compiler for the VC programming language that translates source code to JVM bytecode. Built from the ground up with hand-written lexical analysis, recursive descent parsing, semantic analysis with type inference, and template-based code generation.

## Overview

This compiler implements a complete five-phase compilation pipeline: lexical analysis → syntax analysis → AST construction → semantic checking → JVM code generation. The implementation handles a C-like language with strong static typing, arrays, functions, control flow, and produces executable JVM bytecode.

**Language**: VC (C-like syntax with static typing)  
**Target**: Java Virtual Machine (Jasmin assembly)  
**Implementation**: ~3,000 lines of Java across 5 major components

## Architecture & Technical Implementation

### 1. Lexical Analysis (`Scanner.java` - 484 lines)

Hand-coded deterministic finite automaton (DFA) scanner implementing maximal munch tokenization.

#### Implementation Approach
- **State-based scanning**: Main dispatch via `nextToken()` with character-class routing to specialized handlers
- **N-character lookahead**: `inspectChar(n)` method enables arbitrary lookahead without consuming input stream
- **Incremental lexeme construction**: Uses `StringBuilder` to accumulate token spelling character-by-character
- **Precise position tracking**: Maintains line/column counters with proper tab expansion (8-character tab stops, offset-based calculation: `currColPoss += 8 - ((currColPoss - 1) % 8)`)

#### Key Algorithms

**Numeric Literal Recognition**:
```java
private int numbericElement() {
    // State machine for: digit+ ('.' digit*)? ([eE] [+-]? digit+)?
    boolean isFloat = false;
    
    // Integer part
    while (Character.isDigit(currentChar)) {
        currentSpelling.append(currentChar);
        accept();
    }
    
    // Fractional part (decimal point triggers float)
    if (currentChar == '.') {
        isFloat = true;
        currentSpelling.append(currentChar);
        accept();
        while (Character.isDigit(currentChar)) { /* ... */ }
    }
    
    // Exponent part (handles e10, e+10, e-10)
    if (currentChar == 'e' || currentChar == 'E') {
        // Two-character lookahead for e+/e- patterns
        if (inspectChar(1) == '-' || inspectChar(1) == '+') {
            if (Character.isDigit(inspectChar(2))) {
                isFloat = true;
                // Consume 'e', then sign, then digits
            }
        }
    }
    
    return isFloat ? Token.FLOATLITERAL : Token.INTLITERAL;
}
```

**Maximal Munch for Operators**:
Demonstrates the critical use of lookahead to disambiguate single vs. double-character operators:
```java
case '!':
    if (inspectChar(1) == '=') {
        // != (two characters)
        currentSpelling.append(Token.spell(Token.NOTEQ));
        accept(); accept();
        return Token.NOTEQ;
    } else {
        // ! (one character)
        currentSpelling.append(Token.spell(Token.NOT));
        accept();
        return Token.NOT;
    }
```
This pattern repeats for: `=` vs `==`, `<` vs `<=`, `>` vs `>=`, `&` vs `&&`, `|` vs `||`

**String Literal Processing with Escape Sequences**:
```java
private int stringLiteralfunc() {
    accept();  // consume opening "
    boolean isEscChar = false;
    
    while (true) {
        // Error: unterminated string
        if (currentChar == SourceFile.eof || currentChar == '\n') {
            errorReporter.reportError(...);
            return Token.ERROR;
        }
        
        if (!isEscChar) {
            if (currentChar == '\\') {
                isEscChar = true;  // next char is escaped
                accept();
                continue;
            }
            if (currentChar == '"') {
                accept();  // consume closing "
                return Token.STRINGLITERAL;
            }
        } else {
            // Process escape: \n, \t, \b, \f, \r, \\, \", \'
            isEscChar = false;
            switch(currentChar) {
                case 'n': currentSpelling.append('\n'); break;
                case 't': currentSpelling.append('\t'); break;
                // ... map escape to actual character
                default: 
                    errorReporter.reportError("Invalid escape sequence", ...);
                    return Token.ERROR;
            }
            accept();
            continue;
        }
        
        currentSpelling.append(currentChar);
        accept();
    }
}
```

**Comment Handling**:
```java
private void skipSpaceAndComments() {
    while (true) {
        if (Character.isWhitespace(currentChar)) {
            accept(); continue;
        }
        
        if (currentChar == '/') {
            // Single-line: // ... \n
            if (inspectChar(1) == '/') {
                while (currentChar != '\n' && currentChar != SourceFile.eof) {
                    accept();
                }
                continue;
            }
            // Multi-line: /* ... */
            if (inspectChar(1) == '*') {
                accept(); accept();  // consume /*
                while (true) {
                    if (currentChar == '*' && inspectChar(1) == '/') {
                        accept(); accept();  // consume */
                        break;
                    }
                    if (currentChar == SourceFile.eof) {
                        errorReporter.reportError("Unterminated comment", ...);
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
```

#### Error Recovery
Reports four categories of lexical errors with precise source positions:
- Illegal characters (any char not starting a valid token)
- Unterminated string literals (EOF or newline before closing `"`)
- Unterminated block comments (EOF before `*/`)
- Invalid escape sequences (backslash followed by non-escape character)

### 2. Syntax Analysis (`Recogniser.java` - 549 lines, `Parser.java` - 710 lines)

Two-stage implementation: recognizer for syntax validation, then full parser for AST construction.

#### Recogniser: Predictive Recursive Descent Parser

**Grammar Transformation**:
Converted left-recursive productions to LL(1) iterative form. Example transformation:
```
// Original (left-recursive):
additive-expr -> additive-expr + multiplicative-expr
               | multiplicative-expr

// Transformed (LL(1)):
additive-expr -> multiplicative-expr ('+' multiplicative-expr)*
```

**Implementation Pattern**:
```java
void parseAdditiveExpression() throws SyntaxError {
    parseMultiplicativeExpression();
    // Kleene star (*) becomes while loop
    while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
        match(currentToken.kind);
        parseMultiplicativeExpression();
    }
}
```

**Expression Precedence Hierarchy** (lowest to highest):
```
parseAssignExpr()           // = (right-associative via recursion)
  ↓
parseLogicalOrExpression()  // ||
  ↓
parseLogicalAndExpression() // &&
  ↓
parseEqualityExpression()   // == !=
  ↓
parseRelationalExpression() // < <= > >=
  ↓
parseAdditiveExpression()   // + -
  ↓
parseMultiplicativeExpression() // * /
  ↓
parseUnaryExpression()      // ! - + (right-associative via recursion)
  ↓
parsePrimaryExpression()    // literals, identifiers, (expr)
```

**Dangling-Else Resolution**:
```java
void parseIfStmt() throws SyntaxError {
    match(Token.IF);
    match(Token.LPAREN);
    parseExpr();
    match(Token.RPAREN);
    parseStmt();  // then-branch
    
    // Greedy matching: else binds to nearest if
    if (currentToken.kind == Token.ELSE) {
        match(Token.ELSE);
        parseStmt();  // else-branch
    }
}
```

#### Parser: AST Construction

**Immutable List Construction**:
Uses functional-style immutable linked lists. New elements prepended in reverse order, then reversed:
```java
private List createArgList(java.util.List<Arg> args) {
    List fl = new EmptyArgList(null);
    // Build list backwards (right-to-left)
    for (int i = args.size() - 1; i >= 0; i--) {
        fl = new ArgList(args.get(i), fl, null);
    }
    return fl;
}
```

**Left-Associative Binary Expression Trees**:
```java
private Expr parseAdditiveExpr() throws SyntaxError {
    Expr LExpr = parseMultiplicativeExpr();
    
    // Iteratively build left-leaning tree
    while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
        Operator O = acceptOperator();
        Expr RExpr = parseMultiplicativeExpr();
        // Previous result becomes left child
        LExpr = new BinaryExpr(LExpr, O, RExpr, null);
    }
    
    return LExpr;
}
// Example: 1 + 2 + 3 produces tree: ((1 + 2) + 3)
```

**Right-Associative Assignment**:
```java
private Expr parseAssignmentExpr() throws SyntaxError {
    Expr LExpr = parseLogicalOrExpr();
    
    if (currentToken.kind == Token.EQ) {
        Operator O = acceptOperator();
        // Recursive call makes right-associative
        Expr RExpr = parseAssignmentExpr();
        return new AssignExpr(LExpr, RExpr, null);
    }
    
    return LExpr;
}
// Example: a = b = 5 produces tree: (a = (b = 5))
```

**Type vs. Variable Declaration Disambiguation**:
```java
private List parseDecls(boolean VarDec) throws SyntaxError {
    Type tAST = parseType();     // read type
    Ident identAST = parseIdent(); // read identifier
    
    // Lookahead determines function vs. variable
    if (currentToken.kind == Token.LPAREN) {
        // identifier( -> function declaration
        return createDeclList(parseFuncDecl(tAST, identAST), tAST, VarDec);
    } else {
        // identifier[, identifier; -> variable declaration
        return createDeclList(parseVarDecl(tAST, identAST, VarDec), tAST, VarDec);
    }
}
```

### 3. Semantic Analysis (`Checker.java` - 909 lines)

Single-pass type checker and scope analyzer using the Visitor pattern to traverse the decorated AST.

#### Symbol Table Implementation

**Hash-based stack structure** for nested scopes:
```java
public class SymbolTable {
    private java.util.Stack<java.util.HashMap<String, IdEntry>> table;
    private int level;  // current scope depth
    
    public void openScope() {
        table.push(new java.util.HashMap<>());
        level++;
    }
    
    public void closeScope() {
        table.pop();
        level--;
    }
    
    // Search current scope only (for redeclaration check)
    public Optional<IdEntry> retrieveOneLevel(String id) {
        return Optional.ofNullable(table.peek().get(id));
    }
    
    // Search from innermost to outermost scope
    public Optional<IdEntry> retrieve(String id) {
        for (int i = table.size() - 1; i >= 0; i--) {
            IdEntry entry = table.get(i).get(id);
            if (entry != null) return Optional.of(entry);
        }
        return Optional.empty();
    }
}
```

**Standard Environment Initialization**:
```java
private void establishStdEnvironment() {
    // Pre-populate symbol table with built-in functions
    // Example: putIntLn(int) -> void
    FuncDecl putIntLn = new FuncDecl(
        new VoidType(dummyPos),
        new Ident("putIntLn", dummyPos),
        new ParaList(
            new ParaDecl(new IntType(dummyPos), 
                         new Ident("", dummyPos), 
                         dummyPos),
            new EmptyParaList(dummyPos),
            dummyPos
        ),
        new EmptyStmt(dummyPos),
        dummyPos
    );
    idTable.insert("putIntLn", putIntLn);
    // ... repeat for getInt, putFloat, putString, etc.
}
```

#### Type Inference and Checking

**Binary Expression Type Rules**:
```java
@Override
public Object visitBinaryExpr(BinaryExpr ast, Object o) {
    Type leftT = (Type) ast.E1.visit(this, o);
    Type rightT = (Type) ast.E2.visit(this, o);
    
    // Implicit int->float coercion
    if (leftT.isIntType() && rightT.isFloatType()) {
        ast.E1 = coerceIntToFloat(ast.E1);
        leftT = StdEnvironment.floatType;
    } else if (leftT.isFloatType() && rightT.isIntType()) {
        ast.E2 = coerceIntToFloat(ast.E2);
        rightT = StdEnvironment.floatType;
    }
    
    switch (ast.O.spelling) {
        case "+", "-", "*", "/" -> {
            if (leftT.equals(rightT) && 
                (leftT.isIntType() || leftT.isFloatType())) {
                ast.type = leftT;  // result type = operand type
            } else {
                reportError(INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR);
            }
        }
        case "==", "!=" -> {
            if (leftT.equals(rightT) && 
                (leftT.isIntType() || leftT.isFloatType() || 
                 leftT.isBooleanType())) {
                ast.type = StdEnvironment.booleanType;
            } else {
                reportError(INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR);
            }
        }
        case "&&", "||" -> {
            if (leftT.isBooleanType() && rightT.isBooleanType()) {
                ast.type = StdEnvironment.booleanType;
            } else {
                reportError(INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR);
            }
        }
    }
    return ast.type;
}
```

**Type Coercion Node Insertion**:
```java
private Expr coerceIntToFloat(Expr expr) {
    // Insert i2f conversion node into AST
    UnaryExpr coercion = new UnaryExpr(
        new Operator("i2f", dummyPos),  // special operator for codegen
        expr,
        dummyPos
    );
    coercion.type = StdEnvironment.floatType;
    return coercion;
}
```

#### Control Flow Analysis

**Dead Code Detection**:
```java
@Override
public Object visitStmtList(StmtList ast, Object o) {
    Object first = ast.S.visit(this, o);   // returns true if return found
    Object second = ast.SL.visit(this, o);
    
    // If first statement returns, subsequent statements unreachable
    if (Boolean.TRUE.equals(first) && ast.SL instanceof StmtList) {
        reporter.reportError(STATEMENTS_NOT_REACHED);
    }
    
    // Propagate "returns" status upward
    if (Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second)) {
        return true;
    }
    return null;
}
```

**Loop Context Tracking**:
```java
private int nestedLoopDegree = 0;

@Override
public Object visitWhileStmt(WhileStmt ast, Object o) {
    ast.E.visit(this, o);
    
    nestedLoopDegree++;    // entering loop
    ast.S.visit(this, o);
    nestedLoopDegree--;    // exiting loop
    
    return null;
}

@Override
public Object visitBreakStmt(BreakStmt ast, Object o) {
    if (nestedLoopDegree <= 0) {
        reporter.reportError(BREAK_NOT_IN_LOOP);
    }
    return null;
}
```

**Array Initializer Validation**:
```java
@Override
public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
    ast.E.visit(this, ast);
    
    if (ast.T.isArrayType()) {
        ArrayType arrT = (ArrayType) ast.T;
        
        // Check initializer type matches declaration
        if (!(ast.E instanceof ArrayInitExpr || ast.E instanceof EmptyExpr)) {
            reporter.reportError(INVALID_INITIALISER_SCALAR_FOR_ARRAY);
        }
        
        // Check array size is specified or inferred
        else if (ast.E instanceof EmptyExpr && arrT.E.isEmptyExpr()) {
            reporter.reportError(ARRAY_SIZE_MISSING);
        }
    }
    return null;
}
```

**Function Call Type Checking**:
```java
@Override
public Object visitCallExpr(CallExpr ast, Object o) {
    FuncDecl func = (FuncDecl) ast.I.decl;
    
    // Count actual parameters
    int actualCount = 0;
    List args = ast.AL;
    while (args instanceof ArgList) {
        actualCount++;
        args = ((ArgList) args).AL;
    }
    
    // Count formal parameters
    int formalCount = 0;
    List params = func.PL;
    while (params instanceof ParaList) {
        formalCount++;
        params = ((ParaList) params).PL;
    }
    
    // Check parameter count
    if (actualCount > formalCount) {
        reporter.reportError(TOO_MANY_ACTUAL_PARAMETERS);
    } else if (actualCount < formalCount) {
        reporter.reportError(TOO_FEW_ACTUAL_PARAMETERS);
    }
    
    // Check parameter types pairwise
    args = ast.AL;
    params = func.PL;
    while (args instanceof ArgList && params instanceof ParaList) {
        Expr argExpr = ((ArgList) args).A.E;
        Type paramType = ((ParaList) params).P.T;
        
        if (!paramType.assignable(argExpr.type)) {
            reporter.reportError(WRONG_TYPE_FOR_ACTUAL_PARAMETER);
        }
        
        args = ((ArgList) args).AL;
        params = ((ParaList) params).PL;
    }
    
    ast.type = func.T;  // return type
    return ast.type;
}
```

### 4. Code Generation (`Emitter.java` - 1313 lines)

Template-driven code generator producing Jasmin assembly (JVM bytecode representation).

#### Frame Management

**Local Variable Allocation**:
```java
public class Frame {
    private int nextIndex = 0;
    private int maxStackSize = 0;
    private int currentStackSize = 0;
    
    public int getNewIndex() {
        return nextIndex++;
    }
    
    public void push() {
        currentStackSize++;
        if (currentStackSize > maxStackSize) {
            maxStackSize = currentStackSize;
        }
    }
    
    public void pop(int count) {
        currentStackSize -= count;
    }
}
```

**Stack Depth Tracking** (critical for JVM `.limit stack` directive):
```java
// Example: binary addition
ast.E1.visit(this, frame);  // pushes 1 value
ast.E2.visit(this, frame);  // pushes 1 value (stack = 2)
emit(JVM.IADD);             // pops 2, pushes 1
frame.pop(2);
frame.push();               // stack = 1
```

#### Expression Code Generation

**Integer Constant Optimization**:
```java
private void emitICONST(int value) {
    if (value == -1)
        emit(JVM.ICONST_M1);        // 1 byte
    else if (value >= 0 && value <= 5)
        emit(JVM.ICONST + "_" + value);  // 1 byte
    else if (value >= -128 && value <= 127)
        emit(JVM.BIPUSH, value);    // 2 bytes
    else if (value >= -32768 && value <= 32767)
        emit(JVM.SIPUSH, value);    // 3 bytes
    else
        emit(JVM.LDC, value);       // constant pool
}
```

**Binary Expression Code Templates**:
```java
@Override
public Object visitBinaryExpr(BinaryExpr ast, Object o) {
    Frame frame = (Frame) o;
    String op = ast.O.spelling;
    
    // Evaluate operands (push to stack)
    ast.E1.visit(this, frame);  
    ast.E2.visit(this, frame);
    
    // Arithmetic operations
    if (op.startsWith("f")) {  // float operations
        switch (op) {
            case "f+" -> { 
                emit(JVM.FADD); 
                frame.pop(2); 
                frame.push(); 
            }
            case "f-" -> { emit(JVM.FSUB); /* ... */ }
            case "f*" -> { emit(JVM.FMUL); /* ... */ }
            case "f/" -> { emit(JVM.FDIV); /* ... */ }
        }
    } else {  // integer operations
        switch (op) {
            case "i+" -> { 
                emit(JVM.IADD); 
                frame.pop(2); 
                frame.push(); 
            }
            case "i-" -> { emit(JVM.ISUB); /* ... */ }
            case "i*" -> { emit(JVM.IMUL); /* ... */ }
            case "i/" -> { emit(JVM.IDIV); /* ... */ }
        }
    }
    
    return null;
}
```

**Comparison Operators** (boolean result):
```java
private void emitIF_ICMPCOND(String op, Frame frame) {
    String falseLabel = frame.getNewLabel();
    String nextLabel = frame.getNewLabel();
    
    // Branch based on comparison
    switch (op) {
        case "i==" -> emit(JVM.IF_ICMPNE, falseLabel);
        case "i!=" -> emit(JVM.IF_ICMPEQ, falseLabel);
        case "i<"  -> emit(JVM.IF_ICMPGE, falseLabel);
        case "i<=" -> emit(JVM.IF_ICMPGT, falseLabel);
        case "i>"  -> emit(JVM.IF_ICMPLE, falseLabel);
        case "i>=" -> emit(JVM.IF_ICMPLT, falseLabel);
    }
    
    frame.pop(2);
    
    // True path: push 1
    emit(JVM.ICONST_1);
    emit(JVM.GOTO, nextLabel);
    
    // False path: push 0
    emit(falseLabel + ":");
    emit(JVM.ICONST_0);
    frame.push();
    
    emit(nextLabel + ":");
}
```

#### Short-Circuit Evaluation

**Logical OR** (`||`):
```java
if ("i||".equals(op)) {
    ast.E1.visit(this, frame);
    
    String shortCircuitLabel = frame.getNewLabel();
    String endLabel = frame.getNewLabel();
    
    // If E1 is true (non-zero), skip E2 evaluation
    emit(JVM.IFNE, shortCircuitLabel);
    frame.pop();
    
    // E1 was false, evaluate E2
    ast.E2.visit(this, frame);
    emit(JVM.GOTO, endLabel);
    
    // E1 was true, push 1 directly
    emit(shortCircuitLabel + ":");
    emitICONST(1);
    frame.push();
    
    emit(endLabel + ":");
}
```

**Logical AND** (`&&`):
```java
if ("i&&".equals(op)) {
    ast.E1.visit(this, frame);
    
    String shortCircuitLabel = frame.getNewLabel();
    String endLabel = frame.getNewLabel();
    
    // If E1 is false (zero), skip E2 evaluation
    emit(JVM.IFEQ, shortCircuitLabel);
    frame.pop();
    
    // E1 was true, evaluate E2
    ast.E2.visit(this, frame);
    emit(JVM.GOTO, endLabel);
    
    // E1 was false, push 0 directly
    emit(shortCircuitLabel + ":");
    emitICONST(0);
    frame.push();
    
    emit(endLabel + ":");
}
```

#### Control Flow Translation

**If Statement**:
```java
@Override
public Object visitIfStmt(IfStmt ast, Object o) {
    Frame frame = (Frame) o;
    
    // Evaluate condition
    ast.E.visit(this, frame);
    
    String thenLabel = frame.getNewLabel();
    String endLabel = frame.getNewLabel();
    
    // Branch to then if condition is true
    emit(JVM.IFNE, thenLabel);
    frame.pop();
    
    // Else branch (or fall through)
    if (ast.S2 != null) {
        ast.S2.visit(this, frame);
    }
    emit(JVM.GOTO, endLabel);
    
    // Then branch
    emit(thenLabel + ":");
    ast.S1.visit(this, frame);
    
    emit(endLabel + ":");
    return null;
}
```

**While Loop**:
```java
@Override
public Object visitWhileStmt(WhileStmt ast, Object o) {
    Frame frame = (Frame) o;
    
    String startLabel = frame.getNewLabel();
    String endLabel = frame.getNewLabel();
    
    // Push labels for break/continue
    frame.brkStack.push(endLabel);
    frame.conStack.push(startLabel);
    
    // Loop header
    emit(startLabel + ":");
    
    // Evaluate condition
    ast.E.visit(this, frame);
    
    // Exit loop if false
    emit(JVM.IFEQ, endLabel);
    frame.pop();
    
    // Loop body
    ast.S.visit(this, frame);
    
    // Jump back to condition
    emit(JVM.GOTO, startLabel);
    
    // Exit label
    emit(endLabel + ":");
    
    frame.brkStack.pop();
    frame.conStack.pop();
    
    return null;
}
```

#### Array Operations

**Array Creation**:
```java
@Override
public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
    Frame frame = (Frame) o;
    
    if (ast.T instanceof ArrayType at) {
        // Evaluate array size expression
        at.E.visit(this, frame);
        frame.push();
        
        // Determine element type
        String javaType = VCtoJavaType(at.T);
        String atype = switch (javaType) {
            case "I" -> "int";
            case "F" -> "float";
            case "Z" -> "boolean";
            default -> "int";
        };
        
        // Create array
        emit(JVM.NEWARRAY, atype);
        frame.push();
        
        // Store array reference
        emitASTORE(ast.index);
        frame.pop();
    }
    return null;
}
```

**Array Element Access**:
```java
@Override
public Object visitArrayExpr(ArrayExpr ast, Object o) {
    Frame frame = (Frame) o;
    
    // Load array reference
    ast.V.visit(this, frame);
    
    // Evaluate index expression
    ast.E.visit(this, frame);
    
    // Load element
    if (ast.type.equals(StdEnvironment.floatType)) {
        emit(JVM.FALOAD);
    } else {
        emit(JVM.IALOAD);
    }
    
    frame.pop(2);
    frame.push();
    
    return null;
}
```

**Array Initializer**:
```java
@Override
public Object visitArrayInitExpr(ArrayInitExpr ast, Object o) {
    Frame frame = (Frame) o;
    
    // Count elements
    int count = 0;
    List list = ast.IL;
    while (list instanceof ArrayExprList) {
        count++;
        list = ((ArrayExprList) list).EL;
    }
    
    // Create array with size
    emitICONST(count);
    frame.push();
    emit(JVM.NEWARRAY, determineArrayType(ast));
    frame.push();
    
    // Initialize each element
    int index = 0;
    list = ast.IL;
    while (list instanceof ArrayExprList ael) {
        emit(JVM.DUP);              // duplicate array reference
        frame.push();
        
        emitICONST(index);          // push index
        frame.push();
        
        ael.E.visit(this, frame);   // push value
        
        if (ast.type.equals(StdEnvironment.floatType)) {
            emit(JVM.FASTORE);
        } else {
            emit(JVM.IASTORE);
        }
        
        frame.pop(3);
        
        index++;
        list = ael.EL;
    }
    
    return null;
}
```

#### Global Variable Initialization

**Static Initializer Block**:
```java
@Override
public Object visitProgram(Program ast, Object o) {
    emit(JVM.CLASS, "public", classname);
    emit(JVM.SUPER, "java/lang/Object");
    
    // Declare static fields
    List list = ast.FL;
    while (list instanceof DeclList dl) {
        if (dl.D instanceof GlobalVarDecl gv) {
            emit(JVM.STATIC_FIELD, 
                 gv.I.spelling, 
                 VCtoJavaType(gv.T));
        }
        list = dl.DL;
    }
    
    // Static initializer method
    emit(JVM.METHOD_START, "static <clinit>()V");
    Frame frame = new Frame(false);
    
    // Initialize each global variable
    list = ast.FL;
    while (list instanceof DeclList dl) {
        if (dl.D instanceof GlobalVarDecl gv) {
            if (!gv.E.isEmptyExpr()) {
                gv.E.visit(this, frame);  // evaluate initializer
            } else {
                emitDefaultValue(gv.T);   // zero/null
                frame.push();
            }
            
            emitPUTSTATIC(VCtoJavaType(gv.T), gv.I.spelling);
            frame.pop();
        }
        list = dl.DL;
    }
    
    emit(JVM.LIMIT, "stack", frame.getMaximumStackSize());
    emit(JVM.RETURN);
    emit(JVM.METHOD_END, "method");
    
    // ... continue with function definitions
}
```

## Implementation Statistics

- **Scanner.java**: 484 lines - DFA-based lexical analyzer
- **Recogniser.java**: 549 lines - LL(1) recursive descent recognizer  
- **Parser.java**: 710 lines - AST-building parser
- **Checker.java**: 909 lines - Type checker and semantic analyzer
- **Emitter.java**: 1,313 lines - JVM bytecode generator
- **Total**: ~3,965 lines of production code

**Additional Metrics**:
- 60+ AST node classes in type hierarchy
- 40+ distinct token types
- 32 semantic error categories
- 100+ JVM bytecode instructions used

## Key Algorithms and Techniques

### Compiler Theory
- **Lexical Analysis**: Maximal munch with n-character lookahead
- **Parsing**: LL(1) predictive recursive descent with left-recursion elimination
- **Type Systems**: Bottom-up type inference with implicit coercion
- **Code Generation**: Template-driven translation to stack machine

### Data Structures
- **Symbol Table**: Stack of hash maps for lexical scoping
- **AST**: Immutable tree with visitor pattern traversal
- **Operand Stack Simulation**: Stack depth tracking for JVM limits

### Language Features Implemented
- Static typing with int, float, boolean, void, arrays
- Type inference and implicit int→float coercion
- Functions with parameters and local variables
- Control flow: if-else, while, for, break, continue, return
- Arrays with initializers and bounds checking
- Short-circuit evaluation for logical operators
- Nested scopes with shadowing

### Advanced Compiler Techniques
- **Dead code detection**: Control-flow analysis to find unreachable statements
- **Coercion insertion**: AST transformation for type compatibility
- **Short-circuit optimization**: Conditional branches for && and ||
- **Instruction selection**: Multiple bytecode sequences based on operand values
- **Frame management**: Automatic local variable allocation and stack tracking

## Example Compilation

**Input (VC)**:
```c
int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

void main() {
    putIntLn(factorial(5));
}
```

**Output (Jasmin)**:
```jasmin
.method public static factorial(I)I
.limit stack 4
.limit locals 1
    ; if (n <= 1)
    iload_0
    iconst_1
    if_icmpgt L1
    
    ; return 1
    iconst_1
    ireturn
    
L1: ; return n * factorial(n - 1)
    iload_0
    iload_0
    iconst_1
    isub
    invokestatic Program/factorial(I)I
    imul
    ireturn
.end method
```

## Technical Skills Demonstrated

This project showcases proficiency in:

**Programming Languages**:
- Advanced Java (generics, streams, optionals, pattern matching, visitor pattern)
- Assembly language (JVM bytecode/Jasmin)
- Grammar specification (EBNF, context-free grammars)

**Compiler Construction**:
- Lexical analysis (DFA construction, regular expressions)
- Syntax analysis (LL(1) parsing, grammar transformations)
- Semantic analysis (symbol tables, type checking, scope resolution)
- Code generation (template-based translation, instruction selection)

**Algorithms & Data Structures**:
- Hash tables with chaining
- Stack-based algorithms (scopes, operand stack simulation)
- Tree traversal (visitor pattern on ASTs)
- String processing with finite automata

**Software Engineering**:
- Design patterns (Visitor, Builder, Singleton)
- Modular architecture with clean interfaces
- Error handling and recovery strategies
- Comprehensive testing methodology

## Building and Running

### Compilation
```bash
javac VC/**/*.java
```

### Running the Compiler
```bash
java VC.vc program.vc        # produces program.j
```

### Generating Executable
```bash
jasmin program.j             # produces program.class
java program                 # run on JVM
```

## Project Context

Developed as coursework for COMP3131/9102 (Programming Languages and Compilers) at UNSW. The project was completed in five progressive assignments over 12 weeks:

1. **Scanner** (Week 3): Lexical analysis
2. **Recogniser** (Week 5): Syntax validation  
3. **Parser** (Week 7): AST construction
4. **Checker** (Week 9): Semantic analysis
5. **Emitter** (Week 11): Code generation

Each phase built upon the previous, culminating in a fully functional compiler for a non-trivial language.

---

This compiler demonstrates end-to-end understanding of language implementation, from character-by-character scanning through bytecode generation, with emphasis on correctness, error handling, and code quality.
