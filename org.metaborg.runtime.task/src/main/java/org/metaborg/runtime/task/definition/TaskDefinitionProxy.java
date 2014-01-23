package org.metaborg.runtime.task.definition;

import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.stratego.Strategy;
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
	public IStrategoTerm evaluate(IContext context, IStrategoTerm taskID, Strategy[] sp, IStrategoTerm[] tp) {
		if(target == null)
			resolve();
		return target.evaluate(context, taskID, sp, tp);
	}

	private void resolve() {
		target = registry.get(identifier);
		if(target == null)
			throw new RuntimeException("Cannot resolve task");
	}
}
