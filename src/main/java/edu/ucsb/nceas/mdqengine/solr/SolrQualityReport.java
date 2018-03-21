package edu.ucsb.nceas.mdqengine.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.client.solrj.impl.HttpSolrClient.Builder;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.dataone.service.types.v2.SystemMetadata;

import java.util.List;

public class QualityReport {

    @Field
    public String id;
    @Field
    public String name;
    @Field
    public String metadataId;
    @Field
    public String suiteId;
    @Field
    public String timestamp;
    @Field
    public String formatId;
    @Field
    public String datasource;
    @Field
    public String funder;
    @Field
    public String funderLookup;
    @Field
    public String rightsHolder;
    @Field
    public String group;
    @Field
    public String score;
    @Field
    public String scoreComposite;

    SolrClient client = null;
    static final String solrUrl = "http://localhost:8983/solr";

    public QualityReport(String id, String name) {
        this.id = id;
        this.name = name;
        final SolrClient client = new HttpSolrClient(new Builder(solrUrl));

    }

    public QualityReport(SystemMetadata sysmeta) {

        String urlString = "http://localhost:8983/solr/techproducts";
        SolrClient solr = new HttpSolrClient.Builder(urlString).build();

        SystemMetadata sysmeta = message.getSystemMetadata();
        String pid = message.getMetadataPid();
        //solr.setParser(new XMLResponseParser());
        SolrInputDocument document = new SolrInputDocument();
        document.addField("id", "552199");
        document.addField("nae", "Gouda cheese wheel");
        document.addField("price", "49.99");
    }

    public void update() {

        QualityReport kindle = new QualityReport("kindle-id-4", "Amazon Kindle Paperwhite");
        UpdateResponse response = client.addBean("techproducts", kindle);

        client.commit("techproducts");
    }

    public void read() {
        final SolrClient client = new HttpSolrClient(new Builder("localhost:8983"));

        final SolrQuery query = new SolrQuery("*:*");
        query.addField("id");
        query.addField("name");

        final QueryResponse response = client.query("techproducts", query);
        final List<QualityReport> products = response.getBeans(QualityReport.class);
    }
}

