package org.hyperledger.besu.ethereum.api.jsonrpc.parameters;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceCallManyParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class TraceCallManyParameterTest {
  static final String requestParamsJson =
      "[ [ {\n"
          + "      \"from\" : \"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73\",\n"
          + "      \"value\" : \"0x0\",\n"
          + "      \"to\" : \"0x0010000000000000000000000000000000000000\",\n"
          + "      \"gas\" : \"0xfffff2\",\n"
          + "      \"gasPrice\" : \"0xef\",\n"
          + "      \"data\" : \"0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000002\"\n"
          + "    }, [ \"trace\" ] ], [ {\n"
          + "      \"from\" : \"0x627306090abab3a6e1400e9345bc60c78a8bef57\",\n"
          + "      \"value\" : \"0x0\",\n"
          + "      \"to\" : \"0x0010000000000000000000000000000000000000\",\n"
          + "      \"gas\" : \"0xfffff2\",\n"
          + "      \"gasPrice\" : \"0xef\",\n"
          + "      \"data\" : \"0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004\"\n"
          + "    }, [ \"trace\" ] ], [ {\n"
          + "      \"from\" : \"0x627306090abab3a6e1400e9345bc60c78a8bef57\",\n"
          + "      \"value\" : \"0x0\",\n"
          + "      \"to\" : \"0x0010000000000000000000000000000000000000\",\n"
          + "      \"gas\" : \"0xfffff2\",\n"
          + "      \"gasPrice\" : \"0xef\",\n"
          + "      \"data\" : \"0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000\"\n"
          + "    }, [ \"trace\" ] ] ]";

  @Test
  public void testRequestParameterJsonParsedCorrectly() throws IOException {
    final ObjectMapper mapper = new ObjectMapper();

    final TraceCallManyParameter[] parameter =
        mapper.readValue(requestParamsJson, TraceCallManyParameter[].class);

    assertThat(parameter[0].getTuple()).isNotNull();
    assertThat(parameter[0].getTuple().getJsonCallParameter()).isNotNull();
    assertThat(parameter[0].getTuple().getJsonCallParameter().getFrom())
        .isEqualTo(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    assertThat(parameter[2].getTuple().getJsonCallParameter().getTo())
        .isEqualTo(Address.fromHexString("0x0010000000000000000000000000000000000000"));
    assertThat(parameter[1].getTuple().getTraceTypeParameter().getTraceTypes().size()).isEqualTo(1);
    assertThat(parameter[1].getTuple().getTraceTypeParameter().getTraceTypes())
        .contains(TraceTypeParameter.TraceType.TRACE);
  }
}
