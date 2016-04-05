package org.metaborg.runtime.task.primitives;

import org.metaborg.runtime.task.ITask;
import org.metaborg.runtime.task.engine.ITaskEngine;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoTerm;

public class task_api_add_message_0_2 extends TaskEnginePrimitive {
    public static task_api_add_message_0_2 instance = new task_api_add_message_0_2();

    public task_api_add_message_0_2() {
        super("task_api_add_message", 0, 2);
    }

    @Override public boolean call(ITaskEngine taskEngine, IContext env, Strategy[] svars, IStrategoTerm[] tvars)
        throws InterpreterException {
        final IStrategoTerm taskID = tvars[0];
        final IStrategoTerm message = tvars[1];
        final ITask task = taskEngine.getTask(taskID);
        if(task == null) {
            return false;
        }
        task.setMessage(message);
        return true;
    }
}
