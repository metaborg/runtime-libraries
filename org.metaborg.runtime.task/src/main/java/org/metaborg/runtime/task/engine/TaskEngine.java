package org.metaborg.runtime.task.engine;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import org.metaborg.runtime.task.BaseTaskFactory;
import org.metaborg.runtime.task.ITask;
import org.metaborg.runtime.task.ITaskFactory;
import org.metaborg.runtime.task.TaskStorageType;
import org.metaborg.runtime.task.TaskType;
import org.metaborg.runtime.task.digest.ITermDigester;
import org.metaborg.runtime.task.evaluation.ITaskEvaluationFrontend;
import org.metaborg.runtime.task.util.TermTools;
import org.metaborg.util.collection.BiLinkedHashMultimap;
import org.metaborg.util.collection.BiMap2;
import org.metaborg.util.collection.BiSetMultimap;
import org.metaborg.util.collection.Sets;
import org.metaborg.util.collection.UniqueQueue;
import org.metaborg.util.iterators.Iterables2;
import org.metaborg.util.tuple.Tuple2;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoConstructor;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;

public class TaskEngine implements ITaskEngine {
    private final ITermFactory factory;
    private final ITermDigester digester;
    private ITaskEvaluationFrontend evaluationFrontend;
    private final ITaskFactory baseTaskFactory;
    private final IStrategoConstructor resultConstructor;

    /** Mapping of instruction constructors to their factory. */
    private final Map<IStrategoConstructor, ITaskFactory> taskFactories = new HashMap<>();

    /** Bidirectional mapping between task identifiers and tasks. */
    private final BiMap2<IStrategoTerm, ITask> toTask = new BiMap2<>();

    /** Mapping table of instructions and dependencies to task identifiers. */
    private final Map<Tuple2<IStrategoAppl, IStrategoList>, IStrategoTerm> toTaskID = new HashMap<>();


    /** Origins of tasks. */
    private final BiSetMultimap<IStrategoTerm, IStrategoTerm> toSource = BiLinkedHashMultimap.create();

    /** Bidirectional mapping of dependencies between tasks identifiers. */
    private final BiSetMultimap<IStrategoTerm, IStrategoTerm> toDependency = BiLinkedHashMultimap.create();

    /** Bidirectional mapping of dynamic dependencies between tasks identifiers. Can be updated during evaluation. */
    private final BiSetMultimap<IStrategoTerm, IStrategoTerm> toDynamicDependency = BiLinkedHashMultimap.create();

    /** Bidirectional mapping from task identifiers to URI's that they read during evaluation. */
    private final BiSetMultimap<IStrategoTerm, IStrategoTerm> toRead = BiLinkedHashMultimap.create();


    /** Tasks that do not have a source are garbage. **/
    private final Queue<IStrategoTerm> garbage = new UniqueQueue<IStrategoTerm>();

    /** Set of task that are scheduled for evaluation the next time evaluate is called. */
    private final Set<IStrategoTerm> scheduled = new HashSet<>();


    private final TaskCollection taskCollection;


    public TaskEngine(ITermFactory factory, ITermDigester digester) {
        this.factory = factory;
        this.digester = digester;
        this.taskCollection = new TaskCollection();
        this.baseTaskFactory = new BaseTaskFactory(factory, this);
        this.resultConstructor = factory.makeConstructor("Result", 1);
    }


    @Override public ITermDigester getDigester() {
        return digester;
    }


    @Override public ITaskEvaluationFrontend getEvaluationFrontend() {
        return evaluationFrontend;
    }

    @Override public void setEvaluationFrontend(ITaskEvaluationFrontend evaluationFrontend) {
        this.evaluationFrontend = evaluationFrontend;
    }


    @Override public ITaskFactory getTaskFactory(IStrategoAppl instruction) {
        final ITaskFactory taskFactory = taskFactories.get(instruction.getConstructor());
        if(taskFactory == null)
            return baseTaskFactory;
        return taskFactory;
    }

    @Override public void registerTaskFactory(IStrategoConstructor constructor, ITaskFactory factory) {
        taskFactories.put(constructor, factory);
    }


