package edu.ucsb.nceas.mdqengine.exception;

/**
 * The MetadigProcess Exception exception is thrown when a metadig engine worker
 * encounters an error during generation of a quality document. For the initial
 * exception thrown during processing, the 'causes' should be inspected.
 *
 * @author      Peter Slaughter
 * @version     %I%, %G%
 * @since       1.0
 */
public class MetadigProcessException extends MetadigException {

    private static final long serialVersionUID = 4961208968079220257L;

    public MetadigProcessException(String message) {
        super(message);
    }

    public MetadigProcessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public MetadigProcessException(String message, Throwable cause) {
        super(message, cause);
    }

    public MetadigProcessException(Throwable cause) {
        super(cause);
    }
}
