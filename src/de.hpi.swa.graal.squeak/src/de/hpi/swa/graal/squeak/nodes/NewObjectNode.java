package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObjectWithHash;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.EmptyObject;
import de.hpi.swa.graal.squeak.model.FloatObject;
import de.hpi.swa.graal.squeak.model.LargeIntegerObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.METACLASS;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.model.WeakPointersObject;

public abstract class NewObjectNode extends AbstractNodeWithImage {

    protected NewObjectNode(final SqueakImageContext image) {
        super(image);
    }

    public static NewObjectNode create(final SqueakImageContext image) {
        return NewObjectNodeGen.create(image);
    }

    public final AbstractSqueakObjectWithHash execute(final ClassObject classObject) {
        return execute(classObject, 0);
    }

    public final AbstractSqueakObjectWithHash execute(final ClassObject classObject, final int extraSize) {
        image.reportNewAllocationRequest();
        return image.reportNewAllocationResult(executeAllocation(classObject, extraSize));
    }

    protected abstract AbstractSqueakObjectWithHash executeAllocation(ClassObject classObject, int extraSize);

    @Specialization(guards = "classObject.isZeroSized()")
    protected final EmptyObject doEmpty(final ClassObject classObject, @SuppressWarnings("unused") final int extraSize) {
        return new EmptyObject(image, classObject);
    }

    @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "classObject.isMetaClass()"})
    protected final ClassObject doClass(final ClassObject classObject, final int extraSize) {
        assert classObject.getBasicInstanceSize() == METACLASS.INST_SIZE && extraSize == 0;
        return new ClassObject(image, classObject, METACLASS.INST_SIZE);
    }

    @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "!classObject.isMetaClass()", "classObject.instancesAreClasses()"})
    protected final ClassObject doClassOdd(final ClassObject classObject, final int extraSize) {
        assert extraSize == 0;
        return new ClassObject(image, classObject, classObject.getBasicInstanceSize() + METACLASS.INST_SIZE);
    }

    @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "!classObject.isMetaClass()", "!classObject.instancesAreClasses()"})
    protected final PointersObject doClassPointers(final ClassObject classObject, final int extraSize) {
        assert extraSize == 0;
        return new PointersObject(image, classObject, classObject.getBasicInstanceSize());
    }

    @Specialization(guards = "classObject.isIndexableWithNoInstVars()")
    protected final ArrayObject doIndexedPointers(final ClassObject classObject, final int extraSize) {
        assert classObject.getBasicInstanceSize() == 0;
        if (ArrayObject.ENABLE_STORAGE_STRATEGIES) {
            return ArrayObject.createEmptyStrategy(image, classObject, extraSize);
        } else {
            return ArrayObject.createObjectStrategy(image, classObject, extraSize);
        }
    }

    @Specialization(guards = {"classObject.isIndexableWithInstVars()", "classObject.isMethodContextClass()"})
    protected final ContextObject doContext(final ClassObject classObject, final int extraSize) {
        assert classObject.getBasicInstanceSize() == CONTEXT.INST_SIZE;
        return ContextObject.create(image, CONTEXT.INST_SIZE + extraSize);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"classObject.isIndexableWithInstVars()", "classObject.isBlockClosureClass()"})
    protected final BlockClosureObject doBlockClosure(final ClassObject classObject, final int extraSize) {
        return new BlockClosureObject(image, extraSize);
    }

    @Specialization(guards = {"classObject.isIndexableWithInstVars()", "!classObject.isMethodContextClass()", "!classObject.isBlockClosureClass()"})
    protected final PointersObject doPointers(final ClassObject classObject, final int extraSize) {
        return new PointersObject(image, classObject, classObject.getBasicInstanceSize() + extraSize);
    }

    @Specialization(guards = "classObject.isWeak()")
    protected final WeakPointersObject doWeakPointers(final ClassObject classObject, final int extraSize) {
        return new WeakPointersObject(image, classObject, classObject.getBasicInstanceSize() + extraSize);
    }

    @Specialization(guards = "classObject.isEphemeronClassType()")
    protected final WeakPointersObject doEphemerons(final ClassObject classObject, final int extraSize) {
        return doWeakPointers(classObject, extraSize); // TODO: ephemerons
    }

    @Specialization(guards = "classObject.isLongs()")
    protected final NativeObject doNativeLongs(final ClassObject classObject, final int extraSize) {
        assert classObject.getBasicInstanceSize() == 0;
        return NativeObject.newNativeLongs(image, classObject, extraSize);
    }

    @Specialization(guards = {"classObject.isWords()", "classObject.isFloatClass()"})
    protected final FloatObject doFloat(final ClassObject classObject, final int extraSize) {
        assert classObject.getBasicInstanceSize() + extraSize == 2;
        return new FloatObject(image);
    }

    @Specialization(guards = {"classObject.isWords()", "!classObject.isFloatClass()"})
    protected final NativeObject doNativeInts(final ClassObject classObject, final int extraSize) {
        assert classObject.getBasicInstanceSize() == 0;
        return NativeObject.newNativeInts(image, classObject, extraSize);
    }

    @Specialization(guards = "classObject.isShorts()")
    protected final NativeObject doNativeShorts(final ClassObject classObject, final int extraSize) {
        assert classObject.getBasicInstanceSize() == 0;
        return NativeObject.newNativeShorts(image, classObject, extraSize);
    }

    @Specialization(guards = {"classObject.isBytes()", "classObject.isLargePositiveIntegerClass() || classObject.isLargeNegativeIntegerClass()"})
    protected final LargeIntegerObject doLargeIntegers(final ClassObject classObject, final int extraSize) {
        assert classObject.getBasicInstanceSize() == 0;
        return new LargeIntegerObject(image, classObject, extraSize);
    }

    @Specialization(guards = {"classObject.isBytes()", "!classObject.isLargePositiveIntegerClass()", "!classObject.isLargeNegativeIntegerClass()"})
    protected final NativeObject doNativeBytes(final ClassObject classObject, final int extraSize) {
        assert classObject.getBasicInstanceSize() == 0;
        return NativeObject.newNativeBytes(image, classObject, extraSize);
    }

    @Specialization(guards = {"classObject.isCompiledMethodClassType()"})
    protected final CompiledMethodObject doCompiledMethod(final ClassObject classObject, final int extraSize) {
        assert classObject.getBasicInstanceSize() == 0;
        return CompiledMethodObject.newOfSize(image, extraSize);
    }
}
