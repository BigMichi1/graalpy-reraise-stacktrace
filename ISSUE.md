### Describe the bug

A Python exception raised in code run by `exec()` reaches Java through a Python function with a
`try`/`finally` or `try`/`except … raise`. When the embedding JVM runs with `-ea`, Java receives
`java.lang.IllegalArgumentException: Bytecode index out of range <n>` instead of a
`PolyglotException` for the guest error. The original error is lost.

Without `-ea`, the same code produces the expected `PolyglotException`. Test suites usually run with
`-ea`; JUnit through Gradle and Maven Surefire both enable it.

Without `-ea` nothing is thrown, but the stack trace is wrong (see Additional context), so the line of the failing user code is lost in both cases.

### Operating system

Linux (x86_64, WSL2)

### CPU architecture

x86_64

### GraalPy version

| GraalPy (`python-community` / `polyglot`) | Result with `-ea` |
|---|---|
| 25.4.4.1.1 | BUG (index 114 / 168) |
| 25.3.4.1 | BUG (114 / 168) |
| 25.2.4 | BUG (116 / 170) |
| 25.1.3 | BUG (122 / 176) |
| 25.0.4, 25.0.0 | ok |
| 24.2.2, 24.1.2, 24.0.2 | ok |

- **JDK:** Temurin 25.0.4+7, Linux x86_64 (WSL2), interpreter-only runtime (stock JDK).
- **Regression range:** first seen in 25.1, which is presumably where the Bytecode DSL interpreter
  became the only one. `python.EnableBytecodeDSLInterpreter` no longer exists in 25.4.

### JDK version

Temurin 25.0.4+7 (stock OpenJDK, interpreter-only runtime)

### Context configuration

`Context.create("python")`. The issue also shows with a shared `Engine`, `HostAccess.ALL` and `IOAccess.NONE`.

### Steps to reproduce

Standalone reproducer, Gradle wrapper included: https://github.com/BigMichi1/graalpy-reraise-stacktrace
(`./gradlew -q run -Pea`; the README lists the other modes and the version sweep).

Run this Java program with `-ea` on a stock JDK 25 (Temurin 25.0.4), with
`org.graalvm.polyglot:polyglot` and `org.graalvm.polyglot:python-community`:

```java
import org.graalvm.polyglot.*;

public class ReraiseRepro {
    static final String SUPPORT = """
            def plain(code):
                exec(code, {})

            def with_finally(code):
                try:
                    exec(code, {})
                finally:
                    pass

            def with_except(code):
                try:
                    exec(code, {})
                except BaseException:
                    raise
            """;

    public static void main(String[] args) {
        try (Context context = Context.create("python")) {
            context.eval("python", SUPPORT);
            Value bindings = context.getBindings("python");
            for (String fn : new String[] {"plain", "with_finally", "with_except"}) {
                for (String code : new String[] {"undefined_name", "raise ValueError('boom')", "1 / 0"}) {
                    try {
                        bindings.getMember(fn).execute(code);
                    } catch (PolyglotException e) {
                        System.out.printf("%s(%s): ok  %s%n", fn, code, e.getMessage());
                    } catch (RuntimeException e) {
                        System.out.printf("%s(%s): BUG %s%n", fn, code, e);
                    }
                }
            }
        }
    }
}
```

Output with `-ea` on 25.4.4.1.1:

```
plain(undefined_name): ok  NameError: name 'undefined_name' is not defined
plain(raise ValueError('boom')): ok  ValueError: boom
plain(1 / 0): ok  ZeroDivisionError: division by zero
with_finally(undefined_name): BUG java.lang.IllegalArgumentException: Bytecode index out of range 114
with_finally(raise ValueError('boom')): BUG java.lang.IllegalArgumentException: Bytecode index out of range 114
with_finally(1 / 0): ok  ZeroDivisionError: division by zero
with_except(undefined_name): BUG java.lang.IllegalArgumentException: Bytecode index out of range 168
with_except(raise ValueError('boom')): BUG java.lang.IllegalArgumentException: Bytecode index out of range 168
with_except(1 / 0): ok  ZeroDivisionError: division by zero
```

