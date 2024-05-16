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
package org.hyperledger.besu.evmtool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class T8nServerSubCommandTest {

  @Mock HttpServerRequest httpServerRequest;

  @Mock(answer = Answers.RETURNS_SELF)
  HttpServerResponse httpServerResponse;

  @Test
  void exceptionEncodedProperlyInJSON() {
    T8nServerSubCommand subject = new T8nServerSubCommand();
    ObjectMapper objectMapper = new ObjectMapper();

    when(httpServerRequest.response()).thenReturn(httpServerResponse);

    // Should trigger a NPE within the try block.
    subject.handleT8nRequest(httpServerRequest, objectMapper, null, null);

    ArgumentCaptor<Integer> responseCodeCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<String> responseStringCaptor = ArgumentCaptor.forClass(String.class);

    verify(httpServerResponse).setStatusCode(responseCodeCaptor.capture());
    verify(httpServerResponse).end(responseStringCaptor.capture());

    assertThat(responseCodeCaptor.getValue()).isEqualTo(500);
    assertThat(responseStringCaptor.getValue()).doesNotContain("\\t");
  }
}
