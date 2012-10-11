package com.facebook.presto.block;

import com.facebook.presto.Range;
import com.facebook.presto.Tuple;
import com.facebook.presto.TupleInfo;
import com.facebook.presto.block.uncompressed.UncompressedBlock;
import com.facebook.presto.slice.DynamicSliceOutput;
import com.facebook.presto.slice.Slice;
import com.facebook.presto.slice.Slices;
import io.airlift.units.DataSize;
import io.airlift.units.DataSize.Unit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class BlockBuilder
{
    public static final DataSize DEFAULT_MAX_BLOCK_SIZE = new DataSize(64, Unit.KILOBYTE);
    public static final double DEFAULT_STORAGE_MULTIPLIER = 1.2;

    private final long startPosition;
    private final TupleInfo tupleInfo;
    private final int maxBlockSize;
    private final DynamicSliceOutput sliceOutput;
    private int count;

    private TupleInfo.Builder tupleBuilder;

    public BlockBuilder(long startPosition, TupleInfo tupleInfo)
    {
        this(startPosition, tupleInfo, DEFAULT_MAX_BLOCK_SIZE, DEFAULT_STORAGE_MULTIPLIER);
    }

    public BlockBuilder(long startPosition, TupleInfo tupleInfo, DataSize blockSize, double storageMultiplier)
    {
        checkArgument(startPosition >= 0, "startPosition is negative");
        checkNotNull(blockSize, "blockSize is null");

        this.startPosition = startPosition;
        this.tupleInfo = tupleInfo;
        maxBlockSize = (int) blockSize.toBytes();
        // Use slightly larger storage size to minimize resizing when we just exceed full capacity
        sliceOutput = new DynamicSliceOutput((int) (maxBlockSize * storageMultiplier));

        tupleBuilder = tupleInfo.builder(sliceOutput);
    }

    public boolean isEmpty()
    {
        return count == 0;
    }

    public boolean isFull()
    {
        return sliceOutput.size() > maxBlockSize;
    }

    public BlockBuilder append(long value)
    {
        flushTupleIfNecessary();

        tupleBuilder.append(value);

        return this;
    }

    public BlockBuilder append(double value)
    {
        flushTupleIfNecessary();

        tupleBuilder.append(value);

        return this;
    }

    public BlockBuilder append(byte[] value)
    {
        flushTupleIfNecessary();

        tupleBuilder.append(Slices.wrappedBuffer(value));

        return this;
    }

    public BlockBuilder append(Slice value)
    {
        flushTupleIfNecessary();

        tupleBuilder.append(value);

        return this;
    }

    public BlockBuilder append(Tuple tuple)
    {
        flushTupleIfNecessary();

        tupleBuilder.append(tuple);

        return this;
    }

    private void flushTupleIfNecessary()
    {
        if (tupleBuilder.isComplete()) {
            tupleBuilder.finish();
            count++;
        }
    }

    public UncompressedBlock build()
    {
        flushTupleIfNecessary();

        checkState(!tupleBuilder.isPartial(), "Tuple is not complete");
        checkState(!isEmpty(), "Cannot build an empty block");

        return new UncompressedBlock(Range.create(startPosition, startPosition + count - 1), tupleInfo, sliceOutput.slice());
    }
}