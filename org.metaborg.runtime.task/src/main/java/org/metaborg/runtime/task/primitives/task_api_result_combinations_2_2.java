package org.metaborg.runtime.task.primitives;

import org.metaborg.runtime.task.TaskInsertion;
import org.metaborg.runtime.task.TaskInsertion.PermsOrDeps;
import org.metaborg.runtime.task.engine.ITaskEngine;
import org.metaborg.runtime.task.util.InvokeStrategy;
import org.metaborg.runtime.task.util.TermTools;
import org.metaborg.util.iterators.Iterables2;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;

public class task_api_result_combinations_2_2 extends TaskEnginePrimitive {
    public static task_api_result_combinations_2_2 instance = new task_api_result_combinations_2_2();

    public task_api_result_combinations_2_2() {
        super("task_api_result_combinations", 2, 2);
    }

    @Override public boolean call(ITaskEngine taskEngine, IContext env, Strategy[] svars, IStrategoTerm[] tvars)
        throws InterpreterException {
        final ITermFactory factory = env.getFactory();
        final IStrategoTerm term = tvars[0];
        final boolean singleLevel = TermTools.takeBool(tvars[1]);
        final Strategy collect = svars[0];
        final Strategy insert = svars[1];

        final IStrategoTerm resultIDs = InvokeStrategy.invoke(env, collect, term);
        final PermsOrDeps result = TaskInsertion.insertResultCombinations(taskEngine, env, collect, insert, term,
            resultIDs, Iterables2.empty(), singleLevel);
        if(result == null || result.permsOrDeps == null) {
            return false; // No combinations could be constructed because a dependency failed or had no results.
        }
        final IStrategoList resultList = TermTools.makeList(factory, result.permsOrDeps);

        if(result.hasDeps) {
            // Results are task IDs of dependencies instead.
            env.setCurrent(factory.makeAppl(factory.makeConstructor("Dependency", 1), resultList));
        } else {
            env.setCurrent(resultList);
        }

        return true;
    }
}
