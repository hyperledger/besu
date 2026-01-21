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
package org.hyperledger.besu.tests.acceptance.ethereum;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * Acceptance tests for EIP-7778: Block gas accounting without refunds.
 *
 * <p>EIP-7778 changes block gas accounting to use pre-refund gas, preventing block gas limit
 * circumvention. The receipt now includes a new field `gasSpent` which represents the post-refund
 * gas (what users actually pay).
 *
 * <p>Key changes in EIP-7778:
 *
 * <ul>
 *   <li>cumulative_gas_used: Now represents pre-refund gas (for block accounting)
 *   <li>gas_spent: New field representing post-refund gas (what user pays)
 *   <li>User economics are unchanged - users still receive refunds
 * </ul>
 */
public class EIP7778GasAccountingAcceptanceTest extends AcceptanceTestBase {
  private static final String GENESIS_FILE = "/dev/dev_amsterdam.json";
  private static final SECP256K1 secp256k1 = new SECP256K1();
  private static final MediaType MEDIA_TYPE_JSON =
      MediaType.parse("application/json; charset=utf-8");

  public static final Bytes SENDER_PRIVATE_KEY =
      Bytes.fromHexString("3a4ff6d22d7502ef2452368165422861c01a0f72f851793b372b87888dc3c453");

  private final Account recipient = accounts.createAccount("recipient");

  private BesuNode besuNode;
  private AmsterdamAcceptanceTestHelper testHelper;
  private OkHttpClient httpClient;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() throws IOException {
    besuNode = besu.createExecutionEngineGenesisNode("besuNode", GENESIS_FILE);
    cluster.start(besuNode);

    testHelper = new AmsterdamAcceptanceTestHelper(besuNode, ethTransactions);
    httpClient = new OkHttpClient();
    mapper = new ObjectMapper();
  }

  @AfterEach
  void tearDown() {
    besuNode.close();
  }

  /**
   * Tests that Amsterdam receipts include the gasSpent field. For simple ETH transfers without
   * refunds, gasSpent should equal gasUsed.
   */
  @Test
  public void shouldHaveGasSpentInReceiptForSimpleTransfer() throws IOException {
    final Wei transferAmount = Wei.of(1_000_000_000_000_000L); // 0.001 ETH

    final Transaction tx =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .chainId(BigInteger.valueOf(20211))
            .nonce(0)
            .maxPriorityFeePerGas(Wei.of(1_000_000_000))
            .maxFeePerGas(Wei.fromHexString("0x02540BE400"))
            .gasLimit(21_000)
            .to(Address.fromHexStringStrict(recipient.getAddress()))
            .value(transferAmount)
            .payload(Bytes.EMPTY)
            .signAndBuild(
                secp256k1.createKeyPair(
                    secp256k1.createPrivateKey(SENDER_PRIVATE_KEY.toUnsignedBigInteger())));

    final String txHash =
        besuNode.execute(ethTransactions.sendRawTransaction(tx.encoded().toHexString()));
    testHelper.buildNewBlock();

    final AtomicReference<Optional<TransactionReceipt>> maybeReceiptHolder =
        new AtomicReference<>(Optional.empty());
    WaitUtils.waitFor(
        60,
        () -> {
          maybeReceiptHolder.set(besuNode.execute(ethTransactions.getTransactionReceipt(txHash)));
          assertThat(maybeReceiptHolder.get()).isPresent();
        });

    final TransactionReceipt receipt = maybeReceiptHolder.get().orElseThrow();
    assertThat(receipt.getStatus()).isEqualTo("0x1");

    // Verify gasSpent is present via JSON-RPC
    final JsonNode receiptJson = getReceiptViaJsonRpc(txHash);
    assertThat(receiptJson.has("gasSpent")).isTrue();

    final String gasUsed = receiptJson.get("gasUsed").asText();
    final String gasSpent = receiptJson.get("gasSpent").asText();

    // For simple transfers without refunds, gasSpent should equal gasUsed
    assertThat(gasSpent).isEqualTo(gasUsed);
    assertThat(gasUsed).isEqualTo("0x5208"); // 21000 in hex
  }

  /**
   * Tests that gasSpent is correctly computed for transactions with gas refunds. The gasSpent
   * should be less than or equal to gasUsed when refunds apply.
   */
  @Test
  public void shouldHaveGasSpentLessThanOrEqualGasUsed() throws IOException {
    // Simple transfer - no refunds expected
    final Transaction tx =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .chainId(BigInteger.valueOf(20211))
            .nonce(0)
            .maxPriorityFeePerGas(Wei.of(1_000_000_000))
            .maxFeePerGas(Wei.fromHexString("0x02540BE400"))
            .gasLimit(50_000) // Higher gas limit
            .to(Address.fromHexStringStrict(recipient.getAddress()))
            .value(Wei.of(1000))
            .payload(Bytes.EMPTY)
            .signAndBuild(
                secp256k1.createKeyPair(
                    secp256k1.createPrivateKey(SENDER_PRIVATE_KEY.toUnsignedBigInteger())));

    final String txHash =
        besuNode.execute(ethTransactions.sendRawTransaction(tx.encoded().toHexString()));
    testHelper.buildNewBlock();

    final AtomicReference<Optional<TransactionReceipt>> maybeReceiptHolder =
        new AtomicReference<>(Optional.empty());
    WaitUtils.waitFor(
        60,
        () -> {
          maybeReceiptHolder.set(besuNode.execute(ethTransactions.getTransactionReceipt(txHash)));
          assertThat(maybeReceiptHolder.get()).isPresent();
        });

    final JsonNode receiptJson = getReceiptViaJsonRpc(txHash);
    assertThat(receiptJson.has("gasSpent")).isTrue();

    final long gasUsed = Long.decode(receiptJson.get("gasUsed").asText());
    final long gasSpent = Long.decode(receiptJson.get("gasSpent").asText());

    // gasSpent should always be <= gasUsed (refunds can only reduce, not increase)
    assertThat(gasSpent).isLessThanOrEqualTo(gasUsed);
  }

