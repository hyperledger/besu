package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FlexibleUtilTest {

  @Test
  public void testGetParticipantsFromParameter() {
    final String parameterNaCl =
        "0xb4926e2500000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002035695b4cc4b0941e60551d7a19cf30603db5bfc23e5ac43a56f57f25f75486a2a8d9b56a0fe9cd94d60be4413bcb721d3a7be27ed8e28b3a6346df874ee141b";
    final String expectedNaClParticipant1 = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
    final String expectedNaClParticipant2 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";

    List<String> actualParticipants =
        FlexibleUtil.getParticipantsFromParameter(Bytes.fromHexString(parameterNaCl));

    assertThat(actualParticipants).hasSize(2);
    assertThat(actualParticipants.get(0)).isEqualTo(expectedNaClParticipant1);
    assertThat(actualParticipants.get(1)).isEqualTo(expectedNaClParticipant2);

    final String parameterEC =
        "0xb4926e25000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000023059301306072a8648ce3d020106082a8648ce3d030107034200044bc9c2e2a4ff29da00a13485deab3fe3b0d4b038a1c956d68918d9022cafaa9f5e48392a57547394cfb9f283b09e9151c2f58d64cf80c4c5714fe32fc5db2a303059301306072a8648ce3d020106082a8648ce3d030107034200045c8819a91036e55fb79cdf83a3a6f9af48e252e9ee6fab9b8cf86ac07a4fc4fe38b94621f5128240d467b2a089f4f8de4e70bc47789e264ec7580972714d5bba";
    final String expectedECParticipant1 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAES8nC4qT/KdoAoTSF3qs/47DUsDihyVbWiRjZAiyvqp9eSDkqV1RzlM+58oOwnpFRwvWNZM+AxMVxT+MvxdsqMA==";
    final String expectedECParticipant2 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEXIgZqRA25V+3nN+Do6b5r0jiUunub6ubjPhqwHpPxP44uUYh9RKCQNRnsqCJ9PjeTnC8R3ieJk7HWAlycU1bug==";

    actualParticipants =
        FlexibleUtil.getParticipantsFromParameter(Bytes.fromHexString(parameterEC));

    assertThat(actualParticipants).hasSize(2);
    assertThat(actualParticipants.get(0)).isEqualTo(expectedECParticipant1);
    assertThat(actualParticipants.get(1)).isEqualTo(expectedECParticipant2);
  }
}
