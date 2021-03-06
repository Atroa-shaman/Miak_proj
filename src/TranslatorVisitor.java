import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static java.lang.Math.round;

// TODO:
//  castowanie --------------------------- jeszcze nie działa

public class TranslatorVisitor extends AniLangParserBaseVisitor {
    private AniLangParser parser;

    private List<Scope> scopeStack = new ArrayList<>();

    private Scene scene;

    private Expression currentlyReturnedExpression;

    private AniLangParser.EndContext currentEndContext;


    public TranslatorVisitor(AniLangParser parser) {
        this.parser = parser;

        scopeStack.add(new Scope(null));
    }

    @Override
    public Object visitProgram(AniLangParser.ProgramContext ctx) {
        return super.visitProgram(ctx);
    }

    @Override
    public Object visitAtom(AniLangParser.AtomContext ctx) {
        return ctx.id() == null ? new Expression(ctx.getText()) : visitId(ctx.id()) ;
    }

    @Override
    public Object visitUnary_operator(AniLangParser.Unary_operatorContext ctx) {
        return super.visitUnary_operator(ctx);
    }

    @Override
    public Object visitBreakStat(AniLangParser.BreakStatContext ctx) {
        return super.visitBreakStat(ctx);
    }

    @Override
    public Object visitReturnStat(AniLangParser.ReturnStatContext ctx) {
        return super.visitReturnStat(ctx);
    }

    @Override
    public Object visitReturnValueStat(AniLangParser.ReturnValueStatContext ctx) {
        currentlyReturnedExpression = (Expression) visitExpr(ctx.expr());

        return super.visitReturnValueStat(ctx);
    }

    @Override
    public Object visitContinueStat(AniLangParser.ContinueStatContext ctx) {
        return super.visitContinueStat(ctx);
    }

    @Override
    public Object visitFunction_declaration(AniLangParser.Function_declarationContext ctx) {
//        System.out.println("function declaration for sure");

        try {
            getCurrentScope()
                    .declareFunction(
                            ctx.Id(0).getText(),
                            stringToType(ctx.Type(0)),
                            stringToType(ctx.Type()),
                            ctx.Id(),
                            ctx
                    );
        } catch(Exception e) {
            raiseError(e.toString(), ctx.getStart().getLine());
        }

        return null;//super.visitFunction_declaration(ctx);
    }

    @Override
    public Object visitFunction_call(AniLangParser.Function_callContext ctx) {
//        System.out.println("function call for sure");

        Scope functionArgumentsScope = new Scope(getCurrentScope(), true);
        scopeStack.add(functionArgumentsScope);

        Function func;

        try {
            func = getCurrentScope().getFunction(ctx.Id().getText());

            // check if the function is well called
            if( func.getParamCount() > 0 ) {
                if( ctx.expr() == null || func.getParamCount() != ctx.expr().size() )
                    raiseError(String.format(
                            "Function %s expects %d parameters, but got %d",
                            func.getId(),
                            func.getParamCount(),
                            ctx.expr().size()
                    ), ctx.getStart().getLine());

                // get the values of the expressions to pass as parameters
                List<Expression> values = new ArrayList<>();

                for(int parNum = 0; parNum < ctx.expr().size(); parNum++)
                    values.add((Expression) visitExpr(ctx.expr(parNum)));

                // parameter variables declaration and assignment
                for(int parNum = 0; parNum < func.getParamCount(); parNum++) {

                    Expression value = values.get(parNum);

                    // declaration
                    functionArgumentsScope.declareVariable(new Variable(
                            func.getArgTypes().get(parNum),
                            func.getArgId().get(parNum)
                    ));

                    // assignment
                    if(
                            (value.type == Type.boolType && func.getArgTypes().get(parNum) != Type.boolType) ||
                            (value.type != Type.boolType && func.getArgTypes().get(parNum) == Type.boolType)
                    ) {
                        raiseError(String.format(
                                "Parameter %d to function %s is type %s, but got %s",
                                parNum,
                                func.getId(),
                                func.getArgTypes().get(parNum),
                                value.type
                        ), ctx.getStart().getLine());
                    }

                    functionArgumentsScope.getVariable(
                            func
                                    .getArgId()
                                    .get(parNum)
                    ).assignValue(value);
                }
            }

            super.visitFunction_declaration(func.getCtx());

        } catch (Exception e) {
            raiseError(
                    e.getMessage(),
                    ctx.getStart().getLine()
            );
        }

        scopeStack.remove(getCurrentScope());

        return super.visitFunction_call(ctx);
    }

