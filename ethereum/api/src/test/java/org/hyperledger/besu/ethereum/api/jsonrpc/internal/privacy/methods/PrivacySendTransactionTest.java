package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.DECODE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.ENCLAVE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_PARAMS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.EnclaveIOException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacySendTransaction.ErrorResponseException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivacySendTransactionTest {
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  @Mock private PrivacyController privacyController;
  private PrivacySendTransaction privacySendTransaction;

  @Before
  public void setup() {
    privacySendTransaction =
        new PrivacySendTransaction(privacyController, (user) -> ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void decodingFailsFOrRequestWithMoreThanOneParam() {
    final JsonRpcRequestContext requestContext =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new Object[] {"a", "b"}));

    assertThatThrownBy(() -> privacySendTransaction.decode(requestContext))
        .isInstanceOf(ErrorResponseException.class)
        .hasFieldOrPropertyWithValue("response", new JsonRpcErrorResponse(null, INVALID_PARAMS));
  }

  @Test
  public void decodingFailsForRequestWithInvalidPrivateTransaction() {
    final JsonRpcRequestContext requestContext =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new Object[] {""}));

    assertThatThrownBy(() -> privacySendTransaction.decode(requestContext))
        .isInstanceOf(ErrorResponseException.class)
        .hasFieldOrPropertyWithValue("response", new JsonRpcErrorResponse(null, DECODE_ERROR));
  }

  @Test
  public void decodesRequestWithValidPrivateTransaction() throws ErrorResponseException {
    final SECP256K1.KeyPair keyPair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

    final PrivateTransaction.Builder privateTransactionBuilder =
        PrivateTransaction.builder()
            .nonce(0)
            .gasPrice(Wei.of(1))
            .gasLimit(21000)
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .to(Address.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))
            .chainId(BigInteger.ONE)
            .privateFrom(Bytes.fromBase64String("S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXp="))
            .privacyGroupId(Bytes.fromBase64String("DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w="))
            .restriction(Restriction.RESTRICTED);
    final PrivateTransaction transaction = privateTransactionBuilder.signAndBuild(keyPair);

    final JsonRpcRequestContext requestContext = createSendTransactionRequest(transaction);

    final PrivateTransaction responseTransaction = privacySendTransaction.decode(requestContext);
    assertThat(responseTransaction).isEqualTo(transaction);
  }

  @Test
  public void validationFailsForRequestWithInvalidPrivacyGroupId() {
    final SECP256K1.KeyPair keyPair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

    final PrivateTransaction.Builder privateTransactionBuilder =
        PrivateTransaction.builder()
            .nonce(0)
            .gasPrice(Wei.of(1))
            .gasLimit(21000)
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .to(Address.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))
            .chainId(BigInteger.ONE)
            .privateFrom(Bytes.fromBase64String("S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXp="))
            .privateFor(
                List.of(
                    Bytes.fromBase64String(
                        "S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXpwalJ0K0hUdUZCcz0="),
                    Bytes.fromBase64String("QTFhVnRNeExDVUhtQlZIWG9aenpCZ1BiVy93ajVheER=")))
            .restriction(Restriction.RESTRICTED);
    final PrivateTransaction transaction = privateTransactionBuilder.signAndBuild(keyPair);

    final JsonRpcRequestContext requestContext = createSendTransactionRequest(transaction);

    assertThatThrownBy(() -> privacySendTransaction.validate(requestContext, transaction))
        .isInstanceOf(ErrorResponseException.class)
        .hasFieldOrPropertyWithValue("response", new JsonRpcErrorResponse(null, INVALID_PARAMS));
  }

  @Test
  public void validationFailsForInvalidTransaction() {
    final PrivateTransaction transaction = createValidTransaction();

    final JsonRpcRequestContext requestContext = createSendTransactionRequest(transaction);

    when(privacyController.validatePrivateTransaction(
            eq(transaction), anyString(), eq(ENCLAVE_PUBLIC_KEY)))
        .thenReturn(ValidationResult.invalid(TransactionInvalidReason.PRIVATE_VALUE_NOT_ZERO));

    assertThatThrownBy(() -> privacySendTransaction.validate(requestContext, transaction))
        .isInstanceOf(ErrorResponseException.class)
        .hasFieldOrPropertyWithValue("response", new JsonRpcErrorResponse(null, INVALID_PARAMS));
  }

  @Test
  public void validationSucceedsForValidTransaction() throws ErrorResponseException {
    final PrivateTransaction transaction = createValidTransaction();

    final JsonRpcRequestContext requestContext = createSendTransactionRequest(transaction);

    when(privacyController.validatePrivateTransaction(
            eq(transaction), anyString(), eq(ENCLAVE_PUBLIC_KEY)))
        .thenReturn(ValidationResult.valid());

    privacySendTransaction.validate(requestContext, transaction);
    verify(privacyController)
        .validatePrivateTransaction(eq(transaction), anyString(), eq(ENCLAVE_PUBLIC_KEY));
  }

  @Test
  public void sendsTransactionToPrivacyController() throws ErrorResponseException {
    final PrivateTransaction privateTransaction = createValidTransaction();
    final JsonRpcRequestContext jsonRpcRequest = createSendTransactionRequest(privateTransaction);

    privacySendTransaction.sendToEnclave(privateTransaction, jsonRpcRequest);

    verify(privacyController).sendTransaction(privateTransaction, ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void sendingFailsWithErrorResponseExceptionWhenPrivacyControllerReturnsError() {
    final PrivateTransaction privateTransaction = createValidTransaction();
    final JsonRpcRequestContext jsonRpcRequest = createSendTransactionRequest(privateTransaction);

    when(privacyController.sendTransaction(privateTransaction, ENCLAVE_PUBLIC_KEY))
        .thenThrow(new EnclaveIOException("enclave failed"));

    assertThatThrownBy(
            () -> privacySendTransaction.sendToEnclave(privateTransaction, jsonRpcRequest))
        .isInstanceOf(ErrorResponseException.class)
        .hasFieldOrPropertyWithValue("response", new JsonRpcErrorResponse(null, ENCLAVE_ERROR));

    verify(privacyController).sendTransaction(privateTransaction, ENCLAVE_PUBLIC_KEY);
  }

  private PrivateTransaction createValidTransaction() {
    final SECP256K1.KeyPair keyPair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

    final PrivateTransaction.Builder privateTransactionBuilder =
        PrivateTransaction.builder()
            .nonce(0)
            .gasPrice(Wei.of(1))
            .gasLimit(21000)
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .to(Address.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))
            .chainId(BigInteger.ONE)
            .privateFrom(Bytes.fromBase64String("S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXp="))
            .privateFor(
                List.of(
                    Bytes.fromBase64String("S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXp="),
                    Bytes.fromBase64String("QTFhVnRNeExDVUhtQlZIWG9aenpCZ1BiVy93ajVheER=")))
            .restriction(Restriction.RESTRICTED);

    return privateTransactionBuilder.signAndBuild(keyPair);
  }

  private JsonRpcRequestContext createSendTransactionRequest(final PrivateTransaction transaction) {
    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    transaction.writeTo(bvrlp);
    final String rlpEncodedTransaction = bvrlp.encoded().toHexString();

    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "eea_sendRawTransaction", new Object[] {rlpEncodedTransaction}));
  }
}
