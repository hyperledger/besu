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
package org.hyperledger.besu.evm.operation;

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The AUTH operation. */
public class AuthOperation extends AbstractOperation {

  /** The constant MAGIC defined by EIP-3074 */
  public static final byte MAGIC = 0x4;

  private static final Logger LOG = LoggerFactory.getLogger(AuthOperation.class);

  private static final SignatureAlgorithm signatureAlgorithm =
      SignatureAlgorithmFactory.getInstance();

  /**
   * Instantiates a new AuthOperation.
   *
   * @param gasCalculator a Prague or later gas calculator
   */
  public AuthOperation(final GasCalculator gasCalculator) {
    super(0xF6, "AUTH", 3, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    // create authority from stack
    Address authority = Words.toAddress(frame.getStackItem(0));
    long offset = clampedToLong(frame.getStackItem(1));
    long length = clampedToLong(frame.getStackItem(2));

    final long gasCost =
        super.gasCalculator().authOperationGasCost(frame, offset, length, authority);
    if (frame.getRemainingGas() < gasCost) {
      return new OperationResult(gasCost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    byte yParity = frame.readMemory(offset, 1).get(0);
    Bytes32 r = Bytes32.wrap(frame.readMemory(offset + 1, 32));
    Bytes32 s = Bytes32.wrap(frame.readMemory(offset + 33, 32));
    Bytes32 commit = Bytes32.wrap(frame.readMemory(offset + 65, 32));
    Bytes32 invoker = Bytes32.leftPad(frame.getContractAddress());
    // TODO add test for getting sender nonce when account does not exist
    Bytes32 senderNonce =
        Bytes32.leftPad(
            Bytes.ofUnsignedLong(
                Optional.ofNullable(frame.getWorldUpdater().getAccount(authority))
                    .map(Account::getNonce)
                    .orElse(0L)));
    if (evm.getChainId().isEmpty()) {
      frame.pushStackItem(UInt256.ZERO);
      LOG.error("ChainId is not set");
      return new OperationResult(0, null);
    }
    Bytes authPreImage =
        Bytes.concatenate(
            Bytes.ofUnsignedShort(MAGIC), evm.getChainId().get(), senderNonce, invoker, commit);
    Bytes32 messageHash = Hash.keccak256(authPreImage);
    Optional<SECPPublicKey> publicKey;
    try {
      SECPSignature signature =
          signatureAlgorithm.createSignature(
              r.toUnsignedBigInteger(), s.toUnsignedBigInteger(), yParity);
      publicKey = signatureAlgorithm.recoverPublicKeyFromSignature(messageHash, signature);
    } catch (IllegalArgumentException e) {

      frame.pushStackItem(UInt256.ZERO);
      return new OperationResult(gasCost, null);
    }
    if (publicKey.isPresent()) {
      Address signerAddress = Address.extract(publicKey.get());
      if (signerAddress.equals(authority)) {
        frame.setAuthorizedBy(authority);
        frame.pushStackItem(UInt256.ONE);
      } else {
        frame.pushStackItem(UInt256.ZERO);
      }
    } else {
      frame.pushStackItem(UInt256.ZERO);
    }
    return new OperationResult(gasCost, null);
  }
}
