import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

public class StackTraceRepro {
    public static void main(String[] args) throws Exception {
        String support = String.join("\n",
                "def with_except(code):",          // 1
                "    try:",                         // 2
                "        exec(code, {})",           // 3
                "    except BaseException:",        // 4
                "        raise",                    // 5
                "def plain(code):",                 // 6
                "    exec(code, {})");              // 7
        try (Context context = Context.create("python")) {
            context.eval(Source.newBuilder("python", support, "support.py").build());
            for (String fn : new String[] {"plain", "with_except"}) {
                try {
                    context.getBindings("python").getMember(fn).execute("x = 1\nundefined_name");
                } catch (PolyglotException e) {
                    System.out.println(fn + ":");
                    for (PolyglotException.StackFrame f : e.getPolyglotStackTrace()) {
                        if (f.isGuestFrame()) System.out.println("   " + f);
                    }
                }
            }
        }
    }
}
