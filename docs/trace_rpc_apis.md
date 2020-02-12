# Trace RPC API nodes

This document outlines major differences for `trace_replayBlockTransactions` 
compared to other implementations.

## `stateDiff` 

No major differences have been observed in the `stateDiff` field.

## `trace`

Besu will report `gasUsed` after applying the effects of gas refunds.  Future
implementations of Besu may track gas refunds separately.

## `vmTrace`

### Returned Memory from Calls

In the `vmTrace` `ope.ex.mem` fields Besu will only report actual data returned
fron a `RETURN` opcode.  Other implementations return the contents of the 
reserved output space for the call operations.  Two major differences will be 
noted:

1. Besu will report null when a call operation ends because of a `STOP`, `HALT`,
   `REVERT`, running out of instructions, or any exceptional halts.
2. When a `RETURN` operation returns data of a different length than the space
   reserved by the call only the data passed to the `RETURN` operation will be 
   reported.  Other implementations will include pre-existing memory data or 
   trim the returned data.

### Precompiled contracts calls

Besu reports only the actual cost of the precompiled contract call in the 
`"cost"` field. 



