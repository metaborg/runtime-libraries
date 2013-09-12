package org.metaborg.runtime.task.util;

import java.util.Set;

import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoConstructor;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.IStrategoTuple;
import org.spoofax.interpreter.terms.ITermFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Sets;

public final class Traversals {
	public static Set<IStrategoTerm> collectAll(Predicate<IStrategoTerm> predicate,
		Function<IStrategoTerm, IStrategoTerm> application, IStrategoTerm current) {
		final Set<IStrategoTerm> seen = Sets.newHashSet();
		collectAll(predicate, application, current, seen);
		return seen;
	}

	private static void collectAll(Predicate<IStrategoTerm> predicate,
		Function<IStrategoTerm, IStrategoTerm> application, IStrategoTerm current, Set<IStrategoTerm> seen) {
		if(predicate.apply(current))
			seen.add(application.apply(current));

		final IStrategoList annotations = current.getAnnotations();
		if(!annotations.isEmpty())
			collectAll(predicate, application, annotations, seen);

		for(IStrategoTerm subterm : current)
			collectAll(predicate, application, subterm, seen);
	}


	public static IStrategoTerm insert(ITermFactory factory, Predicate<IStrategoTerm> predicate,
		Function<IStrategoTerm, IStrategoTerm> replacer, IStrategoTerm current) {

		IStrategoList annotations = current.getAnnotations();
		if(!annotations.isEmpty()) {
			annotations = (IStrategoList) insert(factory, predicate, replacer, annotations);
			current = factory.annotateTerm(current, annotations);
		}

		if(predicate.apply(current))
			return factory.replaceTerm(replacer.apply(current), current);

		switch(current.getTermType()) {
			case IStrategoTerm.APPL: {
				final IStrategoAppl appl = (IStrategoAppl) current;
				final IStrategoConstructor constructor = appl.getConstructor();
				final IStrategoTerm[] subterms = insertSubterms(factory, predicate, replacer, current);
				return factory.replaceAppl(constructor, subterms, appl);
			}
			case IStrategoTerm.TUPLE: {
				final IStrategoTerm[] subterms = insertSubterms(factory, predicate, replacer, current);
				return factory.replaceTuple(subterms, (IStrategoTuple) current);
			}
			case IStrategoTerm.LIST: {
				final IStrategoList list = (IStrategoList) current;
				if(list.isEmpty())
					return list;
				final IStrategoTerm oldHead = list.head();
				final IStrategoList oldTail = list.tail();
				final IStrategoTerm head = insert(factory, predicate, replacer, oldHead);
				final IStrategoList tail = (IStrategoList) insert(factory, predicate, replacer, oldTail);
				return factory.replaceListCons(head, tail, oldHead, oldTail);
			}
		}

		return current;
	}

	private static IStrategoTerm[] insertSubterms(ITermFactory factory, Predicate<IStrategoTerm> predicate,
		Function<IStrategoTerm, IStrategoTerm> replacer, IStrategoTerm current) {
		final IStrategoTerm[] subterms = current.getAllSubterms();
		final int subtermCount = current.getSubtermCount();
		for(int i = 0; i < subtermCount; ++i) {
			subterms[i] = insert(factory, predicate, replacer, subterms[i]);
		}
		return subterms;
	}
}
