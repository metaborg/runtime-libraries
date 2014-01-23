package org.metaborg.runtime.task.definition;

import org.metaborg.runtime.task.util.InvokeStrategy;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoTerm;

public final class TaskDefinition implements ITaskDefinition {
	public final TaskDefinitionIdentifier identifier;
	public final Strategy strategy;
	public final boolean isCombinator;
	public final boolean shortCircuit;

	public TaskDefinition(TaskDefinitionIdentifier identifier, Strategy strategy, boolean isCombinator,
		boolean shortCircuit) {
		this.identifier = identifier;
		this.strategy = strategy;
		this.isCombinator = isCombinator;
		this.shortCircuit = shortCircuit;
	}

	@Override
	public TaskDefinitionIdentifier identifier() {
		return identifier;
	}

	@Override
	public IStrategoTerm evaluate(IContext context, IStrategoTerm taskID, Strategy[] sp, IStrategoTerm[] tp) {
		if(sp.length > identifier.strategyArity)
			throw new RuntimeException("Task " + identifier.name + " expected strategy arity of "
				+ identifier.strategyArity + ", got " + sp.length);

		if(tp.length > identifier.termArity)
			throw new RuntimeException("Task " + identifier.name + " expected term arity of " + identifier.termArity
				+ ", got " + tp.length);

		return InvokeStrategy.invoke(context, strategy, taskID, sp, tp);
	}

	@Override
	public int hashCode() {
		return identifier.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		TaskDefinition other = (TaskDefinition) obj;
		if(identifier == null) {
			if(other.identifier != null)
				return false;
		} else if(!identifier.equals(other.identifier))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return identifier.toString();
	}
}
