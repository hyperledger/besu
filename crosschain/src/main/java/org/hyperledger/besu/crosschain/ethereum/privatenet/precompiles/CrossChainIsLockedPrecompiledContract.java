package org.hyperledger.besu.crosschain.ethereum.privatenet.precompiles;


import java.math.BigInteger;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.ethereum.core.*;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.BytesValue;

import javax.swing.*;

public class CrossChainIsLockedPrecompiledContract extends AbstractCrossChainPrecompiledContract {
    protected static final Logger LOG = LogManager.getLogger();
    private static final int addressLength = 20;

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

       BigInteger contractAddress = extractAddress(input, 0, addressLength);
       if (contractAddress.equals(BigInteger.ZERO))
       {
           LOG.error("Invalid contract address");
           return(BytesValue.of(0));
       }

        //MutableAccount contract = WorldUpdater.getMutable(contractAddress)null;
        MutableAccount contract = null;
        if (contract.isLocked()){
            return BytesValue.of(1);
        }
       else {
           return(BytesValue.of(0));
        }

    }

    protected boolean isMatched(final CrosschainTransaction ct) {
        return ct.getType().isOriginatingTransaction() || ct.getType().isSubordinateTransaction();
    }


    private static BigInteger extractAddress(
            final BytesValue input, final int offset, final int length) {
        if (offset > input.size() || length == 0) {
            return BigInteger.ZERO;
        }
        final byte[] raw = Arrays.copyOfRange(input.extractArray(), offset, offset + length);
        return new BigInteger(1, raw);
    }
}
