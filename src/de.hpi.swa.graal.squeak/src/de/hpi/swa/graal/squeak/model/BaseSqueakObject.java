package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

import de.hpi.swa.graal.squeak.image.AbstractImageChunk;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.instrumentation.BaseSqueakObjectMessageResolutionForeign;

public abstract class BaseSqueakObject implements TruffleObject {
    @CompilationFinal private static final int IDENTITY_HASH_MASK = 0x400000 - 1;
    @CompilationFinal public final SqueakImageContext image;
    @CompilationFinal private long hash;
    @CompilationFinal private ClassObject sqClass;

    protected BaseSqueakObject(final SqueakImageContext image) {
        this(image, null);
    }

    protected BaseSqueakObject(final SqueakImageContext image, final ClassObject klass) {
        this.image = image;
        this.hash = hashCode() & IDENTITY_HASH_MASK;
        this.sqClass = klass;
    }

    public static final boolean isInstance(final TruffleObject obj) {
        return obj instanceof BaseSqueakObject;
    }

    public void fillin(final AbstractImageChunk chunk) {
        hash = chunk.getHash();
        sqClass = chunk.getSqClass();
    }

    @Override
    public String toString() {
        return "a " + getSqClassName();
    }

    public final long squeakHash() {
        return hash;
    }

    public final void setSqueakHash(final long hash) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.hash = hash;
    }

    public final ClassObject getSqClass() {
        return sqClass;
    }

    public final void setSqClass(final ClassObject newCls) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.sqClass = newCls;
    }

    public boolean isClass() {
        return false;
    }

    public String nameAsClass() {
        return "???NotAClass";
    }

    public final String getSqClassName() {
        if (isClass()) {
            return nameAsClass() + " class";
        } else {
            return getSqClass().nameAsClass();
        }
    }

    public abstract Object at0(long l);

    public abstract void atput0(long idx, Object object);

    public abstract int size();

    public abstract int instsize();

    public abstract BaseSqueakObject shallowCopy();

    public final int varsize() {
        return size() - instsize();
    }

    public boolean isNil() {
        return false;
    }

    public final boolean isSpecialKindAt(final long index) {
        return getSqClass().equals(image.specialObjectsArray.at0(index));
    }

    public final boolean isSpecialClassAt(final long index) {
        return this.equals(image.specialObjectsArray.at0(index));
    }

    public boolean become(final BaseSqueakObject other) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        final ClassObject otherSqClass = other.sqClass;
        other.sqClass = this.sqClass;
        this.sqClass = otherSqClass;
        return true;
    }

    public void pointersBecomeOneWay(final Object[] from, final Object[] to, final boolean copyHash) {
        final ClassObject oldClass = getSqClass();
        for (int i = 0; i < from.length; i++) {
            if (from[i] == oldClass) {
                final ClassObject newClass = (ClassObject) to[i]; // must be a ClassObject
                setSqClass(newClass);
                if (copyHash) {
                    newClass.setSqueakHash(oldClass.squeakHash());
                }
            }
        }
    }

    @Override
    public final ForeignAccess getForeignAccess() {
        return BaseSqueakObjectMessageResolutionForeign.ACCESS;
    }
}
