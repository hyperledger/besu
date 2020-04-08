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
package org.hyperledger.besu.crypto;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.crypto.BasicAgreement;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.bouncycastle.util.BigIntegers;

/*
 * Adapted from the BitcoinJ ECKey (Apache 2 License) implementation:
 * https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/core/ECKey.java
 *
 *
 * Adapted from the web3j (Apache 2 License) implementations:
 * https://github.com/web3j/web3j/crypto/src/main/java/org/web3j/crypto/*.java
 */
public class SECP256K1 {

  public static final String ALGORITHM = "ECDSA";
  public static final String CURVE_NAME = "secp256k1";
  public static final String PROVIDER = "BC";

  public static final ECDomainParameters CURVE;
  public static final BigInteger HALF_CURVE_ORDER;

  private static final KeyPairGenerator KEY_PAIR_GENERATOR;
  private static final BigInteger CURVE_ORDER;

  static {
    Security.addProvider(new BouncyCastleProvider());

    final X9ECParameters params = SECNamedCurves.getByName(CURVE_NAME);
    CURVE = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
    CURVE_ORDER = CURVE.getN();
    HALF_CURVE_ORDER = CURVE_ORDER.shiftRight(1);
    try {
      KEY_PAIR_GENERATOR = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    final ECGenParameterSpec ecGenParameterSpec = new ECGenParameterSpec(CURVE_NAME);
    try {
      KEY_PAIR_GENERATOR.initialize(ecGenParameterSpec, SecureRandomProvider.createSecureRandom());
    } catch (final InvalidAlgorithmParameterException e) {
      throw new RuntimeException(e);
    }
  }

  /** Decompress a compressed public key (x co-ord and low-bit of y-coord). */
  private static ECPoint decompressKey(final BigInteger xBN, final boolean yBit) {
    final X9IntegerConverter x9 = new X9IntegerConverter();
    final byte[] compEnc = x9.integerToBytes(xBN, 1 + x9.getByteLength(CURVE.getCurve()));
    compEnc[0] = (byte) (yBit ? 0x03 : 0x02);
    // TODO: Find a better way to handle an invalid point compression here.
    // Currently ECCurve#decodePoint throws an IllegalArgumentException.
    return CURVE.getCurve().decodePoint(compEnc);
  }

  /**
   * Given the components of a signature and a selector value, recover and return the public key
   * that generated the signature according to the algorithm in SEC1v2 section 4.1.6.
   *
   * <p>If this method returns null it means recovery was not possible and recId should be iterated.
   *
   * <p>Given the above two points, a correct usage of this method is inside a for loop from 0 to 3,
   * and if the output is null OR a key that is not the one you expect, you try again with the next
   * recId.
   *
   * @param recId Which possible key to recover.
   * @param r The R component of the signature.
   * @param s The S component of the signature.
   * @param dataHash Hash of the data that was signed.
   * @return An ECKey containing only the public part, or null if recovery wasn't possible.
   */
  private static BigInteger recoverFromSignature(
      final int recId, final BigInteger r, final BigInteger s, final Bytes32 dataHash) {
    assert (recId >= 0);
    assert (r.signum() >= 0);
    assert (s.signum() >= 0);
    assert (dataHash != null);

    // 1.0 For j from 0 to h (h == recId here and the loop is outside this function)
    // 1.1 Let x = r + jn
    final BigInteger n = CURVE.getN(); // Curve order.
    final BigInteger i = BigInteger.valueOf((long) recId / 2);
    final BigInteger x = r.add(i.multiply(n));
    // 1.2. Convert the integer x to an octet string X of length mlen using the conversion
    // routine specified in Section 2.3.7, where mlen = ⌈(log2 p)/8⌉ or mlen = ⌈m/8⌉.
    // 1.3. Convert the octet string (16 set binary digits)||X to an elliptic curve point R
    // using the conversion routine specified in Section 2.3.4. If this conversion
    // routine outputs "invalid", then do another iteration of Step 1.
    //
    // More concisely, what these points mean is to use X as a compressed public key.
    final BigInteger prime = SecP256K1Curve.q;
    if (x.compareTo(prime) >= 0) {
      // Cannot have point co-ordinates larger than this as everything takes place modulo Q.
      return null;
    }
    // Compressed keys require you to know an extra bit of data about the y-coord as there are
    // two possibilities. So it's encoded in the recId.
    final ECPoint R = decompressKey(x, (recId & 1) == 1);
    // 1.4. If nR != point at infinity, then do another iteration of Step 1 (callers
    // responsibility).
    if (!R.multiply(n).isInfinity()) {
      return null;
    }
    // 1.5. Compute e from M using Steps 2 and 3 of ECDSA signature verification.
    final BigInteger e = dataHash.toUnsignedBigInteger();
    // 1.6. For k from 1 to 2 do the following. (loop is outside this function via
    // iterating recId)
    // 1.6.1. Compute a candidate public key as:
    // Q = mi(r) * (sR - eG)
    //
    // Where mi(x) is the modular multiplicative inverse. We transform this into the following:
    // Q = (mi(r) * s ** R) + (mi(r) * -e ** G)
    // Where -e is the modular additive inverse of e, that is z such that z + e = 0 (mod n).
    // In the above equation ** is point multiplication and + is point addition (the EC group
    // operator).
    //
    // We can find the additive inverse by subtracting e from zero then taking the mod. For
    // example the additive inverse of 3 modulo 11 is 8 because 3 + 8 mod 11 = 0, and
    // -3 mod 11 = 8.
    final BigInteger eInv = BigInteger.ZERO.subtract(e).mod(n);
    final BigInteger rInv = r.modInverse(n);
    final BigInteger srInv = rInv.multiply(s).mod(n);
    final BigInteger eInvrInv = rInv.multiply(eInv).mod(n);
    final ECPoint q = ECAlgorithms.sumOfTwoMultiplies(CURVE.getG(), eInvrInv, R, srInv);

    if (q.isInfinity()) {
      return null;
    }

    final byte[] qBytes = q.getEncoded(false);
    // We remove the prefix
    return new BigInteger(1, Arrays.copyOfRange(qBytes, 1, qBytes.length));
  }

  public static Signature sign(final Bytes32 dataHash, final KeyPair keyPair) {
    final ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));

