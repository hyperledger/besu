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
package tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.DetachedBlockHeaderValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.EthHasher;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ProofOfWorkValidationRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LogManager.getLogger();

  private static final BigInteger ETHASH_TARGET_UPPER_BOUND = BigInteger.valueOf(2).pow(256);

  static final EthHasher HASHER = new EthHasher.Light();

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    final byte[] hashBuffer = new byte[64];
    final Hash headerHash = hashHeader(header);
    HASHER.hash(hashBuffer, header.getNonce(), header.getNumber(), headerHash.extractArray());

    if (header.getDifficulty().isZero()) {
      LOG.trace("Rejecting header because difficulty is 0");
      return false;
    }
    final BigInteger difficulty =
        BytesValues.asUnsignedBigInteger(header.getDifficulty().getBytes());
    final UInt256 target =
        difficulty.equals(BigInteger.ONE)
            ? UInt256.MAX_VALUE
            : UInt256.of(ETHASH_TARGET_UPPER_BOUND.divide(difficulty));
    final UInt256 result = UInt256.wrap(Bytes32.wrap(hashBuffer, 32));
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
        Hash.wrap(Bytes32.leftPad(BytesValue.wrap(hashBuffer).slice(0, Bytes32.SIZE)));
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
    out.writeBytesValue(header.getParentHash());
    out.writeBytesValue(header.getOmmersHash());
    out.writeBytesValue(header.getCoinbase());
    out.writeBytesValue(header.getStateRoot());
    out.writeBytesValue(header.getTransactionsRoot());
    out.writeBytesValue(header.getReceiptsRoot());
    out.writeBytesValue(header.getLogsBloom().getBytes());
    out.writeUInt256Scalar(header.getDifficulty());
    out.writeLongScalar(header.getNumber());
    out.writeLongScalar(header.getGasLimit());
    out.writeLongScalar(header.getGasUsed());
    out.writeLongScalar(header.getTimestamp());
    out.writeBytesValue(header.getExtraData());
    out.endList();

    return Hash.hash(out.encoded());
  }

  @Override
  public boolean includeInLightValidation() {
    return false;
  }
}
