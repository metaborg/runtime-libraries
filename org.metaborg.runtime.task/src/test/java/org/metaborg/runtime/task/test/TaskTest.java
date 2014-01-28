package org.metaborg.runtime.task.test;

import java.security.NoSuchAlgorithmException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.metaborg.runtime.task.ITaskEngine;
import org.metaborg.runtime.task.TaskManager;
import org.metaborg.runtime.task.definition.ITaskDefinition;
import org.spoofax.interpreter.core.Interpreter;
import org.spoofax.interpreter.library.IOAgent;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoInt;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoString;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.IStrategoTuple;
import org.spoofax.interpreter.terms.ITermFactory;

public class TaskTest {
	protected static Interpreter interpreter;
	protected static ITermFactory factory;
	protected static IOAgent agent;

	protected static TaskManager taskManager;
	protected static ITaskEngine taskEngine;

	protected static ITaskDefinition resolve;
	protected static ITaskDefinition resolveImport;
	protected static ITaskDefinition choice;

	@BeforeClass
	public static void setUpOnce() throws NoSuchAlgorithmException {
		interpreter = new Interpreter();
		factory = interpreter.getFactory();
		agent = interpreter.getIOAgent();

		taskManager = TaskManager.getInstance();
		taskEngine = taskManager.loadTaskEngine(".", factory, agent);

		resolve = addTaskDefinition("Resolve", 1, false, true);
		resolveImport = addTaskDefinition("ResolveImport", 1, false, true);
		choice = addTaskDefinition("Choice", 1, true, false);
	}

	@AfterClass
	public static void tearDownOnce() {
		taskEngine.reset();
		taskEngine = null;
		taskManager = null;
		interpreter.shutdown();
		interpreter = null;
		factory = null;
		agent = null;
	}


	public static ITaskDefinition addTaskDefinition(String name, int arity, Strategy strategy, boolean isCombinator,
		boolean shortCircuit) {
		return taskEngine.getRegistry().register(name, (byte) arity, strategy, isCombinator, shortCircuit);
	}

	public static ITaskDefinition addTaskDefinition(String name, int arity, boolean isCombinator, boolean shortCircuit) {
		return addTaskDefinition(name, arity, null, isCombinator, shortCircuit);
	}


	public static IStrategoTerm addTask(IStrategoString partition, ITaskDefinition definition,
		IStrategoList dependencies, IStrategoTerm... arguments) {
		return resultID(taskEngine.addTask(partition, definition, dependencies, arguments));
	}

	public static IStrategoTerm addTask(IStrategoString partition, ITaskDefinition definition,
		IStrategoTerm... arguments) {
		return addTask(partition, definition, dependencies(), arguments);
	}


	public static IStrategoTerm resolve(IStrategoString partition, IStrategoList dependencies, IStrategoTerm uri) {
		return addTask(partition, resolve, dependencies, uri);
	}

	public static IStrategoTerm resolveImport(IStrategoString partition, IStrategoList dependencies, IStrategoTerm uri) {
		return addTask(partition, resolveImport, dependencies, uri);
	}

	public static IStrategoTerm choice(IStrategoString partition, IStrategoList dependencies, IStrategoTerm... results) {
		return addTask(partition, choice, dependencies, factory.makeList(results));
	}


	public static IStrategoString str(String str) {
		return factory.makeString(str);
	}

	public static IStrategoInt i(int i) {
		return factory.makeInt(i);
	}

	public static IStrategoAppl appl(String constructor, IStrategoTerm... terms) {
		return factory.makeAppl(factory.makeConstructor(constructor, terms.length), terms);
	}

	public static IStrategoList list(IStrategoTerm... terms) {
		return factory.makeList(terms);
	}

	public static IStrategoTuple tuple(IStrategoTerm... terms) {
		return factory.makeTuple(terms);
	}


	public static IStrategoString partition(String file) {
		return str(file);
	}

	public static IStrategoAppl uri(String language, IStrategoTerm... segments) {
		IStrategoTerm[] reversed = new IStrategoTerm[segments.length];
		for(int i = 0; i < reversed.length; ++i)
			// Paths are reversed in Stratego for easy appending of new names.
			reversed[i] = segments[reversed.length - i - 1];
		return appl("URI", appl("Language", str(language), list(reversed)));
	}

	public static IStrategoAppl segment(String namespace, String name) {
		return appl("ID", appl(namespace), str(name), appl("NonUnique"));
	}

	public static IStrategoAppl segment(String namespace, String name, String unique) {
		return appl("ID", appl(namespace), str(name), appl("Unique", str(unique)));
	}

	public static IStrategoTerm resultID(IStrategoTerm result) {
		return result.getSubterm(0);
	}

	public static IStrategoTerm makeResult(IStrategoTerm taskID) {
		return appl("Result", taskID);
	}

	public static IStrategoList dependencies(IStrategoTerm... results) {
		return list(results);
	}

	public static IStrategoAppl def(String language, IStrategoTerm... segments) {
		return appl("Def", uri(language, segments));
	}


	public static <T> boolean assertContains(Iterable<T> iterable, T element) {
		boolean found = false;
		for(T item : iterable)
			found = found || element.equals(item);
		return found;
	}

	public static <T> boolean assertContainsAll(Iterable<T> iterable, T element) {
		if(!iterable.iterator().hasNext())
			return false;

		boolean found = true;
		for(T item : iterable)
			found = found && element.equals(item);
		return found;
	}
}
