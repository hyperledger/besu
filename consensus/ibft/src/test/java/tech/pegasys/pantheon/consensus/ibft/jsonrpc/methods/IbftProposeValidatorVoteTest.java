package tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IbftProposeValidatorVoteTest {
  private final VoteProposer voteProposer = mock(VoteProposer.class);
  private final JsonRpcParameter jsonRpcParameter = new JsonRpcParameter();
  private final String IBFT_METHOD = "ibft_proposeValidatorVote";
  private final String JSON_RPC_VERSION = "2.0";
  private IbftProposeValidatorVote method;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup() {
    method = new IbftProposeValidatorVote(voteProposer, jsonRpcParameter);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(IBFT_METHOD);
  }

  @Test
  public void exceptionWhenNoParamsSupplied() {
    final JsonRpcRequest request = requestWithParams();

    expectedException.expect(InvalidJsonRpcParameters.class);
    expectedException.expectMessage("Missing required json rpc parameter at index 0");

    method.response(request);
  }

  @Test
  public void exceptionWhenNoAuthSupplied() {
    final JsonRpcRequest request = requestWithParams(Address.fromHexString("1"));

    expectedException.expect(InvalidJsonRpcParameters.class);
    expectedException.expectMessage("Missing required json rpc parameter at index 1");

    method.response(request);
  }

  @Test
  public void exceptionWhenNoAddressSupplied() {
    final JsonRpcRequest request = requestWithParams("true");

    expectedException.expect(InvalidJsonRpcParameters.class);
    expectedException.expectMessage("Invalid json rpc parameter at index 0");

    method.response(request);
  }

  @Test
  public void exceptionWhenInvalidBoolParameterSupplied() {
    final JsonRpcRequest request = requestWithParams(Address.fromHexString("1"), "c");

    expectedException.expect(InvalidJsonRpcParameters.class);
    expectedException.expectMessage("Invalid json rpc parameter at index 1");

    method.response(request);
  }

  @Test
  public void addValidator() {
    final Address parameterAddress = Address.fromHexString("1");
    final JsonRpcRequest request = requestWithParams(parameterAddress, "true");
    JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), true);

    JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);

    verify(voteProposer).auth(parameterAddress);
  }

  @Test
  public void removeValidator() {
    final Address parameterAddress = Address.fromHexString("1");
    final JsonRpcRequest request = requestWithParams(parameterAddress, "false");
    JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), true);

    JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);

    verify(voteProposer).drop(parameterAddress);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, IBFT_METHOD, params);
  }
}
