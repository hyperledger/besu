# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Communication Style & Behavior Guidelines

### General Approach
- Be direct and concise in responses
- Focus on technical accuracy over politeness
- Don't be sycophantic or overly agreeable
- Challenge assumptions when appropriate
- Ask questions to confirm assumptions being made
- Provide honest assessments of code quality and architectural decisions

### Code Review Style
- Point out potential issues directly
- Suggest concrete improvements
- Don't hedge criticism with excessive qualifiers
- Focus on maintainability, performance, readability, and correctness


## Architecture Overview

### Core Application Structure
- **Main Entry Point**: `app/src/main/java/org/hyperledger/besu/Besu.java` - Bootstrap class
- **CLI Interface**: `app/src/main/java/org/hyperledger/besu/cli/BesuCommand.java` - Main command line interface using PicoCLI
- **Dependency Injection**: Uses Dagger for dependency injection with `DaggerBesuComponent`

### Major Modules

#### Ethereum Core (`ethereum/`)
- **`ethereum/core`**: Core blockchain functionality (blocks, transactions, world state, chain management)
- **`ethereum/api`**: JSON-RPC, GraphQL, and REST API implementations
- **`ethereum/p2p`**: Peer-to-peer networking and node discovery
- **`ethereum/eth`**: Ethereum protocol implementation and transaction pool
- **`ethereum/evm`**: Ethereum Virtual Machine implementation

#### Consensus Implementations (`consensus/`)
- **`consensus/clique`**: Proof of Authority (Clique) consensus
- **`consensus/ibft`** & **`consensus/qbft`**: Byzantine Fault Tolerant consensus
- **`consensus/merge`**: Post-merge (Proof of Stake) support
- **`consensus/common`**: Shared consensus utilities

#### Configuration & Services
- **`config/`**: Network configurations (mainnet, sepolia, holesky, etc.) and profiles
- **`plugin-api/`**: Plugin system for extending Besu functionality
- **`services/`**: Core services (key-value store, pipeline, task management)

### Test Structure
- **Unit Tests**: Located in `src/test/java` across all modules
- **Integration Tests**: Located in `src/integration-test/java` 
- **Acceptance Tests**: Full end-to-end tests in `acceptance-tests/`
- **Reference Tests**: Ethereum protocol compliance tests in `ethereum/referencetests/`
- **Performance Tests**: JMH benchmarks in `src/jmh/java`

## Development Guidelines

### Besu Practices
- **Besu changes often** make sure you pull `main` regularly
- **VERY IMPORTANT: DCO Signoffs are required** all commits require `--signoff` option
- **VERY IMPORTANT: Claude as Co-Author** all prs or issues created must attribute co-authorship to Claude
- **GitHub Issues** if working on an issue, assign it to yourself to prevent duplicate work

### Code Standards
- **Java 21** required
- **Google Java Format** enforced via Spotless
- **Error Prone** static analysis
- **License headers** required on all files
- **Comprehensive JavaDoc** required for public APIs

### Testing Requirements
- Unit tests for all new functionality
- Integration tests for component interactions
- Reference tests pass for protocol changes

### Module Dependencies
- Modules should have clear, minimal dependencies
- Core modules should not depend on higher-level modules
- Use dependency injection patterns
- Prefer interfaces over concrete implementations

### Key Configuration Files
- **`build.gradle`**: Main build configuration with multi-module setup
- **`gradle.properties`**: Build properties and version information
- **Network configs**: `config/src/main/resources/*.json` for different networks
- **Profiles**: `config/src/main/resources/profiles/*.toml` for different use cases

## Build Commands

### Core Build Commands
```bash
# Build the project
./gradlew build

# Clean and build
./gradlew clean build

# Build distributions (tar.gz, zip)
./gradlew distTar distZip
```

### Code Quality
```bash
# Format code
./gradlew spotlessApply

# Check formatting
./gradlew spotlessCheck

# Run license checks
./gradlew checkLicense
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :ethereum:core:test

# Run integration tests
./gradlew integrationTest

# Run acceptance tests
./gradlew :acceptance-tests:tests:test

# Run reference tests (Ethereum protocol compliance)
./gradlew :ethereum:referencetests:referenceTests

# Run with specific test
./gradlew test -Dtest.single=ClassName

# Run Ethereum reference tests with specific include pattern
./gradlew :ethereum:referencetests:referenceTests -Dtest.ethereum.include=pattern

```

### Development Tools
```bash
# Run EVM tool
./gradlew :ethereum:evmtool:run
```


## Technical Debt Analysis and Issue Generation

## Overview
This configuration enables Claude Code to analyze technical debt patterns in the Hyperledger Besu codebase, perform blast radius analysis, and automatically generate well-structured GitHub issues for debt reduction work.

## Core Capabilities

### 1. Brittleness Pattern Recognition
When a developer describes a techdebt, coupling, or brittleness pattern (e.g., "I changed X and Y broke"), Claude Code will:
- Identify the specific coupling causing the brittleness
- Map direct and transitive dependencies
- Assess blast radius using IntelliJ-compatible analysis
- Propose targeted solutions

### 2. Blast Radius Analysis
For any proposed technical debt fix, Claude Code will:
- Map direct dependencies using static analysis
- Identify hidden connections (serialization, test dependencies, configuration coupling, plugin interfaces)
- Analyze transitive dependencies through inheritance chains and utility usage
- **Distinguish legitimate vs unnecessary coupling patterns** (see Coupling Analysis Criteria)
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

## Coupling Analysis Criteria

### Legitimate Coupling (NOT Technical Debt)
**Type-Safe Strategy Patterns:**
- Controlled casting through strategy pattern (e.g., `getStrategy(ConcreteClass.class)`)
- Components requiring specific API levels in inheritance hierarchy
- Constructor requirements for methods only available on concrete types

**API Contract Requirements:**
- Components needing specific functionality not available on base interface
- Legitimate architectural patterns where casting serves a purpose
- Type-based routing for different implementation strategies

**Example of Legitimate Casting:**
```java
// Type-safe strategy selection
final BonsaiWorldStateKeyValueStorage storage =
    coordinator.getStrategy(BonsaiWorldStateKeyValueStorage.class);

// Component requires specific API level
new TrieLogPruner((BonsaiWorldStateKeyValueStorage) storage, ...)
```

### Unnecessary Coupling (IS Technical Debt)
**Single Implementation Abstractions:**
- Abstract classes with only one concrete implementation
- Interfaces created for anticipated polymorphism that never materialized
- YAGNI violations where abstraction adds complexity without benefit

**Arbitrary Downcasting:**
- Casting without type safety mechanisms
- Downcasting that could be eliminated by API design
- Pattern where all usage goes through concrete type anyway

**Over-Abstraction Signals:**
- All dependency injection uses concrete type, not abstract
- Test mocking targets concrete implementation, not interface
- No actual polymorphic usage found in codebase
- Abstraction provides no extension points actually used

### Analysis Questions to Ask:
1. **Does the abstraction enable actual polymorphism?** If not, it's likely unnecessary
2. **Is the casting type-safe and controlled?** Legitimate if yes, debt if arbitrary
3. **Do components have legitimate API requirements?** Valid if they need specific methods
4. **Would consolidation break any actual extension points?** Only debt if no extensions exist
5. **Is there evidence of anticipated features that never materialized?** Strong debt signal