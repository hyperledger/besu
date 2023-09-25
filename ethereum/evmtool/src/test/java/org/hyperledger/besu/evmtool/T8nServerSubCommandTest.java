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