    @Override
    public Object visitId(AniLangParser.IdContext ctx) {
        Variable var = null;
        try {
            var = getCurrentScope().getVariable(ctx.getText());
        } catch (Exception e) {
            raiseError(e.getMessage(), ctx.getStart().getLine());
        }

        return new Expression(var.getType(), var.getValue());
    }

    @Override // Or
    public Object visitExpr(AniLangParser.ExprContext ctx) {
        if( ctx.expr_1(0) == null ) {
            raiseError(
                    "Expected expression, but got null",
                    ctx.getStart().getLine()
            );
        }

        Expression result = (Expression) this.visitExpr_1(ctx.expr_1(0));

        if( ctx.Or(0) != null ) {

            for(int i=1; i<ctx.expr_1().size(); i++) {
                try {
                    result = result.or( (Expression) this.visitExpr_1(ctx.expr_1(i)) );
                } catch (Exception e) {
                    raiseError(
                            e.getMessage(),
                            ctx.getStart().getLine()
                    );
                }
            }
        }
        return result;
    }

    @Override // And
    public Object visitExpr_1(AniLangParser.Expr_1Context ctx) {
        if( ctx.expr_2(0) == null ) {
            raiseError(
                    "Expected expression, but got null",
                    ctx.getStart().getLine()
            );
        }

        Expression result = (Expression) this.visitExpr_2(ctx.expr_2(0));

        if( ctx.And(0) != null ) {

            for(int i=1; i<ctx.expr_2().size(); i++) {
                try {
                    result = result.and( (Expression) this.visitExpr_2(ctx.expr_2(i)) );
                } catch (Exception e) {
                    raiseError(
                            e.getMessage(),
                            ctx.getStart().getLine()
                    );
                }

            }
        }
        return result;
    }

    @Override // Lesser | Greater | Leq | Geq | Equals
    public Object visitExpr_2(AniLangParser.Expr_2Context ctx) {
        if( ctx.expr_3(0) == null ) {
            raiseError(
                    "Expected expression, but got null",
                    ctx.getStart().getLine()
            );
        }

        Expression left = (Expression) this.visitExpr_3(ctx.expr_3(0));

        if( ctx.expr_3().size() > 1 ) {
            Expression right = (Expression) this.visitExpr_3(ctx.expr_3(1));

            Expression res = null;

            try {
                res = switch(ctx.ComparationToken().getText()) {
                    case "<" -> left.lesser(right);
                    case ">" -> left.greater(right);
                    case "<=" -> left.leq(right);
                    case ">=" -> left.geq(right);
                    case "==" -> left.eq(right);

                    default -> null;
                };
            } catch (Exception e) {
                raiseError(
                        e.getMessage(),
                        ctx.getStart().getLine()
                );
            }

            return res;
        }
        return left;
    }

    @Override // Minus
    public Object visitExpr_3(AniLangParser.Expr_3Context ctx) {
        if( ctx.expr_4(0) == null ) {
            raiseError(
                    "Expected expression, but got null",
                    ctx.getStart().getLine()
            );
        }

        Expression left = (Expression) this.visitExpr_4(ctx.expr_4(0));

        if( ctx.expr_4().size() > 1 ) {
            for(int i=1; i<ctx.expr_4().size(); i++) {
                Expression right = (Expression) this.visitExpr_4(ctx.expr_4(i));

                try {
                    left = switch(ctx.Minus(i-1).getText()) {
                        case "-" -> left.sub(right);
                        default -> null;
                    };
                } catch (Exception e) {
                    raiseError(
                            e.getMessage(),
                            ctx.getStart().getLine()
                    );
                }
            }
        }
        return left;
    }

