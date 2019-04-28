package de.hpi.swa.graal.squeak.nodes.accessing;

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.WeakPointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;

public final class WeakPointersObjectNodes {

    @GenerateUncached
    public abstract static class WeakPointersObjectReadNode extends AbstractNode {

        public final Object executeRead(final WeakPointersObject pointers, final long index) {
            return execute(pointers.getPointer((int) index));
        }

        protected abstract Object execute(Object value);

        @Specialization
        protected static final Object doWeakReference(final WeakReference<?> value) {
            return NilObject.nullToNil(value.get());
        }

        @Fallback
        protected static final Object doOther(final Object value) {
            return value;
        }
    }

    @GenerateUncached
    public abstract static class WeakPointersObjectWriteNode extends AbstractNode {

        public abstract void execute(WeakPointersObject pointers, long index, Object value);

        @Specialization(guards = "classNode.executeClass(pointers).getBasicInstanceSize() <= index", limit = "1")
        protected static final void doWeakInVariablePart(final WeakPointersObject pointers, final long index, final AbstractSqueakObject value,
                        @SuppressWarnings("unused") @Cached final SqueakObjectClassNode classNode) {
            pointers.setWeakPointer((int) index, value);
        }

        @Fallback
        protected static final void doNonWeak(final WeakPointersObject pointers, final long index, final Object value) {
            pointers.setPointer((int) index, value);
        }
    }
}