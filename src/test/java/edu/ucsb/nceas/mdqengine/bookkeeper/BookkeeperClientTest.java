package edu.ucsb.nceas.mdqengine.bookkeeper;

import edu.ucsb.nceas.mdqengine.authorization.BookkeeperClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.bookkeeper.api.Usage;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

public class BookkeeperClientTest {
    private String instanceId = "urn:uuid3b6827b9-4641-40c5-bae8-ccb23159b300";
    protected Log log = LogFactory.getLog(this.getClass());

    @Test
    @Ignore
    public void testGetUsage() {
        log.debug("Checking bookkeeper portal Usage for collection: " + instanceId);
        String msg = null;
        try {
            BookkeeperClient bkClient = BookkeeperClient.getInstance();
            List<Usage> usages = null;
            List<String> subjects = new ArrayList<>();
            usages = bkClient.listUsages(0, instanceId, "portal", null, subjects);
            assert(usages.get(0).getStatus().compareToIgnoreCase("active") == 0);
        } catch (Exception e) {
            msg = "Bookkeeper client test failed: " + e.getMessage();
            fail(msg);
        }
    }
}
