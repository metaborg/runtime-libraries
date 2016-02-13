package org.metaborg.runtime.task.primitives;

import javax.annotation.Nullable;

import org.metaborg.runtime.task.engine.ITaskEngine;

public interface ITaskEngineContext {
    public abstract @Nullable ITaskEngine taskEngine();
}