`1 / 0` is not affected. `NameError` and an explicit `raise` are.

#### Steps

1. Build the program above against `polyglot` and `python-community` 25.4.4.1.1.
2. Run it with `java -ea --enable-native-access=ALL-UNNAMED ReraiseRepro`.
3. Run it again without `-ea`: every case prints `ok`.

### Expected behavior

The exception is a `PolyglotException` carrying the guest `NameError` or `ValueError`, with or
without `-ea`.

### Stack trace

```
java.lang.IllegalArgumentException: Bytecode index out of range 168
    at com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen$AbstractBytecodeNode.validateBytecodeIndex(PBytecodeDSLRootNodeGen.java:6926)
    at com.oracle.truffle.api.bytecode.BytecodeNode.getBytecodeLocation(BytecodeNode.java:151)
    at com.oracle.truffle.api.bytecode.BytecodeLocation.get(BytecodeLocation.java:326)
    at com.oracle.truffle.api.bytecode.BytecodeLocation.get(BytecodeLocation.java:350)
    at com.oracle.truffle.api.bytecode.DefaultBytecodeStackTraceElement.getSourceSectionImpl(DefaultBytecodeStackTraceElement.java:105)
    at com.oracle.truffle.api.bytecode.DefaultBytecodeStackTraceElement.hasSourceLocation(DefaultBytecodeStackTraceElement.java:88)
    at com.oracle.truffle.api.bytecode.DefaultBytecodeStackTraceElementGen$InteropLibraryExports$Uncached.hasSourceLocation(DefaultBytecodeStackTraceElementGen.java:188)
    at com.oracle.truffle.api.interop.InteropLibrary$Asserts.hasSourceLocation(InteropLibrary.java:5246)
    at com.oracle.truffle.api.interop.InteropLibraryGen$UncachedDispatch.hasSourceLocation(InteropLibraryGen.java:8163)
    at com.oracle.truffle.polyglot.PolyglotExceptionFrame.create(PolyglotExceptionFrame.java:235)
    at com.oracle.truffle.polyglot.PolyglotExceptionImpl$FrameGuestObjectIterator.fetchNext(PolyglotExceptionImpl.java:645)
    ...
    at org.graalvm.polyglot.PolyglotException.<init>(PolyglotException.java:122)
    at org.graalvm.polyglot.Engine$APIAccessImpl.newLanguageException(Engine.java:1345)
    at com.oracle.truffle.polyglot.PolyglotImpl.guestToHostException(PolyglotImpl.java:1240)
    at com.oracle.truffle.polyglot.HostToGuestRootNode.handleException(HostToGuestRootNode.java:148)
    at org.graalvm.polyglot.Value.execute(Value.java:1100)
```

### Additional context

- The failure happens while the polyglot exception's stack trace is materialized. A bytecode
  stack-trace element of the frame with the handler carries a bytecode index outside its
  `BytecodeNode`.
- **Without `-ea` the stack trace is silently wrong.** For a source named `support.py` with
  `with_except` on lines 1-5 and `plain` on lines 6-7, executing `"x = 1\nundefined_name"` gives
  these guest frames from `PolyglotException.getPolyglotStackTrace()`:

  ```
  plain:
     <python> <module>(<string>:2:6-19)
     <python> plain(support.py:7:116-129)
  with_except:
     <python> with_except(Unknown)
  ```

  Through the handler frame, the frame of the `exec`'d code (`<module>`, line 2) is missing, and
  the handler frame itself has no location. An embedder therefore cannot report the failing line
  of a user script.
- Workaround in our JSR-223 engine: no exception handler in the Python frame that `exec()`s user
  code; cleanup runs as a separate call from Java.
