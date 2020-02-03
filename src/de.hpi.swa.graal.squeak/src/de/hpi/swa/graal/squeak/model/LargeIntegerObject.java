/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.image.SqueakImageChunk;
import de.hpi.swa.graal.squeak.image.SqueakImageConstants;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.SqueakImageWriter;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

@ExportLibrary(InteropLibrary.class)
public final class LargeIntegerObject extends AbstractSqueakObjectWithClassAndHash {
    private static final BigInteger ONE_SHIFTED_BY_64 = BigInteger.ONE.shiftLeft(64);
    public static final BigInteger LONG_MIN_OVERFLOW_RESULT = BigInteger.valueOf(Long.MIN_VALUE).abs();
    @CompilationFinal(dimensions = 1) private static final byte[] LONG_MIN_OVERFLOW_RESULT_BYTES = toBytes(LONG_MIN_OVERFLOW_RESULT);

    private BigInteger integer;
    private int bitLength;
    private int exposedSize;

    public LargeIntegerObject(final SqueakImageContext image, final BigInteger integer) {
        super(image, integer.signum() >= 0 ? image.largePositiveIntegerClass : image.largeNegativeIntegerClass);
        this.integer = integer;
        bitLength = integer.bitLength();
        exposedSize = calculateExposedSize(integer);
        assert integer.signum() != 0 : "LargePositiveInteger>>isZero returns 'false'";
    }

    public LargeIntegerObject(final SqueakImageContext image, final long hash, final ClassObject klass, final byte[] bytes) {
        super(image, hash, klass);
        integer = new BigInteger(isPositive() ? 1 : -1, ArrayUtils.swapOrderInPlace(bytes));
        bitLength = integer.bitLength();
        exposedSize = bytes.length;
    }

    public LargeIntegerObject(final SqueakImageContext image, final ClassObject klass, final byte[] bytes) {
        super(image, klass);
        integer = new BigInteger(isPositive() ? 1 : -1, ArrayUtils.swapOrderInPlace(bytes));
        bitLength = integer.bitLength();
        exposedSize = bytes.length;
    }

    public LargeIntegerObject(final SqueakImageContext image, final ClassObject klass, final int size) {
        super(image, klass);
        integer = BigInteger.ZERO;
        bitLength = 0;
        exposedSize = size;
    }

    private LargeIntegerObject(final LargeIntegerObject original) {
        super(original);
        integer = original.integer;
        bitLength = original.bitLength;
        exposedSize = original.exposedSize;
    }

    private static int calculateExposedSize(final BigInteger integer) {
        return (integer.abs().bitLength() + 7) / 8;
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        // Nothing to do.
    }

    @TruffleBoundary
    public static LargeIntegerObject createLongMinOverflowResult(final SqueakImageContext image) {
        return new LargeIntegerObject(image, LONG_MIN_OVERFLOW_RESULT);
    }

    public static byte[] getLongMinOverflowResultBytes() {
        return LONG_MIN_OVERFLOW_RESULT_BYTES;
    }

    private static byte[] toBytes(final BigInteger bigInteger) {
        final byte[] bigEndianBytes = toBigEndianBytes(bigInteger);
        final byte[] bytes = bigEndianBytes[0] != 0 ? bigEndianBytes : Arrays.copyOfRange(bigEndianBytes, 1, bigEndianBytes.length);
        return ArrayUtils.swapOrderInPlace(bytes);
    }

    @TruffleBoundary
    private static byte[] toBigEndianBytes(final BigInteger bigInteger) {
        return bigInteger.abs().toByteArray();
    }

    public long getNativeAt0(final long index) {
        assert index < size() : "Illegal index: " + index;
        final byte[] bytes = toBigEndianBytes(integer);
        final int length = bytes.length;
        return index < length ? Byte.toUnsignedLong(bytes[length - 1 - (int) index]) : 0L;
    }