    @Override // Plus
    public Object visitExpr_4(AniLangParser.Expr_4Context ctx) {
        if( ctx.expr_5(0) == null ) {
            raiseError(
                    "Expected expression, but got null",
                    ctx.getStart().getLine()
            );
        }

        Expression left = (Expression) this.visitExpr_5(ctx.expr_5(0));

        if( ctx.expr_5().size() > 1 ) {
            for(int i=1; i<ctx.expr_5().size(); i++) {
                Expression right = (Expression) this.visitExpr_5(ctx.expr_5(i));

                try {
                    left = switch(ctx.Plus(i-1).getText()) {
                        case "+" -> left.add(right);
                        default -> null;
                    };
                } catch( Exception e) {
                    raiseError(
                            e.getMessage(),
                            ctx.getStart().getLine()
                    );
                }
            }
        }
        return left;
    }

    @Override // Star | Slash
    public Object visitExpr_5(AniLangParser.Expr_5Context ctx) {
        if( ctx.expr_6(0) == null ) {
            raiseError(
                    "Expected expression, but got null",
                    ctx.getStart().getLine()
            );
        }

        Expression left = (Expression) this.visitExpr_6(ctx.expr_6(0));

        if( ctx.expr_6().size() > 1 ) {
            for(int i=1; i<ctx.expr_6().size(); i++) {
                Expression right = (Expression) this.visitExpr_6(ctx.expr_6(i));

                try {
                    left = switch(ctx.Star_Slash_Mod(i-1).getText()) {
                        case "*" -> left.mul(right);
                        case "/" -> left.div(right);
                        case "%" -> left.mod(right);
                        default -> null;
                    };
                } catch (Exception e) {
                    raiseError(
                        e.getMessage(),
                        ctx.getStart().getLine()
                    );
                }
            }
        }
        return left;
    }

    @Override // unary operator
    public Object visitExpr_6(AniLangParser.Expr_6Context ctx) {
        if( ctx.expr_7() == null ) {
            raiseError(
                    "Expected expression, but got null",
                    ctx.getStart().getLine()
            );
        }

        Expression result = (Expression) this.visitExpr_7(ctx.expr_7());

        if( ctx.unary_operator() != null ) {

            Expression res = null;

            try {
                res = switch ( ctx.unary_operator().getText() ) {
                    // Arithmetic
                    case "-" -> result.neg();

                    // Boolean
                    case "!" -> result.not();

                    // Default
                    default -> null;
                };
            } catch (Exception e) {
                raiseError(
                        e.getMessage(),
                        ctx.getStart().getLine()
                );
            }
            return res;
        }
        return result;
    }

    @Override // Power
    public Object visitExpr_7(AniLangParser.Expr_7Context ctx) {
        if( ctx.expr_8(0) == null ) {
            raiseError(
                    "Expected expression, but got null",
                    ctx.getStart().getLine()
            );
        }

        Expression left = (Expression) this.visitExpr_8(ctx.expr_8(0));

        if( ctx.Power(0) != null ) {

            for(int i=1; i<ctx.expr_8().size(); i++) {
                Expression right =  (Expression) this.visitExpr_8(ctx.expr_8(i));

                try {
                    left = left.pow( right );
                } catch (Exception e) {
                    raiseError(
                            e.getMessage(),
                            ctx.getStart().getLine()
                    );
                }

            }
        }
        return left;
    }

    @Override // atom | Outer id | function_call | Open_Parenthesis expr Close_Parenthesis
    public Object visitExpr_8(AniLangParser.Expr_8Context ctx) {

        if( ctx.atom() != null ) {
            return this.visitAtom(ctx.atom());

        } else if( ctx.Outer() != null ) {
            return this.visitOuterExpr(ctx.id().getText(), ctx.getStart().getLine());

        } else if( ctx.function_call() != null ) {
            this.visitFunction_call(ctx.function_call());

            try {
                currentlyReturnedExpression.setType( this.getCurrentScope()
                        .getFunction(
                                ctx
                                        .function_call()
                                        .Id()
                                        .getText()
                        ).getReturnType());

            } catch (Exception e) {
                raiseError(
                        e.getMessage(),
                        ctx.getStart().getLine()
                );
            }

            return currentlyReturnedExpression;

        } else if( ctx.Open_Parenthesis() != null ) {
            return visitExpr(ctx.expr());
        }
        raiseError(
                "Expected expression, but got unidentified id",
                ctx.getStart().getLine()
        );
        return null;
    }

    @Override
    public Object visitPrint(AniLangParser.PrintContext ctx) {
        System.out.print(visitExpr(ctx.expr()));
        return null;
    }

