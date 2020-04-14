package org.hyperledger.besu.services;

import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.NodeKeySecurityModuleService;
import org.hyperledger.besu.plugin.services.nodekey.SecurityModule;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class NodeKeySecurityModuleServiceImpl implements NodeKeySecurityModuleService {
  private final Map<String, Function<BesuConfiguration, SecurityModule>> nodeKeySuppliers =
      new ConcurrentHashMap<>();

  @Override
  public void registerNodeKeySecurityModule(
      final String name,
      final Function<BesuConfiguration, SecurityModule> securityProviderSupplier) {
    nodeKeySuppliers.put(name, securityProviderSupplier);
  }

  @Override
  public Optional<Function<BesuConfiguration, SecurityModule>> getByName(final String name) {
    return Optional.ofNullable(nodeKeySuppliers.get(name));
  }
}
