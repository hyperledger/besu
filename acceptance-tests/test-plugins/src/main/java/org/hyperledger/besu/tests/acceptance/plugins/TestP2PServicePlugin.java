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

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.p2p.Capability;
import org.hyperledger.besu.plugin.data.p2p.Message;
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
import java.util.concurrent.atomic.AtomicInteger;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;

@AutoService(BesuPlugin.class)
public class TestP2PServicePlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(TestP2PServicePlugin.class);

  @Option(names = "--plugin-p2p-test-enabled")
  boolean enabled = false;

  @Option(names = "--plugin-p2p-test-protocol")
  String testProtocol = "TEST_P2P";

  private ServiceManager serviceManager;
  private File callbackDir;
  private final AtomicInteger messageCount = new AtomicInteger(0);
  private final AtomicInteger connectionCount = new AtomicInteger(0);

  @Override
  public void register(final ServiceManager serviceManager) {
    LOG.info("Registering TestP2PServicePlugin");
    this.serviceManager = serviceManager;

    // Get the data directory from BesuConfiguration service (node-specific, avoids sticky system
    // property issue)
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
    // P2P service is not available yet, will be added in loadAdditionalServices
    writeP2PStatus("BEFORE_EXTERNAL_SERVICES", "Plugin beforeExternalServices called");
  }

  private void handleTestMessage(final Message message) {
    LOG.info("Received test message from peer: {}", message.getConnection().getPeer().getAddress());
    messageCount.incrementAndGet();
    writeP2PStatus("MESSAGE_RECEIVED", "Test message received");
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

      final String content = String.format("%s: %s\n", status, message);
      Files.write(statusFile.toPath(), content.getBytes(UTF_8));
      LOG.info("P2P status written to {}: {}", statusFile.getAbsolutePath(), content.trim());
    } catch (IOException e) {
      LOG.error("Failed to write P2P status", e);
    }
  }

  @Override
  public void start() {
    if (!enabled) {
      LOG.info("TestP2PServicePlugin disabled, skipping start");
      return;
    }

    LOG.info("Starting TestP2PServicePlugin ");

    // Get P2P services using proper service interfaces (now available with real network)
    final var p2PService = serviceManager.getService(P2PService.class).orElseThrow();
    final P2PMessageService p2PMessageService =
        (P2PMessageService) serviceManager.getService(P2PService.class).orElseThrow();
    final ValidatorNetworkService validatorNetworkService =
        (ValidatorNetworkService) serviceManager.getService(P2PService.class).orElseThrow();
    final ProtocolManagerService protocolManagerService =
        (ProtocolManagerService) serviceManager.getService(P2PService.class).orElseThrow();

    // Enable P2P discovery
    p2PService.enableDiscovery();

    // Register a test protocol manager
    protocolManagerService.registerProtocolManager(testProtocol, new TestProtocolManager());

    // Register a message handler for the test protocol
    p2PMessageService.registerMessageHandler(testProtocol, this::handleTestMessage);

    // Register a validator connection tracker
    validatorNetworkService.registerConnectionTracker(new TestValidatorConnectionTracker());

    // Write initial status
    writeP2PStatus("P2P_SERVICE_STARTED", "P2P service started successfully");
    LOG.info("TestP2PServicePlugin started successfully");
  }

  @Override
  public void stop() {
    LOG.info("Stopping TestP2PServicePlugin");
    writeP2PStatus("P2P_SERVICE_STOPPED", "P2P service stopped");
  }

  /** Test protocol manager implementation. */
  private static class TestProtocolManager implements ProtocolManagerService.ProtocolManager {

    @Override
    public String getSupportedProtocol() {
      return "TEST_P2P";
    }

    @Override
    public List<Capability> getSupportedCapabilities() {
      return List.of(new TestCapability());
    }

    @Override
    public void processMessage(final Capability cap, final Message message) {
      LOG.info("Processing test message with capability: {}", cap.getName());
    }

    @Override
    public void handleNewConnection(final PeerConnection peerConnection) {
      LOG.info("New test protocol connection from: {}", peerConnection.getPeer().getAddress());
    }

    @Override
    public void handleDisconnect(
        final PeerConnection peerConnection,
        final String disconnectReason,
        final boolean initiatedByPeer) {
      LOG.info(
          "Test protocol disconnect: {} - {}",
          peerConnection.getPeer().getAddress(),
          disconnectReason);
    }

    @Override
    public void stop() {
      LOG.info("Test protocol manager stopped");
    }

    @Override
    public int getHighestProtocolVersion() {
      return 1;
    }
  }

  /** Test capability implementation. */
  private static class TestCapability implements Capability {

    @Override
    public String getName() {
      return "TEST_P2P";
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
      LOG.info("Validator connected: {}", peerConnection.getPeer().getAddress());
      connectionCount.incrementAndGet();
      writeP2PStatus(
          "VALIDATOR_CONNECTED", "Validator connected: " + peerConnection.getPeer().getAddress());
    }

    @Override
    public void onValidatorDisconnected(final PeerConnection peerConnection) {
      LOG.info("Validator disconnected: {}", peerConnection.getPeer().getAddress());
      writeP2PStatus(
          "VALIDATOR_DISCONNECTED",
          "Validator disconnected: " + peerConnection.getPeer().getAddress());
    }
  }
}
