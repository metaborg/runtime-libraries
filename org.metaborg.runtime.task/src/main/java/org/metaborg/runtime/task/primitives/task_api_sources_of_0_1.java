package org.metaborg.runtime.task.primitives;

import java.util.HashSet;
import java.util.Set;

import org.metaborg.runtime.task.engine.ITaskEngine;
import org.metaborg.util.iterators.Iterables2;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoTerm;

import org.spoofax.terms.util.TermUtils;

public class task_api_sources_of_0_1 extends TaskEnginePrimitive {
    public static task_api_sources_of_0_1 instance = new task_api_sources_of_0_1();

    public task_api_sources_of_0_1() {
        super("task_api_sources_of", 0, 1);
    }

    @Override public boolean call(ITaskEngine taskEngine, IContext env, Strategy[] svars, IStrategoTerm[] tvars)
        throws InterpreterException {
        final IStrategoTerm taskIDOrTaskIDS = tvars[0];

        final Set<IStrategoTerm> sources = new HashSet<>();
        if(TermUtils.isList(taskIDOrTaskIDS)) {
            for(IStrategoTerm taskID : taskIDOrTaskIDS) {
                Iterables2.addAll(sources, taskEngine.getSourcesOf(taskID));
            }
        } else {
            Iterables2.addAll(sources, taskEngine.getSourcesOf(taskIDOrTaskIDS));
        }

        env.setCurrent(env.getFactory().makeList(sources));

        return true;
    }
}