    @Override public void startCollection(IStrategoTerm source) {
        taskCollection.startCollection(source, getFromSource(source));
    }

    @Override public IStrategoTerm createTaskID(IStrategoAppl instruction, IStrategoList dependencies) {
        IStrategoTerm taskID = getTaskID(instruction, dependencies);
        if(taskID != null)
            return taskID;
        taskID = digester.digest(factory, instruction, dependencies);
        toTaskID.put(Tuple2.of(instruction, dependencies), taskID);
        final ITask task = getTask(taskID);
        if(task == null)
            return taskID;
        final IStrategoTerm instr = task.initialInstruction();
        if(!instruction.match(instr)) {
            reset();
            throw new IllegalStateException("Identifier collision, task " + instruction + " and " + instr
                + " have the same identifier: " + taskID);
        }
        return taskID;
    }

    @Override public IStrategoTerm addTask(IStrategoTerm source, IStrategoList dependencies, IStrategoAppl instruction,
        TaskType type, TaskStorageType storageType, boolean shortCircuit) {
        if(!taskCollection.inCollection(source))
            throw new IllegalStateException(
                "Collection has not been started yet. Call task-start-collection(|partition) before adding tasks.");

        final ITaskFactory taskFactory = getTaskFactory(instruction);

        dependencies = taskFactory.adjustDependencies(dependencies);

        final IStrategoTerm taskID = createTaskID(instruction, dependencies);

        if(getTask(taskID) == null) {
            final ITask task =
                taskFactory.create(instruction, dependencies, type, storageType, storageType, shortCircuit);
            toTask.put(taskID, task);
            taskCollection.addTask(taskID);
            schedule(taskID);
        }
        taskCollection.keepTask(taskID);

        addToSource(taskID, source);
        for(final IStrategoTerm dependency : dependencies)
            addDependency(taskID, dependency);

        return createResult(taskID);
    }

    private IStrategoAppl createResult(IStrategoTerm taskID) {
        return factory.makeAppl(resultConstructor, taskID);
    }

    @Override public void addPersistedTask(IStrategoTerm taskID, ITask task, IStrategoList initialDependencies) {
        if(getTask(taskID) != null)
            throw new RuntimeException("Trying to add a persisted task that already exists.");

        toTask.put(taskID, task);
        toTaskID.put(Tuple2.of(task.initialInstruction(), initialDependencies), taskID);
    }

    @Override public void removeTask(IStrategoTerm taskID) {
        // Thrash higher-order tasks that were created by this task. Make a copy of the result of getFromSource because
        // that collection will be changed by trashUnreferencedTasks, which would result in a concurrent modification.
        trashUnreferencedTasks(Iterables2.toArrayList(getFromSource(taskID)), taskID);

        removeSourcesOf(taskID);
        removeDependencies(taskID);
        removeReads(taskID);
        scheduled.remove(taskID);
        final ITask task = getTask(taskID); // Don't use wrapper, cannot remove from parent in this task engine.
        if(task == null)
            return; // Task is not in this task engine but might be in a parent one.
        toTaskID.remove(Tuple2.of(task.initialInstruction(), TermTools.makeList(factory, task.initialDependencies())));
        toTask.remove(taskID);
    }

    @Override public IStrategoTerm stopCollection(IStrategoTerm source) {
        final Iterable<IStrategoTerm> removedTasks = taskCollection.stopCollection(source);
        final Iterable<IStrategoTerm> addedTasks = taskCollection.addedTasks();

        trashUnreferencedTasks(removedTasks, source);
        collectGarbage();

        return factory.makeTuple(TermTools.makeList(factory, removedTasks), TermTools.makeList(factory, addedTasks));
    }

    /**
     * Removes given tasks from given source, and marks the task as garbage if it is not referenced by any source.
     */
    private void trashUnreferencedTasks(Iterable<IStrategoTerm> taskIDs, IStrategoTerm source) {
        for(final IStrategoTerm removed : taskIDs) {
            removeFromSource(removed, source);
            if(getSourcesOf(removed).isEmpty()) {
                garbage.add(removed);
            }
        }
    }

    /**
     * Removes all tasks that are marked as garbage, and clears garbage.
     */
    private void collectGarbage() {
        for(IStrategoTerm taskID; (taskID = garbage.poll()) != null;)
            removeTask(taskID);
    }

