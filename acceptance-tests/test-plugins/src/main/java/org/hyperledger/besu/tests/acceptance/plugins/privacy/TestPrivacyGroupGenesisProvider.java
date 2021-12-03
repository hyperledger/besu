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
 *
 */
package org.hyperledger.besu.tests.acceptance.plugins.privacy;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.data.PrivacyGenesis;
import org.hyperledger.besu.plugin.data.PrivacyGenesisAccount;
import org.hyperledger.besu.plugin.data.Quantity;
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupGenesisProvider;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class TestPrivacyGroupGenesisProvider implements PrivacyGroupGenesisProvider {
  private boolean genesisEnabled = false;

  public void setGenesisEnabled() {
    this.genesisEnabled = true;
  }

  @Override
  public PrivacyGenesis getPrivacyGenesis(final Bytes privacyGroupId, final long blockNumber) {
    if (!genesisEnabled) return Collections::emptyList;

    return () ->
        List.of(
            new PrivacyGenesisAccount() {
              @Override
              public Address getAddress() {
                return org.hyperledger.besu.datatypes.Address.fromHexString(
                    "0x1000000000000000000000000000000000000001");
              }

              @Override
              public Map<UInt256, UInt256> getStorage() {
                return Collections.emptyMap();
              }

              @Override
              public Long getNonce() {
                return 0L;
              }

              @Override
              public Quantity getBalance() {
                return Wei.of(1000);
              }

              // The code in the genesis file needs to be the deployed contract code, not the code
              // to deploy
              // the contract
              // you can generate it using the solc --bin-runtime flag
              // cd ./acceptance-tests/tests/src/test/java/org/hyperledger/besu/tests/web3j/
              // docker run -v $PWD:/sources ethereum/solc:0.5.0 -o /sources/output --bin-runtime
              // /sources/EventEmitter.sol --overwrite

              @Override
              public Bytes getCode() {
                return Bytes.fromHexString(
                    "0x608060405260043610610057576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680633fa4f2451461005c5780636057361d1461008757806367e404ce146100c2575b600080fd5b34801561006857600080fd5b50610071610119565b6040518082815260200191505060405180910390f35b34801561009357600080fd5b506100c0600480360360208110156100aa57600080fd5b8101908080359060200190929190505050610123565b005b3480156100ce57600080fd5b506100d76101d9565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b6000600254905090565b7fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f53382604051808373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020018281526020019250505060405180910390a18060028190555033600160006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050565b6000600160009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690509056fea165627a7a72305820e74360c3d08936cb1747ad641729261ff5e83b6fc0d303d136e171f15f07d7740029");
              }
            });
  }
}
