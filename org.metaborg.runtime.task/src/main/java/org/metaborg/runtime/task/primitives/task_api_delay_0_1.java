package org.metaborg.runtime.task.primitives;

import org.metaborg.runtime.task.engine.ITaskEngine;
import org.metaborg.runtime.task.evaluation.ITaskEvaluationFrontend;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;

public class task_api_delay_0_1 extends TaskEnginePrimitive {
    public static task_api_delay_0_1 instance = new task_api_delay_0_1();

    public task_api_delay_0_1() {
        super("task_api_delay", 0, 1);
    }

    @Override public boolean call(ITaskEngine taskEngine, IContext env, Strategy[] svars, IStrategoTerm[] tvars)
        throws InterpreterException {
        final IStrategoList dependencies = (IStrategoList) tvars[0];
        final ITaskEvaluationFrontend evaluator = taskEngine.getEvaluationFrontend();
        final IStrategoTerm taskID = evaluator.current();

        if(taskID == null)
            throw new RuntimeException("Cannot delay task while no task evaluation is in progress.");

        evaluator.delayed(taskID, dependencies.getSubterms());
        return false;
    }
}
