package jvmprobe249.transform;

/** Thrown when a class cannot be parsed or is of an unsupported kind. */
public class ClassTransformException extends IllegalArgumentException {

    public ClassTransformException(String message) {
        super(message);
    }

    public ClassTransformException(String message, Throwable cause) {
        super(message, cause);
    }
}
