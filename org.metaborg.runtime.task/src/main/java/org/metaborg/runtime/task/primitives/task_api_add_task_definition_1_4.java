package org.metaborg.runtime.task.primitives;

import org.metaborg.runtime.task.TaskManager;
import org.metaborg.runtime.task.definition.ITaskDefinitionRegistry;
import org.metaborg.runtime.task.util.TermTools;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.library.AbstractPrimitive;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoInt;
import org.spoofax.interpreter.terms.IStrategoString;
import org.spoofax.interpreter.terms.IStrategoTerm;

public class task_api_add_task_definition_1_4 extends AbstractPrimitive {
	public static task_api_add_task_definition_1_4 instance = new task_api_add_task_definition_1_4();

	public task_api_add_task_definition_1_4() {
		super("task_api_add_task_definition", 1, 4);
	}

	@Override
	public boolean call(IContext env, Strategy[] svars, IStrategoTerm[] tvars) throws InterpreterException {
		final Strategy strategy = svars[0];
		final IStrategoString name = (IStrategoString) tvars[0];
		final IStrategoInt arity = (IStrategoInt) tvars[1];
		final boolean isCombinator = TermTools.takeBool(tvars[2]);
		final boolean shortCircuit = TermTools.takeBool(tvars[3]);

		final ITaskDefinitionRegistry registry = TaskManager.getInstance().getCurrent().getRegistry();
		registry.register(name.stringValue(), (byte) arity.intValue(), strategy, isCombinator, shortCircuit);

		return true;
	}
}