    public void setNativeAt0(final long index, final long value) {
        assert index < size() : "Illegal index: " + index;
        assert 0 <= value && value <= NativeObject.BYTE_MAX : "Illegal value for LargeIntegerObject: " + value;
        final byte[] bytes;
        final byte[] bigIntegerBytes = toBigEndianBytes(integer);
        final int offset = bigIntegerBytes[0] != 0 ? 0 : 1;
        final int bigIntegerBytesActualLength = bigIntegerBytes.length - offset;
        if (bigIntegerBytesActualLength <= index) {
            final int newLength = Math.min(size(), (int) index + 1);
            bytes = new byte[newLength];
            System.arraycopy(bigIntegerBytes, offset, bytes, newLength - bigIntegerBytesActualLength, bigIntegerBytesActualLength);
        } else {
            bytes = bigIntegerBytes;
        }
        bytes[bytes.length - 1 - (int) index] = (byte) value;
        integer = new BigInteger(isPositive() ? 1 : -1, bytes);
        bitLength = integer.bitLength();
    }

    public byte[] getBytes() {
        return toBytes(integer);
    }

    public void replaceInternalValue(final LargeIntegerObject other) {
        assert size() == other.size();
        integer = other.getSqueakClass() == getSqueakClass() ? other.integer : other.integer.negate();
        bitLength = integer.bitLength();
    }

    public void setBytes(final byte[] bytes) {
        assert size() == bytes.length;
        integer = new BigInteger(isPositive() ? 1 : -1, ArrayUtils.swapOrderCopy(bytes));
        bitLength = integer.bitLength();
    }

    public void setBytes(final LargeIntegerObject src, final int srcPos, final int destPos, final int length) {
        final byte[] bytes;
        final byte[] srcBytes = toBigEndianBytes(src.integer);
        final byte[] bigIntegerBytes = toBigEndianBytes(integer);
        final int offset = bigIntegerBytes[0] != 0 ? 0 : 1;
        final int bigIntegerBytesActualLength = bigIntegerBytes.length - offset;
        if (bigIntegerBytesActualLength < destPos + length) {
            bytes = new byte[size()];
            System.arraycopy(bigIntegerBytes, offset, bytes, 0, bigIntegerBytesActualLength);
        } else {
            bytes = bigIntegerBytes;
        }
        System.arraycopy(srcBytes, srcBytes.length - length - srcPos, bytes, bytes.length - length - destPos, length);
        integer = new BigInteger(isPositive() ? 1 : -1, bytes);
        bitLength = integer.bitLength();
    }

    public void setBytes(final byte[] srcBytes, final int srcPos, final int destPos, final int length) {
        // destination bytes are big-endian, source bytes are not
        final byte[] bytes;
        final byte[] bigIntegerBytes = toBigEndianBytes(integer);
        final int offset = bigIntegerBytes[0] != 0 ? 0 : 1;
        final int bigIntegerBytesActualLength = bigIntegerBytes.length - offset;
        if (bigIntegerBytesActualLength < destPos + length) {
            bytes = new byte[size()];
            System.arraycopy(bigIntegerBytes, offset, bytes, 0, bigIntegerBytesActualLength);
        } else {
            bytes = bigIntegerBytes;
        }
        for (int i = 0; i < length; i++) {
            bytes[bytes.length - 1 - (destPos + i)] = srcBytes[srcPos + i];
        }
        integer = new BigInteger(isPositive() ? 1 : -1, bytes);
        bitLength = integer.bitLength();
    }

    @Override
    public int getNumSlots() {
        return (int) Math.ceil((double) exposedSize / 8);
    }

    @Override
    public int instsize() {
        return 0;
    }

    @Override
    public int size() {
        return exposedSize;
    }

