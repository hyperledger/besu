package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.PrivacyGroup.Type;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultiTenancyPrivacyControllerTest {

  private static final String ENCLAVE_PUBLIC_KEY = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String ENCLAVE_PUBLIC_KEY2 = "OnviftjiizpjRt+HTuFBsKo2bVqD+nNlNYL5EE7y3Id=";
  private static final String PRIVACY_GROUP_ID = "nNlNYL5EE7y3IdM=";
  private static final String ENCLAVE_KEY = "Ko2bVqD";

  @Mock private PrivacyController privacyController;
  @Mock private Enclave enclave;

  final MultiTenancyPrivacyController multiTenancyPrivacyController =
      new MultiTenancyPrivacyController(privacyController, enclave);

  @Test
  public void sendsEeaTransactionWithMatchingPrivateFromAndEnclavePublicKey() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY))
            .build();

    when(privacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY))
        .thenReturn(new SendTransactionResponse(ENCLAVE_KEY, PRIVACY_GROUP_ID));

    final SendTransactionResponse response =
        multiTenancyPrivacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY);
    assertThat(response.getEnclaveKey()).isEqualTo(ENCLAVE_KEY);
    assertThat(response.getPrivacyGroupId()).isEqualTo(PRIVACY_GROUP_ID);
    verify(privacyController).sendTransaction(transaction, ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void sendsBesuTransactionWithEnclavePublicKeyInPrivacyGroup() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY))
            .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
            .build();

    when(privacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY))
        .thenReturn(new SendTransactionResponse(ENCLAVE_KEY, PRIVACY_GROUP_ID));
    final PrivacyGroup privateGroupWithEnclavePublicKey =
        new PrivacyGroup(
            "", Type.PANTHEON, "", "", List.of(ENCLAVE_PUBLIC_KEY, ENCLAVE_PUBLIC_KEY2));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privateGroupWithEnclavePublicKey);

    final SendTransactionResponse response =
        multiTenancyPrivacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY);
    assertThat(response.getEnclaveKey()).isEqualTo(ENCLAVE_KEY);
    assertThat(response.getPrivacyGroupId()).isEqualTo(PRIVACY_GROUP_ID);
    verify(privacyController).sendTransaction(transaction, ENCLAVE_PUBLIC_KEY);
    verify(enclave).retrievePrivacyGroup(PRIVACY_GROUP_ID);
  }

  @Test
  public void sendEeaTransactionFailsWithValidationExceptionWhenPrivateFromDoesNotMatch() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY2))
            .build();

    assertThatThrownBy(
            () -> multiTenancyPrivacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Transaction privateFrom must match enclave public key");

    verify(privacyController, never()).sendTransaction(transaction, ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void sendBesuTransactionFailsWithValidationExceptionWhenPrivateFromDoesNotMatch() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY2))
            .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
            .build();

    assertThatThrownBy(
            () -> multiTenancyPrivacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Transaction privateFrom must match enclave public key");

    verify(privacyController, never()).sendTransaction(transaction, ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void
      sendBesuTransactionFailsWithValidationExceptionWhenPrivacyGroupDoesNotContainEnclavePublicKey() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY))
            .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
            .build();

    final PrivacyGroup privacyGroupWithoutEnclavePublicKey =
        new PrivacyGroup("", Type.PANTHEON, "", "", List.of(ENCLAVE_PUBLIC_KEY2));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithoutEnclavePublicKey);

    assertThatThrownBy(
            () -> multiTenancyPrivacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Privacy group must contain the enclave public key");

    verify(privacyController, never()).sendTransaction(transaction, ENCLAVE_PUBLIC_KEY);
  }
}
