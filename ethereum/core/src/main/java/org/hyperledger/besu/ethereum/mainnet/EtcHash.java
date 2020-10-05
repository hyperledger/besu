package org.hyperledger.besu.ethereum.mainnet;

public final class EtcHash  {
    public static final int OLD_EPOCH_LENGTH = 30000;
    public static final int NEW_EPOCH_LENGTH = 60000;
    private final long activationBlock;

    public EtcHash(long activationBlock) {
        this.activationBlock = activationBlock;
    }
    private int calcEpochLength(final long block) {
        if (block < activationBlock) {
            return OLD_EPOCH_LENGTH;
        }
        return NEW_EPOCH_LENGTH;
    }

    /**
     * Calculates the EtcHash Epoch for a given block number.
     *
     * @param block Block Number
     * @return EtcHash Epoch
     */
    public  long epoch(final long block) {
        // todo ed fix
        long epochLength = calcEpochLength(block);
        return Long.divideUnsigned(block, epochLength);
    }

    /**
     * Generates the EtcHash cache for given parameters.
     *
     * @param cacheSize Size of the cache to generate
     * @param block Block Number to generate cache for
     * @return EtcHash Cache
     */
    public static int[] mkCache(final int cacheSize, final long block) {
        return EthHash.mkCache(cacheSize, block);
    }

    /**
     * Calculates EtcHash Cache size at a given epoch.
     *
     * @param epoch EtcHash Epoch
     * @return Cache size
     */
    public static long cacheSize(final long epoch) {
        return EthHash.cacheSize(epoch);
    }

    /**
     * Calculates EtcHash DataSet size at a given epoch.
     *
     * @param epoch EtcHash Epoch
     * @return DataSet size
     */
    public static long datasetSize(final long epoch) {
        return EthHash.datasetSize(epoch);
    }
}
