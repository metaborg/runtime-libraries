package org.metaborg.runtime.task;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.metaborg.util.iterators.Iterables2;
import org.spoofax.interpreter.terms.IStrategoTerm;

public class ListTaskResults implements ITaskResults {
    private List<IStrategoTerm> results = new ArrayList<>();


    public ListTaskResults() {

    }

    public ListTaskResults(ITaskResults results) {
        this.results = Iterables2.toArrayList(results);
    }


    @Override public boolean empty() {
        return results.isEmpty();
    }

    @Override public int size() {
        return results.size();
    }

    @Override public void set(Iterable<IStrategoTerm> results) {
        this.results = Iterables2.toArrayList(results);
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
        return TaskStorageType.List;
    }
}
