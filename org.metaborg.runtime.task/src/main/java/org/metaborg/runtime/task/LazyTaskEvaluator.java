package org.metaborg.runtime.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.spoofax.interpreter.core.Tools;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoConstructor;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.IStrategoTuple;
import org.spoofax.interpreter.terms.ITermFactory;
import org.strategoxt.lang.Context;
import org.strategoxt.lang.Strategy;

import com.google.common.collect.Iterables;

public class LazyTaskEvaluator implements ITaskEvaluator {
	private final TaskEngine taskEngine;
	private final ITermFactory factory;
	private final IStrategoConstructor dependencyConstructor;


	/** Set of task that are scheduled for evaluation the next time evaluate is called. */
	private final Set<IStrategoTerm> nextScheduled = new HashSet<IStrategoTerm>();

	/** Queue of task that are scheduled for evaluation. */
	private final Queue<IStrategoTerm> evaluationQueue = new ConcurrentLinkedQueue<IStrategoTerm>();

	/** Dependencies of tasks which are updated during evaluation. */
	private final ManyToManyMap<IStrategoTerm, IStrategoTerm> toRuntimeDependency = ManyToManyMap.create();
	
	
	/** Maps choice IDs to their choices iterator. **/
	private final Map<IStrategoTerm, Iterator<IStrategoTerm>> choiceIterators = new HashMap<IStrategoTerm, Iterator<IStrategoTerm>>();
	private final Map<IStrategoTerm, IStrategoTerm> currentChoice = new HashMap<IStrategoTerm, IStrategoTerm>();

	
	public LazyTaskEvaluator(TaskEngine taskEngine, ITermFactory factory) {
		this.taskEngine = taskEngine;
		this.factory = factory;
		this.dependencyConstructor = factory.makeConstructor("Dependency", 1);
	}

	public void schedule(IStrategoTerm taskID) {
		nextScheduled.add(taskID);
	}

	private void queue(IStrategoTerm taskID) {
		evaluationQueue.add(taskID);
	}

	public IStrategoTuple evaluate(Context context, Strategy performInstruction, Strategy insertResults) {
		try {
			/*
			// Remove solutions and reads for tasks that are scheduled for evaluation.
			for(final IStrategoTerm taskID : nextScheduled) {
				taskEngine.removeSolved(taskID);
				taskEngine.removeReads(taskID);
			}
			
			// Fill toRuntimeDependency for scheduled tasks such that solving the task activates their dependent tasks.
			for(final IStrategoTerm taskID : nextScheduled) {
				final Set<IStrategoTerm> dependencies = new HashSet<IStrategoTerm>(taskEngine.getDependencies(taskID));
				for(final IStrategoTerm dependency : taskEngine.getDependencies(taskID)) {
					if(taskEngine.isSolved(dependency))
						dependencies.remove(dependency);
				}

				// If the task has no unsolved dependencies, queue it for analysis.
				if(dependencies.isEmpty()) {
					queue(taskID);
				} else {
					toRuntimeDependency.putAll(taskID, dependencies);
				}
			}
			*/
			
			for(final IStrategoTerm taskID : nextScheduled) {
				if(TaskIdentification.isChoice(taskEngine.getInstruction(taskID))) {
					taskEngine.removeSolved(taskID);
					taskEngine.removeReads(taskID);
					queue(taskID);
				}
			}
			
			// Evaluate each task in the queue.
			int numTasksEvaluated = 0;
			for(IStrategoTerm taskID; (taskID = evaluationQueue.poll()) != null;) {
				++numTasksEvaluated;
				final IStrategoTerm instruction = taskEngine.getInstruction(taskID);
				
				if(TaskIdentification.isChoice(instruction)) {
					evaluateChoice(context, performInstruction, insertResults, taskID, instruction);
				} else {
					evaluateInstruction(context, performInstruction, insertResults, taskID, instruction);
				}
			}

			return factory.makeTuple(factory.makeList(nextScheduled), factory.makeInt(numTasksEvaluated));
		} finally {
			reset();
		}
	}
	
