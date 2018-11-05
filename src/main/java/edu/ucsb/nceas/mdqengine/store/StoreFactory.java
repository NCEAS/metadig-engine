package edu.ucsb.nceas.mdqengine.store;

import edu.ucsb.nceas.mdqengine.exception.MetadigStoreException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StoreFactory {

    private static Log log = LogFactory.getLog(StoreFactory.class);

    private static MDQStore store = null;

    public static MDQStore getStore(boolean persist) throws MetadigStoreException {
        if (store == null) {
            if (persist) {
                log.debug("Creating new MDQ persistent store");
                try {
                    store = new DatabaseStore();
                } catch (MetadigStoreException e) {
                    e.printStackTrace();
                    throw(e);
                }
            } else {
                log.debug("Creating new MDQ store");
                store = new InMemoryStore();
            }
        }
        return store;
    }
}