    @Override
    public Object visitPrintln(AniLangParser.PrintlnContext ctx) {
        System.out.println(visitExpr(ctx.expr()));
        return super.visitPrintln(ctx);
    }

    @Override
    public Object visitCreateScene(AniLangParser.CreateSceneContext ctx) {
        int[] args = new int[2];
        if(ctx.expr().size()!=2) raiseError(
                String.format("Wrong number of arguments. Got %d, expected 2", ctx.expr().size()),
                ctx.getStart().getLine()
        );
        for(int i = 0 ; i < 2 ; i++){
            Expression tmp = ((Expression)this.visitExpr(ctx.expr(i)));
            if(tmp.type == Type.boolType){
                raiseError("Wrong argument type",ctx.getStart().getLine());
            }
            else args[i] = (int)tmp.value;
        }
        this.scene = new Scene(
                args[0],
                args[1]
        );

        return super.visitCreateScene(ctx);
    }

    @Override
    public Object visitCreateBox(AniLangParser.CreateBoxContext ctx) {
        double[] args = new double[9];
        if(ctx.expr().size()!=9) raiseError(
                "Wrong number of arguments. Got %d, expected 9",
                ctx.getStart().getLine()
        );
        if(scene == null)
            raiseError("Shape was created before the scene",ctx.getStart().getLine());
        for(int i = 0 ; i < 9 ; i++){
            Expression tmp = ((Expression)this.visitExpr(ctx.expr(i)));
            if(tmp.type == Type.boolType){
                raiseError("Wrong argument type",ctx.getStart().getLine());
            }
            else args[i] = tmp.type == Type.intType ? 1. * (int) tmp.value : (double) tmp.value;
        }

        this.scene.addShape(new Box(
                args[0],
                args[1],
                args[2],
                args[3],
                args[4],
                args[5],
                args[6],
                args[7],
                args[8]
        ));

        return super.visitCreateBox(ctx);
    }

    @Override
    public Object visitBuildScene(AniLangParser.BuildSceneContext ctx) {

        String filePath = "./result.html";
        String result = this.scene.build();
        FileWriter fw;
        try {
            File file = new File( filePath );
            fw = new FileWriter(file, false);

            fw.write(result);

            fw.flush();
            fw.close();
        } catch (IOException e) {
            raiseError(
                    "An error occurred when creating output file.",
                    ctx.getStart().getLine()
            );
        }

        return super.visitBuildScene(ctx);
    }

    private Object visitOuterExpr(String id, int line) {
        Variable var = null;
        try {
            var = getCurrentScope().getOuterVariable(id);
        } catch (Exception e) {
            raiseError(
                    e.getMessage(),
                    line
            );
        }

        return new Expression(var.getType(), var.getValue());
    }

    @Override
    public Object visitDeclaration_stat(AniLangParser.Declaration_statContext ctx) {

        Variable result = new Variable(
                stringToType(ctx.Type().getText()),
                ctx.Id().getText()
        );

        try {
            getCurrentScope().declareVariable(result);
        } catch (Exception e) {
            raiseError(
                    e.getMessage(),
                    ctx.getStart().getLine()
            );
        }

        if( ctx.expr() != null ) {
            Expression assignment = (Expression) visitExpr(ctx.expr());

            try {
                result.assignValue(assignment);
            } catch (Exception e) {
                raiseError(
                        e.getMessage(),
                        ctx.getStart().getLine()
                );
            }
            return assignment;
        }

        return null;
    }

    @Override
    public Object visitAssignment_stat(AniLangParser.Assignment_statContext ctx) {
//        System.out.println("assignment for sure");

        Expression assignment = (Expression) visitExpr(ctx.expr());

        try {
            getCurrentScope().assignVariable(ctx.Id().getText(), assignment);
        } catch (Exception e) {
            raiseError(
                    e.getMessage(),
                    ctx.getStart().getLine()
            );
        }

        return assignment;
    }

    @Override
    public Object visitIf_stat(AniLangParser.If_statContext ctx) {
        //system.out.println("if for sure");

        Expression condition = (Expression) visitExpr(ctx.expr());
        if( condition.type != Type.boolType ) raiseError(
                "Condition is not a boolean expression",
                ctx.getStart().getLine()
        );

        currentEndContext = ctx.block().end();

        if( (Boolean) condition.value ) {
            super.visitBlock(ctx.block());
            return true;
        }

        if( ctx.else_if_stat() != null ) {
            for(AniLangParser.Else_if_statContext eisc : ctx.else_if_stat() ) {
                Boolean entered = (Boolean) this.visitElse_if_stat(eisc);

                if( entered ) return true;
            }
        }

        if( ctx.else_stat() != null )
            return this.visitElse_stat(ctx.else_stat());

        return false;
    }

