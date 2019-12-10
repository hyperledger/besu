package org.hyperledger.besu.nat.manual;

import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.core.AbstractNatManager;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class describes the behaviour of the Manual NAT manager. Manual Nat manager add the ability
 * to explicitly configure the external IP and Ports to broadcast without regards to NAT or other
 * considerations.
 */
public class ManualNatManager extends AbstractNatManager {

  private final String advertisedHost;
  private final int p2pPort;
  private final int rpcHttpPort;
  private final List<NatPortMapping> forwardedPorts;

  public ManualNatManager(final String advertisedHost, final int p2pPort, final int rpcHttpPort) {
    super(NatMethod.MANUAL);
    this.advertisedHost = advertisedHost;
    this.p2pPort = p2pPort;
    this.rpcHttpPort = rpcHttpPort;
    this.forwardedPorts = buildForwardedPorts();
  }

  private List<NatPortMapping> buildForwardedPorts() {
    try {
      final String internalHost = queryLocalIPAddress().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return Arrays.asList(
          new NatPortMapping(
              NatServiceType.DISCOVERY,
              NetworkProtocol.UDP,
              internalHost,
              advertisedHost,
              p2pPort,
              p2pPort),
          new NatPortMapping(
              NatServiceType.RLPX,
              NetworkProtocol.TCP,
              internalHost,
              advertisedHost,
              p2pPort,
              p2pPort),
          new NatPortMapping(
              NatServiceType.JSON_RPC,
              NetworkProtocol.TCP,
              internalHost,
              advertisedHost,
              rpcHttpPort,
              rpcHttpPort));
    } catch (Exception e) {
      LOG.warn("Failed to create forwarded port list", e);
    }
    return Collections.emptyList();
  }

  @Override
  protected void doStart() {
    LOG.info("Starting Manual NatManager");
  }

  @Override
  protected void doStop() {
    LOG.info("Stopping Manual NatManager");
  }

  @Override
  protected CompletableFuture<String> retrieveExternalIPAddress() {
    return CompletableFuture.completedFuture(advertisedHost);
  }

  @Override
  public CompletableFuture<List<NatPortMapping>> getPortMappings() {
    return CompletableFuture.completedFuture(forwardedPorts);
  }
}
