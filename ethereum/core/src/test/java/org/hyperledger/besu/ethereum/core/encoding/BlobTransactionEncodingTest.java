/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BlobTransactionEncodingTest {
  private static Stream<Arguments> provideTypedTransactionBytes() throws IOException {
    return Stream.of(
        createArgument(
            "0x03f89d850120b996ed3685012a1a646085012a1a64608303345094ffb38a7a99e3e2335be83fc74b7faa19d55312418308a80280c085012a1a6460e1a00153a6a1e053cf4c5a09e84088ed8ad7cb53d76c8168f1b82f7cfebfcd06da1a01a007785223eec68459d72265f10bdb30ec3415252a63100605a03142fa211ebbe9a07dbbf9e081fa7b9a01202e4d9ee0e0e513f80efbbab6c784635429905389ce86"),
        createArgument(
            "0x03f889850120b996ed81f0847735940084b2d05e158307a1208001855f495f4955c084b2d05e15e1a001d343d3cd62abd9c5754cbe5128c25ea90786a8ae75fb79c8cf95f4dcdd08ec80a014103732b5a9789bbf5ea859ed904155398abbef343f8fd63007efb70795d382a07272e847382789a092eadf08e2b9002e727376f8466fff0e4d4639fd60a528f2"),
        createArgument(
            "0x03f889850120b996ed81f1843b9aca00847735940e8307a1208001855f495f4955c0847735940ee1a001d552e24560ec2f168be1d4a6385df61c70afe4288f00a3ad172da1a6f2b4f280a0b6690786e5fe79df67dcb60e8a9e8555142c3c96ffd5097c838717f0a7f64129a0112f01ed0cd3b86495f01736fbbc1b793f71565223aa26f093471a4d8605d198"),
        createArgument(
            "0x03f897850120b996ed80840bebc200843b9aca078303345094c8d369b164361a8961286cfbab3bc10f962185a88080c08411e1a300e1a0011df88a2971c8a7ac494a7ba37ec1acaa1fc1edeeb38c839b5d1693d47b69b080a032f122f06e5802224db4c8a58fd22c75173a713f63f89936f811c144b9e40129a043a2a872cbfa5727007adf6a48febe5f190d2e4cd5ed6122823fb6ff47ecda32"));
  }

  private static Stream<Arguments> provideTypedTransactionBytesForNetwork() throws IOException {
    return Stream.of(createArgumentFromFile("blob1.txt"));
  }

  @ParameterizedTest(name = "{index} {0}")
  @MethodSource("provideTypedTransactionBytesForNetwork")
  public void blobTransactionEncodingDecodingForNetWorkTest(
      final TypedTransactionBytesArgument argument) {
    Bytes bytes = argument.bytes;
    // Decode the transaction from the wire using the TransactionDecoder.
    final Transaction transaction = TransactionDecoder.decodeForWire(RLP.input(bytes));

    final BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
    BlobTransactionEncoder.encodeForWireNetwork(transaction, bytesValueRLPOutput);
    Bytes encodedRLP = bytesValueRLPOutput.encoded();
    assertThat(encodedRLP.size()).isEqualTo(bytes.size());
    assertThat(encodedRLP).isEqualTo(bytes);
  }

  @ParameterizedTest(name = "{index} {0}")
  @MethodSource("provideTypedTransactionBytes")
  public void blobTransactionEncodingDecodingTest(final TypedTransactionBytesArgument argument) {
    Bytes bytes = argument.bytes;
    // Decode the transaction from the wire using the TransactionDecoder.
    final Transaction transaction = TransactionDecoder.decodeForWire(RLP.input(bytes));
    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    // Encode the transaction for wire using the TransactionEncoder.
    TransactionEncoder.encodeForWire(transaction, output);
    // Assert that the encoded transaction matches the original bytes.
    assertThat(output.encoded().toHexString()).isEqualTo(bytes.toHexString());
  }

  private static Arguments createArgumentFromFile(final String path) throws IOException {
    StringBuilder contentBuilder = new StringBuilder();

    try (InputStream inputStream = BlobTransactionEncodingTest.class.getResourceAsStream(path)) {
      try (InputStreamReader inputStreamReader =
              new InputStreamReader(inputStream, StandardCharsets.UTF_8);
          BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

        String line;
        while ((line = bufferedReader.readLine()) != null) {
          contentBuilder.append(line);
        }
      }
    }

    return createArgument(contentBuilder.toString());
  }

  private static Arguments createArgument(final String hex) {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeBytes(Bytes.fromHexString(hex));
    return Arguments.of(new TypedTransactionBytesArgument(out.encoded()));
  }

  @SuppressWarnings("UnusedVariable")
  private record TypedTransactionBytesArgument(Bytes bytes) {
    @Override
    public String toString() {
      return bytes.size() > 32
          ? String.format("%s...%s", bytes.slice(0, 16), bytes.slice(bytes.size() - 16, 16))
          : bytes.toString();
    }
  }
}
