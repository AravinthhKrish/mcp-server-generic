# k6 regression execution result

Timestamp (UTC): 2026-03-08T07:10:23Z

## Commands executed

```bash
export JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2
export PATH=$JAVA_HOME/bin:$PATH
gradle bootRun
./perf/run-k6-regression.sh
```

## Outcome

1. Application startup failed during Gradle configuration because plugin `org.jetbrains.kotlin.jvm` version `2.3.10` could not be resolved from configured repositories.
2. k6 execution failed because `k6` is not installed in this environment (`command not found`).

## Raw output excerpts

### `gradle bootRun`

```text
FAILURE: Build failed with an exception.

* Where:
Build file '/workspace/mcp-server-generic/build.gradle.kts' line: 1

* What went wrong:
Plugin [id: 'org.jetbrains.kotlin.jvm', version: '2.3.10'] was not found in any of the following sources:
- Gradle Core Plugins
- Included Builds
- Plugin Repositories (Gradle Central Plugin Repository)
```

### `./perf/run-k6-regression.sh`

```text
# k6 regression execution
ERROR: k6 is not installed or not in PATH
```
