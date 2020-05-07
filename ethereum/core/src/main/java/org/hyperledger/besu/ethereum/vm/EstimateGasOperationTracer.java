package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltException;
import org.hyperledger.besu.ethereum.vm.operations.SStoreOperation;

import java.util.Optional;

public class EstimateGasOperationTracer implements OperationTracer{

    private int maxDepth = 0;

    private Gas sStoreStipendNeeded = Gas.ZERO;

    @Override
    public void traceExecution(final MessageFrame frame, final Optional<Gas> currentGasCost, final OperationTracer.ExecuteOperation executeOperation) throws ExceptionalHaltException {
        executeOperation.execute();
        if (frame.getCurrentOperation() instanceof SStoreOperation && sStoreStipendNeeded.toLong()==0) {
            sStoreStipendNeeded = ((SStoreOperation)frame.getCurrentOperation()).getMinumumGasRemaining();
        }
        if(maxDepth <frame.getMessageStackDepth()){
            maxDepth = frame.getMessageStackDepth();
        }
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public Gas getStipendNeeded() {
        return sStoreStipendNeeded;
    }
}
