package jvmprobe249.demo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import jvmprobe249.Probe;

/** Loads named demo classes after running them through the probe transformer. */
public final class TransformedLoader extends ClassLoader {

    private final Set<String> transformedNames;

    public TransformedLoader(ClassLoader parent, Set<String> transformedNames) {
        super(parent);
        this.transformedNames = transformedNames;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> existing = findLoadedClass(name);
            if (existing != null) {
                return existing;
            }
            if (!transformedNames.contains(name)) {
                return super.loadClass(name, resolve);
            }
            String resource = name.replace('.', '/').concat(".class");
            byte[] bytes;
            try (InputStream in = getParent().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                bytes = in.readAllBytes();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
            bytes = Probe.transform(bytes, getParent());
            Class<?> defined = defineClass(name, bytes, 0, bytes.length);
            if (resolve) {
                resolveClass(defined);
            }
            return defined;
        }
    }
}
