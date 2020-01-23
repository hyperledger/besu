/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.crosschain.core.keys;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Holds the Blockchain Public Key and associated meta-data. */
public class BlsThresholdPublicKeyImpl implements BlsThresholdPublicKey {
  protected static final Logger LOG = LogManager.getLogger();

  private long keyVersion;
  private int threshold;
  private BlsPoint publicKey;
  private BigInteger blockchainId;
  private BlsThresholdCryptoSystem algorithm;

  public BlsThresholdPublicKeyImpl(
      final BlsPoint publicKey,
      final long keyVersion,
      final int threshold,
      final BigInteger blockchainId,
      final BlsThresholdCryptoSystem algorithm) {
    this.keyVersion = keyVersion;
    this.threshold = threshold;
    this.publicKey = publicKey;
    this.blockchainId = blockchainId;
    this.algorithm = algorithm;
  }

  @Override
  public BlsPoint getPublicKey() {
    return this.publicKey;
  }

  @Override
  public long getKeyVersion() {
    return this.keyVersion;
  }

  @Override
  public int getThreshold() {
    return this.threshold;
  }

  @Override
  public BlsThresholdCryptoSystem getAlgorithm() {
    return this.algorithm;
  }

  @Override
  public BigInteger getBlockchainId() {
    return this.blockchainId;
  }

  @Override
  public BytesValue getEncodedPublicKey() {
    return RLP.encode(
        out -> {
          out.startList();
          out.writeBigIntegerScalar(this.blockchainId);
          out.writeLongScalar(this.keyVersion);
          out.writeLongScalar(this.threshold);
          out.writeLongScalar(this.algorithm.value);
          out.writeBytesValue(BytesValue.wrap(this.publicKey.store()));
          out.endList();
        });
  }

  @Override
  public BytesValue getEncodedPublicKeyForCoordinationContract() {
    byte[] publicKey = this.publicKey.store();

    final ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + publicKey.length);
    buffer.putInt(this.algorithm.value);
    buffer.put(publicKey);
    return BytesValue.wrapBuffer(buffer);
  }

  public static BlsThresholdPublicKey readFrom(final BytesValue input) {
    RLPInput in = RLP.input(input);
    in.enterList();
    BigInteger blockchainId = in.readBigIntegerScalar();
    long keyVersion = in.readLongScalar();
    int threshold = (int) in.readLongScalar();
    int algorithm = (int) in.readLongScalar();
    BytesValue publicKeyBytesValue = in.readBytesValue();

    BlsThresholdCryptoSystem cryptoSystem = BlsThresholdCryptoSystem.create(algorithm);
    byte[] pubKeyBytes = publicKeyBytesValue.extractArray();
    BlsPoint publicKey;
    switch (cryptoSystem) {
      case ALT_BN_128_WITH_KECCAK256:
        publicKey =
            BlsPoint.load(BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128, pubKeyBytes);
        break;
      default:
        String msg = "Unknown crypto system " + cryptoSystem;
        LOG.error(msg);
        throw new RuntimeException(msg);
    }
    return new BlsThresholdPublicKeyImpl(
        publicKey, keyVersion, threshold, blockchainId, cryptoSystem);
  }

  public static BlsThresholdPublicKey readFromForCoordinationContract(
      final BytesValue input, final BigInteger blockchainId, final long keyVersion) {
    ByteBuffer buffer = ByteBuffer.wrap(input.getByteArray());
    int algorithm = buffer.getInt();
    int pubKeySize = buffer.remaining();
    byte[] pubKeyBytes = new byte[pubKeySize];
    buffer.get(pubKeyBytes);

    BlsThresholdCryptoSystem cryptoSystem = BlsThresholdCryptoSystem.create(algorithm);
    BlsPoint publicKey;
    switch (cryptoSystem) {
      case ALT_BN_128_WITH_KECCAK256:
        publicKey =
            BlsPoint.load(BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128, pubKeyBytes);
        break;
      default:
        String msg = "Unknown crypto system " + cryptoSystem;
        LOG.error(msg);
        throw new RuntimeException(msg);
    }
    return new BlsThresholdPublicKeyImpl(publicKey, keyVersion, 0, blockchainId, cryptoSystem);
  }
}
