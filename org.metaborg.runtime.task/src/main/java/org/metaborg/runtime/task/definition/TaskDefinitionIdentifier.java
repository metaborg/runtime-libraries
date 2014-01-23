package org.metaborg.runtime.task.definition;

public final class TaskDefinitionIdentifier {
	public final String name;
	public final byte strategyArity;
	public final byte termArity;

	public TaskDefinitionIdentifier(String name, byte strategyArity, byte termArity) {
		this.name = name;
		this.strategyArity = strategyArity;
		this.termArity = termArity;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + strategyArity;
		result = prime * result + termArity;
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
		TaskDefinitionIdentifier other = (TaskDefinitionIdentifier) obj;
		if(name == null) {
			if(other.name != null)
				return false;
		} else if(!name.equals(other.name))
			return false;
		if(strategyArity != other.strategyArity)
			return false;
		if(termArity != other.termArity)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return name + "(" + strategyArity + "|" + termArity + ")";
	}
}
