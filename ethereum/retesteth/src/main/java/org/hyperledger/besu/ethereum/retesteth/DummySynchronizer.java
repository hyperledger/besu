package org.hyperledger.besu.ethereum.retesteth;

import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.services.BesuEvents;

import java.util.Optional;

public class DummySynchronizer implements Synchronizer {
    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void awaitStop() throws InterruptedException {

    }

    @Override
    public Optional<SyncStatus> getSyncStatus() {
        return Optional.empty();
    }

    @Override
    public long subscribeSyncStatus(final BesuEvents.SyncStatusListener listener) {
        return 0;
    }

    @Override
    public boolean unsubscribeSyncStatus(final long observerId) {
        return false;
    }

    @Override
    public long subscribeInSync(final InSyncListener listener) {
        return 0;
    }

    @Override
    public long subscribeInSync(final InSyncListener listener, final long syncTolerance) {
        return 0;
    }

    @Override
    public boolean unsubscribeInSync(final long listenerId) {
        return false;
    }
}
