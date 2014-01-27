package org.metaborg.runtime.task.digest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.metaborg.runtime.task.Task;
import org.metaborg.runtime.task.definition.TaskDefinitionIdentifier;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;
import org.spoofax.terms.io.binary.SAFWriter;

public class SHA1TaskDigester implements ITaskDigester {
	private final MessageDigest digest;

	public SHA1TaskDigester() throws NoSuchAlgorithmException {
		digest = MessageDigest.getInstance("SHA-1");
	}

	@Override
	public IStrategoTerm digest(ITermFactory factory, Task task) {
		digest.reset();
		digestTask(task);
		byte[] data = digest.digest();
		return factory.makeTuple(factory.makeInt(toInt(data, 0)), factory.makeInt(toInt(data, 4)));
	}

	@Override
	public IStrategoTerm state(ITermFactory factory) {
		return factory.makeInt(0);
	}

	@Override
	public void setState(IStrategoTerm state) {

	}

	@Override
	public void reset() {

	}

	private void digestTask(Task task) {
		final TaskDefinitionIdentifier identifier = task.definition.identifier();
		digest.update(identifier.name.getBytes());
		digest.update(identifier.arity);

		digestTermSerializer(task.initialDependencies);

		for(IStrategoTerm term : task.arguments) {
			digestTermSerializer(term);
		}
	}

	private void digestTermSerializer(IStrategoTerm term) {
		digest.update(SAFWriter.writeTermToSAFString(term));
	}

	private int toInt(final byte[] digest, final int offset) {
		return ((digest[offset + 0] & 0xFF) << 24) | ((digest[offset + 1] & 0xFF) << 16)
			| ((digest[offset + 2] & 0xFF) << 8) | (digest[offset + 3] & 0xFF);
	}

	// private final ByteBuffer intBuffer = ByteBuffer.allocate(4);
	// private final ByteBuffer doubleBuffer = ByteBuffer.allocate(8);
	//
	// private void digestTop(IStrategoTerm term) {
	// digestTerm(term);
	// digestTerm(term.getAnnotations());
	// }
	//
	// private void digestTerm(IStrategoTerm term) {
	// switch(term.getTermType()) {
	// case IStrategoTerm.APPL: {
	// final IStrategoAppl t = (IStrategoAppl) term;
	// digest.update(t.getConstructor().getName().getBytes());
	// intBuffer.position(0);
	// digest.update(intBuffer.putInt(t.getConstructor().getArity()).array());
	//
	// for(IStrategoTerm subterm : t)
	// digestTop(subterm);
	// break;
	// }
	// case IStrategoTerm.TUPLE: {
	// final IStrategoTuple t = (IStrategoTuple) term;
	// for(IStrategoTerm subterm : t)
	// digestTop(subterm);
	// break;
	// }
	// case IStrategoTerm.LIST: {
	// final IStrategoList t = (IStrategoList) term;
	// for(IStrategoTerm subterm : t)
	// digestTop(subterm);
	// break;
	// }
	// case IStrategoTerm.INT: {
	// final IStrategoInt t = (IStrategoInt) term;
	// intBuffer.position(0);
	// digest.update(intBuffer.putInt(t.intValue()).array());
	// break;
	// }
	// case IStrategoTerm.REAL: {
	// final IStrategoReal t = (IStrategoReal) term;
	// doubleBuffer.position(0);
	// digest.update(doubleBuffer.putDouble(t.realValue()).array());
	// break;
	// }
	// case IStrategoTerm.STRING: {
	// final IStrategoString t = (IStrategoString) term;
	// digest.update(t.stringValue().getBytes());
	// break;
	// }
	// }
	// }
	//
}
