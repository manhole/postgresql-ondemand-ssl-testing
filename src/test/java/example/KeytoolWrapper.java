package example;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/*

https://docs.oracle.com/javase/jp/8/docs/technotes/tools/windows/keytool.html
 */
public class KeytoolWrapper {

    private final Method _mainMethod;

    /*
       keytoolのMainクラスはrt.jarに含まれている。
       final String javaHome = System.getProperty("java.home");
       System.out.println("javaHome = " + javaHome);
       final Path rtJar = Paths.get(javaHome).resolve("lib").resolve("rt.jar");
       System.out.println("rtJar = " + rtJar);
       assertThat(exists(rtJar), is(true));

       final URLClassLoader classLoader = new URLClassLoader(new URL[] { rtJar.toUri().toURL() });
       final Class<?> keytoolMain = Class.forName("sun.security.tools.keytool.Main", true, classLoader);
     */

    // import しようとすると "パッケージsun.security.tools.keytoolは存在しません" というエラーになるため、リフレクションで触る。
    public KeytoolWrapper() throws ClassNotFoundException, NoSuchMethodException {
        final Class<?> keytoolMain = Class.forName("sun.security.tools.keytool.Main");
        _mainMethod = keytoolMain.getMethod("main", String[].class);
    }

    public void execute(final String... args) throws InvocationTargetException {
        try {
            _mainMethod.invoke(null, (Object) args);
        } catch (final IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
