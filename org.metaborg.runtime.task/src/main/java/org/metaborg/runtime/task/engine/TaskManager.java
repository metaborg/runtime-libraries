package org.metaborg.runtime.task.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.Map;
import java.util.Stack;

import org.metaborg.runtime.task.digest.ITermDigester;
import org.metaborg.runtime.task.digest.NonDeterministicCountingTermDigester;
import org.metaborg.runtime.task.evaluation.ITaskEvaluationFrontend;
import org.metaborg.runtime.task.evaluation.TaskEvaluationQueue;
import org.metaborg.runtime.task.specific.RelationMatchTask;
import org.spoofax.interpreter.library.IOAgent;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;
import org.spoofax.terms.io.binary.SAFWriter;
import org.spoofax.terms.io.binary.TermReader;

import com.google.common.collect.Maps;

public class TaskManager {
	private static final TaskManager INSTANCE = new TaskManager();
	private static final Map<URI, WeakReference<ITaskEngine>> taskEngineCache = Maps.newHashMap();
	private final static TaskEngineFactory taskEngineFactory = new TaskEngineFactory();

	private final ThreadLocal<Stack<ITaskEngine>> current = new ThreadLocal<Stack<ITaskEngine>>();
	private final ThreadLocal<URI> currentProject = new ThreadLocal<URI>();

	private TaskManager() {
		// use getInstance()
	}

	public static TaskManager getInstance() {
		return INSTANCE;
	}


	public boolean isInitialized() {
		return current.get() != null;
	}

	private void ensureInitialized() {
		if(!isInitialized())
			throw new IllegalStateException(
				"Task engine has not been set-up, use task-setup(|project-path) to set up the task system before use.");
	}

	public ITaskEngine getCurrent() {
		ensureInitialized();
		return current.get().peek();
	}

	public Stack<ITaskEngine> getCurrentStack() {
		ensureInitialized();
		return current.get();
	}

	public URI getCurrentProject() {
		ensureInitialized();
		return currentProject.get();
	}

	private void setCurrent(URI project, ITaskEngine taskEngine) {
		final Stack<ITaskEngine> stack = new Stack<ITaskEngine>();
		stack.push(taskEngine);
		current.set(stack);
		taskEngineCache.put(project, new WeakReference<ITaskEngine>(taskEngine));
	}

	private void setCurrent(ITaskEngine taskEngine) {
		setCurrent(getCurrentProject(), taskEngine);
	}


	public ITaskEngine pushTaskEngine(ITermFactory factory) {
		final Stack<ITaskEngine> stack = getCurrentStack();
		final ITaskEngine currentTaskEngine = stack.peek();
		final ITaskEngine newTaskEngine = createTaskEngine(currentTaskEngine, factory);
		stack.push(newTaskEngine);
		return newTaskEngine;
	}

	public ITaskEngine popTaskEngine() {
		final Stack<ITaskEngine> stack = getCurrentStack();
		if(stack.size() == 1)
			throw new RuntimeException("Cannot pop the root task engine.");

		final ITaskEngine parentTaskEngine = stack.pop();
		return parentTaskEngine;
	}

	public ITaskEngine popToRootTaskEngine() {
		final ITaskEngine rootTaskEngine = getCurrentStack().get(0);
		setCurrent(rootTaskEngine);
		return rootTaskEngine;
	}

	public ITaskEngine createTaskEngine(ITermFactory factory) {
		return createTaskEngine(factory, createTermDigester());
	}

	public ITaskEngine createTaskEngine(ITermFactory factory, ITermDigester digester) {
		final TaskEngine taskEngine = new TaskEngine(factory, digester);
		taskEngine.setEvaluationFrontend(createTaskEvaluationFrontend(taskEngine, factory));
		return taskEngine;
	}

	public ITaskEngine createTaskEngine(ITaskEngine taskEngine, ITermFactory factory) {
		final IStrategoTerm serializedTaskEngine = taskEngineFactory.toTerm(taskEngine, factory);
		final ITaskEngine taskEngineCopy = createTaskEngine(factory);
		taskEngineFactory.fromTerms(taskEngineCopy, serializedTaskEngine, factory);
		return taskEngineCopy;
	}

