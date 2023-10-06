package org.hyperledger.besu.plugin.services.sync;

import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BesuService;

public interface SynchronizationService extends BesuService {

  BlockHeader getHead();

  boolean setHead(final BlockHeader blockHeader, final BlockBody blockBody);

  boolean setHeadUnsafe(BlockHeader blockHeader, BlockBody blockBody);


  boolean isInitialSyncPhaseDone();

  void startSynchronizer();

  void stopSynchronizer();

  void setWorldStateConfiguration(final WorldStateConfiguration worldStateConfiguration);

}
