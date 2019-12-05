package org.hyperledger.besu.nat.manual;

import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.core.AbstractNatManager;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class describes the behaviour of the Manual NAT manager. Manual Nat manager add the ability
 * to explicitly configure the external IP and Ports to broadcast without regards to NAT or other
 * considerations.
 */
public class ManualNatManager extends AbstractNatManager {

  private final String remoteHost;
  private final int port;
  private final List<NatPortMapping> forwardedPorts;

  public ManualNatManager(final String remoteHost, final int port) {
    super(NatMethod.MANUAL);
    this.remoteHost = remoteHost;
    this.port = port;
    forwardedPorts = buildForwardedPorts();
  }

  private List<NatPortMapping> buildForwardedPorts() {
    try {
      final String internalHost = queryLocalIPAddress().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return Stream.of(
              new NatPortMapping(
                  NatServiceType.DISCOVERY,
                  NetworkProtocol.UDP,
                  internalHost,
                  remoteHost,
                  port,
                  port),
              new NatPortMapping(
                  NatServiceType.RLPX, NetworkProtocol.TCP, internalHost, remoteHost, port, port),
              new NatPortMapping(
                  NatServiceType.JSON_RPC,
                  NetworkProtocol.TCP,
                  internalHost,
                  remoteHost,
                  port,
                  port))
          .collect(Collectors.toList());
    } catch (Exception e) {
      LOG.info("Failed to create forwarded port list");
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
    return CompletableFuture.completedFuture(remoteHost);
  }

  @Override
  public CompletableFuture<List<NatPortMapping>> getPortMappings() {
    return CompletableFuture.completedFuture(forwardedPorts);
  }
}