    /**
     * Schedules task with given identifier for evaluation the next time {@link #evaluate} is called.
     *
     * @param taskID
     *            The identifier of the task to schedule.
     */
    private void schedule(IStrategoTerm taskID) {
        scheduled.add(taskID);
    }

    @Override public ITask invalidate(IStrategoTerm taskID) {
        ITask task = getTask(taskID);
        if(task == null) {
            task = getTask(taskID);
            if(task == null)
                throw new RuntimeException("Cannot invalidate task that does not exist: " + taskID);
            final ITaskFactory taskFactory = getTaskFactory(task.initialInstruction());
            task = taskFactory.clone(task);
            toTask.put(taskID, task);
        }
        task.unsolve();
        task.clearMessage();
        removeReads(taskID);

        return task;
    }

    @Override public Set<IStrategoTerm> invalidateTaskReads(IStrategoList changedReads) {
        // Use work list to prevent recursion, keep collection of seen task ID's to prevent loops.
        final Set<IStrategoTerm> seen = new HashSet<>();
        final Queue<IStrategoTerm> workList = new ArrayDeque<>();
        for(final IStrategoTerm changedRead : changedReads) {
            Iterables2.addAll(seen, getReaders(changedRead));
        }
        workList.addAll(seen);

        // Schedule tasks and transitive dependent tasks that might have changed as a result of a change in reads.
        for(IStrategoTerm taskID; (taskID = workList.poll()) != null;) {
            schedule(taskID);

            final Iterable<IStrategoTerm> dependent = getDependents(taskID, true);
            for(IStrategoTerm dependentTaskID : dependent) {
                if(seen.add(dependentTaskID)) {
                    workList.offer(dependentTaskID);
                }
            }
        }

        return seen;
    }

    @Override public IStrategoTerm evaluateScheduled(IContext context, Strategy collect, Strategy insert,
        Strategy perform) {
        for(IStrategoTerm taskID : scheduled) {
            final ITask task = invalidate(taskID);
            task.clearInstructionOverride();
        }
        clearTimes();
        clearEvaluations();

        IStrategoTerm result = evaluationFrontend.evaluate(scheduled, context, collect, insert, perform);
        scheduled.clear();
        return result;
    }

    @Override public IStrategoTerm evaluateNow(IContext context, Strategy collect, Strategy insert, Strategy perform,
        Iterable<IStrategoTerm> taskIDs) {
        final Set<IStrategoTerm> scheduled = Iterables2.toHashSet(taskIDs);
        for(IStrategoTerm taskID : taskIDs)
            scheduled.addAll(getTransitiveDependencies(taskID));
        return evaluationFrontend.evaluate(scheduled, context, collect, insert, perform);
    }


    @Override public ITask getTask(IStrategoTerm taskID) {
        return toTask.get(taskID);
    }

    @Override public IStrategoTerm getTaskID(ITask task) {
        return toTask.inverse().get(task);
    }

    @Override public IStrategoTerm getTaskID(IStrategoAppl instruction, IStrategoList dependencies) {
        return toTaskID.get(Tuple2.of(instruction, dependencies));
    }


    @Override public Iterable<IStrategoTerm> getTaskIDs() {
        return toTask.keySet();
    }

    @Override public Iterable<ITask> getTasks() {
        return toTask.values();
    }

    @Override public Iterable<Entry<IStrategoTerm, ITask>> getTaskEntries() {
        return toTask.entrySet();
    }


    @Override public Set<IStrategoTerm> getAllSources() {
        return new HashSet<>(toSource.values());
    }

    @Override public Set<IStrategoTerm> getSourcesOf(IStrategoTerm taskID) {
        return toSource.get(taskID);
    }

    @Override public Iterable<IStrategoTerm> getFromSource(IStrategoTerm source) {
        return toSource.getInverse(source);
    }

    @Override public void addToSource(IStrategoTerm taskID, IStrategoTerm source) {
        toSource.put(taskID, source);
    }

    @Override public void removeFromSource(IStrategoTerm taskID, IStrategoTerm source) {
        toSource.remove(taskID, source);
    }

