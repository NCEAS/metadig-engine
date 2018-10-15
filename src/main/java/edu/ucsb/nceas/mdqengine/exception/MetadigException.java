package edu.ucsb.nceas.mdqengine.exception;


/**
 * The MetadigException class is a general base class execption from which all
 * metadig engine exceptions are extended.
 *
 * @author      Peter Slaughter
 * @version     %I%, %G%
 * @since       1.0
 */
public class MetadigException extends Exception {


    private static final long serialVersionUID = -5846707794495529056L;

    public MetadigException(String message) {
        super(message);
    }

    public MetadigException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public MetadigException(String message, Throwable cause) {
        super(message, cause);
    }

    public MetadigException(Throwable cause) {
        super(cause);
    }
}
