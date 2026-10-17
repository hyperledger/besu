/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.storage.keyvalue;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

class KeyValueStorageProviderTest {

  private static KeyValueStorageProvider providerOver(
      final Map<SegmentIdentifier, SegmentedKeyValueStorage> storages) {
    return new KeyValueStorageProvider(
        segments -> storages.get(segments.getFirst()),
        mock(KeyValueStorage.class),
        new NoOpMetricsSystem());
  }

  @Test
  void closeClosesEveryStorageInstanceItHandedOut() throws IOException {
    final SegmentedKeyValueStorage blockchain = mock(SegmentedKeyValueStorage.class);
    final SegmentedKeyValueStorage variables = mock(SegmentedKeyValueStorage.class);
    final KeyValueStorageProvider provider =
        providerOver(
            Map.of(
                KeyValueSegmentIdentifier.BLOCKCHAIN, blockchain,
                KeyValueSegmentIdentifier.VARIABLES, variables));
    provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BLOCKCHAIN);
    provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.VARIABLES);

    provider.close();

    verify(blockchain).close();
    verify(variables).close();
  }

  @Test
  void closeKeepsGoingWhenOneStorageFailsToClose() throws IOException {
    final SegmentedKeyValueStorage failing = mock(SegmentedKeyValueStorage.class);
    doThrow(new IOException("cannot close")).when(failing).close();
    final SegmentedKeyValueStorage closing = mock(SegmentedKeyValueStorage.class);
    final KeyValueStorageProvider provider =
        providerOver(
            Map.of(
                KeyValueSegmentIdentifier.BLOCKCHAIN, failing,
                KeyValueSegmentIdentifier.VARIABLES, closing));
    provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BLOCKCHAIN);
    provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.VARIABLES);

    provider.close();

    verify(failing).close();
    verify(closing).close();
  }

  @Test
  void closeWithNothingHandedOutClosesNothing() throws IOException {
    providerOver(Map.of()).close();
  }
}
