package net.consensys.pantheon.consensus.ibft.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class IbftSubProtocolTest {

  @Test
  public void messageSpaceReportsCorrectly() {
    final IbftSubProtocol subProt = new IbftSubProtocol();

    assertThat(subProt.messageSpace(1)).isEqualTo(4);
  }

  @Test
  public void allIbftMessageTypesAreRecognisedAsValidByTheSubProtocol() {
    final IbftSubProtocol subProt = new IbftSubProtocol();

    assertThat(subProt.isValidMessageCode(1, 0)).isTrue();
    assertThat(subProt.isValidMessageCode(1, 1)).isTrue();
    assertThat(subProt.isValidMessageCode(1, 2)).isTrue();
    assertThat(subProt.isValidMessageCode(1, 3)).isTrue();
  }

  @Test
  public void invalidMessageTypesAreNotAcceptedByTheSubprotocol() {
    final IbftSubProtocol subProt = new IbftSubProtocol();

    assertThat(subProt.isValidMessageCode(1, 4)).isFalse();
  }
}
