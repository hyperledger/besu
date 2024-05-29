/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FlexibleUtilTest {

  private static final String EXPECTED_EC_PARTICIPANT_1 =
      "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAES8nC4qT/KdoAoTSF3qs/47DUsDihyVbWiRjZAiyvqp9eSDkqV1RzlM+58oOwnpFRwvWNZM+AxMVxT+MvxdsqMA==";

  private static final String EXPECTED_EC_PARTICIPANT_2 =
      "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEXIgZqRA25V+3nN+Do6b5r0jiUunub6ubjPhqwHpPxP44uUYh9RKCQNRnsqCJ9PjeTnC8R3ieJk7HWAlycU1bug==";

  @Test
  public void testGetParticipantsFromParameter() {
    final String parameterNaCl =
        "llol7wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACADVpW0zEsJQeYFUdehnPMGA9tb/CPlrEOlb1fyX3VIagAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgKo2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
    final String expectedNaClParticipant1 = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
    final String expectedNaClParticipant2 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";

    List<String> actualParticipants =
        FlexibleUtil.getParticipantsFromParameter(Bytes.fromBase64String(parameterNaCl));

    assertThat(actualParticipants).hasSize(2);
    assertThat(actualParticipants.get(0)).isEqualTo(expectedNaClParticipant1);
    assertThat(actualParticipants.get(1)).isEqualTo(expectedNaClParticipant2);

    final String parameterEC =
        "llol7wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFswWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARLycLipP8p2gChNIXeqz/jsNSwOKHJVtaJGNkCLK+qn15IOSpXVHOUz7nyg7CekVHC9Y1kz4DExXFP4y/F2yowAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABFyIGakQNuVft5zfg6Om+a9I4lLp7m+rm4z4asB6T8T+OLlGIfUSgkDUZ7KgifT43k5wvEd4niZOx1gJcnFNW7oAAAAAAA==";

    actualParticipants =
        FlexibleUtil.getParticipantsFromParameter(Bytes.fromBase64String(parameterEC));

    assertThat(actualParticipants).hasSize(2);
    assertThat(actualParticipants.get(0)).isEqualTo(EXPECTED_EC_PARTICIPANT_1);
    assertThat(actualParticipants.get(1)).isEqualTo(EXPECTED_EC_PARTICIPANT_2);

    final String parameterEC2 =
        "llol7wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABbMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAES8nC4qT/KdoAoTSF3qs/47DUsDihyVbWiRjZAiyvqp9eSDkqV1RzlM+58oOwnpFRwvWNZM+AxMVxT+MvxdsqMAAAAAAA";

    actualParticipants =
        FlexibleUtil.getParticipantsFromParameter(Bytes.fromBase64String(parameterEC2));

    assertThat(actualParticipants).hasSize(1);
    assertThat(actualParticipants.get(0)).isEqualTo(EXPECTED_EC_PARTICIPANT_1);
  }

  @Test
  public void testDecodeList() {
    final Bytes rlpEncodedOneParticipant =
        Bytes.fromBase64String(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFswWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARLycLipP8p2gChNIXeqz/jsNSwOKHJVtaJGNkCLK+qn15IOSpXVHOUz7nyg7CekVHC9Y1kz4DExXFP4y/F2yowAAAAAAA=");

    List<String> actualParticipants = FlexibleUtil.decodeList(rlpEncodedOneParticipant);

    assertThat(actualParticipants).isEqualTo(Arrays.asList(EXPECTED_EC_PARTICIPANT_1));

    final Bytes wrongBytes =
        Bytes.fromBase64String(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==");

    actualParticipants = FlexibleUtil.decodeList(wrongBytes);
    assertThat(actualParticipants).isEmpty();
  }
}
