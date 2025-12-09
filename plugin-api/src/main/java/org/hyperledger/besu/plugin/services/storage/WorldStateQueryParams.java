package org.hyperledger.besu.plugin.services.storage;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockHeader;

import java.util.Optional;

public interface WorldStateQueryParams {
    /**
     * Gets the block header.
     *
     * @return the block header
     */
    BlockHeader getBlockHeader();

    /**
     * Checks if the world state should update the node head.
     *
     * @return true if the world state should update the node head, false otherwise
     */
    boolean shouldWorldStateUpdateHead();

    /**
     * Gets the block hash.
     *
     * @return the block hash
     */
    Hash getBlockHash();

    /**
     * Gets the state root.
     *
     * @return the state root
     */
    Optional<Hash> getStateRoot();
}
