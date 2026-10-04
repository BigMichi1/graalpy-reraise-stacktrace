import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * Minimal reproducer: a Python exception propagating to Java through a Python frame.
 * Run with and without -ea.
 */
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

            def direct_raise():
                raise ValueError('boom')

            def reraise_direct():
                try:
                    raise ValueError('boom')
                except ValueError:
                    raise
            """;

    public static void main(String[] args) {
        boolean ea = false;
        assert ea = true;
        try (Context context = Context.create("python")) {
            System.out.println("GraalPy " + context.getEngine().getVersion() + ", java assertions: " + ea);
            context.eval("python", SUPPORT);
            Value bindings = context.getBindings("python");
            int bugs = 0;
            for (String fn : new String[] {"plain", "with_finally", "with_except"}) {
                for (String code : new String[] {"undefined_name", "raise ValueError('boom')", "1 / 0"}) {
                    bugs += call(bindings.getMember(fn), fn + "(" + code + ")", code);
                }
            }
            bugs += call(bindings.getMember("direct_raise"), "direct_raise()", null);
            bugs += call(bindings.getMember("reraise_direct"), "reraise_direct()", null);
            System.out.println(bugs == 0 ? "RESULT: OK" : "RESULT: BUG in " + bugs + " case(s)");
        }
    }

    static int call(Value fn, String label, String code) {
        try {
            if (code == null) fn.execute(); else fn.execute(code);
            System.out.printf("  %-45s no exception?!%n", label);
            return 1;
        } catch (PolyglotException e) {
            System.out.printf("  %-45s ok   %s%n", label, e.getMessage());
            return 0;
        } catch (RuntimeException e) {
            System.out.printf("  %-45s BUG  %s%n", label, e);
            return 1;
        }
    }
}
