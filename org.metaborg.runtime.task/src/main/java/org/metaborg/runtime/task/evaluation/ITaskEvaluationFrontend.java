package org.metaborg.runtime.task.evaluation;

import java.util.Set;

import org.metaborg.runtime.task.definition.ITaskDefinition;
import org.metaborg.runtime.task.definition.TaskDefinitionIdentifier;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.IStrategoTuple;

/**
 * Frontend interface for task evaluation.
 */
public interface ITaskEvaluationFrontend {
	/**
	 * Adds a task evaluator for given task identifier.
	 */
	public abstract void addTaskEvaluator(TaskDefinitionIdentifier identifier, ITaskEvaluator taskEvaluator);

	/**
	 * Tries to adjust given dependencies using a task evaluator.
	 */
	public abstract IStrategoList adjustDependencies(IStrategoList dependencies, ITaskDefinition definition);

	/**
	 * Evaluates tasks given by a set of task identifiers.
	 *
	 * @param scheduled The task identifiers of tasks to evaluate.
	 * @param context The Stratego context used to insert results and perform instructions.
	 * @param insert The strategy that inserts results into an instruction.
	 * @param collect The strategy that collects result IDs from a term.
	 * @param perform The strategy that evaluates an instruction.
	 *
	 * @return A tuple with all evaluated task identifiers and unevaluated task identifiers.
	 */
	public abstract IStrategoTuple evaluate(Set<IStrategoTerm> scheduled, IContext context, Strategy collect,
		Strategy insert, Strategy perform);

	/**
	 * Returns the task identifier of the task that is currently being evaluated.
	 */
	public abstract IStrategoTerm current();

	/**
	 * Indicates that task with given task identifier is delayed because dynamic dependencies have been found. Updates
	 * the runtime dependency graph with runtime dependencies for given task.
	 */
	public abstract void delay(IStrategoTerm taskID, Iterable<IStrategoTerm> dependencies);

	/**
	 * Resets the task evaluation frontend to the initial state.
	 */
	public abstract void reset();
}