    @Override public void removeSourcesOf(IStrategoTerm taskID) {
        toSource.removeAll(taskID);
    }


    @Override public Iterable<IStrategoTerm> getDependencies(IStrategoTerm taskID, boolean withDynamic) {
        if(withDynamic)
            return Sets.union(toDynamicDependency.get(taskID), toDependency.get(taskID));
        else
            return toDependency.get(taskID);
    }

    @Override public Iterable<IStrategoTerm> getDynamicDependencies(IStrategoTerm taskID) {
        return toDynamicDependency.get(taskID);
    }

    @Override public Set<IStrategoTerm> getTransitiveDependencies(IStrategoTerm taskID) {
        final Set<IStrategoTerm> seen = new HashSet<>();
        final Queue<IStrategoTerm> queue = new ArrayDeque<>();

        queue.add(taskID);
        seen.add(taskID);

        for(IStrategoTerm queueTaskID; (queueTaskID = queue.poll()) != null;) {
            for(IStrategoTerm dependency : getDependencies(queueTaskID, false)) {
                if(seen.add(dependency))
                    queue.add(dependency);
            }
        }

        seen.remove(taskID);
        return seen;
    }

    @Override public Iterable<IStrategoTerm> getDependents(IStrategoTerm taskID, boolean withDynamic) {
        if(withDynamic)
            return Sets.union(toDynamicDependency.getInverse(taskID), toDependency.getInverse(taskID));
        else
            return toDependency.getInverse(taskID);
    }

    @Override public Iterable<IStrategoTerm> getDynamicDependents(IStrategoTerm taskID) {
        return toDynamicDependency.getInverse(taskID);
    }

    @Override public boolean becomesCyclic(IStrategoTerm taskIDFrom, IStrategoTerm taskIDTo) {
        final Set<IStrategoTerm> seen = new HashSet<>();
        final Queue<IStrategoTerm> queue = new ArrayDeque<>();

        queue.add(taskIDTo);
        seen.add(taskIDTo);

        for(IStrategoTerm taskID; (taskID = queue.poll()) != null;) {
            for(IStrategoTerm dependency : getDependencies(taskID, false)) {
                if(dependency.equals(taskIDFrom))
                    return true;
                if(seen.add(dependency))
                    queue.add(dependency);
            }
        }

        return false;
    }

    @Override public void addDependency(IStrategoTerm taskID, IStrategoTerm dependency) {
        toDependency.put(taskID, dependency);
    }

    @Override public void setDynamicDependencies(IStrategoTerm taskID, Iterable<IStrategoTerm> dependencies) {
        toDynamicDependency.removeAll(taskID);
        toDynamicDependency.putAll(taskID, dependencies);
    }

    @Override public void removeDependencies(IStrategoTerm taskID) {
        toDependency.removeAll(taskID);
        toDependency.removeAllInverse(taskID);
        toDynamicDependency.removeAll(taskID);
        toDynamicDependency.removeAllInverse(taskID);
    }


    @Override public Iterable<IStrategoTerm> getReads(IStrategoTerm taskID) {
        return toRead.get(taskID);
    }

    @Override public Iterable<IStrategoTerm> getReaders(IStrategoTerm uri) {
        return toRead.getInverse(uri);
    }

    @Override public void addRead(IStrategoTerm taskID, IStrategoTerm uri) {
        toRead.put(taskID, uri);
    }

    @Override public void removeReads(IStrategoTerm taskID) {
        toRead.removeAll(taskID);
    }


    @Override public void clearTimes() {
        for(ITask task : getTasks()) {
            task.clearTime();
        }
    }

    @Override public void clearEvaluations() {
        for(ITask task : getTasks()) {
            task.clearEvaluations();
        }
    }


    @Override public void recover() {
        evaluationFrontend.reset();
        taskCollection.recover();
    }

    @Override public void reset() {
        digester.reset();
        evaluationFrontend.reset();
        taskCollection.reset();

        toTask.clear();
        toTaskID.clear();

        toSource.clear();
        toDependency.clear();
        toDynamicDependency.clear();
        toRead.clear();

        garbage.clear();
        scheduled.clear();
    }
}