    @Override
    @TruffleBoundary(transferToInterpreterOnException = false)
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        assert bitLength == integer.bitLength();
        if (bitLength < Long.SIZE) {
            return integer.longValue() + " - non-normalized " + getSqueakClass() + " of size " + exposedSize;
        } else if (exposedSize != calculateExposedSize(integer)) {
            return integer + " - non-normalized " + getSqueakClass() + " of size " + exposedSize;
        }
        return integer.toString();
    }

    public boolean equals(final LargeIntegerObject other) {
        return integer.equals(other.integer);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof LargeIntegerObject) {
            return equals((LargeIntegerObject) other);
        } else {
            return super.equals(other);
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public void write(final SqueakImageWriter writerNode) {
        final int formatOffset = getNumSlots() * SqueakImageConstants.WORD_SIZE - size();
        assert 0 <= formatOffset && formatOffset <= 7 : "too many odd bits (see instSpec)";
        if (writeHeader(writerNode, formatOffset)) {
            final byte[] bytes = getBytes();
            writerNode.writeBytes(bytes);
            final int offset = bytes.length % SqueakImageConstants.WORD_SIZE;
            if (offset > 0) {
                writerNode.writePadding(SqueakImageConstants.WORD_SIZE - offset);
            }
        }
    }

    public LargeIntegerObject shallowCopy() {
        return new LargeIntegerObject(this);
    }

    private Object reduceIfPossible(final BigInteger value) {
        return reduceIfPossible(image, value);
    }

    private static Object reduceIfPossible(final SqueakImageContext image, final BigInteger value) {
        if (bitLength(value) < Long.SIZE) {
            return value.longValue();
        } else {
            return new LargeIntegerObject(image, value);
        }
    }

    @TruffleBoundary
    public Object reduceIfPossible() {
        if (bitLength < Long.SIZE) {
            return integer.longValue();
        } else {
            exposedSize = calculateExposedSize(integer);
            return this;
        }
    }

    @TruffleBoundary
    public long longValue() {
        return integer.longValue();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public long longValueExact() throws ArithmeticException {
        return integer.longValueExact();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private byte byteValueExact() throws ArithmeticException {
        return integer.byteValueExact();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private short shortValueExact() throws ArithmeticException {
        return integer.shortValueExact();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public int intValueExact() throws ArithmeticException {
        return integer.intValueExact();
    }

    public boolean fitsIntoLong() {
        return bitLength < Long.SIZE;
    }

    public boolean fitsIntoInt() {
        return bitLength < Integer.SIZE;
    }

    public int bitLength() {
        return bitLength;
    }

    @TruffleBoundary
    private static int bitLength(final BigInteger integer) {
        return integer.bitLength();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static LargeIntegerObject valueOf(final SqueakImageContext image, final long a) {
        return new LargeIntegerObject(image, BigInteger.valueOf(a));
    }

    public boolean isPositive() {
        return getSqueakClass().isLargePositiveIntegerClass();
    }

    public boolean isNegative() {
        return getSqueakClass().isLargeNegativeIntegerClass();
    }

    /*
     * Arithmetic Operations
     */

    // TODO: Find out when reduceIfPossible is really necessary
    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object add(final LargeIntegerObject b) {
        return reduceIfPossible(integer.add(b.integer));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object add(final long b) {
        return reduceIfPossible(integer.add(BigInteger.valueOf(b)));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static Object add(final SqueakImageContext image, final long lhs, final long rhs) {
        /* Inlined version of Math.addExact(x, y) with large integer fallback. */
        final long result = lhs + rhs;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((lhs ^ result) & (rhs ^ result)) < 0) {
            return new LargeIntegerObject(image, BigInteger.valueOf(lhs).add(BigInteger.valueOf(rhs)));
        }
        return result;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object subtract(final LargeIntegerObject b) {
        return reduceIfPossible(integer.subtract(b.integer));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object subtract(final long b) {
        return reduceIfPossible(integer.subtract(BigInteger.valueOf(b)));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static Object subtract(final SqueakImageContext image, final long lhs, final long rhs) {
        /* Inlined version of Math.subtractExact(x, y) with large integer fallback. */
        final long result = lhs - rhs;
        // HD 2-12 Overflow iff the arguments have different signs and
        // the sign of the result is different than the sign of x
        if (((lhs ^ rhs) & (lhs ^ result)) < 0) {
            return new LargeIntegerObject(image, BigInteger.valueOf(lhs).subtract(BigInteger.valueOf(rhs)));
        }
        return result;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static Object subtract(final long a, final LargeIntegerObject b) {
        return reduceIfPossible(b.image, BigInteger.valueOf(a).subtract(b.integer));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object multiply(final LargeIntegerObject b) {
        return reduceIfPossible(image, integer.multiply(b.integer));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object multiply(final long b) {
        if (b == 0) {
            return 0L;
        }
        return reduceIfPossible(image, integer.multiply(BigInteger.valueOf(b)));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static Object multiply(final SqueakImageContext image, final long lhs, final long rhs) {
        /* Inlined version of Math.multiplyExact(x, y) with large integer fallback. */
        final long result = lhs * rhs;
        final long ax = Math.abs(lhs);
        final long ay = Math.abs(rhs);
        if ((ax | ay) >>> 31 != 0) {
            // Some bits greater than 2^31 that might cause overflow
            // Check the result using the divide operator
            // and check for the special case of Long.MIN_VALUE * -1
            if (rhs != 0 && result / rhs != lhs || lhs == Long.MIN_VALUE && rhs == -1) {
                return new LargeIntegerObject(image, BigInteger.valueOf(lhs).multiply(BigInteger.valueOf(rhs)));
            }
        }
        return result;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object divide(final LargeIntegerObject b) {
        return reduceIfPossible(integer.divide(b.integer));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object divide(final long b) {
        return reduceIfPossible(integer.divide(BigInteger.valueOf(b)));
    }

    public static long divide(@SuppressWarnings("unused") final long a, final LargeIntegerObject b) {
        assert !b.fitsIntoLong() : "non-reduced large integer!";
        return 0L;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object floorDivide(final LargeIntegerObject b) {
        return reduceIfPossible(floorDivide(integer, b.integer));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object floorDivide(final long b) {
        return reduceIfPossible(floorDivide(integer, BigInteger.valueOf(b)));
    }

    public static long floorDivide(final long a, final LargeIntegerObject b) {
        assert !b.fitsIntoLong() : "non-reduced large integer!";
        if ((a ^ b.integer.signum()) < 0) {
            return -1L;
        }
        return 0L;
    }

    private static BigInteger floorDivide(final BigInteger x, final BigInteger y) {
        final BigInteger[] r = x.divideAndRemainder(y);
        // if the signs are different and modulo not zero, round down
        if (x.signum() != y.signum() && r[1].signum() != 0) {
            return r[0].subtract(BigInteger.ONE);
        }
        return r[0];
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object floorMod(final LargeIntegerObject b) {
        return reduceIfPossible(integer.subtract(floorDivide(integer, b.integer).multiply(b.integer)));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object floorMod(final long b) {
        final BigInteger bValue = BigInteger.valueOf(b);
        return reduceIfPossible(integer.subtract(floorDivide(integer, bValue).multiply(bValue)));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static Object floorMod(final long a, final LargeIntegerObject b) {
        assert !b.fitsIntoLong() : "non-reduced large integer!";
        if ((a ^ b.integer.signum()) < 0) {
            return b.add(a);
        }
        return a;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public long remainder(final long other) {
        return integer.remainder(BigInteger.valueOf(other)).longValue();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object remainder(final LargeIntegerObject b) {
        return reduceIfPossible(integer.remainder(b.integer));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object negate() {
        return reduceIfPossible(integer.negate());
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public int compareTo(final LargeIntegerObject b) {
        return integer.compareTo(b.integer);
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public int compareTo(final long b) {
        if (bitLength < Long.SIZE) {
            return Long.compare(integer.longValue(), b);
        } else {
            return integer.signum();
        }
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public double doubleValue() {
        return integer.doubleValue();
    }

    /** {@link BigInteger#signum()} does not need a {@link TruffleBoundary}. */
    public boolean isZero() {
        return integer.signum() == 0;
    }

    /** {@link BigInteger#signum()} does not need a {@link TruffleBoundary}. */
    public boolean isZeroOrPositive() {
        return integer.signum() >= 0;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public boolean lessThanOrEqualTo(final long value) {
        if (bitLength < Long.SIZE) {
            return integer.longValue() <= value;
        } else {
            return integer.signum() < 0;
        }
    }

    public boolean lessThanOneShiftedBy64() {
        return bitLength < Long.SIZE + 1;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public boolean inRange(final long minValue, final long maxValue) {
        if (bitLength < Long.SIZE) {
            final long longValueExact = integer.longValue();
            return minValue <= longValueExact && longValueExact <= maxValue;
        }
        return false;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public boolean isIntegralWhenDividedBy(final LargeIntegerObject other) {
        return integer.remainder(other.integer).signum() == 0;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public boolean isIntegralWhenDividedBy(final long other) {
        return integer.remainder(BigInteger.valueOf(other)).signum() == 0;
    }

    public boolean sameSign(final LargeIntegerObject other) {
        return getSqueakClass() == other.getSqueakClass();
    }

    public boolean differentSign(final long other) {
        return isNegative() ^ other < 0;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public long toSignedLong() {
        assert isPositive() && bitLength <= Long.SIZE;
        if (bitLength == Long.SIZE) {
            return integer.subtract(ONE_SHIFTED_BY_64).longValue();
        } else {
            return integer.longValue();
        }
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static LargeIntegerObject toUnsigned(final SqueakImageContext image, final long value) {
        assert value < 0;
        return new LargeIntegerObject(image, BigInteger.valueOf(value).add(ONE_SHIFTED_BY_64));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static Object truncateExact(final SqueakImageContext image, final double value) {
        return reduceIfPossible(image, new BigDecimal(value).toBigInteger());
    }

    /*
     * Bit Operations
     */

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object and(final LargeIntegerObject b) {
        return reduceIfPossible(integer.and(b.integer));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object and(final long b) {
        return reduceIfPossible(integer.and(BigInteger.valueOf(b)));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object or(final LargeIntegerObject b) {
        return reduceIfPossible(integer.or(b.integer));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object or(final long b) {
        return reduceIfPossible(integer.or(BigInteger.valueOf(b)));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object xor(final LargeIntegerObject b) {
        return reduceIfPossible(integer.xor(b.integer));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object xor(final long b) {
        return reduceIfPossible(integer.xor(BigInteger.valueOf(b)));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object shiftLeft(final int b) {
        if (integer.signum() < 0 && b < 0) {
            return reduceIfPossible(integer.abs().shiftLeft(b).negate());
        }
        return reduceIfPossible(integer.shiftLeft(b));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static Object shiftLeftPositive(final SqueakImageContext image, final long a, final int b) {
        assert b >= 0 : "This method must be used with a positive 'b' argument";
        return reduceIfPossible(image, BigInteger.valueOf(a).shiftLeft(b));
    }

    public BigInteger getBigInteger() {
        return integer;
    }

    /*
     * INTEROPERABILITY
     */

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isNumber() {
        return fitsInLong() || fitsInDouble();
    }

    @ExportMessage
    public boolean fitsInByte() {
        return bitLength < Byte.SIZE;
    }

    @ExportMessage
    public boolean fitsInShort() {
        return bitLength < Short.SIZE;
    }

    @ExportMessage
    public boolean fitsInInt() {
        return bitLength < Integer.SIZE;
    }

    @ExportMessage
    public boolean fitsInLong() {
        return bitLength < Long.SIZE;
    }

    @ExportMessage
    @TruffleBoundary
    public boolean fitsInFloat() {
        if (bitLength <= 24) { // 24 = size of float mantissa + 1
            return true;
        } else {
            final float floatValue = integer.floatValue();
            if (!Float.isFinite(floatValue)) {
                return false;
            }
            return new BigDecimal(floatValue).toBigIntegerExact().equals(integer);
        }
    }

    @ExportMessage
    @TruffleBoundary
    public boolean fitsInDouble() {
        if (bitLength() <= 53) { // 53 = size of double mantissa + 1
            return true;
        } else {
            final double doubleValue = doubleValue();
            if (!Double.isFinite(doubleValue)) {
                return false;
            }
            return new BigDecimal(doubleValue).toBigIntegerExact().equals(integer);
        }
    }

    @ExportMessage
    public byte asByte() throws UnsupportedMessageException {
        try {
            return byteValueExact();
        } catch (final ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public short asShort() throws UnsupportedMessageException {
        try {
            return shortValueExact();
        } catch (final ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public int asInt() throws UnsupportedMessageException {
        try {
            return intValueExact();
        } catch (final ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public long asLong() throws UnsupportedMessageException {
        try {
            return longValueExact();
        } catch (final ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    public float asFloat() throws UnsupportedMessageException {
        if (fitsInFloat()) {
            return integer.floatValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    public double asDouble() throws UnsupportedMessageException {
        if (fitsInDouble()) {
            return doubleValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }
}
