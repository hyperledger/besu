package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.ethereum.mainnet.staterootcommitter.StateRootCommitterImplSync;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.storage.MutableWorldState;

public abstract class BaseMutableWorldState implements MutableWorldState {

    @Override
    public void persist(final BlockHeader blockHeader) {
        persist(blockHeader, new StateRootCommitterImplSync());
    }
}
