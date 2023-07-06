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
package org.hyperledger.besu.cli.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.plugin.services.PluginVersionsProvider;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BesuCommandCustomFactoryTest {

  @Mock private PluginVersionsProvider pluginVersionsProvider;

  @Before
  public void initMocks() {
    when(pluginVersionsProvider.getPluginVersions()).thenReturn(Arrays.asList("v1", "v2"));
  }

  @Test
  public void testCreateVersionProviderInstance() throws Exception {
    final BesuCommandCustomFactory besuCommandCustomFactory =
        new BesuCommandCustomFactory(pluginVersionsProvider);
    final VersionProvider versionProvider = besuCommandCustomFactory.create(VersionProvider.class);
    assertThat(versionProvider.getVersion()).containsExactly(BesuInfo.version(), "v1", "v2");
  }
}
