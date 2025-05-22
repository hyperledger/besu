package org.hyperledger.besu.ethereum.mainnet.feemarket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hyperledger.besu.datatypes.BlobGas;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class OsakaFeeMarketTest {

    private static final int BLOB_GAS_PER_BLOB = 131072;

    @Test
    void dataPricePerGas() {
        final OsakaFeeMarket osakaFeeMarket = new OsakaFeeMarket(0, Optional.empty());

        assertEquals(33554432, osakaFeeMarket.blobGasPricePerGas(BlobGas.ZERO).getAsBigInteger().intValue());

        record BlobGasPricing(long excess, long price) {}
        List<BlobGasPricing> testVector = new ArrayList<>();

        int numBlobs = 1;
        long price = 1;
        while (price <= 1000) {
            price = blobGasPrice(BlobGas.of(numBlobs * BLOB_GAS_PER_BLOB));
            testVector.add(new BlobGasPricing(numBlobs * BLOB_GAS_PER_BLOB, price));
            numBlobs++;
        }

        testVector.stream()
                .forEach(
                blobGasPricing -> {
                    assertEquals(
                            blobGasPricing.price,
                            osakaFeeMarket
                                    .blobGasPricePerGas(BlobGas.of(blobGasPricing.excess()))
                                    .getAsBigInteger()
                                    .intValue());
                });
    }

    private long blobGasPrice(final BlobGas excess) {
        double dgufDenominator = 5007716;
        double fakeExpo = excess.getValue().longValue() / dgufDenominator;
        return (long) (Math.pow(2, 25) * Math.exp(fakeExpo));
    }
}

