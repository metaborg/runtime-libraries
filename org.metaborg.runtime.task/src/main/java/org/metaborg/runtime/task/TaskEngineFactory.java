package org.metaborg.runtime.task;

import static org.metaborg.runtime.task.util.TermTools.makeList;
import static org.metaborg.runtime.task.util.TermTools.makeLong;
import static org.metaborg.runtime.task.util.TermTools.makeNullable;
import static org.metaborg.runtime.task.util.TermTools.takeLong;
import static org.metaborg.runtime.task.util.TermTools.takeNullable;
import static org.metaborg.runtime.task.util.TermTools.takeShort;

import java.util.Map.Entry;

import org.metaborg.runtime.task.definition.ITaskDefinition;
import org.metaborg.runtime.task.definition.TaskDefinitionIdentifier;
import org.metaborg.runtime.task.definition.TaskDefinitionProxy;
import org.spoofax.interpreter.core.Tools;
import org.spoofax.interpreter.library.ssl.StrategoHashMap;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoInt;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoString;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.IStrategoTuple;
import org.spoofax.interpreter.terms.ITermFactory;
import org.spoofax.terms.attachments.TermAttachmentSerializer;

public class TaskEngineFactory {
	public IStrategoTerm toTerm(ITaskEngine taskEngine, ITermFactory factory) {
		final TermAttachmentSerializer serializer = new TermAttachmentSerializer(factory);

		IStrategoList tasks = factory.makeList();
		for (final Entry<IStrategoTerm, Task> entry : taskEngine.getTaskEntries()) {
			final IStrategoTerm taskID = entry.getKey();
			final Task task = entry.getValue();

			final Iterable<IStrategoString> partitions = taskEngine.getPartitionsOf(taskID);
			final Iterable<IStrategoTerm> dependencies = taskEngine.getDependencies(taskID);
			final Iterable<IStrategoTerm> reads = taskEngine.getReads(taskID);
			final IStrategoTerm results = serializeResults(task.results(), factory, serializer);
			IStrategoTerm message = task.message();
			if (message != null)
				message = serializer.toAnnotations(message);
			tasks =
				factory.makeListCons(
					createTaskTerm(factory, taskID, task, partitions, dependencies, reads, results, message), tasks);
		}

		final IStrategoTerm digestState = taskEngine.getDigester().state(factory);

		return factory.makeTuple(digestState, tasks);
	}

	public ITaskEngine fromTerms(ITaskEngine taskEngine, IStrategoTerm term, ITermFactory factory) {
		final TermAttachmentSerializer serializer = new TermAttachmentSerializer(factory);

		final IStrategoTerm digestState = term.getSubterm(0);
		taskEngine.getDigester().setState(digestState);

		final IStrategoTerm tasks = term.getSubterm(1);
		for (IStrategoTerm taskTerm : tasks) {

			final IStrategoString name = (IStrategoString) taskTerm.getSubterm(0);
			final IStrategoInt arity = (IStrategoInt) taskTerm.getSubterm(1);
			final IStrategoTerm taskID = taskTerm.getSubterm(2);
			final IStrategoTuple arguments = (IStrategoTuple) taskTerm.getSubterm(3);
			final IStrategoList initialDependencies = (IStrategoList) taskTerm.getSubterm(4);
			final IStrategoTerm results = deserializeResults(taskTerm.getSubterm(5), factory, serializer);
			final IStrategoInt status = (IStrategoInt) taskTerm.getSubterm(6);
			final IStrategoTerm message = taskTerm.getSubterm(7);
			final IStrategoTerm time = taskTerm.getSubterm(8);
			final IStrategoTerm evaluations = taskTerm.getSubterm(9);
			final IStrategoList dependencies = (IStrategoList) taskTerm.getSubterm(10);
			final IStrategoList partitions = (IStrategoList) taskTerm.getSubterm(11);
			final IStrategoList reads = (IStrategoList) taskTerm.getSubterm(12);

			final TaskDefinitionIdentifier identifier = new TaskDefinitionIdentifier(name.stringValue(), (byte)arity.intValue());
			final ITaskDefinition definition = new TaskDefinitionProxy(taskEngine.getRegistry(), identifier);
			final Task task = new Task(definition, arguments.getAllSubterms(), initialDependencies);

			taskEngine.addPersistedTask(taskID, task, partitions, initialDependencies, dependencies, reads,
				takeNullable(results), TaskStatus.get(status.intValue()), takeNullable(message), takeLong(time),
				takeShort(evaluations));
		}

		return taskEngine;
	}

