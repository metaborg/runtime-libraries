package org.metaborg.runtime.task.evaluation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.metaborg.runtime.task.ITask;
import org.metaborg.runtime.task.ITaskResults;
import org.metaborg.runtime.task.engine.ITaskEngine;
import org.metaborg.util.collection.BiLinkedHashMultimap;
import org.metaborg.util.collection.BiSetMultimap;
import org.metaborg.util.collection.ListMultimap;
import org.metaborg.util.collection.MultiSet;
import org.metaborg.util.iterators.Iterables2;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoConstructor;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.IStrategoTuple;
import org.spoofax.interpreter.terms.ITermFactory;


public class TaskEvaluationQueue implements ITaskEvaluationQueue, ITaskEvaluationFrontend {
	private final ITaskEngine taskEngine;
	private final ITermFactory factory;


	/** Queue of task that are scheduled for evaluation. */
	private final Queue<IStrategoTerm> evaluationQueue = new ArrayDeque<>();

	/** Set of tasks in the queue. **/
	private final Set<IStrategoTerm> queued = new HashSet<>();

    /** Dependencies of tasks which are updated during evaluation. */
	private BiSetMultimap<IStrategoTerm, IStrategoTerm> runtimeDependencies =
		BiLinkedHashMultimap.create();


	/** Maps the constructor of an instruction to the queuer that can queue the task. */
	private final Map<IStrategoConstructor, ITaskQueuer> taskQueuers = new LinkedHashMap<>();

	/** The default task queuer that is used to queue tasks for which there is no specific queuer. */
	private final ITaskQueuer baseTaskQueuer;

	/** Maps the constructor of an instruction to the evaluator that can evaluate the task. */
	private final Map<IStrategoConstructor, ITaskEvaluator> taskEvaluators = new LinkedHashMap<>();

	/** The default task evaluator that is used to evaluate tasks for which there is no specific evaluator. */
	private final ITaskEvaluator baseTaskEvaluator;

	private IStrategoTerm currentTaskID = null;
	private boolean currentDelayed = false;


	private Set<IStrategoTerm> scheduled;
	private final Set<IStrategoTerm> skipped = new HashSet<>();
    private final Set<IStrategoTerm> evaluated = new HashSet<>();


    public TaskEvaluationQueue(ITaskEngine taskEngine, ITermFactory factory) {
		this.taskEngine = taskEngine;
		this.factory = factory;
		this.baseTaskQueuer = new BaseTaskQueuer();
		this.baseTaskEvaluator = new BaseTaskEvaluator(factory);
	}

	@Override
	public void queue(IStrategoTerm taskID) {
		if(!queued.contains(taskID)) {
			evaluationQueue.add(taskID);
			queued.add(taskID);
		}
	}

	@Override
	public void queueOrDefer(IStrategoTerm taskID) {
		final Iterable<IStrategoTerm> dependencies = taskEngine.getDependencies(taskID, false);
		final Set<IStrategoTerm> dependenciesSet = Iterables2.toHashSet(dependencies);

		// TODO: this could be done in constant time if task engine keeps a set of solved tasks.
		for(final IStrategoTerm dependency : dependencies) {
			if(taskEngine.getTask(dependency).solved()) {
				dependenciesSet.remove(dependency);
			}
		}

		if(dependenciesSet.isEmpty()) {
			// If the task has no unsolved dependencies, queue it for analysis.
			queue(taskID);
		} else {
			// Fill toRuntimeDependency for scheduled tasks such that solving the task activates their dependent tasks.
			runtimeDependencies.putAll(taskID, dependenciesSet);
		}
	}

	@Override
	public void solved(IStrategoTerm taskID) {
		// Retrieve dependent tasks of the solved task.
		final Set<IStrategoTerm> dependents = Iterables2.toHashSet(taskEngine.getDependents(taskID, false));
		dependents.addAll(runtimeDependencies.getInverse(taskID));

		for(final IStrategoTerm dependentTaskID : dependents) {
			// Remove the dependency to the solved task. If that was the last dependency, schedule the task.
			final boolean removed = runtimeDependencies.remove(dependentTaskID, taskID);
			if(removed && runtimeDependencies.get(dependentTaskID).size() == 0
				&& !taskEngine.getTask(dependentTaskID).solved())
				queue(dependentTaskID);
		}
	}