    final ECPrivateKeyParameters privKey =
        new ECPrivateKeyParameters(
            keyPair.getPrivateKey().getEncodedBytes().toUnsignedBigInteger(), CURVE);
    signer.init(true, privKey);

    final BigInteger[] components = signer.generateSignature(dataHash.toArrayUnsafe());
    final BigInteger r = components[0];
    BigInteger s = components[1];

    // Automatically adjust the S component to be less than or equal to half the curve
    // order, if necessary. This is required because for every signature (r,s) the signature
    // (r, -s (mod N)) is a valid signature of the same message. However, we dislike the
    // ability to modify the bits of a Bitcoin transaction after it's been signed, as that
    // violates various assumed invariants. Thus in future only one of those forms will be
    // considered legal and the other will be banned.
    if (s.compareTo(HALF_CURVE_ORDER) > 0) {
      // The order of the curve is the number of valid points that exist on that curve.
      // If S is in the upper half of the number of valid points, then bring it back to
      // the lower half. Otherwise, imagine that
      // N = 10
      // s = 8, so (-8 % 10 == 2) thus both (r, 8) and (r, 2) are valid solutions.
      // 10 - 8 == 2, giving us always the latter solution, which is canonical.
      s = CURVE.getN().subtract(s);
    }

    // Now we have to work backwards to figure out the recId needed to recover the signature.
    int recId = -1;
    final BigInteger publicKeyBI = keyPair.getPublicKey().getEncodedBytes().toUnsignedBigInteger();
    for (int i = 0; i < 4; i++) {
      final BigInteger k = recoverFromSignature(i, r, s, dataHash);
      if (k != null && k.equals(publicKeyBI)) {
        recId = i;
        break;
      }
    }
    if (recId == -1) {
      throw new RuntimeException(
          "Could not construct a recoverable key. This should never happen.");
    }

