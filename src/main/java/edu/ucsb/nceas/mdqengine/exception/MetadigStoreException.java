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
public class MetadigStoreException extends MetadigException {
    private static final long serialVersionUID = 7945446831458809935L;

    public MetadigStoreException(String message) {
        super(message);
    }

    public MetadigStoreException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public MetadigStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public MetadigStoreException(Throwable cause) {
        super(cause);
    }
}

