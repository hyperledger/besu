package org.hyperledger.besu.evm.operations;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.operation.DataHashOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DataHashOperationTest {

    @Test
    public void putsHashOnStack() {
        Hash version0Hash = Hash.fromHexStringLenient("0xcafebabeb0b0facedeadbeef");
        List<Hash> versionedHashes = Arrays.asList(version0Hash);
        DataHashOperation getHash = new DataHashOperation(new LondonGasCalculator());
        MessageFrame frame = mock(MessageFrame.class);
        when(frame.popStackItem()).thenReturn(Bytes.of(0));
        when(frame.getVersionedHashes()).thenReturn(Optional.of(versionedHashes));
        EVM fakeEVM = mock(EVM.class);
        Operation.OperationResult r = getHash.execute(frame, fakeEVM);
        assertThat(r.getGasCost()).isEqualTo(3);
        assertThat(r.getHaltReason()).isEqualTo(ExceptionalHaltReason.NONE);
        verify(frame).pushStackItem(version0Hash);
    }

    @Test
    public void failsOnBloblessTx() {

        EVM fakeEVM = mock(EVM.class);

        DataHashOperation getHash = new DataHashOperation(new LondonGasCalculator());
        MessageFrame frame = mock(MessageFrame.class);
        when(frame.popStackItem()).thenReturn(Bytes.of(0));
        when(frame.getVersionedHashes()).thenReturn(Optional.empty());

        Operation.OperationResult failed1 = getHash.execute(frame, fakeEVM);
        assertThat(failed1.getGasCost()).isEqualTo(3);
        assertThat(failed1.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);

        when(frame.popStackItem()).thenReturn(Bytes.of(0));
        when(frame.getVersionedHashes()).thenReturn(Optional.of(new ArrayList<>()));
        Operation.OperationResult failed2 = getHash.execute(frame, fakeEVM);
        assertThat(failed2.getGasCost()).isEqualTo(3);
        assertThat(failed2.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
    }
    @Test
    public void failsOnVersionIndexOutOFBounds() {
        Hash version0Hash = Hash.fromHexStringLenient("0xcafebabeb0b0facedeadbeef");
        List<Hash> versionedHashes = Arrays.asList(version0Hash);
        DataHashOperation getHash = new DataHashOperation(new LondonGasCalculator());
        MessageFrame frame = mock(MessageFrame.class);
        when(frame.popStackItem()).thenReturn(Bytes.of(1));
        when(frame.getVersionedHashes()).thenReturn(Optional.of(versionedHashes));
        EVM fakeEVM = mock(EVM.class);
        Operation.OperationResult r = getHash.execute(frame, fakeEVM);
        assertThat(r.getGasCost()).isEqualTo(3);
        assertThat(r.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
    }
}
