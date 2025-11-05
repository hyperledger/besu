/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.testfuzz;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECP256R1;
import org.hyperledger.besu.crypto.SECPSignature;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;

/**
 * Generates initial seed corpus for P256Verify fuzzing. This creates a variety of test cases
 * including valid signatures, boundary conditions, and known edge cases.
 */
@SuppressWarnings({"DoNotCreateSecureRandomDirectly", "MethodInputParametersMustBeFinal"})
public class P256VerifyCorpusGenerator {

  private static final X9ECParameters R1_PARAMS = SECNamedCurves.getByName("secp256r1");
  private static final BigInteger N = R1_PARAMS.getN();
  private static final BigInteger P = R1_PARAMS.getCurve().getField().getCharacteristic();

  private final SECP256R1 secp256r1;
  private final SecureRandom random;

  /** Constructs a new P256VerifyCorpusGenerator with SECP256R1 signature algorithm. */
  public P256VerifyCorpusGenerator() {
    this.secp256r1 = new SECP256R1();
    SecureRandom tempRandom;
    try {
      tempRandom = SecureRandom.getInstanceStrong();
    } catch (Exception e) {
      tempRandom = new SecureRandom(); // Fallback to default
    }
    this.random = tempRandom;
  }

  /**
   * Main entry point for generating P256Verify test corpus.
   *
   * @param args command line arguments - expects output directory path
   * @throws IOException if corpus generation fails
   */
  public static void main(final String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("Usage: P256VerifyCorpusGenerator <output-directory>");
      System.exit(1);
    }

    File outputDir = new File(args[0]);
    outputDir.mkdirs();

    P256VerifyCorpusGenerator generator = new P256VerifyCorpusGenerator();
    generator.generateCorpus(outputDir);

