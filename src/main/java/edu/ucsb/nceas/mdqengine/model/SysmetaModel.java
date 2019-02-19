package edu.ucsb.nceas.mdqengine.model;

import javax.xml.bind.annotation.*;
import java.util.Date;
import java.util.List;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"originMemberNode", "rightsHolder", "groups", "dateUploaded", "formatId", "obsoletes", "obsoletedBy", "seriesId"})
public class SysmetaModel {

    /**
    * The DataONE Member Node that the metadata document was originally uploaded to.
    */
    @XmlElement(required = true)
    private String originMemberNode; // this is 'datasource' in the DataONE Solr index

    /**
    * The DataONE user that owns the metadata document.
    */
    @XmlElement(required = true)
    private String rightsHolder;

    /**
    * The DataONE user groups that this rightsHolder belongs to.
    * user interfaces and reports.
    */
    @XmlElementWrapper(name="groups")
    @XmlElement(name="group", required = false)
    private List<String> groups;

    /**
    * The data that the object was uploaded to the authoritative Member Node.
    * user interfaces and reports.
    */
    @XmlElement(required = true)
    private Date dateUploaded;

    /**
    * The DataONE format for the metadata document, e.g. "eml://ecoinformatics.org/eml-2.1.1"
    */
    @XmlElement(required = true)
    private String formatId;

    /**
    * A DataONE Persistent Identifier of the document that this document obsoletes, if set.
    */
    @XmlElement(required = false)
    private String obsoletes;

    /**
    * The DataONE Persistent Identifier of the document that the documents obsoletes, if set.
    */
    @XmlElement(required = false)
    private String obsoletedBy;

    /**
    * An identifier that always points to the most recent PID in the obsolecense chain.
    */
    @XmlElement(required = false)
    private String seriesId;

    public String getOriginMemberNode() {
        return originMemberNode;
    }

    public void setOriginMemberNode(String originMemberNode) {
            this.originMemberNode = originMemberNode;
    }

    public String getRightsHolder() {
        return rightsHolder;
    }

    public void setRightsHolder(String rightsHolder) {
        this.rightsHolder = rightsHolder;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public Date getDateUploaded() {
        return dateUploaded;
    }

    public void setDateUploaded(Date dateUploaded) {
        this.dateUploaded = dateUploaded;
    }

    public String getFormatId() {
        return formatId;
    }

    public void setFormatId(String formatId) {
        this.formatId = formatId;
    }

    public String getObsoletes() {
        return obsoletes;
    }

    public void setObsoletes(String obsoletes) {
        this.obsoletes = obsoletes;
    }

    public String getObsoletedBy() {
        return obsoletedBy;
    }

    public void setObsoletedBy(String obsoletedBy) {
        this.obsoletedBy = obsoletedBy;
    }

    public String getSeriesId(String seriesId) {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }
}