  /**
   * Tests that SSTORE refunds result in gasSpent being less than gasUsed. This test calls a
   * contract that clears a pre-set storage slot, generating a gas refund.
   *
   * <p>The test contract at address 0x7778 has storage slot 0 pre-set to 1. When called, it
   * executes PUSH0 PUSH0 SSTORE STOP, which clears the storage slot and generates a refund.
   *
   * <p>Expected behavior:
   *
   * <ul>
   *   <li>gasUsed (cumulative): Pre-refund gas for block accounting
   *   <li>gasSpent: Post-refund gas (what user pays), should be less due to SSTORE refund
   * </ul>
   */
  @Test
  public void shouldHaveGasSpentLessThanGasUsedWithSstoreRefund() throws IOException {
    // Contract address for the SSTORE refund test contract (defined in dev_amsterdam.json)
    final Address sstoreRefundContract =
        Address.fromHexStringStrict("0x0000000000000000000000000000000000007778");

    // Call the contract which clears storage slot 0, generating a refund
    final Transaction tx =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .chainId(BigInteger.valueOf(20211))
            .nonce(0)
            .maxPriorityFeePerGas(Wei.of(1_000_000_000))
            .maxFeePerGas(Wei.fromHexString("0x02540BE400"))
            .gasLimit(100_000) // Enough gas for SSTORE and refund
            .to(sstoreRefundContract)
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY) // No calldata needed, contract executes on any call
            .signAndBuild(
                secp256k1.createKeyPair(
                    secp256k1.createPrivateKey(SENDER_PRIVATE_KEY.toUnsignedBigInteger())));

    final String txHash =
        besuNode.execute(ethTransactions.sendRawTransaction(tx.encoded().toHexString()));
    testHelper.buildNewBlock();

    final AtomicReference<Optional<TransactionReceipt>> maybeReceiptHolder =
        new AtomicReference<>(Optional.empty());
    WaitUtils.waitFor(
        60,
        () -> {
          maybeReceiptHolder.set(besuNode.execute(ethTransactions.getTransactionReceipt(txHash)));
          assertThat(maybeReceiptHolder.get()).isPresent();
        });

    final TransactionReceipt receipt = maybeReceiptHolder.get().orElseThrow();
    assertThat(receipt.getStatus()).isEqualTo("0x1");

    final JsonNode receiptJson = getReceiptViaJsonRpc(txHash);
    assertThat(receiptJson.has("gasSpent")).isTrue();
    assertThat(receiptJson.has("gasUsed")).isTrue();

    final long gasUsed = Long.decode(receiptJson.get("gasUsed").asText());
    final long gasSpent = Long.decode(receiptJson.get("gasSpent").asText());

    // With SSTORE refund (clearing storage from non-zero to zero), gasSpent should be less
    // The refund is applied to gasSpent but NOT to gasUsed (which is pre-refund for block
    // accounting)
    assertThat(gasSpent)
        .as(
            "gasSpent (%d) should be less than gasUsed (%d) due to SSTORE refund for clearing storage",
            gasSpent, gasUsed)
        .isLessThan(gasUsed);

    // The difference should be the SSTORE refund amount (4800 gas for clearing storage in
    // post-EIP-3529)
    final long refundAmount = gasUsed - gasSpent;
    assertThat(refundAmount)
        .as("Refund amount should be 4800 (SSTORE_CLEARS_SCHEDULE)")
        .isEqualTo(4800L);
  }

  /**
   * Fetches the transaction receipt via direct JSON-RPC call to access the raw JSON response.
   *
   * <p>This is necessary because web3j's {@link TransactionReceipt} class doesn't include the
   * {@code gasSpent} field introduced by EIP-7778. The standard web3j model only knows about
   * pre-EIP-7778 receipt fields, so we need to parse the raw JSON to verify that Besu correctly
   * includes the new {@code gasSpent} field in the response.
   */
  private JsonNode getReceiptViaJsonRpc(final String txHash) throws IOException {
    final String requestBody =
        "{"
            + "  \"jsonrpc\": \"2.0\","
            + "  \"method\": \"eth_getTransactionReceipt\","
            + "  \"params\": [\""
            + txHash
            + "\"],"
            + "  \"id\": 1"
            + "}";

    final Request request =
        new Request.Builder()
            .url(besuNode.jsonRpcBaseUrl().orElseThrow())
            .post(RequestBody.create(requestBody, MEDIA_TYPE_JSON))
            .build();

    try (final Response response = httpClient.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(200);
      final JsonNode responseJson = mapper.readTree(response.body().string());
      return responseJson.get("result");
    }
  }
}
