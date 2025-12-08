package org.hyperledger.besu.plugin.services.storage;

import org.hyperledger.besu.evm.internal.EvmConfiguration;

public interface WorldStateArchiveProvider {

    /**
     * Creates a world state archive instance.
     *
     * @param preimageStorage the preimage storage
     * @param evmConfiguration the EVM configuration
     * @return a world state archive instance
     */
    WorldStateArchive create(
            WorldStatePreimageStorage preimageStorage,
            EvmConfiguration evmConfiguration);

    /**
     * Returns the data storage format this provider supports.
     *
     * @return the supported data storage format
     */
    DataStorageFormat getDataStorageFormat();
}