	/**
	 * Creates a tuple with information about a task instance in the following
	 * format:
	 *
	 * <pre>
	 * 0.  Task definition name
	 * 1.  Task definition arity
	 * 2.  Task identifier
	 * 3.  Term arguments
	 * 4.  Initial dependencies
	 * 5.  Results
	 * 6.  Status
	 * 7.  Message
	 * 8.  Evaluation time
	 * 9.  Evaluation count
	 * 10. Dependencies (including dynamic dependencies)
	 * 11. Partitions
	 * 12. Reads
	 * </pre>
	 */
	private IStrategoTerm createTaskTerm(ITermFactory factory, IStrategoTerm taskID, Task task,
		Iterable<IStrategoString> partitions, Iterable<IStrategoTerm> dependencies, Iterable<IStrategoTerm> reads,
		IStrategoTerm results, IStrategoTerm message) {
		TaskDefinitionIdentifier identifier = task.definition.identifier();

		// @formatter:off
		return factory.makeTuple(
			factory.makeString(identifier.name),
			factory.makeInt(identifier.arity),
			taskID,
			factory.makeTuple(task.arguments),
			task.initialDependencies,
			makeNullable(factory, results),
			factory.makeInt(task.status().id),
			makeNullable(factory, message),
			makeLong(factory, task.time()),
			factory.makeInt(task.evaluations()),
			makeList(factory, dependencies),
			makeList(factory, partitions),
			makeNullable(factory, results)
		);
		// @formatter:on
	}

	private IStrategoList serializeResults(Iterable<IStrategoTerm> results, ITermFactory factory,
		TermAttachmentSerializer serializer) {
		if (results != null) {
			IStrategoList newResults = factory.makeList();
			for (IStrategoTerm result : results) {
				IStrategoTerm newResult;
				if (isHashMap(result))
					newResult = serializeHashMap((StrategoHashMap) result.getSubterm(0), factory);
				else
					newResult = result;
				newResults = factory.makeListCons(serializer.toAnnotations(newResult), newResults);
			}
			return newResults;
		}
		return null;
	}

	private IStrategoTerm deserializeResults(IStrategoTerm results, ITermFactory factory,
		TermAttachmentSerializer serializer) {
		if (Tools.isTermList(results)) {
			IStrategoList newResults = factory.makeList();
			for (IStrategoTerm result : results) {
				IStrategoTerm newResult;
				if (isHashMap(result))
					newResult = deserializeHashMap(result, factory);
				else
					newResult = result;
				newResults = factory.makeListCons(serializer.fromAnnotations(newResult, false), newResults);
			}
			return newResults;
		}
		return results;
	}

	private IStrategoTerm serializeHashMap(StrategoHashMap hashMap, ITermFactory factory) {
		IStrategoList entries = factory.makeList();
		for (Entry<IStrategoTerm, IStrategoTerm> entry : hashMap.entrySet()) {
			entries = factory.makeListCons(factory.makeTuple(entry.getKey(), entry.getValue()), entries);
		}
		return factory.makeAppl(factory.makeConstructor("Hashtable", 1), entries);
	}

	private boolean isHashMap(IStrategoTerm term) {
		return Tools.isTermAppl(term) && Tools.hasConstructor((IStrategoAppl) term, "Hashtable");
	}

	private IStrategoTerm deserializeHashMap(IStrategoTerm term, ITermFactory factory) {
		StrategoHashMap hashMap = new StrategoHashMap();
		IStrategoTerm entries = term.getSubterm(0);
		for(IStrategoTerm entry : entries) {
			hashMap.put(entry.getSubterm(0), entry.getSubterm(1));
		}
		return factory.makeAppl(factory.makeConstructor("Hashtable", 1), hashMap);
	}
}