	private void evaluateChoice(Context context, Strategy performInstruction, Strategy insertResults,
		IStrategoTerm taskID, final IStrategoTerm instruction) {
		Iterator<IStrategoTerm> choiceIter = choiceIterators.get(taskID);
		if(choiceIter == null) {
			choiceIter = instruction.getSubterm(0).iterator();
			choiceIterators.put(taskID, choiceIter);
		}
			
		System.out.println("Choicez: " + taskID + " - " + instruction);
		
		// HACK: First need to check results of current, because a choice task will cause this Choice to be evaluated again.
		// TODO: get rid of duplicate code.
		final IStrategoTerm currentChoiceTaskID = currentChoice.get(taskID);
		if(currentChoiceTaskID != null) {
			final IStrategoList currentChoiceResults = taskEngine.getResult(currentChoiceTaskID);
			if(currentChoiceResults != null) {
				// Update dynamic dependencies because if any choice task fails or succeeds the Choice should be evaluated again.
				toRuntimeDependency.remove(taskID, currentChoiceTaskID); // TODO: is this required?
				taskEngine.addResult(taskID, currentChoiceResults);
				nextScheduled.remove(taskID);
				tryScheduleNewTasks(taskID);
				System.out.println("Choice succeeded in current: " + taskID + " - " + instruction);
				return;
			}
		}
		
		// Fail the Choice if there are no choices to evaluate any more.
		if(!choiceIter.hasNext()) {
			taskEngine.addFailed(taskID);
			nextScheduled.remove(taskID);
			tryScheduleNewTasks(taskID); // TODO: should this activate tasks?
			System.out.println("Choice failed: " + taskID + " - " + instruction);
			return;
		}
		
		final IStrategoTerm choiceTaskID = choiceIter.next().getSubterm(0);
		currentChoice.put(taskID, choiceTaskID);
		
		// Add a dependency on the choice task. If that task is solved the Choice is activated again.
		// TODO: Do we need to add a static dependency here for incremental analysis?
		toRuntimeDependency.put(taskID, choiceTaskID);
		
		// Check if task has failed.
		if(taskEngine.hasFailed(choiceTaskID)) {
			// Update dynamic dependencies because if any choice task fails or succeeds the Choice should be evaluated again.
			toRuntimeDependency.remove(taskID, choiceTaskID);
			// Try the next choice.
			System.out.println("Recurse: " + taskID + " - " + instruction);
			evaluateChoice(context, performInstruction, insertResults, taskID, instruction);
			return;
		}
		
		// Check if task has a result.
		final IStrategoList choiceResults = taskEngine.getResult(choiceTaskID);
		if(choiceResults != null) {
			// Update dynamic dependencies because if any choice task fails or succeeds the Choice should be evaluated again.
			toRuntimeDependency.remove(taskID, choiceTaskID); // TODO: is this required?
			taskEngine.addResult(taskID, choiceResults);
			nextScheduled.remove(taskID);
			tryScheduleNewTasks(taskID);
			System.out.println("Choice succeeded: " + taskID + " - " + instruction);
			return;
		}
		
		// Otherwise wait for the task to be evaluated.
		System.out.println("Wait: " + taskID + " - " + instruction);
		// TODO: schedule evaluation for the choice task and its transitive dependencies.
	}
	