	@Override
	public void skipped(IStrategoTerm taskID) {
		scheduled.remove(taskID);
		skipped.add(taskID);
	}

	@Override
	public void delayed(IStrategoTerm taskID, Iterable<IStrategoTerm> dependencies) {
		TaskEvaluationDebugging.debugDelayedDependecy(taskEngine, taskID, dependencies);

		currentDelayed = true;

		// Sets the runtime dependencies for a task to the given dependency list.
		runtimeDependencies.removeAll(taskID);
		for(final IStrategoTerm dependency : dependencies) {
			runtimeDependencies.put(taskID, dependency);
		}

		taskEngine.setDynamicDependencies(taskID, dependencies);
		scheduled.add(taskID);
	}

	@Override
	public boolean isDelayed() {
		return currentDelayed;
	}

	@Override
	public void addRuntimeDependency(IStrategoTerm taskID, IStrategoTerm dependencyTaskID) {
		runtimeDependencies.put(taskID, dependencyTaskID);
	}

	@Override
	public void removeRuntimeDependency(IStrategoTerm taskID, IStrategoTerm dependencyTaskID) {
		runtimeDependencies.remove(taskID, dependencyTaskID);
	}


	@Override
	public void addTaskQueuer(IStrategoConstructor constructor, ITaskQueuer taskQueuer) {
		if(taskQueuers.put(constructor, taskQueuer) != null) {
			throw new RuntimeException("Task queuer for " + constructor + " already exists.");
		}
	}

	@Override
	public void registerTaskEvaluator(IStrategoConstructor constructor, ITaskEvaluator taskEvaluator) {
		if(taskEvaluators.put(constructor, taskEvaluator) != null) {
			throw new RuntimeException("Task evaluator for " + constructor + " already exists.");
		}
	}

	@Override
	public IStrategoTuple evaluate(Set<IStrategoTerm> scheduled, IContext context, Strategy collect, Strategy insert,
		Strategy perform) {
		try {
			this.scheduled = scheduled;

			// Queue tasks and evaluate them for each specific task evaluator.
			for(ITaskQueuer taskQueuer : taskQueuers.values()) {
				taskQueuer.queue(taskEngine, this, this.scheduled);
				evaluateQueuedTasks(context, collect, insert, perform, false);
			}

			// Evaluate the remaining tasks with the default task evaluator.
			baseTaskQueuer.queue(taskEngine, this, this.scheduled);
			evaluateQueuedTasks(context, collect, insert, perform, false);

			if(!this.scheduled.isEmpty()) {
				evaluateCyclic(context, collect, insert, perform);
			}

			// Return evaluated, skipped and unevaluated task identifiers.
			return factory.makeTuple(factory.makeList(evaluated), factory.makeList(skipped),
				factory.makeList(this.scheduled));
		} finally {
			reset();
		}
	}

