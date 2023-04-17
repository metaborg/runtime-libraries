package org.metaborg.runtime.task;

import static org.metaborg.runtime.task.util.InvokeStrategy.invoke;
import static org.metaborg.runtime.task.util.TermTools.makeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.metaborg.runtime.task.engine.ITaskEngine;
import org.metaborg.util.Ref;
import org.metaborg.util.collection.ListMultimap;
import org.metaborg.util.iterators.Iterables2;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.library.ssl.StrategoHashMap;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;

public final class TaskInsertion {
    /**
     * Data class representing either permutations or dynamic dependencies.
     */
    public static class PermsOrDeps {
        public final Iterable<IStrategoTerm> permsOrDeps;
        public final boolean hasDeps;


        public PermsOrDeps(Iterable<IStrategoTerm> permsOrDeps, boolean hasDeps) {
            this.permsOrDeps = permsOrDeps;
            this.hasDeps = hasDeps;
        }
    }

    /**
     * Data class representing a result map or dynamic dependencies.
     */
    private static class ResultMapOrDeps {
        public final ListMultimap<IStrategoTerm, IStrategoTerm> resultMap;
        public final Iterable<IStrategoTerm> deps;


        public ResultMapOrDeps(ListMultimap<IStrategoTerm, IStrategoTerm> resultMap) {
            this.resultMap = resultMap;
            this.deps = null;
        }

        public ResultMapOrDeps(Iterable<IStrategoTerm> deps) {
            this.resultMap = null;
            this.deps = deps;
        }


        public static ResultMapOrDeps resultMap(ListMultimap<IStrategoTerm, IStrategoTerm> resultMap) {
            return new ResultMapOrDeps(resultMap);
        }

        public static ResultMapOrDeps deps(Iterable<IStrategoTerm> deps) {
            return new ResultMapOrDeps(deps);
        }


        public boolean hasDeps() {
            return this.deps != null;
        }
    }

    /**
     * Data class representing results or dynamic dependencies;
     */
    private static class ResultsOrDeps {
        public final Collection<IStrategoTerm> results;
        public final Iterable<IStrategoTerm> deps;


        public ResultsOrDeps(Collection<IStrategoTerm> results) {
            this.results = results;
            this.deps = null;
        }

        public ResultsOrDeps(Iterable<IStrategoTerm> deps) {
            this.results = null;
            this.deps = deps;
        }


        public static ResultsOrDeps results(Collection<IStrategoTerm> results) {
            return new ResultsOrDeps(results);
        }

        public static ResultsOrDeps deps(Iterable<IStrategoTerm> deps) {
            return new ResultsOrDeps(deps);
        }


        public boolean hasDeps() {
            return this.deps != null;
        }
    }


    /**
     * Returns all instruction permutations of given task based on its dependencies. For regular tasks,
     * {@link #insertResultCombinations} is called. For task combinators, {@link #insertResultLists} is called. This
     * function assumes that all dependencies of the given task have been solved (have a result or failed).
     */
    public static PermsOrDeps taskCombinations(ITermFactory factory, ITaskEngine taskEngine, IContext context,
        Strategy collect, Strategy insert, IStrategoTerm taskID, ITask task, boolean singleLevel) {
        final IStrategoTerm instruction = task.instruction();
        final Iterable<IStrategoTerm> actualDependencies = getResultIDs(context, collect, instruction);

        switch(task.type()) {
            case Regular: {
                final Iterable<IStrategoTerm> allDependencies = taskEngine.getDependencies(taskID, false);
                if(dependencyFailure(taskEngine, allDependencies))
                    return null;

                if(Iterables2.isEmpty(actualDependencies)) {
                    return new PermsOrDeps(Iterables2.singleton(instruction), false);
                } else {
                    return insertResultCombinations(taskEngine, context, collect, insert, instruction,
                        actualDependencies, Iterables2.singleton(taskID), singleLevel);
                }
            }
            case Combinator: {
                return new PermsOrDeps(
                    Iterables2.singleton(
                        insertResultLists(factory, taskEngine, context, insert, instruction, actualDependencies)),
                    false);
            }
            case Raw: {
                return new PermsOrDeps(Iterables2.singleton(instruction), false);
            }
            default: {
                throw new RuntimeException("Task of type " + task.type() + " not handled.");
            }
        }
    }

