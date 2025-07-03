# Technical Debt Analysis and Issue Generation

## Overview
This configuration enables Claude Code to analyze technical debt patterns in the Hyperledger Besu codebase, perform blast radius analysis, and automatically generate well-structured GitHub issues for debt reduction work.

## Core Capabilities

### 1. Brittleness Pattern Recognition
When a developer describes a brittleness pattern (e.g., "I changed X and Y broke"), Claude Code will:
- Identify the specific coupling causing the brittleness
- Map direct and transitive dependencies
- Assess blast radius using IntelliJ-compatible analysis
- Propose targeted solutions

### 2. Blast Radius Analysis
For any proposed technical debt fix, Claude Code will:
- Map direct dependencies using static analysis
- Identify hidden connections (serialization, test dependencies, configuration coupling, plugin interfaces)
- Analyze transitive dependencies through inheritance chains and utility usage
- Document full impact scope

### 3. GitHub Issue Generation
Claude Code will automatically generate issues using this template structure:

```markdown
## Problem
**Brittleness Signal:** [Original developer complaint]
**Frequency:** [Estimated based on pattern analysis]
**Investigation Time:** [Time typically spent on similar issues]

## Root Cause
**Hidden Coupling:** [Specific dependency analysis]
**Files Involved:** [Concrete file/class list]
**Why It Exists:** [Architectural reason if identifiable]

## Proposed Solution
**Approach:** [Specific refactoring technique]
**Scope:** [Exact files/classes to change]
**Success Criteria:** [Measurable outcomes]

## Implementation Plan
**Step 1:** [Bounded change - max 1 day]
**Step 2:** [Next bounded change - max 1 day]
[Continue for max 3-5 steps total]

## Safety Checks
- [ ] Can revert each step independently
- [ ] Tests exist to verify coupling is broken
- [ ] No changes to public APIs without deprecation
- [ ] Change only affects mentioned modules

## Definition of Done
- [ ] Original brittleness scenario no longer reproduces
- [ ] Tests pass with same predictability
- [ ] Code reviewer confirms coupling is broken
- [ ] No new surprise failures introduced
```

## Analysis Guidelines

### Besu-Specific Brittleness Hotspots
When analyzing patterns, pay special attention to:
- **EVM changes affecting consensus:** Gas calculations, opcode implementations
- **Storage modifications breaking P2P:** Interface changes affecting network layer
- **Plugin interface changes:** API evolution requiring widespread updates
- **Transaction/Block structure changes:** Data model modifications cascading through all layers
- **Test data updates:** Shared fixtures breaking acceptance tests

### Blast Radius Assessment Categories

**Small Blast Radius (1-3 days, individual developer):**
- Changes confined to single module
- No plugin interface modifications
- No data structure serialization changes
- Test changes isolated to feature being modified

**Medium Blast Radius (coordinate with team):**
- Changes span 2-3 modules
- Plugin interface additions (backward compatible)
- Configuration changes with defaults
- Integration test updates required

**Large Blast Radius (careful planning required):**
- Cross-module data structure changes
- Plugin interface breaking changes
- Consensus or EVM modifications
- Changes affecting network protocol or storage format

### IntelliJ Analysis Integration
When performing dependency analysis, utilize these IntelliJ features:
- **Dependencies Graph:** Right-click → "Analyze" → "Dependencies"
- **Call Hierarchy:** Ctrl+Alt+H for caller analysis
- **Usage Search:** Alt+F7 with inheritance chain inclusion
- **Dependency Structure Matrix:** Module-level coupling visualization
- **Structural Search:** Complex pattern matching for coupling detection
- **Method Hierarchy:** Ctrl+H for inheritance impact analysis

## Issue Sizing and Decomposition

### Automatically Break Down Large Issues
If blast radius analysis indicates large scope, automatically decompose into smaller issues:

**Example Decomposition:**
```
Large Pattern: "EVM changes break consensus tests"
Auto-generated Issues:
1. Extract GasCalculator interface from consensus code
2. Update EVM to use GasCalculator interface  
3. Add integration tests for gas/consensus boundary
4. Remove direct EVM dependencies from consensus tests
```

### Prioritization Signals
Assign priority labels based on:
- **P0 (fix-this-week):** Multiple modules affected, high-frequency pattern
- **P1 (fix-this-release):** Clear pattern, medium frequency
- **P2 (next-release):** Single occurrence, high impact
- **P3 (backlog):** Low frequency, low impact

## Usage Instructions

### For Pattern Analysis
1. Describe the brittleness pattern: "When I change [specific thing], [unexpected things] break"
2. Provide concrete examples: file names, test failures, modules affected
3. Claude Code will perform blast radius analysis and generate issues

### For Existing Code Analysis  
1. Point to specific classes/modules showing brittleness
2. Claude Code will analyze coupling patterns and dependencies
3. Receives recommendations for decoupling approaches

### For Issue Generation
1. Provide brittleness description and affected code areas
2. Claude Code generates properly sized, actionable GitHub issues
3. Issues include implementation plans, safety checks, and success criteria

## Output Format

### Analysis Report
```markdown
# Technical Debt Analysis: [Pattern Name]

## Brittleness Pattern Identified
[Description of the coupling causing surprises]

## Blast Radius Assessment
- **Direct Dependencies:** [X files, Y classes]
- **Hidden Connections:** [Serialization, tests, config, plugins]
- **Transitive Impact:** [Inheritance chains, utility usage]
- **Risk Level:** [Small/Medium/Large]

## Recommended Approach
[Specific refactoring strategy with rationale]

## Generated Issues
[List of auto-generated GitHub issues with links]
```

### Issue Labels
Automatically apply these labels:
- `techdebt` (primary label)
- `blast-radius-small|medium|large` (scope indicator)  
- Module-specific labels (`ethereum-core`, `consensus`, `evm`, etc.)
- Priority labels (`P0`, `P1`, `P2`, `P3`)

## Safety and Quality Assurance

### Scope Validation
- Ensure each issue is bounded to 1-3 days of work
- Verify revert plans exist for each implementation step
- Confirm no breaking API changes without deprecation path

### Testing Strategy
- Include verification steps for coupling breakage
- Ensure tests exist to prevent regression
- Validate that changes only affect intended modules

### Review Integration
- Generate PR templates with review focus areas
- Include links back to original brittleness pattern
- Specify success criteria for code review validation