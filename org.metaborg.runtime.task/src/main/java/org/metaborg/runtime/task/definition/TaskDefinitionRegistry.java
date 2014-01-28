package org.metaborg.runtime.task.definition;

import java.util.Map;

import org.spoofax.interpreter.stratego.Strategy;

import com.google.common.collect.Maps;

public final class TaskDefinitionRegistry implements ITaskDefinitionRegistry {
	private final Map<TaskDefinitionIdentifier, TaskDefinition> taskDefinitions = Maps.newHashMap();

	@Override
	public ITaskDefinition register(String name, byte arity, Strategy strategy, boolean isCombinator,
		boolean shortCircuit) {
		final TaskDefinitionIdentifier identifier = new TaskDefinitionIdentifier(name, arity);

		TaskDefinition definition = taskDefinitions.get(identifier);
		if(definition == null) {
			definition = new TaskDefinition(identifier, strategy, isCombinator, shortCircuit);
			taskDefinitions.put(identifier, definition);
		}
		return definition;
	}

	@Override
	public ITaskDefinition get(String name, byte arity) {
		// TODO: instantiation expensive for task definition retrieval?
		return get(new TaskDefinitionIdentifier(name, arity));
	}

	@Override
	public ITaskDefinition get(TaskDefinitionIdentifier identifier) {
		ITaskDefinition definition = taskDefinitions.get(identifier);
		if(definition == null)
			throw new RuntimeException("Trying to retrieve task definition for " + identifier + " that does not exist.");
		return definition;
	}

	@Override
	public void reset() {
		taskDefinitions.clear();
	}
}
