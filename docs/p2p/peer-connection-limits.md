Summary of Peer Connection Limits in Besu

I've completed a thorough analysis of how peer connection limits work in the Besu codebase. Here's the comprehensive picture:

1. Where TOO_MANY_PEERS Disconnect Reason is Used

The TOO_MANY_PEERS disconnect reason (code 0x04) is defined in:
- File: ./ethereum/p2p/src/main/java/org/hyperledger/besu/ethereum/p2p/rlpx/wire/messages/DisconnectMessage.java (line 138)
- Definition: TOO_MANY_PEERS((byte) 0x04, "Too many peers")

It's used in 4 main places in ./ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/manager/EthPeers.java:

1. enforceRemoteConnectionLimits() (line 708) - Disconnects remotely initiated connections when inbound limit exceeded
2. enforceConnectionLimits() (line 733) - Disconnects low-priority peers when overall peer count exceeds limit
3. addPeerToEthPeers() (lines 802, 817, 833) - Multiple places where existing peers are disconnected to make room for new higher-priority peers

2. How RlpxAgent Tracks Active Connections

The RlpxAgent (./ethereum/p2p/src/main/java/org/hyperledger/besu/ethereum/p2p/rlpx/RlpxAgent.java) is the core connection management class:

- Stores maxPeers: Field at line 72: private final int maxPeers;
- Gets connection count: Method getConnectionCount() (line 156-162) - returns count of active connections via supplier
- Connection suppliers: Lines 73-74 use lambdas to get all connections and all active connections
- Key method getMaxPeers() (line 359-361): Returns the configured maximum peers

However, RlpxAgent does NOT directly enforce the limit - it just stores the config and provides access to connections. The actual enforcement happens in EthPeers.

3. Where the "Too Many Peers" Check Happens in Connection Flow

The limit checks happen AFTER the HELLO exchange, not during:

Connection Flow Timeline:
1. TCP connection accepted by Netty server
2. Handshake handler (AbstractHandshakeHandler lines 98-146) performs ECIES encryption handshake
3. After handshake completes, DeFramer is added and HELLO messages are exchanged
4. After HELLO exchange (in DeFramer.decode(), line 124-198):
  - Creates peer connection object
  - Validates shared capabilities
  - Completes the connection future
5. Connection is registered with EthPeers via registerNewConnection() (line 201-224)
6. After protocol status exchange, ethPeerStatusExchanged() is called (lines 560-633)
7. Finally, addPeerToEthPeers() is called (line 626) - THIS IS WHERE THE LIMITS ARE ACTUALLY ENFORCED

4. Classes Holding maxPeers Configuration

Two main classes hold the peer limit configuration:

A. RlpxAgent (./ethereum/p2p/src/main/java/org/hyperledger/besu/ethereum/p2p/rlpx/RlpxAgent.java):
- Field: private final int maxPeers; (line 72)
- Method: public int getMaxPeers() (line 359-361)
- Set via Builder: .maxPeers(int maxPeers) (line 474-477)

B. EthPeers (./ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/manager/EthPeers.java):
- Field: private final int peerUpperBound; (line 105) - the actual limit
- Field: private final int maxRemotelyInitiatedConnections; (line 106) - separate limit for inbound connections
- Method: public int getMaxPeers() (line 352-354) - returns peerUpperBound
- Constructor: Sets these from parameters (lines 143-152)

5. Existing "Should Accept Connection" Checks

There is a sophisticated callback-based system for deciding whether to accept connections:

The Flow:

1. RlpxAgent has a list of ShouldConnectCallback subscribers (line 67):
private final List<ShouldConnectCallback> connectRequestSubscribers = new ArrayList<>();
2. Before accepting any connection, RlpxAgent calls checkWhetherToConnect() (lines 249-252):
private boolean checkWhetherToConnect(final Peer peer, final boolean incoming) {
  return connectRequestSubscribers.stream()
      .anyMatch(callback -> callback.shouldConnect(peer, incoming));
}
3. This is called in two places:
  - Outbound (line 216): Before initiating outbound connections
  - Inbound (line 327): Before accepting inbound connections in handleIncomingConnection()
4. The callback is registered via NetworkRunner.builder():
network.subscribeConnectRequest(ethPeersShouldConnect::apply);
4. This passes a BiFunction<Peer, Boolean, Boolean> that typically comes from EthPeers
5. The actual limit enforcement happens in EthPeers.addPeerToEthPeers() (line 769-862):
  - Checks if at peerUpperBound (line 780)
  - For non-random priority mode: Can optionally disconnect an existing peer to make room
  - For random priority mode: Adds peer first, then enforces limits via:
      - enforceRemoteConnectionLimits() (lines 858)
    - enforceConnectionLimits() (lines 859)

6. Key Limits in EthPeers

There are actually 3 separate limits:

1. peerUpperBound (line 105) - Overall maximum peers (from --max-peers CLI option)
2. maxRemotelyInitiatedConnections (line 106) - Maximum inbound connections (separate limit to prevent eclipse attacks)
3. snapServerTargetNumber (line 150-151) - 50% of peers should be snap servers during snap sync

The system also uses:
- Peer privileges (via rlpxAgent.canExceedConnectionLimits(), line 306-308) to allow certain peers (like trusted nodes) to exceed limits
- Peer comparison (method comparePeerPriorities(), lines 661-675) to prioritize peers by privilege level and connection age
- Peer reputation/utility scoring to determine which low-priority peers to disconnect when limits are hit

7. File Locations Summary

Key files for understanding peer limits:

1. ./ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/manager/EthPeers.java - Main enforcement logic (lines 769-862)
2. ./ethereum/p2p/src/main/java/org/hyperledger/besu/ethereum/p2p/rlpx/RlpxAgent.java - Connection tracking and config storage
3. ./ethereum/p2p/src/main/java/org/hyperledger/besu/ethereum/p2p/rlpx/connections/netty/DeFramer.java - Post-HELLO peer creation (lines
124-210)
4. ./ethereum/p2p/src/main/java/org/hyperledger/besu/ethereum/p2p/rlpx/wire/ShouldConnectCallback.java - Callback interface for accept/reject
decisions
5. ./ethereum/p2p/src/main/java/org/hyperledger/besu/ethereum/p2p/network/NetworkRunner.java - Registers the callback with RlpxAgent
