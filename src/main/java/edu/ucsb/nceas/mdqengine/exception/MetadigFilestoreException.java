
package edu.ucsb.nceas.mdqengine.exception;

/**
 * The MetadigFilestoreException exception is thrown when metadig engine
 * encounters an error saving or retrieving a file entry from the filestore
 * For the initial exception thrown during processing, the 'causes' should be inspected.
 *
 * @author      Peter Slaughter
 */

public class MetadigFilestoreException extends MetadigException {

    private static final long serialVersionUID = -878814123317889182L;

    public MetadigFilestoreException(String message) {
        super(message);
    }

    public MetadigFilestoreException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public MetadigFilestoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public MetadigFilestoreException(Throwable cause) {
        super(cause);
    }
}
