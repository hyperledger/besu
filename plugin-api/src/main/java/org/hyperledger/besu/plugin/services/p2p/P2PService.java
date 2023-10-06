package org.hyperledger.besu.plugin.services.p2p;

import org.hyperledger.besu.plugin.services.BesuService;

public interface P2PService extends BesuService {

    void enableDiscovery();
    void disableDiscovery();
}
