package org.metaborg.runtime.task.digest;

import org.metaborg.runtime.task.Task;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;

public class HashCodeTaskDigester implements ITaskDigester {
	@Override
	public IStrategoTerm digest(ITermFactory factory, Task task) {
		return factory.makeInt(task.hashCode());
	}

	@Override
	public IStrategoTerm state(ITermFactory factory) {
		return factory.makeInt(0);
	}

	@Override
	public void setState(IStrategoTerm state) {

	}

	@Override
	public void reset() {

	}
}