    /**
     * Returns term permutations, or dynamic dependencies encountered while creating the term permutations. To create
     * all permutations, a cartesian product of all results of task dependencies is created and applied to the term.
     *
     * If all task dependencies have only one result, this will result in just one term. Otherwise multiple terms are
     * returned. If a task dependency has failed or has no results, null is returned instead. If dynamic task
     * dependencies are encountered, the resulting iterable contains task IDs of these dependencies.
     *
     * @param taskEngine
     *            The task engine to retrieve tasks from.
     * @param context
     *            A Stratego context for executing strategies.
     * @param collect
     *            Collect strategy that collects all result IDs in a term.
     * @param insert
     *            Insert strategy that inserts results into a term.
     * @param term
     *            The term to create permutations for.
     * @param dependencies
     *            The task IDs of tasks this term depends on.
     *
     * @return A 2-pair. If the second element is false, the first element contains permutations of the term. Otherwise
     *         it contains task IDs of dynamic task dependencies.
     */
    public static PermsOrDeps insertResultCombinations(ITaskEngine taskEngine, IContext context, Strategy collect,
        Strategy insert, IStrategoTerm term, Iterable<IStrategoTerm> dependencies, Iterable<IStrategoTerm> initialSeen,
        boolean singleLevel) {
        final Set<IStrategoTerm> seen = Iterables2.toHashSet(initialSeen);
        final ResultMapOrDeps result =
            createResultMapping(taskEngine, context, collect, insert, dependencies, seen, singleLevel);

        if(result == null) {
            return null;
        } else if(result.hasDeps()) {
            return new PermsOrDeps(result.deps, true);
        } else {
            return new PermsOrDeps(insertCarthesianProduct(context, insert, term, result.resultMap), false);
        }
    }

    private static ResultMapOrDeps createResultMapping(ITaskEngine taskEngine, IContext context, Strategy collect,
        Strategy insert, Iterable<IStrategoTerm> resultIDs, Set<IStrategoTerm> seen, boolean singleLevel) {
        final ListMultimap<IStrategoTerm, IStrategoTerm> resultsMap = new ListMultimap<>();
        final Collection<IStrategoTerm> dynamicDependencies = new ArrayList<>();

        for(final IStrategoTerm resultID : resultIDs) {
            if(seen.contains(resultID)) {
                resultsMap.put(resultID, createCycleTerm(context.getFactory(), resultID));
                continue;
            }

            final ResultsOrDeps results =
                getResultsOf(taskEngine, context, collect, insert, resultID, new HashSet<>(seen), singleLevel);

            if(results == null) {
                return null;
            } else if(results.hasDeps()) {
                Iterables2.addAll(dynamicDependencies, results.deps);
            } else {
                results.results.forEach(r -> resultsMap.put(resultID, r));
            }
        }

        if(dynamicDependencies.isEmpty())
            return ResultMapOrDeps.resultMap(resultsMap);
        else
            return ResultMapOrDeps.deps(dynamicDependencies);
    }

    private static ResultsOrDeps getResultsOf(ITaskEngine taskEngine, IContext context, Strategy collect,
        Strategy insert, IStrategoTerm taskID, Set<IStrategoTerm> seen, boolean singleLevel) {
        seen.add(taskID);
        final ITask task = taskEngine.getTask(taskID);

        if(!task.solved()) {
            return ResultsOrDeps.deps(Iterables2.singleton(taskID));
        } else if(task.failed() || task.results().empty()) {
            return null; // If a dependency does not have any results, the task cannot be executed.
        }

        final Collection<IStrategoTerm> results = new ArrayList<>();
        final Collection<IStrategoTerm> dynamicDependencies = new ArrayList<>();

        for(final IStrategoTerm result : task.results()) {
            final Iterable<IStrategoTerm> nestedResultIDs = getResultIDs(context, collect, result);
            if(singleLevel || Iterables2.isEmpty(nestedResultIDs)) {
                results.add(result);
            } else {
                final ResultMapOrDeps resultMapping =
                    createResultMapping(taskEngine, context, collect, insert, nestedResultIDs, seen, singleLevel);

                if(resultMapping == null) {
                    return null;
                } else if(resultMapping.hasDeps()) {
                    Iterables2.addAll(dynamicDependencies, resultMapping.deps);
                } else {
                    final Collection<IStrategoTerm> insertedResults =
                        insertCarthesianProduct(context, insert, result, resultMapping.resultMap);
                    results.addAll(insertedResults);
                }
            }
        }

        if(dynamicDependencies.isEmpty())
            return ResultsOrDeps.results(results);
        else
            return ResultsOrDeps.deps(dynamicDependencies);
    }