	public ITermDigester createTermDigester() {
		return new NonDeterministicCountingTermDigester();
	}

	public ITaskEvaluationFrontend createTaskEvaluationFrontend(ITaskEngine taskEngine, ITermFactory factory) {
		final ITaskEvaluationFrontend taskEvaluationFrontend = new TaskEvaluationQueue(taskEngine, factory);
		RelationMatchTask.register(taskEvaluationFrontend, factory);
		return taskEvaluationFrontend;
	}


	public ITaskEngine loadTaskEngine(String projectPath, ITermFactory factory, IOAgent agent) {
		URI project = getProjectURI(projectPath, agent);
		synchronized(TaskManager.class) {
			WeakReference<ITaskEngine> taskEngineRef = taskEngineCache.get(project);
			ITaskEngine taskEngine = taskEngineRef == null ? null : taskEngineRef.get();
			if(taskEngine == null) {
				File taskEngineFile = getTaskEngineFile(project);
				if(taskEngineFile.exists())
					taskEngine = read(taskEngineFile, factory);
			}
			if(taskEngine == null) {
				taskEngine = createTaskEngine(factory);
			}
			setCurrent(project, taskEngine);
			currentProject.set(project);
			return taskEngine;
		}
	}

	public void unloadTaskEngine(String projectPath, IOAgent agent) {
		final URI removedProject = getProjectURI(projectPath, agent);
		synchronized(TaskManager.class) {
			final WeakReference<ITaskEngine> removedTaskEngine = taskEngineCache.remove(removedProject);

			final Stack<ITaskEngine> stack = getCurrentStack();
			if(stack != null) {
				for(ITaskEngine taskEngine : stack) {
					if(taskEngine == removedTaskEngine.get()) {
						current.set(null);
						break;
					}
				}
			}

			final URI project = currentProject.get();
			if(project != null && project.equals(removedProject)) {
				currentProject.set(null);
			}
		}
	}

	public ITaskEngine read(File file, ITermFactory factory) {
		try {
			ITaskEngine taskEngine = createTaskEngine(factory);
			IStrategoTerm tasks = new TermReader(factory).parseFromFile(file.toString());
			return taskEngineFactory.fromTerms(taskEngine, tasks, factory);
		} catch(Exception e) {
			if(!file.delete())
				throw new RuntimeException("Failed to load task engine from " + file.getName()
					+ ". The file could not be deleted, please manually delete the file and restart analysis.", e);
			else
				throw new RuntimeException("Failed to load task engine from " + file.getName()
					+ ". The file has been deleted, a new task engine will be created on the next analysis.", e);
		}
	}

	public void write(ITaskEngine taskEngine, File file, ITermFactory factory) throws IOException {
		final IStrategoTerm serialized = taskEngineFactory.toTerm(taskEngine, factory);
		file.createNewFile();
		final FileOutputStream fos = new FileOutputStream(file);
		try {
			SAFWriter.writeTermToSAFStream(serialized, fos);
			fos.flush();
		} finally {
			fos.close();
		}
	}

	public void writeCurrent(ITermFactory factory) throws IOException {
		write(getCurrent(), getTaskEngineFile(getCurrentProject()), factory);
	}


	public URI getProjectURI(String projectPath, IOAgent agent) {
		File file = new File(projectPath);
		if(!file.isAbsolute())
			file = new File(agent.getWorkingDir(), projectPath);
		return file.toURI();
	}

	public URI getProjectURIFromAbsolute(String projectPath) {
		File file = new File(projectPath);
		if(!file.isAbsolute())
			throw new RuntimeException("Project path is not absolute.");
		return file.toURI();
	}


	public File getTaskEngineFile(URI projectPath) {
		File container = new File(new File(projectPath), ".cache");
		container.mkdirs();
		return new File(container, "taskengine.idx");
	}
}
