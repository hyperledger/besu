package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.ethereum.worldstate.PrunerConfiguration;

public class PrunerOptionsTest extends AbstractCLIOptionsTest<PrunerConfiguration, PrunerOptions> {

  @Override
  PrunerConfiguration createDefaultDomainObject() {
    return PrunerConfiguration.getDefault();
  }

  @Override
  PrunerConfiguration createCustomizedDomainObject() {
    return new PrunerConfiguration(4, 10);
  }

  @Override
  PrunerOptions optionsFromDomainObject(final PrunerConfiguration domainObject) {
    return PrunerOptions.fromDomainObject(domainObject);
  }

  @Override
  PrunerOptions getOptionsFromBesuCommand(final TestBesuCommand besuCommand) {
    return besuCommand.getPrunerOptions();
  }
}
