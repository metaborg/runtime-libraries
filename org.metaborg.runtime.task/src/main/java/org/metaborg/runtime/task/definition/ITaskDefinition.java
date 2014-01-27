package org.metaborg.runtime.task.definition;

import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.terms.IStrategoTerm;


public interface ITaskDefinition {
	public abstract TaskDefinitionIdentifier identifier();

	public abstract IStrategoTerm evaluate(IContext context, IStrategoTerm taskID, IStrategoTerm[] tp);

	public abstract boolean combinator();

	public abstract boolean shortCircuit();
}