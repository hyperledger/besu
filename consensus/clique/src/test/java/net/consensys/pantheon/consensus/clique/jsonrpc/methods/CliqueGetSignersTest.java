package net.consensys.pantheon.consensus.clique.jsonrpc.methods;

import static java.util.Arrays.asList;
import static net.consensys.pantheon.ethereum.core.Address.fromHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockWithMetadata;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.TransactionWithMetadata;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CliqueGetSignersTest {
  private CliqueGetSigners method;
  private BlockHeader blockHeader;
  private List<Address> validators;

  @Mock private BlockchainQueries blockchainQueries;

  @Mock private BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata;

  @Before
  public void setup() {
    method = new CliqueGetSigners(blockchainQueries, new JsonRpcParameter());

    final byte[] genesisBlockExtraData =
        Hex.decode(
            "52657370656374206d7920617574686f7269746168207e452e436172746d616e42eb768f2244c8811c63729a21a3569731535f067ffc57839b00206d1ad20c69a1981b489f772031b279182d99e65703f0076e4812653aab85fca0f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    final BytesValue bufferToInject = BytesValue.wrap(genesisBlockExtraData);
    final BlockHeaderTestFixture blockHeaderTestFixture = new BlockHeaderTestFixture();
    blockHeader = blockHeaderTestFixture.extraData(bufferToInject).buildHeader();

    validators =
        asList(
            fromHexString("0x42eb768f2244c8811c63729a21a3569731535f06"),
            fromHexString("0x7ffc57839b00206d1ad20c69a1981b489f772031"),
            fromHexString("0xb279182d99e65703f0076e4812653aab85fca0f0"));
  }

  @Test
  public void returnsMethodName() {
    assertThat(method.getName()).isEqualTo("clique_getSigners");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void returnsValidatorsWhenNoParam() {
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "clique_getSigners", new Object[] {});

    when(blockchainQueries.headBlockNumber()).thenReturn(3065995L);
    when(blockchainQueries.blockByNumber(3065995L)).thenReturn(Optional.of(blockWithMetadata));
    when(blockWithMetadata.getHeader()).thenReturn(blockHeader);

    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);
    final List<Address> result = (List<Address>) response.getResult();
    assertEquals(validators, result);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void returnsValidatorsForBlockNumber() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "clique_getSigners", new Object[] {"0x2EC88B"});

    when(blockchainQueries.blockByNumber(3065995L)).thenReturn(Optional.of(blockWithMetadata));
    when(blockWithMetadata.getHeader()).thenReturn(blockHeader);

    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);
    final List<Address> result = (List<Address>) response.getResult();
    assertEquals(validators, result);
  }

  @Test
  public void failsOnInvalidBlockNumber() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "clique_getSigners", new Object[] {"0x1234"});

    when(blockchainQueries.blockByNumber(4660)).thenReturn(Optional.empty());

    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) method.response(request);
    assertThat(response.getError().name()).isEqualTo(JsonRpcError.INTERNAL_ERROR.name());
  }
}
