package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StoreFactory {

    private static Log log = LogFactory.getLog(StoreFactory.class);

    public static MDQStore getStore(boolean persist) throws MetadigStoreException {
        MDQStore store = null;
        if (persist) {
            log.trace("Creating new MDQ persistent store");
            try {
                store = new DatabaseStore();
            } catch (MetadigStoreException e) {
                e.printStackTrace();
                throw(e);
            }
        } else {
            log.trace("Creating new MDQ store");
            try {
                store = new InMemoryStore();
            } catch (MetadigStoreException mse) {
                throw new MetadigStoreException(mse.getMessage());
            }
        }
        return store;
    }
}