	private void evaluateCyclic(IContext context, Strategy collect, Strategy insert, Strategy perform) {
		// Debug unevaluated tasks if debugging is enabled.
		TaskEvaluationDebugging.debugUnevaluated(taskEngine, this.scheduled, runtimeDependencies);

		// Make a copy of the dynamic dependency graph for later use.
		final BiSetMultimap<IStrategoTerm, IStrategoTerm> copiedRuntimeDependencies =
			BiLinkedHashMultimap.create(runtimeDependencies);
		final Set<IStrategoTerm> taskIDs = new HashSet<>(copiedRuntimeDependencies.keySet());

		// Evaluate all tasks left in the dependency graph using a special strategy to break cycles.
		for(final IStrategoTerm taskID : taskIDs) {
			queue(taskID);
		}
		evaluateQueuedTasks(context, collect, insert, perform, true);

		// Store values
		final ListMultimap<IStrategoTerm, IStrategoTerm> values = new ListMultimap<>();
		for(final IStrategoTerm taskID : taskIDs) {
			final ITask task = taskEngine.getTask(taskID);
			if(!task.failed())
				task.results().forEach(r -> values.put(taskID, r));
		}

		// Do fixpoint evaluation until the results of tasks stop changing.
		for(int i = 0; i < 25; ++i) {
			System.out.println("Fixpoint cycle " + i);

			runtimeDependencies = BiLinkedHashMultimap.create(copiedRuntimeDependencies);
			for(final IStrategoTerm taskID : taskIDs) {
				queue(taskID);
			}
			evaluateQueuedTasks(context, collect, insert, perform, true);

			// Compare values
			boolean done = true;
			for(final IStrategoTerm taskID : taskIDs) {
				final ITask task = taskEngine.getTask(taskID);

				// TODO: this assumes that no results and failure means the same, is that correct?
				if(values.get(taskID).isEmpty()) {
					if(task.failed() || task.results().empty()) {
						continue;
					} else {
						done = false;
						break;
					}
				} else if(task.failed() || task.results().empty()) {
					done = false;
					break;
				}

				final List<IStrategoTerm> oldValues = values.get(taskID);
				final ITaskResults newValues = task.results();
				if(oldValues.size() != newValues.size()) {
					done = false;
					break;
				}
				// TODO: creating two sets and taking the symmetric difference is VERY expensive?
				final MultiSet.Transient<IStrategoTerm> diff1 = MultiSet.Transient.of();
				diff1.addAll(newValues);
				diff1.removeAll(oldValues);
				final MultiSet.Transient<IStrategoTerm> diff2 = MultiSet.Transient.of();
				diff2.addAll(oldValues);
				diff2.removeAll(newValues);

				if(!diff1.isEmpty() || !diff2.isEmpty()) {
					done = false;
					break;
				}
			}

			if(done) {
				System.out.println("Done with fixpoint evaluation!");
				break;
			}

			values.clear();
			for(final IStrategoTerm taskID : taskIDs) {
				final ITask task = taskEngine.getTask(taskID);
				if(!task.failed())
					task.results().forEach(r -> values.put(taskID, r));
			}
		}
	}

	@Override
	public IStrategoTerm current() {
		return currentTaskID;
	}

	@Override
	public void reset() {
		evaluationQueue.clear();
		queued.clear();
		runtimeDependencies.clear();

		currentTaskID = null;
		currentDelayed = false;

		scheduled = null;
		skipped.clear();
		evaluated.clear();

		for(ITaskEvaluator evaluator : taskEvaluators.values())
			evaluator.reset();
		baseTaskEvaluator.reset();
	}


	/**
	 * Evaluates queued tasks and updates the scheduled and evaluated sets.
	 */
	private void evaluateQueuedTasks(IContext context, Strategy collect, Strategy insert, Strategy perform,
		boolean cycle) {
		// Evaluate each task in the queue.
		for(IStrategoTerm taskID; (taskID = evaluationQueue.poll()) != null;) {
			currentTaskID = taskID;
			currentDelayed = false;

			final ITask task = taskEngine.getTask(taskID);

			evaluated.add(taskID);
			scheduled.remove(taskID);
			queued.remove(taskID);

			// Clean up data for this task again, since a task may be scheduled multiple times. A re-schedule should
			// overwrite previous data.
			taskEngine.invalidate(taskID);

			final ITaskEvaluator taskEvaluator = getTaskEvaluator(task.instruction());
			taskEvaluator.evaluate(taskID, task, taskEngine, this, context, collect, insert, perform, cycle);

			if(currentDelayed) {
				taskEngine.invalidate(taskID);
			}

			currentTaskID = null;
			currentDelayed = false;
		}
	}

	/**
	 * Returns a task evaluator for given instruction.
	 */
	private ITaskEvaluator getTaskEvaluator(IStrategoAppl instruction) {
		final ITaskEvaluator taskEvaluator = taskEvaluators.get(instruction.getConstructor());
		if(taskEvaluator == null)
			return baseTaskEvaluator;
		return taskEvaluator;
	}
}