    System.out.printf("Generated initial corpus in %s%n", outputDir.getAbsolutePath());
  }

  /**
   * Generates a comprehensive test corpus for P256Verify fuzzing.
   *
   * @param outputDir the directory to write corpus files to
   * @throws IOException if file writing fails
   */
  public void generateCorpus(final File outputDir) throws IOException {
    int testCount = 0;

    // Generate valid signatures
    for (int i = 0; i < 20; i++) {
      byte[] validSig = generateValidSignature();
      writeTestCase(outputDir, String.format("valid_%03d.hex", testCount++), validSig);
    }

    // Generate boundary value test cases
    testCount = generateBoundaryTests(outputDir, testCount);

    // Generate malleability test cases
    testCount = generateMalleabilityTests(outputDir, testCount);

    // Generate invalid curve point tests
    testCount = generateInvalidPointTests(outputDir, testCount);

    // Generate arithmetic edge cases
    testCount = generateArithmeticEdgeCases(outputDir, testCount);

    System.out.printf("Generated %d initial test cases%n", testCount);
  }

  private byte[] generateValidSignature() {
    try {
      KeyPair keyPair = secp256r1.generateKeyPair();
      Bytes32 messageHash = randomBytes32();
      SECPSignature signature = secp256r1.sign(messageHash, keyPair);

      MutableBytes input = MutableBytes.create(160);
      input.set(0, messageHash);
      input.set(32, Bytes32.leftPad(Bytes.wrap(signature.getR().toByteArray())));
      input.set(64, Bytes32.leftPad(Bytes.wrap(signature.getS().toByteArray())));
      input.set(96, keyPair.getPublicKey().getEncodedBytes());

      return input.toArray();
    } catch (Exception e) {
      return new byte[160]; // Return zeros if generation fails
    }
  }

  private int generateBoundaryTests(final File outputDir, int testCount) throws IOException {
    // r = 1, s = 1 (minimum valid values)
    MutableBytes test1 = MutableBytes.create(160);
    test1.set(0, randomBytes32());
    test1.set(32, Bytes32.leftPad(Bytes.of(1)));
    test1.set(64, Bytes32.leftPad(Bytes.of(1)));
    test1.set(96, randomValidPoint());
    writeTestCase(outputDir, String.format("boundary_%03d.hex", testCount++), test1.toArray());

    // r = n-1, s = n-1 (maximum valid values)
    MutableBytes test2 = MutableBytes.create(160);
    test2.set(0, randomBytes32());
    test2.set(32, bigIntegerToBytes32(N.subtract(BigInteger.ONE)));
    test2.set(64, bigIntegerToBytes32(N.subtract(BigInteger.ONE)));
    test2.set(96, randomValidPoint());
    writeTestCase(outputDir, String.format("boundary_%03d.hex", testCount++), test2.toArray());

    // r = 0 (invalid)
    MutableBytes test3 = MutableBytes.create(160);
    test3.set(0, randomBytes32());
    test3.set(32, Bytes32.ZERO);
    test3.set(64, Bytes32.leftPad(Bytes.of(1)));
    test3.set(96, randomValidPoint());
    writeTestCase(outputDir, String.format("boundary_%03d.hex", testCount++), test3.toArray());

    // s = 0 (invalid)
    MutableBytes test4 = MutableBytes.create(160);
    test4.set(0, randomBytes32());
    test4.set(32, Bytes32.leftPad(Bytes.of(1)));
    test4.set(64, Bytes32.ZERO);
    test4.set(96, randomValidPoint());
    writeTestCase(outputDir, String.format("boundary_%03d.hex", testCount++), test4.toArray());

    // Point at infinity (0, 0)
    MutableBytes test5 = MutableBytes.create(160);
    test5.set(0, randomBytes32());
    test5.set(32, Bytes32.leftPad(Bytes.of(1)));
    test5.set(64, Bytes32.leftPad(Bytes.of(1)));
    test5.set(96, Bytes32.ZERO);
    test5.set(128, Bytes32.ZERO);
    writeTestCase(outputDir, String.format("boundary_%03d.hex", testCount++), test5.toArray());

    return testCount;
  }

  private int generateMalleabilityTests(final File outputDir, int testCount) throws IOException {
    // Generate valid signature, then create malleable version
    byte[] validSig = generateValidSignature();

    // Extract s value and create malleable version (s' = n - s)
    BigInteger s = new BigInteger(1, java.util.Arrays.copyOfRange(validSig, 64, 96));
    BigInteger malleableS = N.subtract(s);

    byte[] malleableSig = validSig.clone();
    byte[] malleableSBytes = malleableS.toByteArray();
    java.util.Arrays.fill(malleableSig, 64, 96, (byte) 0);
    int copyLength = Math.min(32, malleableSBytes.length);
    System.arraycopy(
        malleableSBytes,
        Math.max(0, malleableSBytes.length - 32),
        malleableSig,
        64 + 32 - copyLength,
        copyLength);

    writeTestCase(outputDir, String.format("malleable_%03d.hex", testCount++), malleableSig);

    return testCount;
  }

  private int generateInvalidPointTests(final File outputDir, int testCount) throws IOException {
    // Random point not on curve
    MutableBytes test1 = MutableBytes.create(160);
    test1.set(0, randomBytes32());
    test1.set(32, Bytes32.leftPad(Bytes.of(1)));
    test1.set(64, Bytes32.leftPad(Bytes.of(1)));
    test1.set(96, randomBytes32()); // Random x
    test1.set(128, randomBytes32()); // Random y (likely not on curve)
    writeTestCase(outputDir, String.format("invalid_point_%03d.hex", testCount++), test1.toArray());

    // Coordinates >= p (field prime)
    MutableBytes test2 = MutableBytes.create(160);
    test2.set(0, randomBytes32());
    test2.set(32, Bytes32.leftPad(Bytes.of(1)));
    test2.set(64, Bytes32.leftPad(Bytes.of(1)));
    test2.set(96, bigIntegerToBytes32(P)); // x = p (invalid)
    test2.set(128, Bytes32.leftPad(Bytes.of(1)));
    writeTestCase(outputDir, String.format("invalid_point_%03d.hex", testCount++), test2.toArray());

    return testCount;
  }

  private int generateArithmeticEdgeCases(final File outputDir, int testCount) throws IOException {
    // All ones
    MutableBytes test1 = MutableBytes.create(160);
    for (int i = 0; i < 160; i++) {
      test1.set(i, (byte) 0xFF);
    }
    writeTestCase(outputDir, String.format("edge_%03d.hex", testCount++), test1.toArray());

    // Alternating pattern
    MutableBytes test2 = MutableBytes.create(160);
    for (int i = 0; i < 160; i++) {
      test2.set(i, (byte) (i % 2 == 0 ? 0xAA : 0x55));
    }
    writeTestCase(outputDir, String.format("edge_%03d.hex", testCount++), test2.toArray());

    // Powers of 2 in each field
    MutableBytes test3 = MutableBytes.create(160);
    test3.set(0, randomBytes32()); // message
    test3.set(32 + 31, (byte) 0x01); // r = 1
    test3.set(64 + 30, (byte) 0x02); // s = 2
    test3.set(96 + 29, (byte) 0x04); // qx starts with 4
    test3.set(128 + 28, (byte) 0x08); // qy starts with 8
    writeTestCase(outputDir, String.format("edge_%03d.hex", testCount++), test3.toArray());

    return testCount;
  }

  private Bytes32 randomBytes32() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Bytes32.wrap(bytes);
  }

  private Bytes randomValidPoint() {
    // Generate a valid public key and return its encoding
    try {
      KeyPair keyPair = secp256r1.generateKeyPair();
      return keyPair.getPublicKey().getEncodedBytes();
    } catch (Exception e) {
      // Fallback to generator point
      byte[] gEncoded = R1_PARAMS.getG().getEncoded(false);
      return Bytes.wrap(java.util.Arrays.copyOfRange(gEncoded, 1, 65));
    }
  }

  private void writeTestCase(final File outputDir, final String filename, final byte[] data)
      throws IOException {
    final File file = new File(outputDir, filename);
    Files.write(file.toPath(), Bytes.wrap(data).toHexString().getBytes(StandardCharsets.UTF_8));
  }

  private Bytes32 bigIntegerToBytes32(final BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length <= 32) {
      return Bytes32.leftPad(Bytes.wrap(bytes));
    } else {
      // Remove leading zero byte if present
      if (bytes[0] == 0 && bytes.length == 33) {
        return Bytes32.wrap(bytes, 1);
      } else {
        // Value too large, truncate to 32 bytes
        return Bytes32.wrap(bytes, bytes.length - 32);
      }
    }
  }
}
