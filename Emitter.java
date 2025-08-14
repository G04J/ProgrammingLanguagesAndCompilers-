/*
*** Emitter.java 
*
* Sun 30 Mar 2025 14:56:56 AEDT
*
* A new frame object is created for every function just before the
* function is being translated in visitFuncDecl.
*
* All the information about the translation of a function should be
* placed in this Frame object and passed across the AST nodes as the
* 2nd argument of every visitor method in Emitter.java.
*
*/
package VC.CodeGen;
import VC.ASTs.*;
import VC.ErrorReporter;
import VC.StdEnvironment;
import java.util.ArrayList;
import java.util.Set;
public final class Emitter implements Visitor {
private final ErrorReporter errorReporter;
private String inputFilename;
private String classname;
private String outputFilename;
public Emitter(String inputFilename, ErrorReporter reporter) {
    this.inputFilename = inputFilename;
    errorReporter = reporter;
    
    int i = inputFilename.lastIndexOf('.');
    if (i > 0)
        classname = inputFilename.substring(0, i);
    else
        classname = inputFilename;
    
}
//************************************************************************
public final void gen(AST ast) {
    ast.visit(this, null); 
    JVM.dump(classname + ".j");
}
//************************************************************************
@Override
public Object visitProgram(Program ast, Object o) {
    
    emit(JVM.CLASS, "public", classname);
    emit(JVM.SUPER, "java/lang/Object");
    emit("");
    
    List lt = ast.FL;
    while (!lt.isEmpty()) {
        DeclList dl = (DeclList) lt;
        if (dl.D instanceof GlobalVarDecl gv) {
            emit(JVM.STATIC_FIELD,
                 gv.I.spelling,
                 VCtoJavaType(gv.T));
        }
        lt = dl.DL;
    }
    
    emit("");
    emit("; standard class static initializer");
    emit(JVM.METHOD_START, "static <clinit>()V");
    emit("");

    Frame frame = new Frame(false);
    lt = ast.FL;
    while (!lt.isEmpty()) {
        DeclList dl = (DeclList) lt;
        if (dl.D instanceof GlobalVarDecl gv) {
            Type vAST = gv.T;
            if (vAST instanceof ArrayType at) {
                if (gv.E instanceof ArrayInitExpr) {
                    gv.E.visit(this, frame);
                } 
                else {
                    at.E.visit(this, frame); 
                    frame.push();
                    String javaT  = VCtoJavaType(at.T);
                    String atype;
                    if (null == javaT) {
                        atype = "int";
                    } else atype = switch (javaT) {
                        case "F" -> "float";
                        case "Z" -> "boolean";
                        default -> "int";
                    };
                    emit(JVM.NEWARRAY, atype);
                    frame.push();
                }

            } 
            
            else {
                if (!gv.E.isEmptyExpr()) {
                    gv.E.visit(this, frame);
                } else {
                    if (vAST.equals(StdEnvironment.floatType))
                        emit(JVM.FCONST_0);
                    else
                        emit(JVM.ICONST_0);
                    frame.push();
                }
            }
            emitPUTSTATIC(VCtoJavaType(vAST), gv.I.spelling);
            frame.pop();
        }
        lt = dl.DL;
    }
    emit("");
    emit(JVM.LIMIT, "locals", frame.getNewIndex());
    emit(JVM.LIMIT, "stack",  frame.getMaximumStackSize());
    emit(JVM.RETURN);
    emit(JVM.METHOD_END, "method");
    emit("");
    emit("; standard constructor initializer");
    emit(JVM.METHOD_START, "public <init>()V");
    emit(JVM.LIMIT, "stack 1");
    emit(JVM.LIMIT, "locals 1");
    emit(JVM.ALOAD_0);
    emit(JVM.INVOKESPECIAL, "java/lang/Object/<init>()V");
    emit(JVM.RETURN);
    emit(JVM.METHOD_END, "method");
    return ast.FL.visit(this, o);
}
//************************************************************************
@Override
public Object visitStmtList(StmtList ast, Object o) {
    ast.S.visit(this, o);
    ast.SL.visit(this, o);
    return null;
}
//************************************************************************
@Override
public Object visitCompoundStmt(CompoundStmt ast, Object o) {
    Frame frame = (Frame) o; 
    String startL= frame.getNewLabel();
    String endL = frame.getNewLabel();
    frame.scopeStart.push(startL);
    frame.scopeEnd.push(endL);
    
    emit(startL+ ":");
    if (ast.parent instanceof FuncDecl funcDecl) {
    	if (funcDecl.I.spelling.equals("main")) {
            
            emit(JVM.VAR, "0 is argv [Ljava/lang/String; from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
            
            emit(JVM.VAR, "1 is vc$ L" + classname + "; from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
            
            emit(JVM.NEW, classname);
            emit(JVM.DUP);
            frame.push(2);
            emit("invokenonvirtual", classname + "/<init>()V");
            frame.pop();
            emit(JVM.ASTORE_1);
            frame.pop();
    	} else {
            
            emit(JVM.VAR, "0 is this L" + classname + "; from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
            funcDecl.PL.visit(this, o);
        }
    }
    ast.DL.visit(this, o);
    ast.SL.visit(this, o);
    emit(endL + ":");
    frame.scopeStart.pop();
    frame.scopeEnd.pop();
    return null;
}
//************************************************************************
@Override
public Object visitReturnStmt(ReturnStmt ast, Object o) {
    Frame frame = (Frame) o;
    if (frame.isMain()) {  
        emit(JVM.RETURN);
        return null; 
    }  
    
    if (ast.E instanceof EmptyExpr) {
        emit(JVM.RETURN);
    } else {
        ast.E.visit(this, frame);
        Type retT = ast.E.type;
        if (retT.equals(StdEnvironment.intType) ||
            retT.equals(StdEnvironment.booleanType)) {
            emit(JVM.IRETURN);
        } else if (retT.equals(StdEnvironment.floatType)) {
            emit(JVM.FRETURN);
        } else {
            emit(JVM.RETURN);
        }
    }           
    return null;
}
//************************************************************************
@Override
public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitEmptyStmt(EmptyStmt ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitCallExpr(CallExpr ast, Object o) {
    Frame frame = (Frame) o;
    String fname = ast.I.spelling;
    switch (fname) {
        case "getInt" -> {
            ast.AL.visit(this, o);
            emit("invokestatic VC/lang/System/getInt()I");
            frame.push();
        }
        case "putInt" -> {
            ast.AL.visit(this, o);
            emit("invokestatic VC/lang/System/putInt(I)V");
            frame.pop();
        }
        case "putIntLn" -> {
            ast.AL.visit(this, o);
            emit("invokestatic VC/lang/System/putIntLn(I)V");
            frame.pop();
        }
        case "getFloat" -> {
            ast.AL.visit(this, o);
            emit("invokestatic VC/lang/System/getFloat()F");
            frame.push();
        }
        case "putFloat" -> {
            ast.AL.visit(this, o);
            emit("invokestatic VC/lang/System/putFloat(F)V");
            frame.pop();
        }
        case "putFloatLn" -> {
            ast.AL.visit(this, o);
            emit("invokestatic VC/lang/System/putFloatLn(F)V");
            frame.pop();
        }
        case "putBool" -> {
            ast.AL.visit(this, o);
            emit("invokestatic VC/lang/System/putBool(Z)V");
            frame.pop();
        }
        case "putBoolLn" -> {
            ast.AL.visit(this, o);
            emit("invokestatic VC/lang/System/putBoolLn(Z)V");
            frame.pop();
        }
        case "putString" -> {
            ast.AL.visit(this, o);
            emit(JVM.INVOKESTATIC, "VC/lang/System/putString(Ljava/lang/String;)V");
            frame.pop();
        }
        case "putStringLn" -> {
            ast.AL.visit(this, o);
            emit(JVM.INVOKESTATIC, "VC/lang/System/putStringLn(Ljava/lang/String;)V");
            frame.pop();
        }
        case "putLn" -> {
            ast.AL.visit(this, o);
            emit("invokestatic VC/lang/System/putLn()V");
        }
        default -> {
            FuncDecl fAST = (FuncDecl) ast.I.decl;
            if (frame.isMain()) {
                emit("aload_1");
            } else {
                emit("aload_0");
            }   frame.push();
            ast.AL.visit(this, o);
            StringBuilder argsTypes = new StringBuilder();
            int ArgCount = 0;
            List paramL = fAST.PL;
            while (!paramL.isEmpty()) {
                ParaList plNode = (ParaList) paramL;
                Type paraT = plNode.P.T;
            
                if (paraT instanceof ArrayType at) {
                    argsTypes.append("[").append(VCtoJavaType(at.T));
                }
                else if (paraT.equals(StdEnvironment.booleanType)) {
                    argsTypes.append("Z");
                }
                else if (paraT.equals(StdEnvironment.intType)) {
                    argsTypes.append("I");
                }
                else {
                    argsTypes.append("F");
                }
            
                ArgCount++;
                paramL = plNode.PL;
            }
            
            String retType = VCtoJavaType(fAST.T);
            emit("invokevirtual", classname + "/" + fname + "(" + argsTypes + ")" + retType);
            frame.pop(ArgCount + 1);
            if (!retType.equals("V")) {
                frame.push();
            }
        }
    }
    return null;
}
//************************************************************************
@Override
public Object visitEmptyExpr(EmptyExpr ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitIntExpr(IntExpr ast, Object o) {
    ast.IL.visit(this, o);
    return null;
}
//************************************************************************
@Override
public Object visitFloatExpr(FloatExpr ast, Object o) {
    ast.FL.visit(this, o);
    return null;
}
//************************************************************************
@Override
public Object visitBooleanExpr(BooleanExpr ast, Object o) {
    ast.BL.visit(this, o);
    return null;
}
//************************************************************************
@Override
public Object visitStringExpr(StringExpr ast, Object o) {
    ast.SL.visit(this, o);
    return null;
}
//************************************************************************
@Override
public Object visitDeclList(DeclList ast, Object o) {
    ast.D.visit(this, o);
    ast.DL.visit(this, o);
    return null;
}
//************************************************************************
@Override
public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitFuncDecl(FuncDecl ast, Object o) {
    Frame frame;
    
    if (ast.I.spelling.equals("main")) {
        frame = new Frame(true);
        
        frame.getNewIndex();
        emit(JVM.METHOD_START, "public static main([Ljava/lang/String;)V");
        
        frame.getNewIndex();
    } else {
        frame = new Frame(false);
        
        frame.getNewIndex();
        
        StringBuilder argsTypes = new StringBuilder();
        String retType = VCtoJavaType(ast.T);
        List paramL = ast.PL;
        while (!paramL.isEmpty()) {
            ParaList paraNode = (ParaList) paramL;
            Type paraT = paraNode.P.T;
            if (paraT instanceof ArrayType at) {
                argsTypes.append("[").append(VCtoJavaType(at.T));
            } else if (paraT.equals(StdEnvironment.booleanType)) {
                argsTypes.append("Z");
            } else if (paraT.equals(StdEnvironment.intType)) {
                argsTypes.append("I");
            } else {
                argsTypes.append("F");
            }
        
            paramL = paraNode.PL;
        }
        
        emit(JVM.METHOD_START,
             ast.I.spelling + "(" + argsTypes + ")" + retType);
    }
    
    ast.S.visit(this, frame);
    
    if (ast.T.equals(StdEnvironment.voidType) ||
        ast.I.spelling.equals("main")) {
        emit(JVM.RETURN);
    } else {
        emit(JVM.NOP);
    }
    emit("");
    emit("; set limits used by this method");
    emit(JVM.LIMIT, "locals", frame.getNewIndex());
    emit(JVM.LIMIT, "stack", frame.getMaximumStackSize());
    emit(".end method");
    return null;
}
//************************************************************************
@Override
public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
    Frame frame = (Frame) o;
    
    ast.index = frame.getNewIndex();
    
    String typeSig;
    if (ast.T instanceof ArrayType at) {
        typeSig = "[" + VCtoJavaType(at.T);
    } else {
        typeSig = VCtoJavaType(ast.T);
    }
    emit(JVM.VAR,
         ast.index + " is " + ast.I.spelling + " " + typeSig +
         " from " + frame.scopeStart.peek() +
         " to "   + frame.scopeEnd.peek());
    
    if (!ast.E.isEmptyExpr()) {
        
        ast.E.visit(this, frame);
        if (ast.T instanceof ArrayType) {
            
            if (ast.index <= 3)
                emit(JVM.ASTORE + "_" + ast.index);
            else
                emit(JVM.ASTORE, ast.index);
        }
        else if (ast.T.equals(StdEnvironment.floatType)) {
            emitFSTORE(ast.I);
        }
        else {
            emitISTORE(ast.I);
        }
        frame.pop();
        return null;
    } 
    
    if (ast.T instanceof ArrayType at) {
        
        at.E.visit(this, frame);   
        frame.push();
        
        String javaT  = VCtoJavaType(at.T);
       
        String atype;
        if (null == javaT) {
            atype = "int";
        } else atype = switch (javaT) {
            case "F" -> "float";
            case "Z" -> "boolean";
            default -> "int";
        };
                     
        emit(JVM.NEWARRAY, atype);
        frame.push();
        
        if (ast.index <= 3)
            emit(JVM.ASTORE + "_" + ast.index);
        else
            emit(JVM.ASTORE, ast.index);
        frame.pop();
    }
    
    return null;
}
//************************************************************************
@Override
public Object visitParaList(ParaList ast, Object o) {
    ast.P.visit(this, o);
    ast.PL.visit(this, o);
    return null;
}
//************************************************************************
@Override
public Object visitParaDecl(ParaDecl ast, Object o) {
    Frame frame = (Frame) o;
    
    ast.index = frame.getNewIndex();
    
    String typeSig;
    if (ast.T instanceof ArrayType at) {
        typeSig = "[" + VCtoJavaType(at.T);
    } else {
        typeSig = VCtoJavaType(ast.T);
    }
    
    emit(JVM.VAR,
         ast.index + " is " + ast.I.spelling + " " + typeSig +
         " from " + frame.scopeStart.peek() +
         " to "   + frame.scopeEnd.peek());
    return null;
}
//************************************************************************
@Override
public Object visitEmptyParaList(EmptyParaList ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitArgList(ArgList ast, Object o) {
    ast.A.visit(this, o);
    ast.AL.visit(this, o);
    return null;
}
//************************************************************************
@Override
public Object visitArg(Arg ast, Object o) {
    ast.E.visit(this, o);
    return null;
}
//************************************************************************
@Override
public Object visitEmptyArgList(EmptyArgList ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitIntType(IntType ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitFloatType(FloatType ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitBooleanType(BooleanType ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitVoidType(VoidType ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitErrorType(ErrorType ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitIdent(Ident ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitIntLiteral(IntLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emitICONST(Integer.parseInt(ast.spelling));
    frame.push();
    return null;
}
//************************************************************************
@Override
public Object visitFloatLiteral(FloatLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emitFCONST(Float.parseFloat(ast.spelling));
    frame.push();
    return null;
}
//************************************************************************
@Override
public Object visitBooleanLiteral(BooleanLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emitBCONST(ast.spelling.equals("true"));
    frame.push();
    return null;
}
//************************************************************************
@Override
public Object visitStringLiteral(StringLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emit(JVM.LDC, "\"" + ast.spelling.replace("\"","\\\"") + "\"");
    frame.push();
    return null;
}
//************************************************************************
@Override
public Object visitOperator(Operator ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitSimpleVar(SimpleVar ast, Object o) {
    Frame frame = (Frame) o;
    Ident id = ast.I;
    if (!(id.decl instanceof LocalVarDecl || id.decl instanceof ParaDecl)) {
        emitGETSTATIC(VCtoJavaType(ast.type), id.spelling);
        frame.push();
        return null;
    }
    
    int idx;
    if (id.decl instanceof ParaDecl paraDecl) {
        idx = paraDecl.index;
    } else {
        idx = ((LocalVarDecl) id.decl).index;
    }
    
    if (ast.type.equals(StdEnvironment.floatType)) {
        
        emitFLOAD(idx);
    }
    else if (ast.type instanceof ArrayType) {
        
        if (idx >= 0 && idx <= 3) {
            emit(JVM.ALOAD + "_" + idx);
        } else {
            emit(JVM.ALOAD, idx);
        }
    }
    else {
        
        emitILOAD(idx);
    }
    

    frame.push();
    return null;
}
//************************************************************************
private void emit(String s) {
    JVM.append(new Instruction(s)); 
}
//************************************************************************
private void emit(String s1, String s2) {
    emit(s1 + " " + s2);
}
//************************************************************************
private void emit(String s1, int i) {
    emit(s1 + " " + i);
}
//************************************************************************
private void emit(String s1, float f) {
    emit(s1 + " " + f);
}
//************************************************************************
private void emit(String s1, String s2, int i) {
    emit(s1 + " " + s2 + " " + i);
}
//************************************************************************
private void emit(String s1, String s2, String s3) {
    emit(s1 + " " + s2 + " " + s3);
}
//************************************************************************
private void emitIF_ICMPCOND(String op, Frame frame) {
    String opcode;
    opcode = switch (op) {
        case "i!=" -> JVM.IF_ICMPNE;
        case "i==" -> JVM.IF_ICMPEQ;
        case "i<" -> JVM.IF_ICMPLT;
        case "i<=" -> JVM.IF_ICMPLE;
        case "i>" -> JVM.IF_ICMPGT;
        default -> JVM.IF_ICMPGE;
    };
    String falseLabel = frame.getNewLabel();
    String nextLabel = frame.getNewLabel();
    emit(opcode, falseLabel);
    frame.pop(2); 
    emit("iconst_0");
    emit("goto", nextLabel);
    emit(falseLabel + ":");
    emit(JVM.ICONST_1);
    frame.push(); 
    emit(nextLabel + ":");
}
//************************************************************************
private void emitFCMP(String op, Frame frame) {
    String opcode;
    if (op.equals("f!="))
    	opcode = JVM.IFNE;
    else if (op.equals("f=="))
    	opcode = JVM.IFEQ;
    else if (op.equals("f<"))
    	opcode = JVM.IFLT;
    else if (op.equals("f<="))
    	opcode = JVM.IFLE;
    else if (op.equals("f>"))
    	opcode = JVM.IFGT;
    else 
    	opcode = JVM.IFGE;
    String falseLabel = frame.getNewLabel();
    String nextLabel = frame.getNewLabel();
    emit(JVM.FCMPG);
    frame.pop(2);
    emit(opcode, falseLabel);
    emit(JVM.ICONST_0);
    emit("goto", nextLabel);
    emit(falseLabel + ":");
    emit(JVM.ICONST_1);
    frame.push();
    emit(nextLabel + ":");
}
//************************************************************************
private void emitILOAD(int index) {
    if (index >= 0 && index <= 3) 
    	emit(JVM.ILOAD + "_" + index); 
    else
    	emit(JVM.ILOAD, index); 
}
//************************************************************************
private void emitFLOAD(int index) {
    if (index >= 0 && index <= 3) 
    	emit(JVM.FLOAD + "_"  + index); 
    else
    	emit(JVM.FLOAD, index); 
}
//************************************************************************
private void emitGETSTATIC(String T, String I) {
    emit(JVM.GETSTATIC, classname + "/" + I, T); 
}
//************************************************************************
private void emitISTORE(Ident ast) {
    int index;
    if (ast.decl instanceof ParaDecl paraDecl)
    	index = paraDecl.index; 
    else
    	index = ((LocalVarDecl) ast.decl).index; 
    
    if (index >= 0 && index <= 3) 
    	emit(JVM.ISTORE + "_" + index); 
    else
    	emit(JVM.ISTORE, index); 
}
//************************************************************************
private void emitFSTORE(Ident ast) {
    int index;
    if (ast.decl instanceof ParaDecl paraDecl)
    	index = paraDecl.index; 
    else
    	index = ((LocalVarDecl) ast.decl).index; 
    if (index >= 0 && index <= 3) 
    	emit(JVM.FSTORE + "_" + index); 
    else
    	emit(JVM.FSTORE, index); 
}
//************************************************************************
private void emitPUTSTATIC(String T, String I) {
    emit(JVM.PUTSTATIC, classname + "/" + I, T); 
}
//************************************************************************
private void emitICONST(int value) {
    if (value == -1)
    	emit(JVM.ICONST_M1); 
    else if (value >= 0 && value <= 5) 
    	emit(JVM.ICONST + "_" + value); 
    else if (value >= -128 && value <= 127) 
    	emit(JVM.BIPUSH, value); 
    else if (value >= -32768 && value <= 32767)
    	emit(JVM.SIPUSH, value); 
    else 
    	emit(JVM.LDC, value); 
}
//************************************************************************
private void emitFCONST(float value) {
    if (value == 0.0f) {
        emit(JVM.FCONST_0);
    } else if (value == 1.0f) {
        emit(JVM.FCONST_1);
    } else if (value == 2.0f) {
        emit(JVM.FCONST_2);
    } else {
        emit(JVM.LDC, value);
    }
}

//************************************************************************
private void emitBCONST(boolean value) {
    if (value)
    	emit(JVM.ICONST_1);
    else
    	emit(JVM.ICONST_0);
}
//************************************************************************
private String VCtoJavaType(Type t) {
    if (t instanceof ArrayType at) {
        return "[" + VCtoJavaType(at.T);
    }
    if (t.equals(StdEnvironment.booleanType)) {
        return "Z";
    } else if (t.equals(StdEnvironment.intType)) {
        return "I";
    } else if (t.equals(StdEnvironment.floatType)) {
        return "F";
    }
    return "V";
}
//************************************************************************
@Override
public Object visitEmptyArrayExprList(EmptyArrayExprList ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitIfStmt(IfStmt ast, Object o) {
    Frame frame = (Frame) o;
    
    ast.E.visit(this, frame);
    
    if (ast.E.type.equals(StdEnvironment.floatType)) {
        emit(JVM.FCONST_0);
        frame.push();
        emitFCMP("f!=", frame);
    }
    
    String thenL = frame.getNewLabel();
    String endL  = frame.getNewLabel();
    emit(JVM.IFNE, thenL);
    frame.pop();
    
    if (ast.S2 != null) {
        ast.S2.visit(this, frame);
    }
    emit(JVM.GOTO, endL);
    
    emit(thenL + ":");
    ast.S1.visit(this, frame);
    emit(endL + ":");
    return null;
}
//************************************************************************
@Override
public Object visitWhileStmt(WhileStmt ast, Object o) {
    Frame frame = (Frame) o;
    String startL = frame.getNewLabel();
    String endL   = frame.getNewLabel();
    frame.brkStack.push(endL);
    frame.conStack.push(startL);
    emit(startL + ":");
    
    ast.E.visit(this, frame);
    if (ast.E.type.equals(StdEnvironment.floatType)) {
        emit(JVM.FCONST_0);
        frame.push();
        emitFCMP("f!=", frame);
    }
    
    emit(JVM.IFEQ, endL);
    frame.pop();
    ast.S.visit(this, frame);
    emit(JVM.GOTO, startL);
    emit(endL + ":");
    frame.brkStack.pop();
    frame.conStack.pop();
    return null;
}
//************************************************************************
@Override
public Object visitForStmt(ForStmt ast, Object o) {
    Frame frame = (Frame) o;
    if (!(ast.E1 instanceof EmptyExpr)) {
        ast.E1.visit(this, frame);
        emit(JVM.POP);
        frame.pop();
    }
    
    String startL  = frame.getNewLabel();
    String midL = frame.getNewLabel();
    String endL    = frame.getNewLabel();
    frame.brkStack.push(endL);
    frame.conStack.push(midL);
    
    emit(startL + ":");
    if (!(ast.E2 instanceof EmptyExpr)) {
        ast.E2.visit(this, frame);
        
        if (ast.E2.type.equals(StdEnvironment.floatType)) {
            emit(JVM.FCONST_0);
            frame.push();
            emitFCMP("f!=", frame);
        }
        emit(JVM.IFEQ, endL);
        frame.pop();
    }
    
    ast.S.visit(this, frame);
    
    emit(midL + ":");
    if (!(ast.E3 instanceof EmptyExpr)) {
        ast.E3.visit(this, frame);
        emit(JVM.POP);
        frame.pop();
    }
    
    emit(JVM.GOTO, startL);
    
    
    emit(endL + ":");
    frame.brkStack.pop();
    frame.conStack.pop();
    return null;
}
//************************************************************************
@Override
public Object visitBreakStmt(BreakStmt ast, Object o) {
    Frame frame = (Frame) o;
    
    emit(JVM.GOTO, frame.brkStack.peek());
    return null;
}
//************************************************************************
@Override
public Object visitContinueStmt(ContinueStmt ast, Object o) {
    Frame frame = (Frame) o;
    emit(JVM.GOTO, frame.conStack.peek());
    return null;
}
//************************************************************************
@Override
public Object visitExprStmt(ExprStmt ast, Object o) {
    Frame frame = (Frame) o;
    
    ast.E.visit(this, frame);
    
    boolean isVoid = ast.E.type.equals(StdEnvironment.voidType);
    Set<String> builtInCall = Set.of(
        "putInt",    "putIntLn",
        "putFloat",  "putFloatLn",
        "putBool",   "putBoolLn",
        "putString","putStringLn",
        "putLn"
    );
    boolean isBuiltin = ast.E instanceof CallExpr call
                        && builtInCall.contains(call.I.spelling);
    
    if (!isVoid && !isBuiltin) {
        
        emit(JVM.POP);
        
        frame.pop();
    }
    return null;
}
//************************************************************
@Override
public Object visitUnaryExpr(UnaryExpr ast, Object o) {
    Frame frame = (Frame) o;
    String op = ast.O.spelling;
    
    ast.E.visit(this, frame);
    
    switch (op) {
        case "-", "i-" -> {
            if (ast.type.equals(StdEnvironment.intType)) {
                emit(JVM.INEG);
            } else {
                emit(JVM.FNEG);
            }
          } 
      case "+", "i+", "f+" -> {
        }

      case "!" -> {
          emitICONST(0);
          frame.push();
          emitIF_ICMPCOND("i==", frame);
        }
      
      case "i2f" -> emit(JVM.I2F);
      default -> {  
          errorReporter.reportError("unsupported unary operator: %", op, ast.O.position);
          emit(JVM.POP);
          emitICONST(0);
          frame.push();
        }
    }
    return null;
}
//**************************************************************
@Override
public Object visitBinaryExpr(BinaryExpr ast, Object o) {
    Frame frame = (Frame) o;
    String op = ast.O.spelling;
    
    if ("i||".equals(op) || "i&&".equals(op)) {
        ast.E1.visit(this, frame);
    
        String startL = frame.getNewLabel();
        String endL = frame.getNewLabel();
    
        if ("i||".equals(op)) {
            emit(JVM.IFNE, startL);  
        } else {
            emit(JVM.IFEQ, startL); 
        }
        frame.pop();
    
        ast.E2.visit(this, frame);
        emit(JVM.GOTO, endL);
    
        emit(startL + ":");
    
        if ("i||".equals(op)) {
            emitICONST(1);
        } else {
            emitICONST(0);
        }
        frame.push();
    
        emit(endL + ":");
        return null;
    }
    
    ast.E1.visit(this, frame);
    ast.E2.visit(this, frame);
    
    if (op.startsWith("f")) {
        switch (op) {
            case "f+"  -> { emit(JVM.FADD); frame.pop(2); frame.push(); }
            case "f-"  -> { emit(JVM.FSUB); frame.pop(2); frame.push(); }
            case "f/"  -> { emit(JVM.FDIV); frame.pop(2); frame.push(); }
            case "f*"  -> { emit(JVM.FMUL); frame.pop(2); frame.push(); }
            case "f!=" -> emitFCMP("f!=", frame);
            
            case "f==" -> emitFCMP("f==", frame);
            case "f<=" -> emitFCMP("f<=", frame);
            case "f<"  -> emitFCMP("f<",  frame);
            case "f>=" -> emitFCMP("f>=", frame);
            case "f>"  -> emitFCMP("f>",  frame);
            default    -> {
                errorReporter.reportError("unsupported float operator: %", op, ast.O.position);
            }
        }
        return null;
    }
    
    if (ast.type.equals(StdEnvironment.intType) ||
        ast.type.equals(StdEnvironment.booleanType)) {
        switch (op) {
            case "i+"  -> { emit(JVM.IADD); frame.pop(2); frame.push(); }
            case "i/"  -> { emit(JVM.IDIV); frame.pop(2); frame.push(); }
            case "i*"  -> { emit(JVM.IMUL); frame.pop(2); frame.push(); }
            case "i-"  -> { emit(JVM.ISUB); frame.pop(2); frame.push(); }
            case "i!=" -> emitIF_ICMPCOND("i!=", frame);
            case "i>=" -> emitIF_ICMPCOND("i>=", frame);
            case "i<"  -> emitIF_ICMPCOND("i<",  frame);
            case "i>"  -> emitIF_ICMPCOND("i>",  frame);
            case "i==" -> emitIF_ICMPCOND("i==", frame);
            case "i<=" -> emitIF_ICMPCOND("i<=", frame);
           
            default    -> {
                errorReporter.reportError("unsupported integer operator: %", op, ast.O.position);
            }
        }
        return null;
    }
    
    errorReporter.reportError("unsupported operator: %", op, ast.O.position);
    return null;
}
//************************************************************************
@Override
public Object visitArrayInitExpr(ArrayInitExpr ast, Object o) {
    Frame frame = (Frame) o;
    
    ArrayType at;
    if (ast.parent instanceof LocalVarDecl localVarDecl) {
        at = (ArrayType)localVarDecl.T;
    } else if (ast.parent instanceof ParaDecl paraDecl) {
        at = (ArrayType)paraDecl.T;
    }  else if (ast.parent instanceof GlobalVarDecl globalVarDecl) {
        at = (ArrayType) globalVarDecl.T;            
    }
    else {
        
        at = (ArrayType)ast.type;
    }


    Type varT = at.T;
    String javaT = VCtoJavaType(varT);
    
    int count = 0;
    List ArrList = ast.IL;
    while (!ArrList.isEmptyArrayExprList()) {
        ArrayExprList ael = (ArrayExprList) ArrList;
        count++;
        ArrList = ael.EL;
    }
    
    emitICONST(count);         
    frame.push();
    
    String atype;
    switch (javaT) {
        case "I" -> atype = "int";
        case "F" -> atype = "float";
        case "Z" -> atype = "boolean";   
        default -> {
            errorReporter.reportError(
              "unsupported array element type: %",
              javaT,
              ast.position
            );
            atype = "int";
        }
    }
    emit(JVM.NEWARRAY, atype);
    frame.push();
    
    int idx = 0;
    List arrayInitList = ast.IL;
    while (!arrayInitList.isEmptyArrayExprList()) {
        ArrayExprList elementNode = (ArrayExprList) arrayInitList;
        emit(JVM.DUP);
        frame.push();
        emitICONST(idx++);
        frame.push();
        elementNode.E.visit(this, frame);
        if (varT.equals(StdEnvironment.floatType)) {
            emit(JVM.FASTORE);
        } else if (varT.equals(StdEnvironment.booleanType)) {
            emit(JVM.BASTORE);
        } else {
            emit(JVM.IASTORE);
        }
    
        frame.pop(3);
        arrayInitList = elementNode.EL;
    }
    return null;
}
//************************************************************************
@Override
public Object visitArrayExprList(ArrayExprList ast, Object o) {
    ast.E.visit(this, o);
    ast.EL.visit(this, o);
    return null;
}
//************************************************************************
@Override
public Object visitArrayExpr(ArrayExpr ast, Object o) {
    Frame frame = (Frame) o;
    
    ast.V.visit(this, frame);
    
    ast.E.visit(this, frame);
    
    if (ast.type.equals(StdEnvironment.floatType)) {
        emit(JVM.FALOAD);
    } else if (ast.type.equals(StdEnvironment.booleanType)) {
        emit(JVM.BALOAD);
    } else {
        emit(JVM.IALOAD);
    }
    frame.pop(2);
    frame.push();
    return null;
}
//************************************************************
@Override
public Object visitVarExpr(VarExpr ast, Object o) {
    return ast.V.visit(this, o); 
}
//************************************************************
@Override
public Object visitAssignExpr(AssignExpr ast, Object o) {
    Frame frame = (Frame) o;
    
    ArrayList<Expr> lhsList = new ArrayList<>();
    AssignExpr cur = ast;
    while (cur instanceof AssignExpr) {
        lhsList.add(cur.E1);
        if (cur.E2 instanceof AssignExpr next) {
            cur = next;
        } 
        else {
            break;
        }
    }
    
    Expr finalRHS;
    if (cur.E2 instanceof AssignExpr assignExpr) {
        finalRHS = assignExpr.E2;
    } else {
        finalRHS = cur.E2;
    }
    finalRHS.visit(this, frame);
    
    
    if (cur.E1.type.equals(StdEnvironment.floatType)
     && finalRHS.type.equals(StdEnvironment.intType)) {
        emit(JVM.I2F);
        frame.pop();
        frame.push();
    }
    
    int tmp = frame.getNewIndex();
    if (finalRHS.type.equals(StdEnvironment.floatType)) {
        if (tmp <= 3) {
            emit(JVM.FSTORE + "_" + tmp);
        }
        else {
            emit(JVM.FSTORE, tmp);
        }          
    } else {
        if (tmp <= 3) {
            emit(JVM.ISTORE + "_" + tmp);
        }
        else {
            emit(JVM.ISTORE, tmp);
        }          
    }
    frame.pop();
    
    for (Object o1 : lhsList) {
        Expr e1 = (Expr)o1;
        if (e1 instanceof VarExpr ve) {
            SimpleVar v = (SimpleVar)ve.V;
            
            if (v.type.equals(StdEnvironment.floatType)) {
                if (tmp <= 3) {
                    emit(JVM.FLOAD + "_" + tmp);
                }
                else {
                    emit(JVM.FLOAD, tmp);
                }          
            } else {
                if (tmp <= 3) {
                    emit(JVM.ILOAD + "_" + tmp);
                }
                else {
                    emit(JVM.ILOAD, tmp);
                }        
            }
            frame.push();
            
            if (v.I.decl instanceof GlobalVarDecl) {
                emitPUTSTATIC(VCtoJavaType(v.type), v.I.spelling);
            } else if (v.type.equals(StdEnvironment.floatType)) {
                emitFSTORE(v.I);
            } else {
                emitISTORE(v.I);
            }
            frame.pop();
        } else if (e1 instanceof ArrayExpr ae) {
            
            ae.V .visit(this, frame);
            ae.E .visit(this, frame);
            
            if (ae.type.equals(StdEnvironment.floatType)) {
                if (tmp <= 3) {
                    emit(JVM.FLOAD + "_" + tmp);
                }
                else {
                    emit(JVM.FLOAD, tmp);
                }          
            } else {
                if (tmp <= 3) {
                    emit(JVM.ILOAD + "_" + tmp);
                }
                else {
                    emit(JVM.ILOAD, tmp);
                }          
            }
            frame.push();
            
            if (ae.type.equals(StdEnvironment.floatType)) {
                emit(JVM.FASTORE);
            } else if (ae.type.equals(StdEnvironment.booleanType)) {
                emit(JVM.BASTORE);
            } else {
                emit(JVM.IASTORE);
            }
            frame.pop(3);
        } else {
            throw new RuntimeException("Unexpected LHS in assign: " + e1);
        }
    }
    
    if (finalRHS.type.equals(StdEnvironment.floatType)) {
        if (tmp <= 3) {
            emit(JVM.FLOAD + "_" + tmp);
        }
        else {
            emit(JVM.FLOAD, tmp);
        }          
    } else {
        if (tmp <= 3) {
            emit(JVM.ILOAD + "_" + tmp);
        } 
        else {
            emit(JVM.ILOAD, tmp);
        }         
    }
    frame.push();
    return null;
}
//************************************************************
@Override
public Object visitStringType(StringType ast, Object o) {
    return null;
}
//************************************************************************
@Override
public Object visitArrayType(ArrayType ast, Object o) {
    return null;
}
}
