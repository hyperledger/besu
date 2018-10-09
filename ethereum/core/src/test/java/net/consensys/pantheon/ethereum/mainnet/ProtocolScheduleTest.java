package net.consensys.pantheon.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ProtocolScheduleTest {

  @SuppressWarnings("unchecked")
  @Test
  public void getByBlockNumber() {
    final ProtocolSpec<Void> spec1 = mock(ProtocolSpec.class);
    final ProtocolSpec<Void> spec2 = mock(ProtocolSpec.class);
    final ProtocolSpec<Void> spec3 = mock(ProtocolSpec.class);
    final ProtocolSpec<Void> spec4 = mock(ProtocolSpec.class);

    final MutableProtocolSchedule<Void> schedule = new MutableProtocolSchedule<>();
    schedule.putMilestone(20, spec3);
    schedule.putMilestone(0, spec1);
    schedule.putMilestone(30, spec4);
    schedule.putMilestone(10, spec2);

    assertThat(schedule.getByBlockNumber(0)).isEqualTo(spec1);
    assertThat(schedule.getByBlockNumber(15)).isEqualTo(spec2);
    assertThat(schedule.getByBlockNumber(35)).isEqualTo(spec4);
    assertThat(schedule.getByBlockNumber(105)).isEqualTo(spec4);
  }

  @Test
  public void emptySchedule() {
    Assertions.assertThatThrownBy(() -> new MutableProtocolSchedule<>().getByBlockNumber(0))
        .hasMessage("At least 1 milestone must be provided to the protocol schedule");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void conflictingSchedules() {
    final ProtocolSpec<Void> spec1 = mock(ProtocolSpec.class);
    final ProtocolSpec<Void> spec2 = mock(ProtocolSpec.class);

    final MutableProtocolSchedule<Void> protocolSchedule = new MutableProtocolSchedule<>();
    protocolSchedule.putMilestone(0, spec1);
    protocolSchedule.putMilestone(0, spec2);
    assertThat(protocolSchedule.getByBlockNumber(0)).isSameAs(spec2);
  }
}
