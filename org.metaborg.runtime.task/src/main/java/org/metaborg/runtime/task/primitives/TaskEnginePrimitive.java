package org.metaborg.runtime.task.primitives;

import org.metaborg.runtime.task.engine.ITaskEngine;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.library.AbstractPrimitive;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoTerm;

public abstract class TaskEnginePrimitive extends AbstractPrimitive {
    public TaskEnginePrimitive(String name, int svars, int tvars) {
        super(name, svars, tvars);
    }

    @Override public final boolean call(IContext env, Strategy[] svars, IStrategoTerm[] tvars)
        throws InterpreterException {
        final Object contextObj = env.contextObject();
        if(!(contextObj instanceof ITaskEngineContext)) {
            throw new InterpreterException(
                "Context does not implement ITaskEngineContext, cannot retrieve current task engine");
        }
        final ITaskEngineContext context = (ITaskEngineContext) env.contextObject();
        final ITaskEngine taskEngine = context.taskEngine();
        if(taskEngine == null) {
            throw new InterpreterException("Task engine has not been initialized, cannot retrieve current task engine");
        }
        return call(taskEngine, env, svars, tvars);
    }

    public abstract boolean call(ITaskEngine taskEngine, IContext env, Strategy[] svars, IStrategoTerm[] tvars)
        throws InterpreterException;
}