	private void evaluateInstruction(Context context, Strategy performInstruction, Strategy insertResults,
		IStrategoTerm taskID, final IStrategoTerm instruction) {
		final IStrategoTerm result = solve(context, performInstruction, insertResults, taskID, instruction);
		if(result != null && Tools.isTermAppl(result)) {
			// The task has dynamic dependencies.
			final IStrategoAppl resultAppl = (IStrategoAppl) result;
			if(resultAppl.getConstructor().equals(dependencyConstructor)) {
				updateDelayedDependencies(taskID, (IStrategoList) resultAppl.getSubterm(0));
			} else { 
				throw new IllegalStateException("Unexpected result from perform-task(|taskID): " + result
					+ ". Must be a list, Dependency(_) constructor or failure.");
			}
		} else if(result == null) {
			// The task failed to produce a result.
			taskEngine.addFailed(taskID);
			nextScheduled.remove(taskID);
			tryScheduleNewTasks(taskID);
		} else if(Tools.isTermList(result)) {
			// The task produced a result.
			taskEngine.addResult(taskID, (IStrategoList) result);
			nextScheduled.remove(taskID);
			tryScheduleNewTasks(taskID);
		} else {
			throw new IllegalStateException("Unexpected result from perform-task(|taskID): " + result
				+ ". Must be a list, Dependency(_) constructor or failure.");
		}
	}

	public void reset() {
		nextScheduled.clear();
		evaluationQueue.clear();
		toRuntimeDependency.clear();
		choiceIterators.clear();
	}

	private IStrategoTerm solve(Context context, Strategy performInstruction, Strategy insertResults,
		IStrategoTerm taskID, IStrategoTerm instruction) {
		final IStrategoTerm insertedInstruction = insertResults(context, insertResults, instruction);
		return performInstruction.invoke(context, insertedInstruction, taskID);
	}

	private IStrategoTerm insertResults(Context context, Strategy insertResults, IStrategoTerm instruction) {
		return insertResults.invoke(context, instruction);
	}

	private void scheduleWithDependencies(IStrategoTerm taskID) {
		final Set<IStrategoTerm> seen = new HashSet<IStrategoTerm>();
		final Queue<IStrategoTerm> workList = new LinkedList<IStrategoTerm>();
		workList.add(taskID);
		for(IStrategoTerm scheduleTaskID; (scheduleTaskID = workList.poll()) != null;) {
			evaluator.schedule(taskID);
			seen.add(taskID);
			Collection<IStrategoTerm> dependent = getDependent(taskID);
			for(IStrategoTerm dependentTaskID : dependent) {
				if(!seen.contains(dependentTaskID))
					workList.offer(dependentTaskID);
			}
		}
	}
	
	private void tryScheduleNewTasks(IStrategoTerm solved) {
		// Retrieve dependent tasks of the solved task.
		final Collection<IStrategoTerm> dependents = taskEngine.getDependent(solved);
		// Make a copy for toRuntimeDependency because a remove operation can occur while iterating.
		final Collection<IStrategoTerm> runtimeDependents =
			new ArrayList<IStrategoTerm>(toRuntimeDependency.getInverse(solved));

		for(final IStrategoTerm dependent : Iterables.concat(dependents, runtimeDependents)) {
			// Retrieve dependencies for a dependent task.
			Collection<IStrategoTerm> dependencies = toRuntimeDependency.get(dependent);
			int dependenciesSize = dependencies.size();
			if(dependenciesSize == 0) {
				// If toRuntimeDependency does not contain dependencies for dependent yet, add them.
				dependencies = taskEngine.getDependencies(dependent);
				dependenciesSize = dependencies.size();
				toRuntimeDependency.putAll(dependent, dependencies);
			}

			// Remove the dependency to the solved task. If that was the last dependency, schedule the task.
			final boolean removed = toRuntimeDependency.remove(dependent, solved);
			if(dependenciesSize == 1 && removed && !taskEngine.isSolved(dependent))
				queue(dependent);
		}
	}

	private void updateDelayedDependencies(IStrategoTerm delayed, IStrategoList dependencies) {
		// Sets the runtime dependencies for a task to the given dependency list.
		toRuntimeDependency.removeAll(delayed);
		for(final IStrategoTerm dependency : dependencies)
			toRuntimeDependency.put(delayed, dependency);
	}
}
