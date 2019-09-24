
package edu.ucsb.nceas.mdqengine.solr;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.cn.indexer.XmlDocumentUtility;
import org.dataone.cn.indexer.parser.IDocumentSubprocessor;
import org.dataone.cn.indexer.parser.SubprocessorUtility;
import org.dataone.cn.indexer.solrhttp.SolrDoc;
import org.dataone.cn.indexer.solrhttp.SolrElementField;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.xpath.*;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.*;


/**
 * Quality Report Document processor.  Operates on one quality report for
 * a unique metadata id, suite id key combination, extracting values from the document and
 * calculating scores for each check type.
 *
 */
public class QualityReportSubprocessor implements IDocumentSubprocessor {

    private static Log log = LogFactory.getLog(QualityReportSubprocessor.class);
    private SubprocessorUtility processorUtility;
    private List<String> matchDocuments = null;
    private List<String> fieldsToMerge = new ArrayList<String>();

    /**
     * Implements IDocumentSubprocessor.processDocument method.
     * Given the existing Solr document for this quality report, add any dynamic Solr fields that
     * are required. Currently the quality check 'type' fields can have any name the check writer
     * desires, so we have to extract these field names and add a dynamic field for each name. In
     * addition, we have to score each unique check type (multiple checks can have the same type).
     */
    @Override
    public Map<String, SolrDoc> processDocument(String identifier, Map<String, SolrDoc> docs,
                                                InputStream is) throws XPathExpressionException, IOException, EncoderException {

        ArrayList<String> checkTypes = new ArrayList<String>();
        // There should only be one quality document
        SolrDoc qualityReportSolrDoc = docs.get(identifier);
        // The Solr doc that contains MetaDIG quality check type fields that will be dynamically added to the SolrDoc.
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression checkTypeXpath = null;
        XPathExpression checkXpath = null;
        Document xmldoc = null;
        DecimalFormat df2 = new DecimalFormat(".##");
        log.debug("Processing document for id: " + identifier);

        // Parse the quality report
        try {
            xmldoc = XmlDocumentUtility.generateXmlDocument(is);
        } catch (SAXException e) {
            e.printStackTrace();
        }

        // Get the set of unique check type names. The count and status (SUCCESS, FAILURE) of these
        // check types will be used to calculate the metadata quality score for each check type, e.g.
        // the number of 'passed' checks for a type over the total number of checks for that type.
        checkTypeXpath = xpath.compile("//result/check/type");
        NodeList result = (NodeList) checkTypeXpath.evaluate(xmldoc, XPathConstants.NODESET);
        for(int index = 0; index < result.getLength(); index ++) {
            Node node = result.item(index);
            checkTypes.add(node.getTextContent());
            log.trace("found check type: " + node.getTextContent());
        }

        // Get deduped list of check types
        HashSet<String> set = new HashSet<>(checkTypes);
        ArrayList<String> uniqueTypes = new ArrayList<>(set);
        log.debug("Unique check type name count: " + uniqueTypes.size());

        String xpathExStr = null;
        SolrElementField sField = null;
        Double checkTypeScore;
        Double checkCountPassed;
        Double checkCountFailed;
        String fieldName = null;
        // Calculate the score for each check type. Note that the scores for "passed", "warned", etc are also calculated from the indexer, but those calculations
        // are defined in the MetaDIG application context file "application-context-mdq.xml".
        for (String typeName: uniqueTypes) {
            // Count of checks that passed for the current check type
            xpathExStr = String.format("count(//result[check/level[text() != 'INFO' and text() != 'METADATA'] and check/type[text() = '%s']]/status[text() = 'SUCCESS'])", typeName);
            checkXpath = xpath.compile(xpathExStr);
            checkCountPassed = (Double) checkXpath.evaluate(xmldoc, XPathConstants.NUMBER);

            // Count of checks that failed for the current check type
            xpathExStr = String.format("count(//result[check/level[text() = 'REQUIRED'] and check/type[text() = '%s']]/status[text() = 'ERROR'] | //result[check/level[text() = 'REQUIRED'] and check/type[text() = '%s']]/status[text() = 'FAILURE'])", typeName, typeName);
            checkXpath = xpath.compile(xpathExStr);
            checkCountFailed = (Double) checkXpath.evaluate(xmldoc, XPathConstants.NUMBER);

            // The score for this check type: checks pass / (checks passed + checks failed), including only checks for this type
            checkTypeScore = checkCountPassed / (checkCountPassed + checkCountFailed);
            if(checkTypeScore.isNaN() | checkTypeScore.isInfinite()) {
                checkTypeScore = 0.0;
            }

            sField = new SolrElementField();
            // Set the dynamic field type to be Float, i.e. append "_f" to the name
            fieldName = "scoreByType_" + typeName + "_f";
            sField.setName(fieldName);
            sField.setValue(df2.format(checkTypeScore));
            //sField.setValue(String.valueOf(checkScore));
            qualityReportSolrDoc.addField(sField);
            log.trace("Added check type field " + sField.getName() + ", value: " + sField.getValue());
            log.trace("Number of fields in document: " + qualityReportSolrDoc.getFieldList().size());
        }

        // Only one Solr document will be updated
        Map<String, SolrDoc> mergedDocs = new HashMap<String, SolrDoc>();
        mergedDocs.put(qualityReportSolrDoc.getFirstFieldValue("metadataId"), qualityReportSolrDoc);
        log.debug("Completed subprocessor processing for metadata id: " + qualityReportSolrDoc.getFirstFieldValue("metadataId"));

        return mergedDocs;
    }

    @Override
    public SolrDoc mergeWithIndexedDocument(SolrDoc indexDocument) throws IOException,
            EncoderException, XPathExpressionException {
        return processorUtility.mergeWithIndexedDocument(indexDocument, fieldsToMerge);
    }

    public List<String> getMatchDocuments() {
        return matchDocuments;
    }

    public void setMatchDocuments(List<String> matchDocuments) {
        this.matchDocuments = matchDocuments;
    }

    public boolean canProcess(String formatId) {
        return matchDocuments.contains(formatId);
    }

    public List<String> getFieldsToMerge() {
        return fieldsToMerge;
    }

    public void setFieldsToMerge(List<String> fieldsToMerge) {
        this.fieldsToMerge = fieldsToMerge;
    }
}