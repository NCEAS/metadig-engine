package edu.ucsb.nceas.mdqengine.exception;

public class MetadigEntryNotFound extends MetadigException {

    private static final long serialVersionUID = -8029761486983526125L;


    public MetadigEntryNotFound(String message) {
        super(message);
    }

    public MetadigEntryNotFound(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public MetadigEntryNotFound(String message, Throwable cause) {
        super(message, cause);
    }

    public MetadigEntryNotFound(Throwable cause) {
        super(cause);
    }
}
