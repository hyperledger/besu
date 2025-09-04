package org.hyperledger.besu.ethereum.vm.operations;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.evm.operation.CallDataCopyOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(value = TimeUnit.NANOSECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
public class CallDataCopyOperationBenchmark {

    private CallDataCopyOperation callDataCopyOperation;
    protected static final int SAMPLE_SIZE = 30_000;

    // Parameters matching the test case variations
    @Param({"0", "100", "10240", "1048576"}) // 0 bytes, 100 bytes, 10KiB, 1MiB
    public int dataSize;
    // dataSize=0 byte      -> gas cost = 3 gas
    // dataSize=100 bytes   -> gas cost = 15 gas
    // dataSize=

    @Param({"false", "true"})
    public boolean fixedSrcDst;

    @Param({"false", "true"})
    public boolean nonZeroData;

    protected Bytes[] destOffsetPool;
    protected Bytes[] srcOffsetPool;
    protected Bytes[] sizePool;
    protected int index;
    protected MessageFrame frame;
    protected Bytes callData;

    @Setup
    public void setUp() {
        callDataCopyOperation = new CallDataCopyOperation(new CancunGasCalculator());
        callData = BenchmarkHelper.createCallData(dataSize, nonZeroData);
        frame = BenchmarkHelper.createMessageCallFrameWithCallData(callData);

        // Initialize parameter pools
        destOffsetPool = new Bytes[SAMPLE_SIZE];
        srcOffsetPool = new Bytes[SAMPLE_SIZE];
        sizePool = new Bytes[SAMPLE_SIZE];

        // Fill pools with appropriate values based on test parameters
        BenchmarkHelper.fillPoolsForCallData(sizePool,destOffsetPool,srcOffsetPool,dataSize,fixedSrcDst);

        index = 0;
    }
/*
    @Benchmark
    public void baseline() {
        frame.pushStackItem(sizePool[index]);
        frame.pushStackItem(srcOffsetPool[index]);
        frame.pushStackItem(destOffsetPool[index]);

        frame.popStackItem();
        frame.popStackItem();
        frame.popStackItem();
        index = (index + 1) % SAMPLE_SIZE;

    }
*/
    @Benchmark
    public void executeOperation(final Blackhole blackhole) {
        frame.pushStackItem(sizePool[index]);
        frame.pushStackItem(srcOffsetPool[index]);
        frame.pushStackItem(destOffsetPool[index]);

        blackhole.consume(callDataCopyOperation.execute(frame, null));

        index = (index + 1) % SAMPLE_SIZE;
    }
}
