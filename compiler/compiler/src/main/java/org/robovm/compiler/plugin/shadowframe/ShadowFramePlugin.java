package org.robovm.compiler.plugin.shadowframe;

import java.io.IOException;

import org.robovm.compiler.Functions;
import org.robovm.compiler.ModuleBuilder;
import org.robovm.compiler.Types;
import org.robovm.compiler.clazz.Clazz;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.llvm.Alloca;
import org.robovm.compiler.llvm.BasicBlock;
import org.robovm.compiler.llvm.Call;
import org.robovm.compiler.llvm.Function;
import org.robovm.compiler.llvm.PlainTextInstruction;
import org.robovm.compiler.llvm.Ret;
import org.robovm.compiler.llvm.Value;
import org.robovm.compiler.llvm.Variable;
import org.robovm.compiler.llvm.VariableRef;
import org.robovm.compiler.plugin.AbstractCompilerPlugin;

import soot.SootMethod;

public class ShadowFramePlugin extends AbstractCompilerPlugin {
    private static final String SHADOW_FRAME_VAR_NAME = "__shadowFrame";
    
    @Override
    public void afterMethod(Config config, Clazz clazz, SootMethod method, ModuleBuilder moduleBuilder,
            Function function) throws IOException {
        if (!config.isUseLineNumbers()) {
            return;
        }

        // don't try to generate shadow frames for native or abstract methods
        // or methods that don't have any instructions in them
        if (method.isNative() || method.isAbstract() || !method.hasActiveBody()) {
            return;
        }

        // insert generation of shadow frame and wiring it up to previously set
        // shadow frame in the entry basic block
        BasicBlock entryBlock = function.getBasicBlocks().get(0);
        Variable shadowVariable = function.newVariable(SHADOW_FRAME_VAR_NAME, Types.SHADOW_FRAME_PTR);
        Alloca shadowAlloca = new Alloca(shadowVariable, Types.SHADOW_FRAME);
        entryBlock.getInstructions().add(0, shadowAlloca);
        
        // push frame into Env        
        Value env = function.getParameterRef(0);               
        entryBlock.getInstructions().add(1, new Call(Functions.PUSH_SHADOW_FRAME, env, new VariableRef(shadowVariable)));
        
        // initiate shadow frame with function address and dummy line number
        String functionSignature = function.getSignature();
        PlainTextInstruction initiateFrame = new PlainTextInstruction(                
                  "%funcAddr = bitcast " + functionSignature + "* @\"" + function.getName() +"\" to i8*\n"
                + "    %__shadowFrame_funcAddr = getelementptr %ShadowFrame* %__shadowFrame, i32 0, i32 1\n"
                + "    store i8* %funcAddr, i8** %__shadowFrame_funcAddr\n"
                + "    %__shadowFrame_lineNumber = getelementptr %ShadowFrame* %__shadowFrame, i32 0, i32 2\n"
                + "    store i32 -1, i32* %__shadowFrame_lineNumber\n");        
        entryBlock.getInstructions().add(2, initiateFrame);
        
        // TODO: insert line updates
        
        // insert pops on returns
        // TODO: handle unwinding due to exception
        for(BasicBlock bb: function.getBasicBlocks()) {
            for(int i = 0; i < bb.getInstructions().size(); i++) {
                if(bb.getInstructions().get(i) instanceof Ret) {
                    bb.getInstructions().add(i, new Call(Functions.POP_SHADOW_FRAME, env));
                    break;
                }
            }
        }
    }
}