    return new Signature(r, s, (byte) recId);
  }

  /**
   * Verifies the given ECDSA signature against the message bytes using the public key bytes.
   *
   * <p>When using native ECDSA verification, data must be 32 bytes, and no element may be larger
   * than 520 bytes.
   *
   * @param data Hash of the data to verify.
   * @param signature ASN.1 encoded signature.
   * @param pub The public key bytes to use.
   * @return True if the verification is successful.
   */
  public static boolean verify(final Bytes data, final Signature signature, final PublicKey pub) {
    final ECDSASigner signer = new ECDSASigner();
    final Bytes toDecode = Bytes.wrap(Bytes.of((byte) 4), pub.getEncodedBytes());
    final ECPublicKeyParameters params =
        new ECPublicKeyParameters(CURVE.getCurve().decodePoint(toDecode.toArrayUnsafe()), CURVE);
    signer.init(false, params);
    try {
      return signer.verifySignature(data.toArrayUnsafe(), signature.r, signature.s);
    } catch (final NullPointerException e) {
      // Bouncy Castle contains a bug that can cause NPEs given specially crafted signatures. Those
      // signatures
      // are inherently invalid/attack sigs so we just fail them here rather than crash the thread.
      return false;
    }
  }

  /**
   * Verifies the given ECDSA signature using the public key bytes against the message bytes,
   * previously passed through a preprocessor function, which is normally a hashing function.
   *
   * @param data The data to verify.
   * @param signature ASN.1 encoded signature.
   * @param pub The public key bytes to use.
   * @param preprocessor The function to apply to the data before verifying the signature, normally
   *     a hashing function.
   * @return True if the verification is successful.
   */
  public static boolean verify(
      final Bytes data,
      final Signature signature,
      final PublicKey pub,
      final UnaryOperator<Bytes> preprocessor) {
    checkArgument(preprocessor != null, "preprocessor must not be null");
    return verify(preprocessor.apply(data), signature, pub);
  }

  /**
   * Calculates an ECDH key agreement between the private and the public key.
   *
   * @param privKey The private key.
   * @param theirPubKey The public key.
   * @return The agreed secret.
   */
  public static Bytes32 calculateECDHKeyAgreement(
      final PrivateKey privKey, final PublicKey theirPubKey) {
    checkArgument(privKey != null, "missing private key");
    checkArgument(theirPubKey != null, "missing remote public key");

    final ECPrivateKeyParameters privKeyP = new ECPrivateKeyParameters(privKey.getD(), CURVE);
    final ECPublicKeyParameters pubKeyP = new ECPublicKeyParameters(theirPubKey.asEcPoint(), CURVE);

    final ECDHBasicAgreement agreement = new ECDHBasicAgreement();
    agreement.init(privKeyP);
    final BigInteger agreed = agreement.calculateAgreement(pubKeyP);

    return UInt256.valueOf(agreed).toBytes();
  }

  public static Bytes calculateECIESKeyAgreement(final PrivateKey privKey, final PublicKey pubKey) {
    final ECDomainParameters dp = SECP256K1.CURVE;

    final CipherParameters pubParam = new ECPublicKeyParameters(pubKey.asEcPoint(), dp);
    final CipherParameters privParam = new ECPrivateKeyParameters(privKey.getD(), dp);

    final BasicAgreement agree = new ECDHBasicAgreement();
    agree.init(privParam);
    final BigInteger z = agree.calculateAgreement(pubParam);
    return Bytes.wrap(BigIntegers.asUnsignedByteArray(agree.getFieldSize(), z));
  }

  public static class PrivateKey implements java.security.PrivateKey {
    private final Bytes32 encoded;

    private PrivateKey(final Bytes32 encoded) {
      checkNotNull(encoded);
      this.encoded = encoded;
    }

    public static PrivateKey create(final BigInteger key) {
      checkNotNull(key);
      return create(UInt256.valueOf(key).toBytes());
    }

    public static PrivateKey create(final Bytes32 key) {
      return new PrivateKey(key);
    }

    public ECPoint asEcPoint() {
      return CURVE.getCurve().decodePoint(encoded.toArrayUnsafe());
    }

    @Override
    public boolean equals(final Object other) {
      if (!(other instanceof PrivateKey)) {
        return false;
      }

      final PrivateKey that = (PrivateKey) other;
      return this.encoded.equals(that.encoded);
    }

    @Override
    public byte[] getEncoded() {
      return encoded.toArrayUnsafe();
    }

    public Bytes32 getEncodedBytes() {
      return encoded;
    }

    public BigInteger getD() {
      return encoded.toUnsignedBigInteger();
    }

    @Override
    public String getAlgorithm() {
      return ALGORITHM;
    }

    @Override
    public String getFormat() {
      return null;
    }

    @Override
    public int hashCode() {
      return encoded.hashCode();
    }

    @Override
    public String toString() {
      return encoded.toString();
    }
  }

  public static class PublicKey implements java.security.PublicKey {

    private static final int BYTE_LENGTH = 64;

    private final Bytes encoded;

    public static PublicKey create(final PrivateKey privateKey) {
      BigInteger privKey = privateKey.getEncodedBytes().toUnsignedBigInteger();

      /*
       * TODO: FixedPointCombMultiplier currently doesn't support scalars longer than the group
       * order, but that could change in future versions.
       */
      if (privKey.bitLength() > CURVE.getN().bitLength()) {
        privKey = privKey.mod(CURVE.getN());
      }

      final ECPoint point = new FixedPointCombMultiplier().multiply(CURVE.getG(), privKey);
      return PublicKey.create(Bytes.wrap(Arrays.copyOfRange(point.getEncoded(false), 1, 65)));
    }

    private static Bytes toBytes64(final byte[] backing) {
      if (backing.length == BYTE_LENGTH) {
        return Bytes.wrap(backing);
      } else if (backing.length > BYTE_LENGTH) {
        return Bytes.wrap(backing, backing.length - BYTE_LENGTH, BYTE_LENGTH);
      } else {
        final MutableBytes res = MutableBytes.create(BYTE_LENGTH);
        Bytes.wrap(backing).copyTo(res, BYTE_LENGTH - backing.length);
        return res;
      }
    }

    public static PublicKey create(final BigInteger key) {
      checkNotNull(key);
      return create(toBytes64(key.toByteArray()));
    }

    public static PublicKey create(final Bytes encoded) {
      return new PublicKey(encoded);
    }

    public static Optional<PublicKey> recoverFromSignature(
        final Bytes32 dataHash, final Signature signature) {
      final BigInteger publicKeyBI =
          SECP256K1.recoverFromSignature(
              signature.getRecId(), signature.getR(), signature.getS(), dataHash);
      return Optional.ofNullable(publicKeyBI).map(PublicKey::create);
    }

    private PublicKey(final Bytes encoded) {
      checkNotNull(encoded);
      checkArgument(
          encoded.size() == BYTE_LENGTH,
          "Encoding must be %s bytes long, got %s",
          BYTE_LENGTH,
          encoded.size());
      this.encoded = encoded;
    }

    /**
     * Returns this public key as an {@link ECPoint} of Bouncy Castle, to facilitate cryptographic
     * operations.
     *
     * @return This public key represented as an Elliptic Curve point.
     */
    public ECPoint asEcPoint() {
      // 0x04 is the prefix for uncompressed keys.
      final Bytes val = Bytes.concatenate(Bytes.of(0x04), encoded);
      return CURVE.getCurve().decodePoint(val.toArrayUnsafe());
    }

    @Override
    public boolean equals(final Object other) {
      if (!(other instanceof PublicKey)) {
        return false;
      }

      final PublicKey that = (PublicKey) other;
      return this.encoded.equals(that.encoded);
    }

    @Override
    public byte[] getEncoded() {
      return encoded.toArrayUnsafe();
    }

    public Bytes getEncodedBytes() {
      return encoded;
    }

    @Override
    public String getAlgorithm() {
      return ALGORITHM;
    }

    @Override
    public String getFormat() {
      return null;
    }

    @Override
    public int hashCode() {
      return encoded.hashCode();
    }

    @Override
    public String toString() {
      return encoded.toString();
    }
  }

  public static class KeyPair {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public KeyPair(final PrivateKey privateKey, final PublicKey publicKey) {
      checkNotNull(privateKey);
      checkNotNull(publicKey);
      this.privateKey = privateKey;
      this.publicKey = publicKey;
    }

    public static KeyPair create(final PrivateKey privateKey) {
      return new KeyPair(privateKey, PublicKey.create(privateKey));
    }

    public static KeyPair generate() {
      final java.security.KeyPair rawKeyPair = KEY_PAIR_GENERATOR.generateKeyPair();
      final BCECPrivateKey privateKey = (BCECPrivateKey) rawKeyPair.getPrivate();
      final BCECPublicKey publicKey = (BCECPublicKey) rawKeyPair.getPublic();

      final BigInteger privateKeyValue = privateKey.getD();

      // Ethereum does not use encoded public keys like bitcoin - see
      // https://en.bitcoin.it/wiki/Elliptic_Curve_Digital_Signature_Algorithm for details
      // Additionally, as the first bit is a constant prefix (0x04) we ignore this value
      final byte[] publicKeyBytes = publicKey.getQ().getEncoded(false);
      final BigInteger publicKeyValue =
          new BigInteger(1, Arrays.copyOfRange(publicKeyBytes, 1, publicKeyBytes.length));

      return new KeyPair(PrivateKey.create(privateKeyValue), PublicKey.create(publicKeyValue));
    }

    @Override
    public int hashCode() {
      return Objects.hash(privateKey, publicKey);
    }

    @Override
    public boolean equals(final Object other) {
      if (!(other instanceof KeyPair)) {
        return false;
      }

      final KeyPair that = (KeyPair) other;
      return this.privateKey.equals(that.privateKey) && this.publicKey.equals(that.publicKey);
    }

    public PrivateKey getPrivateKey() {
      return privateKey;
    }

    public PublicKey getPublicKey() {
      return publicKey;
    }
  }

  public static class Signature {
    public static final int BYTES_REQUIRED = 65;
    /**
     * The recovery id to reconstruct the public key used to create the signature.
     *
     * <p>The recId is an index from 0 to 3 which indicates which of the 4 possible keys is the
     * correct one. Because the key recovery operation yields multiple potential keys, the correct
     * key must either be stored alongside the signature, or you must be willing to try each recId
     * in turn until you find one that outputs the key you are expecting.
     */
    private final byte recId;

    private final BigInteger r;
    private final BigInteger s;

    private Signature(final BigInteger r, final BigInteger s, final byte recId) {
      this.r = r;
      this.s = s;
      this.recId = recId;
    }

    /**
     * Creates a new signature object given its parameters.
     *
     * @param r the 'r' part of the signature.
     * @param s the 's' part of the signature.
     * @param recId the recovery id part of the signature.
     * @return the created {@link Signature} object.
     * @throws NullPointerException if {@code r} or {@code s} are {@code null}.
     * @throws IllegalArgumentException if any argument is invalid (for instance, {@code v} is
     *     neither 27 or 28).
     */
    public static Signature create(final BigInteger r, final BigInteger s, final byte recId) {
      checkNotNull(r);
      checkNotNull(s);
      checkInBounds("r", r);
      checkInBounds("s", s);
      if (recId != 0 && recId != 1) {
        throw new IllegalArgumentException(
            "Invalid 'recId' value, should be 0 or 1 but got " + recId);
      }
      return new Signature(r, s, recId);
    }

    private static void checkInBounds(final String name, final BigInteger i) {
      if (i.compareTo(BigInteger.ONE) < 0) {
        throw new IllegalArgumentException(
            String.format("Invalid '%s' value, should be >= 1 but got %s", name, i));
      }

      if (i.compareTo(CURVE_ORDER) >= 0) {
        throw new IllegalArgumentException(
            String.format("Invalid '%s' value, should be < %s but got %s", CURVE_ORDER, name, i));
      }
    }

    public static Signature decode(final Bytes bytes) {
      checkArgument(
          bytes.size() == BYTES_REQUIRED, "encoded SECP256K1 signature must be 65 bytes long");

      final BigInteger r = bytes.slice(0, 32).toUnsignedBigInteger();
      final BigInteger s = bytes.slice(32, 32).toUnsignedBigInteger();
      final byte recId = bytes.get(64);
      return SECP256K1.Signature.create(r, s, recId);
    }

    public Bytes encodedBytes() {
      final MutableBytes bytes = MutableBytes.create(BYTES_REQUIRED);
      UInt256.valueOf(r).toBytes().copyTo(bytes, 0);
      UInt256.valueOf(s).toBytes().copyTo(bytes, 32);
      bytes.set(64, recId);
      return bytes;
    }

    @Override
    public boolean equals(final Object other) {
      if (!(other instanceof Signature)) {
        return false;
      }

      final Signature that = (Signature) other;
      return this.r.equals(that.r) && this.s.equals(that.s) && this.recId == that.recId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(r, s, recId);
    }

    public byte getRecId() {
      return recId;
    }

    public BigInteger getR() {
      return r;
    }

    public BigInteger getS() {
      return s;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append("SECP256K1.Signature").append("{");
      sb.append("r=").append(r).append(", ");
      sb.append("s=").append(s).append(", ");
      sb.append("recId=").append(recId);
      return sb.append("}").toString();
    }
  }
}
