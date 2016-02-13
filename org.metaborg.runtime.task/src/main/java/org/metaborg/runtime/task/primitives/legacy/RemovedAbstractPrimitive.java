package org.metaborg.runtime.task.primitives.legacy;

import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.LoggerUtils;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.library.AbstractPrimitive;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoTerm;

public class RemovedAbstractPrimitive extends AbstractPrimitive {
    private static final ILogger logger = LoggerUtils.logger(RemovedAbstractPrimitive.class);

    public RemovedAbstractPrimitive(String name, int svars, int tvars) {
        super(name, svars, tvars);
    }

    @Override public boolean call(IContext env, Strategy[] svars, IStrategoTerm[] tvars) throws InterpreterException {
        logger.debug("Calling removed task engine primitive {}", name);
        return true;
    }
}
