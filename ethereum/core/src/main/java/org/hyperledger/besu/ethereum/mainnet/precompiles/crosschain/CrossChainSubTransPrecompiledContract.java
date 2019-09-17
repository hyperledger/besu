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
package org.hyperledger.besu.ethereum.mainnet.precompiles.crosschain;

import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.BytesValue;

public class CrossChainSubTransPrecompiledContract extends AbstractCrossChainPrecompiledContract {

  public CrossChainSubTransPrecompiledContract(final GasCalculator gasCalculator) {
    super("CrossChainSubTrans", gasCalculator);
  }

  @Override
  public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
    LOG.info("CrossChainSubTrans Precompile called with " + input.size() + "bytes:" + input.toString());
      BytesValue outputOrError = processSubordinateTxOrView(input);
      if (outputOrError == null) {
          return null;
      }
      return BytesValue.of(1);
  }

  @Override
  protected boolean isMatched(final CrosschainTransaction ct) {
      return ct.getType().isOriginatingTransaction() || ct.getType().isSubordinateTransaction();
  }
}
