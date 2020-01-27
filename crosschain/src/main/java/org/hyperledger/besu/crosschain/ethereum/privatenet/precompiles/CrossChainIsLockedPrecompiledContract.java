package org.hyperledger.besu.crosschain.ethereum.privatenet.precompiles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.BytesValue;

public class CrossChainIsLockedPrecompiledContract extends AbstractPrecompiledContract {
    protected static final Logger LOG = LogManager.getLogger();

    // TODO: Not sure what this should really be
    private static final long FIXED_GAS_COST = 10L;


    public CrossChainIsLockedPrecompiledContract(final GasCalculator gasCalculator) {
        super("CrosschainIsLocked", gasCalculator);
    }

    @Override
    public Gas gasRequirement(final BytesValue input) {
        return Gas.of(FIXED_GAS_COST);
    }
    @Override
    public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
        LOG.info(
                "CrosschainIsLocked Precompile called with " + input.size() + "bytes:" + input.toString());


        return BytesValue.of(1);
    }

}
