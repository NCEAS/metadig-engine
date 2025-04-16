package edu.ucsb.nceas.mdqengine.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsb.nceas.mdqengine.serialize.XmlMarshaller;
import jep.JepConfig;
import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Selector;
import edu.ucsb.nceas.mdqengine.model.Expression;
import net.thisptr.jackson.jq.exception.JsonQueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class JSONDialectTest {

    private JSONDialect dialect;
    private JsonNode jsonDoc;
    private Check check;

    @BeforeEach
    public void setUp() throws Exception {

        InputStream jsonStream = getClass().getClassLoader().getResourceAsStream("test-docs/schema-dot-org-ex.json");
        assertNotNull(jsonStream, "Test JSON file not found in test-docs directory");

        ObjectMapper mapper = new ObjectMapper();
        this.jsonDoc = mapper.readTree(jsonStream);
        this.dialect = new JSONDialect(getClass().getClassLoader().getResourceAsStream("test-docs/schema-dot-org-ex.json"));
    }

    @Test
    public void testSelectJsonPath_singleValue() throws Exception {
        Object result = dialect.selectJsonPath("(.name | length) > 0", jsonDoc);
        assertEquals("Virginia Forest - DTW, Water Temperature, Specific conductance - Jun 2021-Jul 2024", result);
    }

    @Test
    public void testSelectJsonPath_arrayTextValues() throws Exception {
        Object result = dialect.selectJsonPath(".creator[\"@list\"][] | .name", jsonDoc);
        assertTrue(result instanceof java.util.List);
        assertEquals(4, ((java.util.List<?>) result).size());
        assertEquals("Dannielle Pratt", ((java.util.List<?>) result).get(0));
    }

    @Test
    public void testRunCheck_simpleSelector() throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-docs/resource.abstractLength.xml");
        if (inputStream == null) {
            throw new IOException("XML file not found");
        }
        String xml = new String(inputStream.readAllBytes(), "UTF-8");
        check = (Check) XmlMarshaller.fromXml(xml, Check.class);

        assertNotNull(check, "Check object should be deserialized successfully from XML");

        List<Selector> selector = new ArrayList<>(check.getSelector());
        for (Selector sel : selector) {
            Expression ex = sel.getExpression();
            if (ex != null){
                Object result = dialect.selectJsonPath(ex.getValue(), jsonDoc);
                assertNotNull(result, "Selector should select something");
            }

        }
    }
}
