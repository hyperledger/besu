/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class TransactionSSZEncodingTest {

  private static final String BLOB_TRANSACTIONS_TEST_VECTORS_JSON =
      "org/hyperledger/besu/ethereum/core/encoding/blob_transactions_test_vectors.json";

  private static final String BLOB_TRANSACTIONS_WITHOUT_BLOBS_TEST_VECTORS_JSON =
      "org/hyperledger/besu/ethereum/core/encoding/blob_transactions_without_blobs_test_vectors.json";

  private static Collection<Object[]> blobTransactionsTestVectors() throws IOException {

    ClassLoader classLoader = TransactionSSZEncodingTest.class.getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream(BLOB_TRANSACTIONS_TEST_VECTORS_JSON);
    JsonParser parser = new JsonFactory().createParser(inputStream);
    ObjectMapper mapper =
        JsonMapper.builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .build();
    List<Model> models = mapper.readValue(parser, new TypeReference<>() {});

    return models.stream()
        .map(
            model ->
                new Object[] {
                  generateName(model.getInput()), model.getInput(), model.getRawEncodedTransaction()
                })
        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
  }

  private static Collection<Object[]> blobTransactionsTestVectorsWithoutBlobs() throws IOException {

    ClassLoader classLoader = TransactionSSZEncodingTest.class.getClassLoader();
    InputStream inputStream =
        classLoader.getResourceAsStream(BLOB_TRANSACTIONS_WITHOUT_BLOBS_TEST_VECTORS_JSON);
    JsonParser parser = new JsonFactory().createParser(inputStream);
    ObjectMapper mapper =
        JsonMapper.builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .build();
    List<ModelWithoutBlobs> models = mapper.readValue(parser, new TypeReference<>() {});

    return models.stream()
        .map(
            model ->
                new Object[] {
                  generateNameWithoutblobs(model.getInput()),
                  model.getInput(),
                  model.getRawEncodedTransaction()
                })
        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
  }

  private static String generateName(final Input input) {
    return " To: "
        + input.getTo()
        + " Value: "
        + input.getValue()
        + " GasLimit: "
        + input.getGasLimit()
        + " GasPrice: "
        + input.getGasPrice()
        + " Nonce: "
        + input.getNonce();
  }

  private static String generateNameWithoutblobs(final InputWithoutBlobs input) {
    return " hash: " + input.getHash();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("blobTransactionsTestVectors")
  void shouldDecodeSSZTransactions(
      final String name, final Input input, final String rawTransaction) {
    final Bytes bytes = Bytes.fromHexString(rawTransaction);
    final Transaction transaction = TransactionDecoder.decodeOpaqueBytes(bytes);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getTo()).hasValue(Address.fromHexString(input.getTo()));
    assertThat(transaction.getGasLimit()).isEqualTo(Long.parseLong(input.getGasLimit()));
    assertThat(transaction.getNonce()).isEqualTo(Long.parseLong(input.getNonce()));
    final Bytes encodedBytes = TransactionEncoder.encodeOpaqueBytesForNetwork(transaction);
    assertThat(encodedBytes).isNotNull();
    assertThat(encodedBytes.toHexString()).isEqualTo(rawTransaction);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("blobTransactionsTestVectorsWithoutBlobs")
  void shouldDecodeSSZTransactionsWithoutBlobs(
      final String name, final InputWithoutBlobs input, final String rawTransaction) {
    final Bytes bytes = Bytes.fromHexString(rawTransaction);
    final Transaction transaction = TransactionDecoder.decodeOpaqueBytes(bytes);

    assertThat(transaction).isNotNull();
    assertThat(transaction.getPayload()).isNotNull();
    assertThat(transaction.getHash()).isEqualTo(Hash.fromHexString(input.getHash()));
    final Bytes encodedBytes = TransactionEncoder.encodeOpaqueBytes(transaction);
    assertThat(encodedBytes).isNotNull();
    assertThat(encodedBytes.toHexString()).isEqualTo(rawTransaction);
  }

  public static class Model {
    private Input input;
    private String rawEncodedTransaction;

    public Input getInput() {
      return input;
    }

    public void setInput(final Input input) {
      this.input = input;
    }

    public String getRawEncodedTransaction() {
      return rawEncodedTransaction;
    }

    public void setRawEncodedTransaction(final String rawEncodedTransaction) {
      this.rawEncodedTransaction = rawEncodedTransaction;
    }
  }

  public static class ModelWithoutBlobs {
    private InputWithoutBlobs input;
    private String rawEncodedTransaction;

    public InputWithoutBlobs getInput() {
      return input;
    }

    public void setInput(final InputWithoutBlobs input) {
      this.input = input;
    }

    public String getRawEncodedTransaction() {
      return rawEncodedTransaction;
    }

    public void setRawEncodedTransaction(final String rawEncodedTransaction) {
      this.rawEncodedTransaction = rawEncodedTransaction;
    }
  }

  public static class Input {
    String privateKey;
    String to;
    String nonce;
    String value;
    String gasLimit;
    String gasPrice;
    String priorityGasPrice;
    String maxFeePerDataGas;
    String data;

    public String getPrivateKey() {
      return privateKey;
    }

    public void setPrivateKey(final String privateKey) {
      this.privateKey = privateKey;
    }

    public String getTo() {
      return to;
    }

    public void setTo(final String to) {
      this.to = to;
    }

    public String getNonce() {
      return nonce;
    }

    public void setNonce(final String nonce) {
      this.nonce = nonce;
    }

    public String getValue() {
      return value;
    }

    public void setValue(final String value) {
      this.value = value;
    }

    public String getGasLimit() {
      return gasLimit;
    }

    public void setGasLimit(final String gasLimit) {
      this.gasLimit = gasLimit;
    }

    public String getGasPrice() {
      return gasPrice;
    }

    public void setGasPrice(final String gasPrice) {
      this.gasPrice = gasPrice;
    }

    public String getPriorityGasPrice() {
      return priorityGasPrice;
    }

    public void setPriorityGasPrice(final String priorityGasPrice) {
      this.priorityGasPrice = priorityGasPrice;
    }

    public String getMaxFeePerDataGas() {
      return maxFeePerDataGas;
    }

    public void setMaxFeePerDataGas(final String maxFeePerDataGas) {
      this.maxFeePerDataGas = maxFeePerDataGas;
    }

    public String getData() {
      return data;
    }

    public void setData(final String data) {
      this.data = data;
    }
  }

  public static class InputWithoutBlobs {
    String hash;

    public String getHash() {
      return hash;
    }

    public void setHash(final String hash) {
      this.hash = hash;
    }
  }
}
