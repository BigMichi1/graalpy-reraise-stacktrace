# GraalPy: a guest exception is lost through a `try/finally` or `try/except … raise` frame

A reproducer for a GraalPy defect. A Python exception passes through a Python function that has a
`try`/`finally`, or a `try`/`except` that re-raises, on its way to Java. When it does, the embedder
does not get the exception it expects:

- **With Java assertions on (`-ea`), the exception itself is lost.** `Value.execute` throws
  `java.lang.IllegalArgumentException: Bytecode index out of range <n>` instead of a
  `PolyglotException` carrying the guest error.
- **Without assertions, the stack trace is wrong.** The frame of the `exec`'d code is missing, and the
  handler frame has no source location, so the failing line of user code cannot be reported.

`ISSUE.md` is the full report, filed upstream as [oracle/graalpython#1186](https://github.com/oracle/graalpython/issues/1186).

## Run

JDK 25 (`mise install` pins Temurin 25.0.4); the Gradle wrapper brings everything else.

```sh
./gradlew -q run -Pea                          # IllegalArgumentException in 4 of 11 cases
./gradlew -q run                               # no exception without assertions
./gradlew -q run -Pmain=StackTraceRepro        # missing frame, "Unknown" location
./gradlew -q run -Pea -Pgraalpy=25.0.4         # last release without the crash
```

## Results

`ReraiseRepro` with `-ea`, on Temurin 25.0.4, Linux x86_64, interpreter-only runtime:

| GraalPy | Result |
|---|---|
| 25.4.4.1.1, 25.3.4.1, 25.2.4, 25.1.3 | `IllegalArgumentException: Bytecode index out of range` for `NameError` and `raise` through `try/finally` and `try/except … raise`; `1 / 0` is not affected |
| 25.0.4, 25.0.0, 24.2.2, 24.1.2, 24.0.2 | no crash, but the stack-trace locations are wrong (`StackTraceRepro`) |
| 23.1.x | does not start on JDK 25 |

`StackTraceRepro` without `-ea`, guest frames of `PolyglotException.getPolyglotStackTrace()`:

```
25.4.4.1.1
plain:
   <python> <module>(<string>:2:6-19)
   <python> plain(support.py:7:116-129)
with_except:
   <python> with_except(Unknown)

25.0.4
plain:
   <python> <module>(Unknown)
   <python> plain(Unknown)
with_except:
   <python> with_except(<string>:1-2:0-19)
```

## Workaround

Keep exception handlers out of the Python frame that executes user code. Do cleanup, such as
flushing `sys.stdout`, in a separate call from the host after the user code returns or fails.
