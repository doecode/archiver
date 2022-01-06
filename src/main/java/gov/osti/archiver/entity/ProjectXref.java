package gov.osti.archiver.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.PrePersist;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 * A cross reference to a projects code ids.
 * 
 * @author sowerst
 */
@Embeddable
@JsonIgnoreProperties ( ignoreUnknown = true )
public class ProjectXref implements Serializable, Comparable<ProjectXref> {
    // Code ID using Project
    @Column (name = "code_id")
    private Long codeId;
    // administrative dates
    @Basic (optional = false)
    @JsonFormat (shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Column (columnDefinition="timestamp", name = "date_record_added", insertable = true, updatable = false, nullable = false)
    @Temporal (TemporalType.TIMESTAMP)
    private Date dateRecordAdded;


    public ProjectXref() {

    }

    public ProjectXref(Long codeId, Date dateAdded) {
        this.codeId = codeId;
        this.dateRecordAdded = dateAdded;
    }

    public ProjectXref(Long codeId) {
        this(codeId, new Date());
    }

    /**
     * the CODE ID value
     */
    public Long getCodeId() {
        return this.codeId;
    }

    /**
     * @param codeId the CODE ID value
     */
    public void setCodeId(Long codeId) {
        this.codeId = codeId;
    }

    /**
     * Method called when a record is first created.  Sets dates added and
     * updated.
     */
    @PrePersist
    void createdAt() {
        setDateRecordAdded();
    }

    /**
     * @return the dateRecordAdded
     */
    public Date getDateRecordAdded() {
        return dateRecordAdded;
    }

    /**
     * @param dateRecordAdded the dateRecordAdded to set
     */
    public void setDateRecordAdded(Date dateRecordAdded) {
        this.dateRecordAdded = dateRecordAdded;
    }

    /**
     * Set the DATE ADDED to now.
     */
    public void setDateRecordAdded() {
        setDateRecordAdded(new Date());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ProjectXref ) {
            return ((ProjectXref)o).getCodeId().equals(getCodeId()) &&
                   ((ProjectXref)o).getDateRecordAdded().equals(getDateRecordAdded());
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.codeId);
        hash = 83 * hash + Objects.hashCode(this.dateRecordAdded);
        return hash;
    }

    @Override
    public int compareTo(ProjectXref other) {
        int result = 0;
        Date thisDate = this.getDateRecordAdded();
        Date otherDate = other.getDateRecordAdded();

        if (thisDate == null && otherDate != null){
            return -1;
        }
        else if (thisDate != null && otherDate == null){
            return 1;
        }
        else if ((thisDate == null && otherDate == null) || otherDate.equals(thisDate)) {
            result = other.getCodeId().compareTo(this.getCodeId());
        }
        else
            result = otherDate.compareTo(thisDate);

        return result;
    }
}