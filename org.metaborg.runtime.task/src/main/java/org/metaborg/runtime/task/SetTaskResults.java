package org.metaborg.runtime.task;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.metaborg.util.iterators.Iterables2;
import org.spoofax.interpreter.terms.IStrategoTerm;

public class SetTaskResults implements ITaskResults {
    private Set<IStrategoTerm> results = new HashSet<>();


    public SetTaskResults() {

    }

    public SetTaskResults(ITaskResults results) {
        this.results = Iterables2.toHashSet(results);
    }


    public boolean contains(IStrategoTerm term) {
        return results.contains(term);
    }

    @Override public boolean empty() {
        return results.isEmpty();
    }

    @Override public int size() {
        return results.size();
    }

    @Override public void set(Iterable<IStrategoTerm> results) {
        this.results = Iterables2.toHashSet(results);
    }

    @Override public void addAll(Iterable<IStrategoTerm> results) {
        for(IStrategoTerm result : results)
            this.results.add(result);
    }

    @Override public void add(IStrategoTerm result) {
        results.add(result);
    }

    @Override public void clear() {
        results.clear();
    }


    @Override public Iterator<IStrategoTerm> iterator() {
        return results.iterator();
    }

    @Override public TaskStorageType type() {
        return TaskStorageType.Set;
    }
}
