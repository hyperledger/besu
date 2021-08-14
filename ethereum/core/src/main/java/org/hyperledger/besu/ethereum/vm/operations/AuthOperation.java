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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class AuthOperation extends AbstractFixedCostOperation {

  private static final Bytes MAGIC = Bytes.of(0x03);

  public AuthOperation(final GasCalculator gasCalculator) {
    super(0xf6, "AUTH", 4, 1, false, 1, gasCalculator, Gas.of(3100));
  }

  @Override
  protected OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    UInt256 commit = frame.popStackItem();
    UInt256 yParity = frame.popStackItem();
    UInt256 r = frame.popStackItem();
    UInt256 s = frame.popStackItem();
    if (!(yParity.equals(UInt256.ONE) || yParity.equals(UInt256.ZERO))) {
      frame.pushStackItem(UInt256.ZERO);
      return successResponse;
    }
    Bytes32 paddedInvokerAddress = Bytes32.leftPad(frame.getContractAddress());
    Bytes32 messageHash = Hash.keccak256(Bytes.concatenate(MAGIC, paddedInvokerAddress, commit));
    SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    Optional<SECPPublicKey> publicKey;
    try {
      SECPSignature signature =
          signatureAlgorithm.createSignature(
              r.toUnsignedBigInteger(),
              s.toUnsignedBigInteger(),
              yParity.equals(UInt256.ONE) ? (byte) 1 : 0);
      publicKey = signatureAlgorithm.recoverPublicKeyFromSignature(messageHash, signature);
    } catch (IllegalArgumentException e) {

      frame.pushStackItem(UInt256.ZERO);
      return successResponse;
    }

    if (publicKey.isPresent()) {
      Address signerAddress = Address.extract(publicKey.get());
      frame.setAuthorized(signerAddress);
      frame.pushStackItem(UInt256.fromBytes(Bytes32.leftPad(signerAddress)));
    } else {
      frame.pushStackItem(UInt256.ZERO);
    }
    return successResponse;
  }
}
