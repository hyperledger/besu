package org.hyperledger.besu.ethereum.core.contract;

public class ContractCacheConfiguration {
    public static final ContractCacheConfiguration DEFAULT_CONFIG = new ContractCacheConfiguration(250_000L);
    private final long contractCacheWeightKilobytes;

    public ContractCacheConfiguration(final long contractCacheWeightKilobytes) {
        this.contractCacheWeightKilobytes = contractCacheWeightKilobytes;
    }

    public long getContractCacheWeight() {
        return contractCacheWeightKilobytes * 1024L;
    }

    public long getContractCacheWeightKilobytes() {
        return contractCacheWeightKilobytes;
    }


}
