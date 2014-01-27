package org.metaborg.runtime.task.primitives;

import org.metaborg.runtime.task.ITaskEngine;
import org.metaborg.runtime.task.TaskInsertion;
import org.metaborg.runtime.task.TaskManager;
import org.metaborg.runtime.task.evaluation.ITaskEvaluationFrontend;
import org.metaborg.runtime.task.util.EmptyIterable;
import org.metaborg.runtime.task.util.InvokeStrategy;
import org.metaborg.runtime.task.util.TermTools;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.library.AbstractPrimitive;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;

import fj.data.Either;

public class task_api_result_combinations_2_2 extends AbstractPrimitive {
	public static task_api_result_combinations_2_2 instance = new task_api_result_combinations_2_2();

	public task_api_result_combinations_2_2() {
		super("task_api_result_combinations", 2, 2);
	}

	@Override
	public boolean call(IContext env, Strategy[] svars, IStrategoTerm[] tvars) throws InterpreterException {
		final ITermFactory factory = env.getFactory();
		final ITaskEngine taskEngine = TaskManager.getInstance().getCurrent();
		final IStrategoTerm term = tvars[0];
		final boolean singleLevel = TermTools.takeBool(tvars[1]);
		final Strategy collect = svars[0];
		final Strategy insert = svars[1];

		final IStrategoTerm resultIDs = InvokeStrategy.invoke(env, collect, term);
		final Either<? extends Iterable<IStrategoTerm>, ? extends Iterable<IStrategoTerm>> result =
			TaskInsertion.insertResultCombinations(taskEngine, env, collect, insert, term, resultIDs,
				new EmptyIterable<IStrategoTerm>(), singleLevel);
		if(result == null || (result.isLeft() && result.left().value() == null))
			return false; // No combinations could be constructed because a dependency failed or had no results.

		if(result.isRight()) {
			final ITaskEvaluationFrontend evaluator = taskEngine.getEvaluationFrontend();
			final IStrategoTerm taskID = evaluator.current();
			evaluator.delay(taskID, result.right().value());
			return false;
		} else {
			env.setCurrent(TermTools.makeList(factory, result.left().value()));
		}

		return true;
	}
}
