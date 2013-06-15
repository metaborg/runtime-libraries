package org.metaborg.runtime.task.interop;

import java.security.NoSuchAlgorithmException;

import org.metaborg.runtime.task.TaskManager;
import org.spoofax.interpreter.core.Tools;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.jsglr.client.imploder.ImploderOriginTermFactory;
import org.strategoxt.lang.Context;
import org.strategoxt.lang.Strategy;

public class task_api_setup_0_1 extends Strategy {
	public static task_api_setup_0_1 instance = new task_api_setup_0_1();

	@Override
	public IStrategoTerm invoke(Context context, IStrategoTerm current, IStrategoTerm projectPath) {
		if(!(context.getFactory() instanceof ImploderOriginTermFactory)) {
			context.setFactory(new ImploderOriginTermFactory(context.getFactory().getFactoryWithStorageType(
				IStrategoTerm.MUTABLE)));
		}

		try {
			TaskManager.getInstance().loadTaskEngine(Tools.asJavaString(projectPath), context.getFactory(),
				context.getIOAgent());
			return current;
		} catch(NoSuchAlgorithmException e) {
			return null;
		}
	}
}
