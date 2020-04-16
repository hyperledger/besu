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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;
import org.hyperledger.besu.ethereum.mainnet.EthHasher;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public final class ProofOfWorkValidationRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LogManager.getLogger();

  private static final BigInteger ETHASH_TARGET_UPPER_BOUND = BigInteger.valueOf(2).pow(256);

  static final EthHasher HASHER = new EthHasher.Light();

  private final boolean includeBaseFee;

  public ProofOfWorkValidationRule() {
    this(false);
  }

  public ProofOfWorkValidationRule(final boolean includeBaseFee) {
    this.includeBaseFee = includeBaseFee;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    if (includeBaseFee) {
      if (!ExperimentalEIPs.eip1559Enabled) {
        LOG.warn("Invalid block header: EIP-1559 feature flag must be enabled --Xeip1559-enabled");
        return false;
      } else if (header.getBaseFee().isEmpty()) {
        LOG.warn("Invalid block header: missing mandatory base fee.");
        return false;
      }
    }

    final byte[] hashBuffer = new byte[64];
    final Hash headerHash = hashHeader(header);
    HASHER.hash(hashBuffer, header.getNonce(), header.getNumber(), headerHash.toArray());

    if (header.getDifficulty().isZero()) {
      LOG.trace("Rejecting header because difficulty is 0");
      return false;
    }
    final BigInteger difficulty = header.getDifficulty().toBytes().toUnsignedBigInteger();
    final UInt256 target =
        difficulty.equals(BigInteger.ONE)
            ? UInt256.MAX_VALUE
            : UInt256.valueOf(ETHASH_TARGET_UPPER_BOUND.divide(difficulty));
    final UInt256 result = UInt256.fromBytes(Bytes32.wrap(hashBuffer, 32));
    if (result.compareTo(target) > 0) {
      LOG.warn(
          "Invalid block header: the EthHash result {} was greater than the target {}.\n"
              + "Failing header:\n{}",
          result,
          target,
          header);
      return false;
    }

    final Hash mixedHash =
        Hash.wrap(Bytes32.leftPad(Bytes.wrap(hashBuffer).slice(0, Bytes32.SIZE)));
    if (!header.getMixHash().equals(mixedHash)) {
      LOG.warn(
          "Invalid block header: header mixed hash {} does not equal calculated mixed hash {}.\n"
              + "Failing header:\n{}",
          header.getMixHash(),
          mixedHash,
          header);
      return false;
    }

    return true;
  }

  Hash hashHeader(final BlockHeader header) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();

    // Encode header without nonce and mixhash
    out.startList();
    out.writeBytes(header.getParentHash());
    out.writeBytes(header.getOmmersHash());
    out.writeBytes(header.getCoinbase());
    out.writeBytes(header.getStateRoot());
    out.writeBytes(header.getTransactionsRoot());
    out.writeBytes(header.getReceiptsRoot());
    out.writeBytes(header.getLogsBloom());
    out.writeUInt256Scalar(header.getDifficulty());
    out.writeLongScalar(header.getNumber());
    out.writeLongScalar(header.getGasLimit());
    out.writeLongScalar(header.getGasUsed());
    out.writeLongScalar(header.getTimestamp());
    out.writeBytes(header.getExtraData());
    if (includeBaseFee && header.getBaseFee().isPresent()) {
      ExperimentalEIPs.eip1559MustBeEnabled();
      out.writeLongScalar(header.getBaseFee().get());
    }
    out.endList();

    return Hash.hash(out.encoded());
  }

  @Override
  public boolean includeInLightValidation() {
    return false;
  }
}
