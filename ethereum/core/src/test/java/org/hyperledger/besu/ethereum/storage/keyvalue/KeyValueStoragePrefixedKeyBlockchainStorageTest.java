/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.CHAIN_HEAD_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.FINALIZED_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.SAFE_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.core.VariablesStorageHelper.SAMPLE_CHAIN_HEAD;
import static org.hyperledger.besu.ethereum.core.VariablesStorageHelper.assertNoVariablesInStorage;
import static org.hyperledger.besu.ethereum.core.VariablesStorageHelper.assertVariablesPresentInVariablesStorage;
import static org.hyperledger.besu.ethereum.core.VariablesStorageHelper.assertVariablesReturnedByBlockchainStorage;
import static org.hyperledger.besu.ethereum.core.VariablesStorageHelper.getSampleVariableValues;
import static org.hyperledger.besu.ethereum.core.VariablesStorageHelper.populateBlockchainStorage;
import static org.hyperledger.besu.ethereum.core.VariablesStorageHelper.populateVariablesStorage;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.chain.VariablesStorage;
import org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KeyValueStoragePrefixedKeyBlockchainStorageTest {
  private final BlockHeaderFunctions blockHeaderFunctions = mock(BlockHeaderFunctions.class);
  private KeyValueStorage kvBlockchain;
  private KeyValueStorage kvVariables;
  private VariablesStorage variablesStorage;
  private Map<Keys, Bytes> variableValues;

  @BeforeEach
  public void setup() {
    kvBlockchain = new InMemoryKeyValueStorage();
    kvVariables = new InMemoryKeyValueStorage();
    variablesStorage = new VariablesKeyValueStorage(kvVariables);
    variableValues = getSampleVariableValues();
  }

  @Test
  public void migrationToVariablesStorage() {
    populateBlockchainStorage(kvBlockchain, variableValues);

    assertNoVariablesInStorage(kvVariables);

    final var blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            kvBlockchain, variablesStorage, blockHeaderFunctions, false);

    assertNoVariablesInStorage(kvBlockchain);
    assertVariablesPresentInVariablesStorage(kvVariables, variableValues);

    assertVariablesReturnedByBlockchainStorage(blockchainStorage, variableValues);
  }

  @Test
  public void migrationToVariablesStorageWhenSomeVariablesDoNotExist() {
    variableValues.remove(FINALIZED_BLOCK_HASH);
    variableValues.remove(SAFE_BLOCK_HASH);
    populateBlockchainStorage(kvBlockchain, variableValues);

    assertNoVariablesInStorage(kvVariables);

    final var blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            kvBlockchain, variablesStorage, blockHeaderFunctions, false);

    assertNoVariablesInStorage(kvBlockchain);
    assertVariablesPresentInVariablesStorage(kvVariables, variableValues);

    assertVariablesReturnedByBlockchainStorage(blockchainStorage, variableValues);
  }

  @Test
  public void doesNothingIfVariablesAlreadyMigrated() {
    populateVariablesStorage(kvVariables, variableValues);

    assertNoVariablesInStorage(kvBlockchain);

    final var blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            kvBlockchain, variablesStorage, blockHeaderFunctions, false);

    assertNoVariablesInStorage(kvBlockchain);
    assertVariablesPresentInVariablesStorage(kvVariables, variableValues);

    assertVariablesReturnedByBlockchainStorage(blockchainStorage, variableValues);
  }

  @Test
  public void failIfInconsistencyDetectedDuringVariablesMigration() {
    populateBlockchainStorage(kvBlockchain, variableValues);
    // create and inconsistency putting a different chain head in variables storage
    variableValues.put(CHAIN_HEAD_HASH, SAMPLE_CHAIN_HEAD.shiftLeft(1));
    populateVariablesStorage(kvVariables, variableValues);
    assertThrows(
        IllegalStateException.class,
        () ->
            new KeyValueStoragePrefixedKeyBlockchainStorage(
                kvBlockchain, variablesStorage, blockHeaderFunctions, false));
  }
}
