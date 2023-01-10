package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.List;
import java.util.Optional;

public class DataHashOperation extends AbstractOperation{

    public static final int OPCODE = 0x49;
    public DataHashOperation(final GasCalculator gasCalculator) {
        super(OPCODE, "DATAHASH", 1, 1, gasCalculator);
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        int blobIndex = frame.popStackItem().toInt();
        final Optional<List<Hash>> maybeHashes = frame.getVersionedHashes();
        if(frame.getVersionedHashes().isPresent()) {

            List<Hash> versionedHashes = maybeHashes.get();
            if(blobIndex < versionedHashes.size()) {
                Hash requested = versionedHashes.get(blobIndex);
                frame.pushStackItem(requested);
            } else {
                return new OperationResult(3, ExceptionalHaltReason.INVALID_OPERATION);
            }
        } else {
            return new OperationResult(3, ExceptionalHaltReason.INVALID_OPERATION);
        }
        return new OperationResult(3, ExceptionalHaltReason.NONE);
    }

    @Override
    public boolean isVirtualOperation() {
        return super.isVirtualOperation();
    }
}
