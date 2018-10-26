/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolution;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolverInputs;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

public interface MiningCoordinator {

  void enable();

  void disable();

  boolean isRunning();

  // Required for JSON RPC, and are deemed to be valid for all mining mechanisms
  void setMinTransactionGasPrice(Wei minGasPrice);

  Wei getMinTransactionGasPrice();

  void setExtraData(BytesValue extraData);

  void setCoinbase(Address coinbase);

  Optional<Address> getCoinbase();

  Optional<Long> hashesPerSecond();

  Optional<EthHashSolverInputs> getWorkDefinition();

  boolean submitWork(EthHashSolution solution);
}
