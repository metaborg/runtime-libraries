package org.metaborg.runtime.task.definition;

import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.terms.IStrategoTerm;

public final class TaskDefinitionProxy implements ITaskDefinition {
	public final ITaskDefinitionRegistry registry;
	public final TaskDefinitionIdentifier identifier;

	public ITaskDefinition target;

	public TaskDefinitionProxy(ITaskDefinitionRegistry registry, TaskDefinitionIdentifier identifier) {
		this.registry = registry;
		this.identifier = identifier;
	}

	@Override
	public TaskDefinitionIdentifier identifier() {
		return identifier;
	}

	@Override
	public boolean combinator() {
		if(target == null)
			resolve();
		return target.combinator();
	}

	@Override
	public boolean shortCircuit() {
		if(target == null)
			resolve();
		return target.shortCircuit();
	}

	@Override
	public IStrategoTerm evaluate(IContext context, IStrategoTerm taskID, IStrategoTerm[] tp) {
		if(target == null)
			resolve();
		return target.evaluate(context, taskID, tp);
	}

	private void resolve() {
		target = registry.get(identifier);
		if(target == null)
			throw new RuntimeException("Cannot resolve task");
	}
}
