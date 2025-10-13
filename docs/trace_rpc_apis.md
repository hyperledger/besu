# Trace RPC API Notes

This document outlines major differences for `trace_replayBlockTransactions` 
compared to other implementations.

## `stateDiff` 

No major differences were observed in the `stateDiff` field.

## `trace`

Besu reports `gasUsed` after applying the effects of gas refunds.  Future
implementations of Besu might track gas refunds separately.

## `vmTrace`

### Returned Memory from Calls

In the `vmTrace` `op.ex.mem` fields Besu only reports actual data returned
from a `RETURN` opcode.  Other implementations return the contents of the 
reserved output space for the call operations.  Note two major differences:

1. Besu reports `null` when a call operation ends because of a `STOP`,  `HALT`, 
   `REVERT`, running out of instructions, or any exceptional halts.
2. When a `RETURN` operation returns data of a different length than the space
   reserved by the call only the data passed to the `RETURN` operation is 
   reported.  Other implementations will include pre-existing memory data or 
   trim the returned data.

### Precompiled Contracts Calls

Besu reports only the actual cost of the precompiled contract call in the 
`cost` field. 

### Out of Gas 

Besu reports the operation that causes out of gas exceptions, including 
calculated gas cost.  The operation is not executed so no `ex` values are 
reported.
