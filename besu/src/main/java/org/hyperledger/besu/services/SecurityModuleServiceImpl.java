package org.hyperledger.besu.services;

import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class SecurityModuleServiceImpl implements SecurityModuleService {
  private final Map<String, Function<BesuConfiguration, SecurityModule>> securityModuleProviders =
      new ConcurrentHashMap<>();

  @Override
  public void registerSecurityModule(
      final String name,
      final Function<BesuConfiguration, SecurityModule> securityProviderSupplier) {
    securityModuleProviders.put(name, securityProviderSupplier);
  }

  @Override
  public Optional<Function<BesuConfiguration, SecurityModule>> getByName(final String name) {
    return Optional.ofNullable(securityModuleProviders.get(name));
  }
}
