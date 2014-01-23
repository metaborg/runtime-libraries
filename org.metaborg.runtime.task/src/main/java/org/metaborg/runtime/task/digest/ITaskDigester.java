package org.metaborg.runtime.task.digest;

import org.metaborg.runtime.task.Task;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;

public interface ITaskDigester {
	public IStrategoTerm digest(ITermFactory factory, Task task);

	public IStrategoTerm state(ITermFactory factory);
	public void setState(IStrategoTerm state);
	public void reset();
}
