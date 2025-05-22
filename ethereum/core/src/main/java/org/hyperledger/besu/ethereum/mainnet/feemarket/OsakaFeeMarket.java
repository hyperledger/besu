package org.hyperledger.besu.ethereum.mainnet.feemarket;

import org.hyperledger.besu.config.BlobSchedule;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Wei;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Optional;

public class OsakaFeeMarket extends PragueFeeMarket {
    private static final Logger LOG = LoggerFactory.getLogger(OsakaFeeMarket.class);

    // From EIP-7762
    private static final long MIN_BASE_FEE_PER_BLOB_GAS = 33554432L;

    public OsakaFeeMarket(
            final long londonForkBlockNumber,
            final Optional<Wei> baseFeePerGasOverride,
            final long baseFeeUpdateFraction) {
        super(londonForkBlockNumber, baseFeePerGasOverride, baseFeeUpdateFraction);
    }

    public OsakaFeeMarket(final long londonForkBlockNumber, final Optional<Wei> baseFeePerGasOverride) {
        this(
                londonForkBlockNumber,
                baseFeePerGasOverride,
                BlobSchedule.OSAKA_DEFAULT.getBaseFeeUpdateFraction());
    }

    @Override
    public Wei blobGasPricePerGas(final BlobGas excessBlobGas) {
        final var blobGasPrice =
                Wei.of(
                        fakeExponential(
                                BigInteger.valueOf(MIN_BASE_FEE_PER_BLOB_GAS),
                                excessBlobGas.toBigInteger(),
                                getBaseFeeUpdateFraction()));
        LOG.atTrace()
                .setMessage("parentExcessBlobGas: {} blobGasPrice (Osaka): {}")
                .addArgument(excessBlobGas::toShortHexString)
                .addArgument(blobGasPrice::toHexString)
                .log();

        return blobGasPrice;
    }
}
