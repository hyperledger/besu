/*
 * Copyright PublicMint.
 * https://www.gnu.org/licenses/gpl-3.0.html
 * SPDX-License-Identifier: GNU GPLv3
 */
package org.hyperledger.besu.ethereum.eth.transactions.soruba;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionPoolAuthTxObjectMapper {
  private static final Logger LOG =
      LoggerFactory.getLogger(TransactionPoolAuthTxObjectMapper.class);
  /** Encode auth transaction object */
  public static String encodeAuthTxObject(
      final Transaction transaction, final String rawTransaction) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    objectMapper.registerModule(new Jdk8Module());

    TransactionPoolAuthTxMessage tx =
        new TransactionPoolAuthTxMessage(
            transaction.getHash().toString(),
            transaction.getSender().toString(),
            transaction.getTo().isPresent() ? transaction.getTo().get().toString() : "",
            transaction.getValue().toString(),
            transaction.getPayload().toString(),
            transaction.getGasLimit(),
            transaction.getGasPrice().get().toString(),
            transaction.getNonce(),
            rawTransaction,
            Optional.empty());

    StringWriter stringAuthTxMessage = new StringWriter();
    objectMapper.writeValue(stringAuthTxMessage, tx);
    return stringAuthTxMessage.toString();
  }

  /** Decode auth transaction object and set type as AUTH_SERVICE */
  public static Optional<Transaction> decodeAuthTxObject(
      final String jsonData, final Boolean rejectedReasonEnabled) throws JsonProcessingException {
    ObjectMapper objectMapper =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    objectMapper.registerModule(new Jdk8Module());

    TransactionPoolAuthTxMessage transactionObject =
        objectMapper.readValue(jsonData, TransactionPoolAuthTxMessage.class);

    String rawTransaction = transactionObject.getRawTransaction();

    if (rawTransaction == null || rawTransaction.trim().equals("")) {
      throw new RuntimeException("Invalid transaction for processing");
    }

    Transaction tx = decodeRawTransaction(rawTransaction).setType(TransactionType.AUTH_SERVICE);

    if (rejectedReasonEnabled) {
      LOG.debug("Rejected reason enabled");
      if (transactionObject.getRejectedReason().isEmpty()) {
        // return Empty means that transaction will be dropped by not have auth service reason to be
        // stored
        LOG.debug("Dropping the transaction by not get any reason");
        return Optional.empty();
      }
      Bytes reason =
          Bytes.wrap(transactionObject.getRejectedReason().get().getBytes(StandardCharsets.UTF_8));
      tx.setRejectedReason(Optional.of(reason));
      LOG.debug("Adding revert reason with code {} ", String.valueOf(reason));
    }
    return Optional.of(tx);
  }

  public static Transaction decodeRawTransaction(final String rawTransaction) {
    Bytes txnBytes = Bytes.fromHexString(rawTransaction);
    return TransactionDecoder.decodeOpaqueBytes(txnBytes, EncodingContext.POOLED_TRANSACTION);
  }
}
