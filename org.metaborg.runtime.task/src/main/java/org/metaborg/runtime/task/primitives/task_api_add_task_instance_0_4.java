package org.metaborg.runtime.task.primitives;

import org.metaborg.runtime.task.ITaskEngine;
import org.metaborg.runtime.task.TaskManager;
import org.metaborg.runtime.task.definition.ITaskDefinition;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.library.AbstractPrimitive;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoString;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.IStrategoTuple;

public class task_api_add_task_instance_0_4 extends AbstractPrimitive {
	public static task_api_add_task_instance_0_4 instance = new task_api_add_task_instance_0_4();

	public task_api_add_task_instance_0_4() {
		super("task_api_add_task", 0, 4);
	}

	@Override
	public boolean call(IContext env, Strategy[] svars, IStrategoTerm[] tvars) throws InterpreterException {
		final IStrategoString partition = (IStrategoString) tvars[0];
		final IStrategoString name = (IStrategoString) tvars[1];
		final IStrategoTuple argumentsTuple = (IStrategoTuple) tvars[2];
		final IStrategoList dependencies = (IStrategoList) tvars[3];

		final IStrategoTerm[] arguments = argumentsTuple.getAllSubterms();
		final ITaskEngine taskEngine = TaskManager.getInstance().getCurrent();
		final ITaskDefinition definition = taskEngine.getRegistry().get(name.stringValue(), (byte) arguments.length);
		env.setCurrent(TaskManager.getInstance().getCurrent().addTask(partition, definition, dependencies, arguments));
		return true;
	}
}