    @Override
    public Object visitElse_if_stat(AniLangParser.Else_if_statContext ctx) {
        Expression condition = (Expression) visitExpr(ctx.expr());
        if( condition.type != Type.boolType ) raiseError("Condition is not a boolean expression", ctx.getStart().getLine());

        if( (Boolean) condition.value ) {
            super.visitBlock(ctx.block());
            return true;
        }

        return false;
    }

    @Override
    public Object visitElse_stat(AniLangParser.Else_statContext ctx) {
        super.visitBlock(ctx.block());
        return true;
    }

    @Override
    public Object visitFor_stat(AniLangParser.For_statContext ctx) {
//        System.out.println("for for sure");

        Scope forDeclarationScope = new Scope(getCurrentScope(), true);
        scopeStack.add(forDeclarationScope);

        if( ctx.assignment_stat() != null ) this.visitAssignment_stat(ctx.assignment_stat());
        else if( ctx.declaration_stat() != null ) this.visitDeclaration_stat(ctx.declaration_stat());

        Expression condition = (Expression) visitExpr(ctx.expr());
        if( condition.type != Type.boolType ) raiseError("Condition is not a boolean expression", ctx.getStart().getLine());

        while( (Boolean) condition.value ) {
            super.visitBlock(ctx.block());

            if( ctx.blockless_stat() != null ) this.visitBlockless_stat(ctx.blockless_stat());

            // evaluating the condition at the end
            condition = (Expression) visitExpr(ctx.expr());
        }

        scopeStack.remove(getCurrentScope());

        return null;//super.visitFor_stat(ctx);
    }

    @Override
    public Object visitWhile_stat(AniLangParser.While_statContext ctx) {
        //system.out.println("while for sure");
        Expression condition = (Expression) visitExpr(ctx.expr());
        if( condition.type != Type.boolType ) raiseError("Condition is not a boolean expression", ctx.getStart().getLine());

        while( (Boolean) condition.value ) {
            super.visitBlock(ctx.block());
            condition = (Expression) visitExpr(ctx.expr());
        }
        return null;
    }

    @Override
    public Object visitBlock_stat(AniLangParser.Block_statContext ctx) {
        return super.visitBlock_stat(ctx);
    }

    @Override
    public Object visitBlockless_stat(AniLangParser.Blockless_statContext ctx) {
        return super.visitBlockless_stat(ctx);
    }

    @Override
    public Object visitStat_with_semicolon(AniLangParser.Stat_with_semicolonContext ctx) {
        return super.visitStat_with_semicolon(ctx);
    }

    @Override
    public Object visitThen(AniLangParser.ThenContext ctx) {
        Scope childScope = new Scope(getCurrentScope());
        scopeStack.add(childScope);

        return super.visitThen(ctx);
    }

    @Override
    public Object visitEnd(AniLangParser.EndContext ctx) {
        scopeStack.remove(getCurrentScope());

        return super.visitEnd(ctx);
    }

    @Override
    public Object visitBlock(AniLangParser.BlockContext ctx) {
        return super.visitBlock(ctx);
    }

    private void raiseError(String error, int line) {
        System.err.println(String.format("line %d: ", line) + error);
        System.exit(1);
    }
//    private void raiseError(String error) {
//        System.err.println(error);
//        System.exit(1);
//    }
    private Scope getCurrentScope(){
        return scopeStack.get(scopeStack.size()-1);
    }
    private Type stringToType(TerminalNode type){
        return stringToType(type.getText());
    }
    private Type stringToType(String type){
        return switch (type) {
            case "bool" -> Type.boolType;
            case "int" -> Type.intType;
            case "double" -> Type.doubleType;
            default -> null;
        };
    }
    private List<Type> stringToType(List<TerminalNode> type){
        List<Type> res = new ArrayList<>();
        for(int i = 0 ; i < type.size() ; i++){
            res.add(stringToType(type.get(i)));
        }
        return res;
    }
}
