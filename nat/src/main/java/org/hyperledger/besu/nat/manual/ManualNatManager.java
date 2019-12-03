package org.hyperledger.besu.nat.manual;

import static java.util.Arrays.stream;

import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.core.AbstractNatManager;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    final List<NatPortMapping> natPortMappings = new ArrayList<>();
    try {
      final String internalHost = queryLocalIPAddress().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      stream(NatServiceType.values())
          .forEach(
              natServiceType ->
                  stream(NetworkProtocol.values())
                      .forEach(
                          networkProtocol ->
                              natPortMappings.add(
                                  new NatPortMapping(
                                      natServiceType,
                                      networkProtocol,
                                      internalHost,
                                      remoteHost,
                                      port,
                                      port))));
    } catch (Exception e) {
      LOG.info("Failed to create forwarded port list");
    }
    return natPortMappings;
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
