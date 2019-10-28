package edu.ucsb.nceas.mdqengine.solr;

import org.apache.solr.client.solrj.beans.Field;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

/**
 * This class holds a single quality score that is downloaded from the Quality Solr index, in
 * response to a Solr query. The Solrj library provides this option of returning query results
 * one at a time via a Java Object, vs paged as a JSON or XML document.
 */

public class QualityScore {

    @Field public String metadataId;
    @Field public String formatId;
    @Field public String metadataFormatId;
    @Field public String suiteId;
    @Field public Date timestamp;
    @Field public String datasource;
    @Field public Date dateUploaded;
    @Field public String sequenceId;
    @Field public Boolean isLatest;
    @Field public ArrayList<String> funder;
    @Field public ArrayList<String> funderInfo;
    @Field public ArrayList<String> group;
    @Field public String rightsHolder;
    @Field public Integer checksPassed;
    @Field public Integer checksWarned;
    @Field public Integer checksFailed;
    @Field public Integer checksInfo;
    @Field public Integer checkCount;
    @Field public Float scoreOverall;
    @Field public String obsoletes;
    @Field public String obsoletedBy;
    @Field public String seriesId;
    // All dynamic 'float' fields will get put in this map
    @Field("*_f") public Map<String, Float> scores_by_type;

    public QualityScore(String metadataId,
                        String formatId,
                        String suiteId,
                        Date timestamp,
                        String datasource,
                        String metadataFormatId,
                        Date dateUploaded,
                        String sequenceId,
                        String rightsHolder,
                        ArrayList<String> funder,
                        ArrayList<String> funderInfo,
                        ArrayList<String> group,
                        Integer checksPassed,
                        Integer checksWarned,
                        Integer checksFailed,
                        Integer checksInfo,
                        Integer checkCount,
                        Float scoreOverall,
                        String obsoletes,
                        String obsoletedBy,
                        String seriesId,
                        Boolean isLatest,
                        Map scores_by_type) {

        this.metadataId = metadataId;
        this.formatId = formatId;
        this.metadataFormatId = metadataFormatId;
        this.suiteId = suiteId;
        this.timestamp = timestamp;
        this.datasource = datasource;
        this.dateUploaded = dateUploaded;
        this.sequenceId = sequenceId;
        this.isLatest = isLatest;
        this.funder = funder;
        this.funderInfo = funderInfo;
        this.group = group;
        this.rightsHolder = rightsHolder;
        this.checksPassed = checksPassed;
        this.checksWarned = checksWarned;
        this.checksFailed = checksFailed;
        this.checksInfo = checksInfo;
        this.checkCount = checkCount;
        this.scoreOverall = scoreOverall;
        this.obsoletes = obsoletes;
        this.obsoletedBy = obsoletedBy;
        this.seriesId = seriesId;
        this.scores_by_type = scores_by_type;
    }

    //public QualityScore(QualityScore qualityScore) {}
    public QualityScore() {}
}