    private static Collection<IStrategoTerm> insertCarthesianProduct(IContext context, Strategy insert,
        IStrategoTerm term, ListMultimap<IStrategoTerm, IStrategoTerm> resultMapping) {
        final Collection<StrategoHashMap> resultCombinations = cartesianProduct(resultMapping);
        final Collection<IStrategoTerm> instructions = new ArrayList<>();
        for(StrategoHashMap mapping : resultCombinations) {
            instructions.add(insertResults(context, insert, term, mapping));
        }
        return instructions;
    }

    /**
     * Returns an iterable that only has one instruction where the results have been inserted as lists.
     */
    private static IStrategoTerm insertResultLists(ITermFactory factory, ITaskEngine taskEngine, IContext context,
        Strategy insert, IStrategoTerm term, Iterable<IStrategoTerm> resultIDs) {
        final StrategoHashMap mapping = new StrategoHashMap();
        for(IStrategoTerm resultID : resultIDs) {
            final ITask task = taskEngine.getTask(resultID);
            mapping.put(resultID, makeList(factory, task.results()));
        }

        return insertResults(context, insert, term, mapping);
    }

    /**
     * Given a multimap from task identifiers to their results, returns the cartesian product of that mapping. The
     * product is returned as a collection of maps that map task identifiers to one result.
     */
    public static Collection<StrategoHashMap> cartesianProduct(ListMultimap<IStrategoTerm, IStrategoTerm> results) {
        final Ref<Collection<StrategoHashMap>> result = new Ref<>(new ArrayList<>());
        if(results.size() > 0)
            result.get().add(new StrategoHashMap());

        results.forEach((IStrategoTerm k, List<IStrategoTerm> v) -> {
            Collection<StrategoHashMap> newResults = new ArrayList<>();
            for(StrategoHashMap map : result.get()) {
                for(IStrategoTerm val : v) {
                    StrategoHashMap mapping = new StrategoHashMap();
                    mapping.putAll(map);
                    mapping.put(k, val);
                    newResults.add(mapping);
                }
            }
            result.set(newResults);
        });
        return result.get();
    }

    /**
     * Checks if any tasks with given identifiers fail.
     */
    private static boolean dependencyFailure(ITaskEngine taskEngine, Iterable<IStrategoTerm> taskIDs) {
        for(IStrategoTerm taskID : taskIDs) {
            final ITask task = taskEngine.getTask(taskID);
            if(task.failed() || task.results().empty()) {
                return true; // If a dependency does not have any results, the task cannot be executed.
            }
        }
        return false;
    }


    private static Iterable<IStrategoTerm> getResultIDs(IContext context, Strategy collect, IStrategoTerm term) {
        return invoke(context, collect, term);
    }

    private static IStrategoTerm insertResults(IContext context, Strategy insertResults, IStrategoTerm instruction,
        StrategoHashMap resultCombinations) {
        return invoke(context, insertResults, instruction,
            createHashtableTerm(context.getFactory(), resultCombinations));
    }

    private static IStrategoAppl createHashtableTerm(ITermFactory factory, StrategoHashMap hashMap) {
        return factory.makeAppl(factory.makeConstructor("Hashtable", 1), hashMap);
    }

    private static IStrategoAppl createCycleTerm(ITermFactory factory, IStrategoTerm taskID) {
        return factory.makeAppl(factory.makeConstructor("Result", 1), taskID);
    }
}
