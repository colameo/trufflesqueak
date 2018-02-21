package de.hpi.swa.trufflesqueak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.model.BaseSqueakObject;
import de.hpi.swa.trufflesqueak.model.CompiledMethodObject;
import de.hpi.swa.trufflesqueak.model.ContextObject;
import de.hpi.swa.trufflesqueak.model.EmptyObject;
import de.hpi.swa.trufflesqueak.model.LargeIntegerObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.ArithmeticPrimitives.AbstractArithmeticPrimitiveNode;

public class ArrayStreamPrimitives extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return ArrayStreamPrimitivesFactory.getFactories();
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 60, numArguments = 2)
    protected static abstract class PrimBasicAtNode extends AbstractPrimitiveNode {
        protected PrimBasicAtNode(CompiledMethodObject method) {
            super(method);
        }

        @Override
        public final Object executeWithArguments(VirtualFrame frame, Object... arguments) {
            try {
                return executeWithArgumentsSpecialized(frame, arguments);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        @Override
        public final Object executePrimitive(VirtualFrame frame) {
            try {
                return executeAt(frame);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executeAt(VirtualFrame frame);

        @Specialization
        protected long doCharacter(final char receiver, final long index) {
            if (index == 1) {
                return receiver;
            } else {
                throw new PrimitiveFailed();
            }
        }

        @Specialization(guards = "!isSmallInteger(receiver)")
        Object doLong(final long receiver, final long index) {
            return doSqueakObject(LargeIntegerObject.valueOf(code, receiver), index);
        }

        @Specialization
        protected long doDouble(final double receiver, final long index) {
            long doubleBits = Double.doubleToLongBits(receiver);
            if (index == 1) {
                return Math.abs(doubleBits >> 32);
            } else if (index == 2) {
                return Math.abs((int) doubleBits);
            } else {
                throw new PrimitiveFailed();
            }
        }

        @Specialization
        protected long doNativeObject(final NativeObject receiver, final long index) {
            return receiver.getNativeAt0(index - 1);
        }

        @Specialization
        protected Object doSqueakObject(final BaseSqueakObject receiver, final long index) {
            return receiver.at0(index - 1 + receiver.instsize());
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 61, numArguments = 3)
    protected static abstract class PrimBasicAtPutNode extends AbstractPrimitiveNode {
        protected PrimBasicAtPutNode(CompiledMethodObject method) {
            super(method);
        }

        @Override
        public final Object executeWithArguments(VirtualFrame frame, Object... arguments) {
            try {
                return executeWithArgumentsSpecialized(frame, arguments);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        @Override
        public final Object executePrimitive(VirtualFrame frame) {
            try {
                return executeAtPut(frame);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executeAtPut(VirtualFrame frame);

        @Specialization
        protected char doNativeObject(final NativeObject receiver, final long idx, final char value) {
            receiver.setNativeAt0(idx - 1, value);
            return value;
        }

        @Specialization
        protected long doNativeObject(final NativeObject receiver, final long index, final long value) {
            if (value < 0) {
                throw new PrimitiveFailed();
            }
            receiver.setNativeAt0(index - 1, value);
            return value;
        }

        @Specialization
        protected Object doNativeObject(final NativeObject receiver, final long idx, final LargeIntegerObject value) {
            try {
                receiver.atput0(idx - 1, value.reduceToLong());
                return value;
            } catch (ArithmeticException e) {
                throw new PrimitiveFailed();
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object doEmptyObject(final EmptyObject receiver, final long idx, final Object value) {
            throw new PrimitiveFailed();
        }

        @Specialization
        protected Object doSqueakObject(final BaseSqueakObject receiver, final long index, final Object value) {
            receiver.atput0(index - 1 + receiver.instsize(), value);
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 62)
    protected static abstract class PrimSizeNode extends AbstractArithmeticPrimitiveNode {
        protected PrimSizeNode(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected long size(@SuppressWarnings("unused") char obj) {
            return 0;
        }

        @Specialization
        protected long size(@SuppressWarnings("unused") boolean o) {
            return 0;
        }

        @Specialization(guards = "!isSmallInteger(value)")
        protected long doLong(final long value) {
            return doLargeInteger(asLargeInteger(value));
        }

        @Specialization
        protected long doString(final String s) {
            return s.getBytes().length;
        }

        @Specialization
        protected long doLargeInteger(final LargeIntegerObject value) {
            return LargeIntegerObject.byteSize(value);
        }

        @Specialization
        protected long size(@SuppressWarnings("unused") double o) {
            return 2; // Float in words
        }

        @Specialization(guards = {"!isNil(obj)", "hasVariableClass(obj)"})
        protected long size(BaseSqueakObject obj) {
            return obj.varsize();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 63, numArguments = 2)
    protected static abstract class PrimStringAtNode extends AbstractPrimitiveNode {
        protected PrimStringAtNode(CompiledMethodObject method) {
            super(method);
        }

        @Override
        public final Object executeWithArguments(VirtualFrame frame, Object... arguments) {
            try {
                return executeWithArgumentsSpecialized(frame, arguments);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        @Override
        public final Object executePrimitive(VirtualFrame frame) {
            try {
                return executeStringAt(frame);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executeStringAt(VirtualFrame frame);

        @Specialization
        protected char doNativeObject(NativeObject obj, long idx) {
            int intValue = ((Long) obj.getNativeAt0(idx - 1)).intValue();
            return (char) intValue;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 64, numArguments = 3)
    protected static abstract class PrimStringAtPutNode extends AbstractPrimitiveNode {
        protected PrimStringAtPutNode(CompiledMethodObject method) {
            super(method);
        }

        @Override
        public final Object executeWithArguments(VirtualFrame frame, Object... arguments) {
            try {
                return executeWithArgumentsSpecialized(frame, arguments);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        @Override
        public final Object executePrimitive(VirtualFrame frame) {
            try {
                return executeStringAtPut(frame);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executeStringAtPut(VirtualFrame frame);

        @Specialization
        protected char doNativeObject(NativeObject obj, long idx, char value) {
            obj.setNativeAt0(idx - 1, value);
            return value;
        }

        @Specialization
        protected char doNativeObject(NativeObject obj, long idx, long value) {
            assert value >= 0;
            obj.setNativeAt0(idx - 1, value);
            return (char) ((Long) value).intValue();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 210, numArguments = 2)
    protected static abstract class PrimContextAt extends AbstractPrimitiveNode {
        protected PrimContextAt(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object doContextObject(final ContextObject receiver, final long index) {
            try {
                return receiver.atTemp(index - 1);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 211, numArguments = 3)
    protected static abstract class PrimContextAtPut extends AbstractPrimitiveNode {
        protected PrimContextAtPut(CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object doContextObject(final ContextObject receiver, final long index, final Object value) {
            try {
                receiver.atTempPut(index - 1, value);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }
    }

}
