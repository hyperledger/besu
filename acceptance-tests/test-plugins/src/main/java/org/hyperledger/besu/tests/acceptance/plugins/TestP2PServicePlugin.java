/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.tests.acceptance.plugins;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.p2p.Capability;
import org.hyperledger.besu.plugin.data.p2p.Message;
import org.hyperledger.besu.plugin.data.p2p.MessageData;
import org.hyperledger.besu.plugin.data.p2p.PeerConnection;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.p2p.P2PMessageService;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.p2p.ProtocolManagerService;
import org.hyperledger.besu.plugin.services.p2p.ValidatorNetworkService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.auto.service.AutoService;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;

@AutoService(BesuPlugin.class)
public class TestP2PServicePlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(TestP2PServicePlugin.class);

  @Option(names = "--plugin-p2p-test-enabled")
  boolean enabled = false;

  @Option(names = "--plugin-p2p-test-mode")
  String testMode = "basic"; // "basic", "comprehensive", "validator", "protocol"

  @Option(names = "--plugin-p2p-test-protocol")
  String testProtocol = "TEST_P2P";

  private ServiceManager serviceManager;
  private File callbackDir;
  private final AtomicInteger messageCount = new AtomicInteger(0);
  private final AtomicInteger connectionCount = new AtomicInteger(0);
  private final AtomicInteger validatorConnectionCount = new AtomicInteger(0);
  private final AtomicInteger protocolMessageCount = new AtomicInteger(0);
  private final AtomicInteger validatorMessagesSent = new AtomicInteger(0);
  private ScheduledExecutorService scheduler;

  // P2P Services
  private P2PService p2pService;
  private P2PMessageService p2pMessageService;
  private ValidatorNetworkService validatorNetworkService;
  private ProtocolManagerService protocolManagerService;

  // Protocol managers for testing
  private TestProtocolManager primaryProtocolManager;
  private TestProtocolManager secondaryProtocolManager;

  @Override
  public void register(final ServiceManager serviceManager) {
    LOG.info("Registering TestP2PServicePlugin");
    this.serviceManager = serviceManager;

    // Data directory will be resolved later in start() when services are fully initialized
    serviceManager
        .getService(PicoCLIOptions.class)
        .orElseThrow()
        .addPicoCLIOptions("p2p-test", this);
  }

  @Override
  public void beforeExternalServices() {
    if (!enabled) {
      LOG.info("TestP2PServicePlugin disabled, skipping service registration");
      return;
    }

    LOG.info("TestP2PServicePlugin beforeExternalServices - P2P service not yet available");
    writeP2PStatus("BEFORE_EXTERNAL_SERVICES", "Plugin beforeExternalServices called");
  }

  private void initializeCallbackDirectory() {
    // Get the data directory from BesuConfiguration service (now properly initialized)
    final var besuConfigService = serviceManager.getService(BesuConfiguration.class);
    if (besuConfigService.isPresent()) {
      final var dataPath = besuConfigService.get().getDataPath();
      if (dataPath != null) {
        callbackDir = dataPath.resolve("plugins").toFile();
        LOG.info("BesuConfiguration service available, status logging enabled to: {}", callbackDir);
      } else {
        callbackDir = null;
        LOG.warn("BesuConfiguration dataPath is null, plugin status logging will be disabled");
      }
    } else {
      callbackDir = null;
      LOG.warn("BesuConfiguration service not available, plugin status logging will be disabled");
    }
  }

  private void handleTestMessage(final Message message) {
    final int msgCount = messageCount.incrementAndGet();
    final String peerAddress = message.getConnection().getPeer().getAddress().toString();
    LOG.info("Received test message #{} from peer: {}", msgCount, peerAddress);
    writeP2PStatus(
        "MESSAGE_RECEIVED",
        String.format("Test message #%d received from %s", msgCount, peerAddress));

    // Log message details
    final MessageData data = message.getData();
    writeP2PStatus(
        "MESSAGE_DETAILS",
        String.format("Message code: %d, size: %d bytes", data.getCode(), data.getSize()));
  }

  private void writeP2PStatus(final String status, final String message) {
    if (callbackDir == null) {
      LOG.debug("P2P status logging disabled - callbackDir is null: {} - {}", status, message);
      return;
    }

    try {
      final File statusFile = new File(callbackDir, "p2p_status.txt");
      if (!statusFile.getParentFile().exists()) {
        statusFile.getParentFile().mkdirs();
        statusFile.getParentFile().deleteOnExit();
      }

      final String content =
          String.format("[%d] %s: %s\n", System.currentTimeMillis(), status, message);
      Files.write(
          statusFile.toPath(),
          content.getBytes(UTF_8),
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.APPEND);
      LOG.info("P2P status written to {}: {}", statusFile.getAbsolutePath(), content.trim());
    } catch (IOException e) {
      LOG.error("Failed to write P2P status", e);
    }
  }

  private void logServiceCapabilities() {
    try {
      if (p2pMessageService != null) {
        final List<Capability> capabilities = p2pMessageService.getSupportedCapabilities();
        writeP2PStatus(
            "CAPABILITIES", String.format("Supported capabilities: %d", capabilities.size()));
        for (Capability cap : capabilities) {
          writeP2PStatus(
              "CAPABILITY_DETAIL",
              String.format("Capability: %s v%d", cap.getName(), cap.getVersion()));
        }
      }

      if (protocolManagerService != null) {
        final List<String> protocols = protocolManagerService.getRegisteredProtocols();
        writeP2PStatus(
            "REGISTERED_PROTOCOLS", String.format("Registered protocols: %s", protocols));
      }
    } catch (Exception e) {
      writeP2PStatus("ERROR", "Failed to log service capabilities: " + e.getMessage());
    }
  }

  private void logPeerConnections() {
    try {
      if (p2pMessageService != null) {
        final List<PeerConnection> peers = p2pMessageService.getPeerConnections();
        final int peerCount = peers.size();
        writeP2PStatus("PEER_COUNT", String.format("Connected peers: %d", peerCount));

        for (int i = 0; i < Math.min(peers.size(), 5); i++) { // Log first 5 peers
          final PeerConnection peer = peers.get(i);
          writeP2PStatus(
              "PEER_DETAIL", String.format("Peer %d: %s", i, peer.getPeer().getAddress()));
        }
      }

      if (validatorNetworkService != null) {
        final List<PeerConnection> validatorPeers =
            validatorNetworkService.getValidatorPeerConnections();
        writeP2PStatus(
            "VALIDATOR_PEER_COUNT", String.format("Validator peers: %d", validatorPeers.size()));
      }

      // Log all counter values for test assertions
      writeP2PStatus(
          "COUNTER_STATS",
          String.format(
              "Messages: %d, Connections: %d, ValidatorConns: %d, ProtocolMsgs: %d, ValidatorMsgsSent: %d",
              messageCount.get(),
              connectionCount.get(),
              validatorConnectionCount.get(),
              protocolMessageCount.get(),
              validatorMessagesSent.get()));

    } catch (Exception e) {
      writeP2PStatus("ERROR", "Failed to log peer connections: " + e.getMessage());
    }
  }

  private void sendTestMessage() {
    // Only send test messages in comprehensive or basic mode
    if (!testMode.equals("basic") && !testMode.equals("comprehensive")) {
      return;
    }

    try {
      if (p2pMessageService != null) {
        final List<PeerConnection> peers = p2pMessageService.getPeerConnections();
        if (!peers.isEmpty()) {
          final PeerConnection peer = peers.get(0);
          final TestMessageData testMsg =
              new TestMessageData(0x01, Bytes.of("Hello from test plugin".getBytes((UTF_8))));

          p2pMessageService.sendMessageForProtocol(peer, testProtocol, testMsg);
          writeP2PStatus(
              "MESSAGE_SENT",
              String.format("Test message sent to peer: %s", peer.getPeer().getAddress()));
        } else {
          writeP2PStatus("NO_PEERS", "No peers available to send test message");
        }
      }
    } catch (Exception e) {
      writeP2PStatus("ERROR", "Failed to send test message: " + e.getMessage());
    }
  }

  private void testProtocolUnregistration() {
    if (protocolManagerService == null) {
      return;
    }

    try {
      // Test unregistering and re-registering the primary protocol
      protocolManagerService.unregisterProtocolManager(testProtocol);
      writeP2PStatus(
          "PROTOCOL_UNREGISTERED", String.format("Protocol %s unregistered", testProtocol));

      // Wait a bit then re-register
      Thread.sleep(2000);

      primaryProtocolManager = new TestProtocolManager("PRIMARY");
      protocolManagerService.registerProtocolManager(testProtocol, primaryProtocolManager);
      writeP2PStatus(
          "PROTOCOL_RE_REGISTERED", String.format("Protocol %s re-registered", testProtocol));

      // Log the current registered protocols
      final List<String> protocols = protocolManagerService.getRegisteredProtocols();
      writeP2PStatus(
          "PROTOCOLS_AFTER_UNREG",
          String.format("Protocols after unregistration test: %s", protocols));

    } catch (Exception e) {
      writeP2PStatus("ERROR", "Failed to test protocol unregistration: " + e.getMessage());
    }
  }

  private void testMultipleProtocolRegistration() {
    if (protocolManagerService == null) {
      return;
    }

    try {
      // Register a second protocol for testing
      final String secondProtocol = "TEST_P2P_SECONDARY";
      secondaryProtocolManager = new TestProtocolManager("SECONDARY");
      protocolManagerService.registerProtocolManager(secondProtocol, secondaryProtocolManager);
      writeP2PStatus(
          "SECONDARY_PROTOCOL_REGISTERED",
          String.format("Secondary protocol %s registered", secondProtocol));

      // Register message handler for secondary protocol
      p2pMessageService.registerMessageHandler(secondProtocol, this::handleSecondaryMessage);
      writeP2PStatus(
          "SECONDARY_HANDLER_REGISTERED",
          String.format("Message handler registered for %s", secondProtocol));

      // Check if both protocols are registered
      final List<String> protocols = protocolManagerService.getRegisteredProtocols();
      writeP2PStatus(
          "MULTIPLE_PROTOCOLS_CHECK", String.format("All registered protocols: %s", protocols));

      final boolean hasPrimary = protocolManagerService.isProtocolRegistered(testProtocol);
      final boolean hasSecondary = protocolManagerService.isProtocolRegistered(secondProtocol);
      writeP2PStatus(
          "PROTOCOL_REGISTRATION_STATUS",
          String.format(
              "Primary registered: %s, Secondary registered: %s", hasPrimary, hasSecondary));

    } catch (Exception e) {
      writeP2PStatus("ERROR", "Failed to test multiple protocol registration: " + e.getMessage());
    }
  }

  private void handleSecondaryMessage(final Message message) {
    final int msgCount = protocolMessageCount.incrementAndGet();
    final String peerAddress = message.getConnection().getPeer().getAddress().toString();
    LOG.info("Received secondary protocol message #{} from peer: {}", msgCount, peerAddress);
    writeP2PStatus(
        "SECONDARY_MESSAGE_RECEIVED",
        String.format("Secondary protocol message #%d received from %s", msgCount, peerAddress));
  }

  private void testValidatorIdentification() {
    if (validatorNetworkService == null) {
      return;
    }

    try {
      // Test validator identification functionality
      final List<Address> validatorAddresses = validatorNetworkService.getValidatorAddresses();
      writeP2PStatus(
          "VALIDATOR_ADDRESSES",
          String.format("Found %d validator addresses", validatorAddresses.size()));

      if (validatorAddresses.isEmpty()) {
        writeP2PStatus(
            "VALIDATOR_ADDRESS_DETAIL", "No validator addresses found in test environment");
      } else {
        for (int i = 0; i < Math.min(validatorAddresses.size(), 3); i++) {
          final Address addr = validatorAddresses.get(i);
          writeP2PStatus("VALIDATOR_ADDRESS_DETAIL", String.format("Validator %d: %s", i, addr));
        }
      }

      // Test validator peer connections
      final List<PeerConnection> validatorPeers =
          validatorNetworkService.getValidatorPeerConnections();
      writeP2PStatus(
          "VALIDATOR_PEERS", String.format("Connected validator peers: %d", validatorPeers.size()));

      // Test individual peer validation
      if (p2pMessageService != null) {
        final List<PeerConnection> allPeers = p2pMessageService.getPeerConnections();
        int validatorCount = 0;
        int nonValidatorCount = 0;

        for (PeerConnection peer : allPeers) {
          final boolean isValidator = validatorNetworkService.isValidator(peer);
          final boolean isValidatorByAddress =
              validatorNetworkService.isValidator(peer.getPeer().getAddress());

          if (isValidator || isValidatorByAddress) {
            validatorCount++;
            writeP2PStatus(
                "PEER_VALIDATOR_CHECK",
                String.format(
                    "Peer %s is validator: %b (by connection: %b, by address: %b)",
                    peer.getPeer().getAddress(),
                    isValidator || isValidatorByAddress,
                    isValidator,
                    isValidatorByAddress));
          } else {
            nonValidatorCount++;
          }
        }

        writeP2PStatus(
            "VALIDATOR_IDENTIFICATION_SUMMARY",
            String.format(
                "Identified %d validators and %d non-validators from %d total peers",
                validatorCount, nonValidatorCount, allPeers.size()));
      }

    } catch (Exception e) {
      writeP2PStatus("ERROR", "Failed to test validator identification: " + e.getMessage());
    }
  }

  private void testValidatorMessaging() {
    if (validatorNetworkService == null) {
      return;
    }

    try {
      // Test sending messages to validators
      final TestMessageData validatorMsg =
          new TestMessageData(0x02, Bytes.of("Hello validators".getBytes((UTF_8))));

      try {
        validatorNetworkService.sendToValidators(validatorMsg);
        final int msgCount = validatorMessagesSent.incrementAndGet();
        writeP2PStatus(
            "VALIDATOR_MESSAGE_SENT",
            String.format("Validator message #%d sent to all validators", msgCount));
      } catch (Exception sendEx) {
        writeP2PStatus(
            "VALIDATOR_MESSAGE_SENT",
            "Validator message sending attempted but failed: " + sendEx.getMessage());
      }
    } catch (Exception e) {
      writeP2PStatus(
          "VALIDATOR_MESSAGE_ERROR", "Validator messaging test failed: " + e.getMessage());
    }
  }

  private void testValidatorConnectionTracking() {
    if (validatorNetworkService == null) {
      return;
    }

    try {
      // Check current validator connection state
      final List<PeerConnection> validatorPeers =
          validatorNetworkService.getValidatorPeerConnections();
      writeP2PStatus(
          "VALIDATOR_CONNECTION_STATE",
          String.format("Currently tracking %d validator connections", validatorPeers.size()));

      // Test validator addresses
      final List<Address> validatorAddresses = validatorNetworkService.getValidatorAddresses();
      writeP2PStatus(
          "VALIDATOR_ADDRESS_COUNT",
          String.format("Found %d validator addresses", validatorAddresses.size()));

      // Compare connected vs known validators
      final long connectedValidatorCount = validatorPeers.size();
      final long knownValidatorCount = validatorAddresses.size();

      writeP2PStatus(
          "VALIDATOR_CONNECTION_RATIO",
          String.format(
              "Connected validators: %d/%d (%.1f%%)",
              connectedValidatorCount,
              knownValidatorCount,
              knownValidatorCount > 0
                  ? (100.0 * connectedValidatorCount / knownValidatorCount)
                  : 0.0));

      // Test validator connection details
      for (int i = 0; i < Math.min(validatorPeers.size(), 3); i++) {
        final PeerConnection peer = validatorPeers.get(i);
        writeP2PStatus(
            "VALIDATOR_PEER_DETAIL",
            String.format(
                "Validator peer %d: %s (connected: %b)",
                i, peer.getPeer().getAddress(), !peer.isDisconnected()));
      }

    } catch (Exception e) {
      writeP2PStatus("ERROR", "Failed to test validator connection tracking: " + e.getMessage());
    }
  }

  @Override
  public void start() {
    if (!enabled) {
      LOG.info("TestP2PServicePlugin disabled, skipping start");
      return;
    }

    LOG.info("Starting TestP2PServicePlugin");

    // Initialize callback directory now that services are fully available
    initializeCallbackDirectory();

    try {
      // Get P2P services using proper service interfaces (now available with real network)
      p2pService = serviceManager.getService(P2PService.class).orElse(null);
      if (p2pService == null) {
        writeP2PStatus("ERROR", "P2PService not available");
        return;
      }

      // Cast to get additional services - this is a temporary workaround
      // In a real implementation, these would be separate service interfaces
      if (p2pService instanceof org.hyperledger.besu.services.P2PServiceImpl) {
        final org.hyperledger.besu.services.P2PServiceImpl p2pImpl =
            (org.hyperledger.besu.services.P2PServiceImpl) p2pService;
        p2pMessageService = p2pImpl;
        validatorNetworkService = p2pImpl;
        protocolManagerService = p2pImpl;
      } else {
        writeP2PStatus("ERROR", "P2P service implementation not recognized");
        return;
      }

      // Enable P2P discovery
      p2pService.enableDiscovery();
      writeP2PStatus("DISCOVERY_ENABLED", "P2P discovery enabled");

      // Register a test protocol manager
      primaryProtocolManager = new TestProtocolManager("PRIMARY");
      protocolManagerService.registerProtocolManager(testProtocol, primaryProtocolManager);
      writeP2PStatus(
          "PROTOCOL_REGISTERED",
          String.format("Protocol manager registered for: %s", testProtocol));

      // Register a message handler for the test protocol
      p2pMessageService.registerMessageHandler(testProtocol, this::handleTestMessage);
      writeP2PStatus(
          "MESSAGE_HANDLER_REGISTERED",
          String.format("Message handler registered for: %s", testProtocol));

      // Register a validator connection tracker
      validatorNetworkService.registerConnectionTracker(new TestValidatorConnectionTracker());
      writeP2PStatus("VALIDATOR_TRACKER_REGISTERED", "Validator connection tracker registered");

      // Start periodic monitoring
      scheduler = Executors.newSingleThreadScheduledExecutor();
      scheduler.scheduleAtFixedRate(this::logServiceCapabilities, 5, 10, TimeUnit.SECONDS);
      scheduler.scheduleAtFixedRate(this::logPeerConnections, 10, 15, TimeUnit.SECONDS);
      scheduler.scheduleAtFixedRate(this::sendTestMessage, 20, 30, TimeUnit.SECONDS);

      // Test multiple protocols if enabled
      // This logic is now handled by the testMode option
      if (testMode.equals("comprehensive") || testMode.equals("protocol")) {
        scheduler.schedule(this::testMultipleProtocolRegistration, 15, TimeUnit.SECONDS);
      }

      // Test protocol unregistration if enabled
      // This logic is now handled by the testMode option
      if (testMode.equals("comprehensive") || testMode.equals("protocol")) {
        scheduler.schedule(this::testProtocolUnregistration, 30, TimeUnit.SECONDS);
      }

      // Test validator functionality if enabled
      // This logic is now handled by the testMode option
      if (testMode.equals("comprehensive") || testMode.equals("validator")) {
        scheduler.schedule(this::testValidatorIdentification, 10, TimeUnit.SECONDS);
        scheduler.schedule(this::testValidatorMessaging, 15, TimeUnit.SECONDS);
        scheduler.schedule(this::testValidatorConnectionTracking, 25, TimeUnit.SECONDS);
      }

      // Write initial status
      writeP2PStatus("P2P_SERVICE_STARTED", "P2P service started successfully");
      LOG.info("TestP2PServicePlugin started successfully");

    } catch (Exception e) {
      writeP2PStatus("ERROR", "Failed to start P2P plugin: " + e.getMessage());
      LOG.error("Failed to start TestP2PServicePlugin", e);
    }
  }

  @Override
  public void stop() {
    LOG.info("Stopping TestP2PServicePlugin");

    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
    }

    writeP2PStatus("P2P_SERVICE_STOPPED", "P2P service stopped");
  }

  /** Simple test message data implementation. */
  private static class TestMessageData implements MessageData {
    private final int code;
    private final Bytes data;

    public TestMessageData(final int code, final Bytes data) {
      this.code = code;
      this.data = data;
    }

    @Override
    public int getSize() {
      return data.size();
    }

    @Override
    public int getCode() {
      return code;
    }

    @Override
    public Bytes getData() {
      return data;
    }

    @Override
    public MessageData wrapMessageData(final java.math.BigInteger requestId) {
      // Simple implementation - in real code this would properly wrap with request ID
      return this;
    }

    @Override
    public java.util.Map.Entry<java.math.BigInteger, MessageData> unwrapMessageData() {
      // Simple implementation - in real code this would properly unwrap request ID
      return new java.util.AbstractMap.SimpleEntry<>(java.math.BigInteger.ZERO, this);
    }
  }

  /** Test protocol manager implementation. */
  private class TestProtocolManager implements ProtocolManagerService.ProtocolManager {
    private final AtomicInteger connectionsHandled = new AtomicInteger(0);
    private final AtomicInteger messagesProcessed = new AtomicInteger(0);
    private final String managerName;
    private final String protocolName;

    public TestProtocolManager() {
      this("PRIMARY");
    }

    public TestProtocolManager(final String name) {
      this.managerName = name;
      this.protocolName = "SECONDARY".equals(name) ? "TEST_P2P_SECONDARY" : testProtocol;
      LOG.info("Creating TestProtocolManager for: {} with protocol: {}", name, protocolName);
      writeP2PStatus(
          "PROTOCOL_MANAGER_CREATED",
          String.format("Protocol manager %s created for protocol %s", name, protocolName));
    }

    @Override
    public String getSupportedProtocol() {
      return protocolName;
    }

    @Override
    public List<Capability> getSupportedCapabilities() {
      return List.of(new TestCapability(protocolName));
    }

    @Override
    public void processMessage(final Capability cap, final Message message) {
      final int msgCount = messagesProcessed.incrementAndGet();
      LOG.info(
          "Processing test message #{} with capability: {} in manager: {}",
          msgCount,
          cap.getName(),
          managerName);
      writeP2PStatus(
          "PROTOCOL_MESSAGE_PROCESSED",
          String.format(
              "Message #%d processed by protocol %s manager %s",
              msgCount, cap.getName(), managerName));
    }

    @Override
    public void handleNewConnection(final PeerConnection peerConnection) {
      final int connCount = connectionsHandled.incrementAndGet();
      LOG.info(
          "New test protocol connection #{} from: {} in manager: {}",
          connCount,
          peerConnection.getPeer().getAddress(),
          managerName);
      writeP2PStatus(
          "PROTOCOL_CONNECTION",
          String.format(
              "Connection #%d handled by protocol %s manager %s from %s",
              connCount, protocolName, managerName, peerConnection.getPeer().getAddress()));
    }

    @Override
    public void handleDisconnect(
        final PeerConnection peerConnection,
        final String disconnectReason,
        final boolean initiatedByPeer) {
      LOG.info(
          "Test protocol disconnect: {} - {} (peer initiated: {}) in manager: {}",
          peerConnection.getPeer().getAddress(),
          disconnectReason,
          initiatedByPeer,
          managerName);
      writeP2PStatus(
          "PROTOCOL_DISCONNECT",
          String.format(
              "Peer %s disconnected: %s (initiated by peer: %b) from manager %s",
              peerConnection.getPeer().getAddress(),
              disconnectReason,
              initiatedByPeer,
              managerName));
    }

    @Override
    public void stop() {
      LOG.info("Test protocol manager {} stopped", managerName);
      writeP2PStatus(
          "PROTOCOL_STOPPED", String.format("Test protocol manager %s stopped", managerName));
    }

    @Override
    public int getHighestProtocolVersion() {
      return 1;
    }

    @SuppressWarnings("UnusedMethod")
    public String getManagerName() {
      return managerName;
    }

    @SuppressWarnings("UnusedMethod")
    public int getConnectionsHandled() {
      return connectionsHandled.get();
    }

    @SuppressWarnings("UnusedMethod")
    public int getMessagesProcessed() {
      return messagesProcessed.get();
    }
  }

  /** Test capability implementation. */
  private static class TestCapability implements Capability {

    private final String name;

    public TestCapability() {
      this("TEST_P2P");
    }

    public TestCapability(final String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public int getVersion() {
      return 1;
    }
  }

  /** Test validator connection tracker implementation. */
  private class TestValidatorConnectionTracker
      implements ValidatorNetworkService.ValidatorConnectionTracker {

    @Override
    public void onValidatorConnected(final PeerConnection peerConnection) {
      final int validatorCount = validatorConnectionCount.incrementAndGet();
      final int totalConnections = connectionCount.incrementAndGet();
      LOG.info(
          "Validator #{} connected: {} (Total connections: {})",
          validatorCount,
          peerConnection.getPeer().getAddress(),
          totalConnections);
      writeP2PStatus(
          "VALIDATOR_CONNECTED",
          String.format(
              "Validator #%d connected: %s (Total connections: %d)",
              validatorCount, peerConnection.getPeer().getAddress(), totalConnections));
    }

    @Override
    public void onValidatorDisconnected(final PeerConnection peerConnection) {
      LOG.info("Validator disconnected: {}", peerConnection.getPeer().getAddress());
      writeP2PStatus(
          "VALIDATOR_DISCONNECTED",
          String.format("Validator disconnected: %s", peerConnection.getPeer().getAddress()));
    }
  }
}
