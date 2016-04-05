package org.metaborg.runtime.task.engine;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.vfs2.FileObject;
import org.metaborg.runtime.task.digest.NonDeterministicCountingTermDigester;
import org.metaborg.runtime.task.evaluation.ITaskEvaluationFrontend;
import org.metaborg.runtime.task.evaluation.TaskEvaluationQueue;
import org.metaborg.runtime.task.specific.RelationMatchTask;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;
import org.spoofax.terms.ParseError;
import org.spoofax.terms.io.binary.SAFWriter;
import org.spoofax.terms.io.binary.TermReader;

public class TaskManager {
    public static ITaskEngine create(ITermFactory termFactory) {
        final TaskEngine taskEngine = new TaskEngine(termFactory, new NonDeterministicCountingTermDigester());
        final ITaskEvaluationFrontend taskEvaluationFrontend = new TaskEvaluationQueue(taskEngine, termFactory);
        RelationMatchTask.register(taskEvaluationFrontend, termFactory);
        taskEngine.setEvaluationFrontend(taskEvaluationFrontend);
        return taskEngine;
    }

    public static ITaskEngine read(FileObject taskEngineFile, ITermFactory termFactory) throws ParseError, IOException {
        final IStrategoTerm term = readTerm(taskEngineFile, termFactory);
        final TaskEngineFactory factory = factory();
        final ITaskEngine taskEngine = create(termFactory);
        return factory.fromTerms(taskEngine, term, termFactory);
    }

    public static void write(ITaskEngine taskEngine, FileObject taskEngineFile, ITermFactory termFactory)
        throws IOException {
        taskEngineFile.createFile();
        final TaskEngineFactory factory = factory();
        final IStrategoTerm term = factory.toTerm(taskEngine, termFactory);
        writeTerm(taskEngineFile, term);
    }


    private static TaskEngineFactory factory() {
        return new TaskEngineFactory();
    }


    private static IStrategoTerm readTerm(FileObject file, ITermFactory termFactory) throws ParseError, IOException {
        final TermReader termReader = new TermReader(termFactory);
        return termReader.parseFromStream(file.getContent().getInputStream());
    }

    private static void writeTerm(FileObject file, IStrategoTerm term) throws IOException {
        file.createFile();
        final OutputStream output = file.getContent().getOutputStream();
        try {
            SAFWriter.writeTermToSAFStream(term, output);
            output.flush();
        } finally {
            output.close();
        }
    }
}
