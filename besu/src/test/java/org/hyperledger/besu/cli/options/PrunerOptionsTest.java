/*
 * Copyright ConsenSys AG.
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
