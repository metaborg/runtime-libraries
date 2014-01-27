package org.metaborg.runtime.task;

import java.util.Arrays;
import java.util.List;

import org.metaborg.runtime.task.definition.ITaskDefinition;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;

import com.google.common.collect.Lists;

public final class Task {
	public final ITaskDefinition definition;
	public final IStrategoTerm[] arguments;
	public final IStrategoList initialDependencies;

	private List<IStrategoTerm> results = Lists.newLinkedList();
	private TaskStatus status = TaskStatus.Unknown;
	private IStrategoTerm message;
	private long time = -1;
	private short evaluations = 0;

	public Task(ITaskDefinition definition, IStrategoTerm[] arguments, IStrategoList initialDependencies) {
		this.definition = definition;
		this.arguments = arguments;
		this.initialDependencies = initialDependencies;
	}

	public Task(Task task) {
		this.definition = task.definition;
		this.arguments = task.arguments;
		this.initialDependencies = task.initialDependencies;

		this.results = Lists.newLinkedList(task.results);
		this.status = task.status;
		this.message = task.message;
		this.time = task.time;
		this.evaluations = task.evaluations;
	}

	public Iterable<IStrategoTerm> results() {
		return results;
	}

	public boolean hasResults() {
		return !results.isEmpty();
	}

	public void setResults(Iterable<IStrategoTerm> results) {
		this.results = Lists.newLinkedList(results);
		status = TaskStatus.Success;
	}

	public void addResults(Iterable<IStrategoTerm> results) {
		for(IStrategoTerm result : results)
			this.results.add(result);
		status = TaskStatus.Success;
	}

	public void addResult(IStrategoTerm result) {
		results.add(result);
		status = TaskStatus.Success;
	}

	public TaskStatus status() {
		return status;
	}

	public void setStatus(TaskStatus status) {
		this.status = status;
	}

	public boolean failed() {
		return status == TaskStatus.Fail || status == TaskStatus.DependencyFail;
	}

	public void setFailed() {
		status = TaskStatus.Fail;
	}

	public boolean dependencyFailed() {
		return status == TaskStatus.DependencyFail;
	}

	public void setDependencyFailed() {
		status = TaskStatus.DependencyFail;
	}

	public boolean solved() {
		return status != TaskStatus.Unknown;
	}

	public void unsolve() {
		status = TaskStatus.Unknown;
		results.clear();
		clearMessage();
		clearTime();
		clearEvaluations();
	}

	public IStrategoTerm message() {
		return message;
	}

	public void setMessage(IStrategoTerm message) {
		this.message = message;
	}

	public void clearMessage() {
		message = null;
	}

	public long time() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public void addTime(long time) {
		this.time += time;
	}

	public void clearTime() {
		time = -1;
	}

	public short evaluations() {
		return evaluations;
	}

	public void setEvaluations(short evaluations) {
		this.evaluations = evaluations;
	}

	public void addEvaluation() {
		++evaluations;
	}

	public void clearEvaluations() {
		evaluations = 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((definition == null) ? 0 : definition.hashCode());
		result = prime * result + ((initialDependencies == null) ? 0 : initialDependencies.hashCode());
		result = prime * result + Arrays.hashCode(arguments);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		Task other = (Task) obj;
		if(definition == null) {
			if(other.definition != null)
				return false;
		} else if(!definition.equals(other.definition))
			return false;
		if(initialDependencies == null) {
			if(other.initialDependencies != null)
				return false;
		} else if(!initialDependencies.equals(other.initialDependencies))
			return false;
		if(!Arrays.equals(arguments, other.arguments))
			return false;
		return true;
	}
}
