package org.metaborg.runtime.task.definition;

import java.util.Map;

import org.spoofax.interpreter.stratego.Strategy;

import com.google.common.collect.Maps;

public final class TaskDefinitionRegistry implements ITaskDefinitionRegistry {
	private final Map<TaskDefinitionIdentifier, TaskDefinition> taskDefinitions = Maps.newHashMap();

	@Override
	public ITaskDefinition register(String name, byte strategyArity, byte termArity, Strategy strategy,
		boolean isCombinator, boolean shortCircuit) {
		final TaskDefinitionIdentifier identifier = new TaskDefinitionIdentifier(name, strategyArity, termArity);

		TaskDefinition definition = taskDefinitions.get(identifier);
		if(definition == null) {
			definition = new TaskDefinition(identifier, strategy, isCombinator, shortCircuit);
			taskDefinitions.put(identifier, definition);
		}
		return definition;
	}

	@Override
	public ITaskDefinition get(String name, byte strategyArity, byte termArity) {
		// TODO: instantiation expensive for task definition retrieval?
		return get(new TaskDefinitionIdentifier(name, strategyArity, termArity));
	}

	@Override
	public ITaskDefinition get(TaskDefinitionIdentifier identifier) {
		return taskDefinitions.get(identifier);
	}

	@Override
	public void reset() {
		taskDefinitions.clear();
	}
}
