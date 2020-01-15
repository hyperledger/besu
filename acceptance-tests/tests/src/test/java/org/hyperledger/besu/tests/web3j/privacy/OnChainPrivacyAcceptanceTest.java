package org.hyperledger.besu.tests.web3j.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory.PrivxCreatePrivacyGroup;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.besu.response.privacy.PrivacyGroup;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.utils.Base64String;

public class OnChainPrivacyAcceptanceTest extends PrivacyAcceptanceTestBase {
  private static final long POW_CHAIN_ID = 2018;

  private PrivacyNode alice;
  private PrivacyNode bob;
  private PrivacyNode charlie;

  @Before
  public void setUp() throws Exception {
    alice =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            "node1", privacyAccountResolver.resolve(0));
    bob =
        privacyBesu.createPrivateTransactionEnabledNode("node2", privacyAccountResolver.resolve(1));
    charlie =
        privacyBesu.createPrivateTransactionEnabledNode("node3", privacyAccountResolver.resolve(2));
    privacyCluster.start(alice, bob, charlie);
  }

  @Test
  public void nodeCanCreatePrivacyGroup() {
    final PrivxCreatePrivacyGroup privxCreatePrivacyGroup =
        alice.execute(
            privacyTransactions.createOnChainPrivacyGroup(
                "", "", alice, bob));

    assertThat(privxCreatePrivacyGroup).isNotNull();

    final org.hyperledger.besu.tests.web3j.generated.PrivacyGroup privacyGroup =
            org.hyperledger.besu.tests.web3j.generated.PrivacyGroup.load(
            null, null, Credentials.create(alice.getTransactionSigningKey()), null);

    try {
      TimeUnit.SECONDS.sleep(5);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    final String rlpParticipants =
        alice.execute(
            privateContractTransactions.callOnChainPermissioningSmartContract(
                Address.PRIVACY_PROXY.toHexString(),
                privacyGroup
                    .getParticipants(Bytes.fromBase64String(alice.getEnclaveKey()).toArrayUnsafe())
                    .encodeFunctionCall(),
                alice.getTransactionSigningKey(),
                POW_CHAIN_ID,
                alice.getEnclaveKey(),
                privxCreatePrivacyGroup.getPrivacyGroupId()));

    try {
      TimeUnit.SECONDS.sleep(5);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    alice.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            rlpParticipants,
            new PrivateTransactionReceipt(
                null,
                "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
                "0x000000000000000000000000000000000000007c",
                "0x0000000000000000000000000000000000000000000000000000000000000020" // dynamic array offset
                    + "0000000000000000000000000000000000000000000000000000000000000002" // length of array
                    + "035695b4cc4b0941e60551d7a19cf30603db5bfc23e5ac43a56f57f25f75486a" // first element
                    + "2a8d9b56a0fe9cd94d60be4413bcb721d3a7be27ed8e28b3a6346df874ee141b", // second element
                Collections.emptyList(),
                null,
                null,
                "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=",
                null,
                privxCreatePrivacyGroup.getPrivacyGroupId(),
                "0x1",
                null)));

        final PrivacyGroup expected =
            new PrivacyGroup(
                privxCreatePrivacyGroup.getPrivacyGroupId(),
                PrivacyGroup.Type.PANTHEON,
                "",
                "",
                Base64String.wrapList(alice.getEnclaveKey(), bob.getEnclaveKey()));

    alice.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));

    bob.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));
  }
}
