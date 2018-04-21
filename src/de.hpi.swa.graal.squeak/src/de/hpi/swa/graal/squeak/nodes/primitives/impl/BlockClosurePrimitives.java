package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.exceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.BaseSqueakObject;
import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.ListObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.BlockActivationNode;
import de.hpi.swa.graal.squeak.nodes.BlockActivationNodeGen;
import de.hpi.swa.graal.squeak.nodes.GetOrCreateContextNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.util.FrameAccess;
import de.hpi.swa.graal.squeak.util.FrameMarker;

public final class BlockClosurePrimitives extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return BlockClosurePrimitivesFactory.getFactories();
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 195, numArguments = 2)
    protected abstract static class PrimFindNextUnwindContextUpToNode extends AbstractPrimitiveNode {

        public PrimFindNextUnwindContextUpToNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"receiver.hasVirtualSender()"})
        @TruffleBoundary
        protected Object doFindNextVirtualized(final ContextObject receiver, final ContextObject previousContext) {
            final ContextObject handlerContext = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<ContextObject>() {
                boolean foundMyself = false;
                final FrameMarker frameMarker = receiver.getFrameMarker();

                @Override
                public ContextObject visitFrame(final FrameInstance frameInstance) {
                    final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                    if (current.getArguments().length < FrameAccess.RCVR_AND_ARGS_START) {
                        return null;
                    }
                    final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                    if (!foundMyself) {
                        if (FrameAccess.isMatchingMarker(frameMarker, contextOrMarker)) {
                            foundMyself = true;
                        }
                    } else {
                        if (previousContext != null && FrameAccess.isMatchingMarker(previousContext.getFrameMarker(), contextOrMarker)) {
                            return null;
                        } else {
                            final CompiledCodeObject frameMethod = FrameAccess.getMethod(current);
                            if (frameMethod.isUnwindMarked()) {
                                final Frame currentMaterializable = frameInstance.getFrame(FrameInstance.FrameAccess.MATERIALIZE);
                                return GetOrCreateContextNode.getOrCreate(currentMaterializable);
                            }
                        }
                    }
                    return null;
                }
            });
            if (handlerContext == null) {
                return code.image.nil;
            } else {
                return handlerContext;
            }
        }

        @Specialization(guards = {"receiver.hasVirtualSender()"})
        protected Object doFindNextVirtualizedNil(final ContextObject receiver, @SuppressWarnings("unused") final NilObject nil) {
            return doFindNextVirtualized(receiver, null);
        }

        @Specialization(guards = {"!receiver.hasVirtualSender()"})
        protected Object doFindNext(final ContextObject receiver, final BaseSqueakObject previousContextOrNil) {
            ContextObject current = receiver;
            while (current != previousContextOrNil) {
                final BaseSqueakObject sender = current.getSender();
                if (sender == code.image.nil || sender == previousContextOrNil) {
                    break;
                } else {
                    current = (ContextObject) sender;
                    if (current.isUnwindContext()) {
                        return current;
                    }
                }
            }
            return code.image.nil;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 196, numArguments = 2)
    protected abstract static class PrimTerminateToNode extends AbstractPrimitiveNode {

        public PrimTerminateToNode(final CompiledMethodObject method) {
            super(method);
        }

        /*
         * Terminate all the Contexts between me and previousContext, if previousContext is on my
         * Context stack. Make previousContext my sender.
         */
        @Specialization
        protected Object doTerminate(final ContextObject receiver, final ContextObject previousContext) {
            if (hasSender(receiver, previousContext)) {
                ContextObject currentContext = receiver.getNotNilSender();
                while (!currentContext.equals(previousContext)) {
                    final ContextObject sendingContext = currentContext.getNotNilSender();
                    currentContext.terminate();
                    currentContext = sendingContext;
                }
            }
            receiver.atput0(CONTEXT.SENDER_OR_NIL, previousContext); // flagging context as dirty
            return receiver;
        }

        /*
         * Answer whether the receiver is strictly above context on the stack (Context>>hasSender:).
         */
        private static boolean hasSender(final ContextObject context, final ContextObject previousContext) {
            if (context.equals(previousContext)) {
                return false;
            }
            BaseSqueakObject sender = context.getSender();
            while (!sender.isNil()) {
                if (sender.equals(previousContext)) {
                    return true;
                }
                sender = ((ContextObject) sender).getSender();
            }
            return false;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 197)
    protected abstract static class PrimNextHandlerContextNode extends AbstractPrimitiveNode {

        protected PrimNextHandlerContextNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"receiver.hasVirtualSender()"})
        @TruffleBoundary
        Object findNextVirtualized(final ContextObject receiver) {
            final ContextObject handlerContext = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<ContextObject>() {
                boolean foundMyself = false;
                final FrameMarker frameMarker = receiver.getFrameMarker();

                @Override
                public ContextObject visitFrame(final FrameInstance frameInstance) {
                    final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                    if (current.getArguments().length < FrameAccess.RCVR_AND_ARGS_START) {
                        return null;
                    }
                    if (!foundMyself) {
                        final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                        if (FrameAccess.isMatchingMarker(frameMarker, contextOrMarker)) {
                            foundMyself = true;
                        }
                    } else {
                        final CompiledCodeObject frameMethod = FrameAccess.getMethod(current);
                        if (frameMethod.isExceptionHandlerMarked()) {
                            final Frame currentMaterializable = frameInstance.getFrame(FrameInstance.FrameAccess.MATERIALIZE);
                            return GetOrCreateContextNode.getOrCreate(currentMaterializable);
                        }
                    }
                    return null;
                }
            });
            if (handlerContext == null) {
                return code.image.nil;
            } else {
                return handlerContext;
            }
        }

        @Specialization(guards = {"!receiver.hasVirtualSender()"})
        Object findNext(final ContextObject receiver) {
            ContextObject context = receiver;
            while (true) {
                if (context.getMethod().isExceptionHandlerMarked()) {
                    return context;
                }
                final BaseSqueakObject sender = context.getSender();
                if (sender instanceof ContextObject) {
                    context = (ContextObject) sender;
                } else {
                    assert sender == code.image.nil;
                    return code.image.nil;
                }
            }
        }

    }

    private abstract static class AbstractClosureValuePrimitiveNode extends AbstractPrimitiveNode {
        @Child protected BlockActivationNode dispatch = BlockActivationNodeGen.create();

        protected AbstractClosureValuePrimitiveNode(final CompiledMethodObject method) {
            super(method);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 200, numArguments = 3)
    public abstract static class PrimClosureCopyWithCopiedValuesNode extends AbstractPrimitiveNode {

        protected PrimClosureCopyWithCopiedValuesNode(final CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object doCopy(final VirtualFrame frame, final ContextObject outerContext, final long numArgs, final ListObject copiedValues) {
            throw new SqueakException("Not implemented and not used in Squeak anymore");
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = {201, 221})
    public abstract static class PrimClosureValue0Node extends AbstractClosureValuePrimitiveNode {

        protected PrimClosureValue0Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object doClosure(final VirtualFrame frame, final BlockClosureObject block) {
            return dispatch.executeBlock(block, block.getFrameArguments(frame));
        }

        // Additional specializations to speed up eager sends
        @Specialization
        protected Object doBoolean(final boolean receiver) {
            return receiver;
        }

        @Specialization
        protected Object doNilObject(final NilObject receiver) {
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 202, numArguments = 2)
    protected abstract static class PrimClosureValue1Node extends AbstractClosureValuePrimitiveNode {

        protected PrimClosureValue1Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object value(final VirtualFrame frame, final BlockClosureObject block, final Object arg) {
            return dispatch.executeBlock(block, block.getFrameArguments(frame, arg));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 203, numArguments = 3)
    protected abstract static class PrimClosureValue2Node extends AbstractClosureValuePrimitiveNode {

        protected PrimClosureValue2Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object value(final VirtualFrame frame, final BlockClosureObject block, final Object arg1, final Object arg2) {
            return dispatch.executeBlock(block, block.getFrameArguments(frame, arg1, arg2));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 204, numArguments = 4)
    protected abstract static class PrimClosureValue3Node extends AbstractClosureValuePrimitiveNode {

        protected PrimClosureValue3Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object value(final VirtualFrame frame, final BlockClosureObject block, final Object arg1, final Object arg2, final Object arg3) {
            return dispatch.executeBlock(block, block.getFrameArguments(frame, arg1, arg2, arg3));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 205, numArguments = 5)
    protected abstract static class PrimClosureValue4Node extends AbstractClosureValuePrimitiveNode {

        protected PrimClosureValue4Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object value(final VirtualFrame frame, final BlockClosureObject block, final Object arg1, final Object arg2, final Object arg3, final Object arg4) {
            return dispatch.executeBlock(block, block.getFrameArguments(frame, arg1, arg2, arg3, arg4));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = {206, 222}, numArguments = 2)
    protected abstract static class PrimClosureValueAryNode extends AbstractClosureValuePrimitiveNode {

        protected PrimClosureValueAryNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected Object value(final VirtualFrame frame, final BlockClosureObject block, final ListObject argArray) {
            return dispatch.executeBlock(block, block.getFrameArguments(frame, argArray.getPointers()));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 212)
    protected abstract static class PrimContextSizeNode extends AbstractPrimitiveNode {

        protected PrimContextSizeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        long doSize(final ContextObject receiver) {
            return receiver.varsize();
        }
    }
}