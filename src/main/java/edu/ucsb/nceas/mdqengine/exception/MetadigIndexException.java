package edu.ucsb.nceas.mdqengine.exception;

/**
 * The MetadigIndexException exception is thrown when a metadig engine worker
 * encounter an error indexing a quality document, for example, into a
 * Solr index. For the initial exception thrown during indexing, the 'causes'
 * should be inspected.
 *
 * @author      Peter Slaughter
 * @version     %I%, %G%
 * @since       1.0
 */
public class MetadigIndexException extends MetadigException {

    private static final long serialVersionUID = 8193967571022563612L;

    public MetadigIndexException(String message) {
        super(message);
    }

    public MetadigIndexException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public MetadigIndexException(String message, Throwable cause) {
        super(message, cause);
    }

    public MetadigIndexException(Throwable cause) {
        super(cause);
    }
}
