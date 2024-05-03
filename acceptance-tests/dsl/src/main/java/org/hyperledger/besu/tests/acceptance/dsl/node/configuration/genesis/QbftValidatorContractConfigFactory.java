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
package org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis;

import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;

import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;

public class QbftValidatorContractConfigFactory {
  private static final String BALANCE = "0";
  // This is compiled from
  // https://github.com/hyperledger/besu/blob/9deb5ea5d21c810300d5b7005bf6c3777be3f6e5/consensus/qbft/src/integration-test/resources/validator_contract.sol
  // using solc --evm-version byzantium --bin-runtime validator_contract.sol",
  private static final String CODE =
      "608060405234801561001057600080fd5b5060043610610048576000357c010000000000000000000000000000000000000000000000000000000090048063b7ab4db51461004d575b600080fd5b61005561006b565b604051610062919061017e565b60405180910390f35b606060008054806020026020016040519081016040528092919081815260200182805480156100ef57602002820191906000526020600020905b8160009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190600101908083116100a5575b5050505050905090565b60006101058383610111565b60208301905092915050565b61011a816101d9565b82525050565b600061012b826101b0565b61013581856101c8565b9350610140836101a0565b8060005b8381101561017157815161015888826100f9565b9750610163836101bb565b925050600181019050610144565b5085935050505092915050565b600060208201905081810360008301526101988184610120565b905092915050565b6000819050602082019050919050565b600081519050919050565b6000602082019050919050565b600082825260208201905092915050565b60006101e4826101eb565b9050919050565b600073ffffffffffffffffffffffffffffffffffffffff8216905091905056fea26469706673582212206d880cf012c1677c691bf6f2f0a0e4eadf57866ffe5cd2d9833d3cfdf27b15f664736f6c63430008060033";
  // The following address location is specific to above contract. It is used to pre-initialize the
  // array in the contract with validators
  private static final BigInteger ARRAY_ADDR =
      new BigInteger("290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563", 16);

  public final Map<String, Object> buildContractConfig(
      final Collection<? extends RunnableNode> validators) {
    Map<String, Object> contractAllocation = new LinkedHashMap<>();
    contractAllocation.put("balance", BALANCE);
    contractAllocation.put("code", CODE);
    contractAllocation.put("storage", buildContractStorageConfig(validators));

    return contractAllocation;
  }

  private Map<String, String> buildContractStorageConfig(
      final Collection<? extends RunnableNode> validators) {
    final List<String> addresses =
        validators.stream()
            .map(RunnableNode::getAddress)
            .map(Bytes::toUnprefixedHexString)
            .map(hex -> (padZero(0) + hex).substring(hex.length()))
            .collect(toList());

    final Map<String, String> storageValues = new LinkedHashMap<>();
    // zero location is size of array
    storageValues.put(padZero(0), padZero(addresses.size()));

    // further locations are allocation of addresses to array
    for (int i = 0; i < addresses.size(); i++) {
      final BigInteger varStorage = ARRAY_ADDR.add(BigInteger.valueOf(i));
      storageValues.put(varStorage.toString(16), addresses.get(i));
    }
    return storageValues;
  }

  private String padZero(final int value) {
    return String.format("%064x", value);
  }
}
