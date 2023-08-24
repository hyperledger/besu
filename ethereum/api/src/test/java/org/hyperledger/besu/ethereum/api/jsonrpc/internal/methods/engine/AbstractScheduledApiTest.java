/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.mainnet.DefaultProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;

import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

public class AbstractScheduledApiTest {

  protected final ScheduledProtocolSpec.Hardfork londonHardfork =
      new ScheduledProtocolSpec.Hardfork("London", 0);
  protected final ScheduledProtocolSpec.Hardfork parisHardfork =
      new ScheduledProtocolSpec.Hardfork("Paris", 10);
  protected final ScheduledProtocolSpec.Hardfork shanghaiHardfork =
      new ScheduledProtocolSpec.Hardfork("Shanghai", 20);
  protected final ScheduledProtocolSpec.Hardfork cancunHardfork =
      new ScheduledProtocolSpec.Hardfork("Cancun", 30);
  protected final ScheduledProtocolSpec.Hardfork experimentalHardfork =
      new ScheduledProtocolSpec.Hardfork("Experimental", 40);

  @Mock protected DefaultProtocolSchedule protocolSchedule;

  static class HardforkMatcher implements ArgumentMatcher<Predicate<ScheduledProtocolSpec>> {
    private final ScheduledProtocolSpec.Hardfork fork;
    private final ScheduledProtocolSpec spec;

    public HardforkMatcher(final ScheduledProtocolSpec.Hardfork hardfork) {
      this.fork = hardfork;
      this.spec = mock(ScheduledProtocolSpec.class);
      lenient().when(spec.fork()).thenReturn(fork);
    }

    @Override
    public boolean matches(final Predicate<ScheduledProtocolSpec> value) {
      if (value == null) {
        return false;
      }
      return value.test(spec);
    }
  }

  @BeforeEach
  public void before() {
    lenient()
        .when(protocolSchedule.hardforkFor(argThat(new HardforkMatcher(londonHardfork))))
        .thenReturn(Optional.of(londonHardfork));
    lenient()
        .when(protocolSchedule.hardforkFor(argThat(new HardforkMatcher(parisHardfork))))
        .thenReturn(Optional.of(parisHardfork));
    lenient()
        .when(protocolSchedule.hardforkFor(argThat(new HardforkMatcher(cancunHardfork))))
        .thenReturn(Optional.of(cancunHardfork));
    lenient()
        .when(protocolSchedule.hardforkFor(argThat(new HardforkMatcher(shanghaiHardfork))))
        .thenReturn(Optional.of(shanghaiHardfork));
    lenient()
        .when(protocolSchedule.hardforkFor(argThat(new HardforkMatcher(experimentalHardfork))))
        .thenReturn(Optional.of(experimentalHardfork));
  }
}
