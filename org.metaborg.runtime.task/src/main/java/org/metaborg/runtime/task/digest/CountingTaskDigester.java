package org.metaborg.runtime.task.digest;

import org.metaborg.runtime.task.Task;
import org.spoofax.interpreter.core.Tools;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;

public class CountingTaskDigester implements ITaskDigester {
	private static int RESET_COUNT = 0;

	public int count = RESET_COUNT;

	@Override
	public IStrategoTerm digest(ITermFactory factory, Task task) {
		if(count == Integer.MAX_VALUE)
			throw new IllegalStateException("Counter has reached maximum number, cannot assign new identifier.");

		return factory.makeInt(count++);
	}

	@Override
	public IStrategoTerm state(ITermFactory factory) {
		return factory.makeInt(count);
	}

	@Override
	public void setState(IStrategoTerm state) {
		count = Tools.asJavaInt(state);
	}

	@Override
	public void reset() {
		count = RESET_COUNT;
	}
}
