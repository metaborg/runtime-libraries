package org.metaborg.runtime.task.primitives;

import jakarta.annotation.Nullable;

import org.metaborg.runtime.task.engine.ITaskEngine;

public interface ITaskEngineContext {
    public abstract @Nullable ITaskEngine taskEngine();
}
