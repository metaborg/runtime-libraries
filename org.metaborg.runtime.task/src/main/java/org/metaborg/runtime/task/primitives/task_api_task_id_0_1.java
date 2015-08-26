package org.metaborg.runtime.task.primitives;

import org.metaborg.runtime.task.engine.ITaskEngine;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoTerm;

public class task_api_task_id_0_1 extends TaskEnginePrimitive {
    public static task_api_task_id_0_1 instance = new task_api_task_id_0_1();

    public task_api_task_id_0_1() {
        super("task_api_task_id", 0, 1);
    }

    @Override public boolean call(ITaskEngine taskEngine, IContext env, Strategy[] svars, IStrategoTerm[] tvars)
        throws InterpreterException {
        final IStrategoAppl instruction = (IStrategoAppl) tvars[0];
        // HACK: pass empty list as dependencies.
        env.setCurrent(taskEngine.createTaskID(instruction, env.getFactory().makeList()));
        return true;
    }
}
