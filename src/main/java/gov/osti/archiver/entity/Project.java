/*
 */
package gov.osti.archiver.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.Basic;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ARCHIVER Project entity.
 * 
 * Archive/Cache software project information on local filesystem storage.
 * 
 * FILES archive as-is; REPOSITORY caches extract/clone/checkout via GIT, SVN, or HG.
 * Keep the latter updated on a daily basis optionally via scripts.
 * 
 * PROJECT has a unique ID; the REPOSITORY LINK value is effectively UNIQUE; if 
 * the value is found already, the project is assumed to already be "cached" and
 * is thus skipped.
 * 
 * @author ensornl
 */
@Entity
@Table (name = "ARCHIVE_PROJECT")
@JsonIgnoreProperties (ignoreUnknown = true)
@NamedQueries ({
    @NamedQuery (name = "Project.findByRepositoryLink", query = "SELECT p FROM Project p WHERE UPPER(p.repositoryLink) = UPPER(:url)"),
    @NamedQuery (name = "Project.findById", query = "SELECT p FROM Project p WHERE p.projectId = :id"),
    @NamedQuery (name = "Project.findByCodeId", query = "SELECT p FROM Project p WHERE p.codeIds = :ids"),
    @NamedQuery (name = "Project.findByStatus", query = "SELECT p FROM Project p WHERE p.status = :status"),
    @NamedQuery (name = "Project.findByType", query = "SELECT p FROM Project p WHERE p.repositoryType = :type and p.status = :status"),
    @NamedQuery (name = "Project.countByType", query = "SELECT COUNT(p) FROM Project p WHERE p.repositoryType = :type and p.status = :status")
})
public class Project implements Serializable {
    // logger
    private static final Logger log = LoggerFactory.getLogger(Project.class);
    
    // Jackson object mapper
    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    /**
     * @return the repositoryLink
     */
    public String getRepositoryLink() {
        return repositoryLink;
    }

    /**
     * @param repositoryLink the repositoryLink to set
     */
    public void setRepositoryLink(String repositoryLink) {
        this.repositoryLink = repositoryLink;
    }

    /**
     * Get the FILE NAME associated with this Project.
     * For serialization, only emit the base file name itself.
     * 
     * @return the fileName contains the absolute file path of the Project
     */
    @JsonSerialize (using = FileNameSerializer.class)
    public String getFileName() {
        return fileName;
    }

    /**
     * @param fileName the fileName to set
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * @return the status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * @return the projectId
     */
    public Long getProjectId() {
        return projectId;
    }

    /**
     * @param projectId the projectId to set
     */
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    /**
     * @return the codeIds
     */
    public Set<Long> getCodeIds() {
        return codeIds;
    }

    /**
     * @param codeIds the codeIds to set
     */
    public void setCodeIds(Set<Long> codeIds) {
        this.codeIds = codeIds;
    }
    
    public boolean addCodeId(Long id) {
        return codeIds.add(id);
    }

    /**
     * The FILESYSTEM area in which the information is cached/stored.  Should
     * NOT be emitted on JSON calls.
     * 
     * @return the cacheFolder the absolute filesystem location for the cached
     * files
     */
    @JsonIgnore
    public String getCacheFolder() {
        return cacheFolder;
    }

    /**
     * @param cacheFolder the cacheFolder to set
     */
    public void setCacheFolder(String cacheFolder) {
        this.cacheFolder = cacheFolder;
    }

    /**
     * Obtain the Date this Project was last maintained/updated if remote.  Only
     * applies to non-File repositories.
     * @return the dateLastMaintained the DATE this project was last maintained
     */
    public Date getDateLastMaintained() {
        return dateLastMaintained;
    }

    /**
     * Set the DATE LAST MAINTAINED value
     * @param dateLastMaintained the dateLastMaintained to set
     */
    public void setDateLastMaintained(Date dateLastMaintained) {
        this.dateLastMaintained = dateLastMaintained;
    }
    
    /**
     * Set the DATE LAST MAINTAINED to now.
     */
    public void setDateLastMaintained() {
        this.setDateLastMaintained(new Date());
    }

    /**
     * @return the maintenanceStatus
     */
    public Status getMaintenanceStatus() {
        return maintenanceStatus;
    }

    /**
     * @param maintenanceStatus the maintenanceStatus to set
     */
    public void setMaintenanceStatus(Status maintenanceStatus) {
        this.maintenanceStatus = maintenanceStatus;
    }

    /**
     * @return the maintenanceMessage
     */
    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }

    /**
     * @param maintenanceMessage the maintenanceMessage to set
     */
    public void setMaintenanceMessage(String maintenanceMessage) {
        this.maintenanceMessage = maintenanceMessage;
    }
    
    /**
     * Differing status values of the Project.
     */
    public enum Status {
        Pending,
        Processing,
        Error,
        Complete
    }
    
    /**
     * Get the TYPE of repository link, if applicable
     * @return the TYPE
     */
    public RepositoryType getRepositoryType(){
        return repositoryType;
    }
    
    /**
     * Set the TYPE of repository
     * @param t the TYPE to set
     */
    public void setRepositoryType(RepositoryType t) {
        repositoryType = t;
    }
    
    /**
     * Supported REPOSITORY TYPE values.
     */
    public enum RepositoryType {
        Git,
        Subversion,
        File
    }
    
    public void setStatusMessage(String msg) {
        statusMessage = msg;
    }
    
    public String getStatusMessage() {
        return (null==statusMessage) ? "" : statusMessage.trim();
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

    public void setDateRecordAdded () {
        setDateRecordAdded(new Date());
    }

    /**
     * @return the dateRecordUpdated
     */
    public Date getDateRecordUpdated() {
        return dateRecordUpdated;
    }

    /**
     * @param dateRecordUpdated the dateRecordUpdated to set
     */
    public void setDateRecordUpdated(Date dateRecordUpdated) {
        this.dateRecordUpdated = dateRecordUpdated;
    }

    public void setDateRecordUpdated() {
        setDateRecordUpdated(new Date());
    }

    /**
     * Method called when a record is first created.  Sets dates added and
     * updated.
     */
    @PrePersist
    void createdAt() {
        setDateRecordAdded();
        setDateRecordUpdated();
    }

    /**
     * Method called when the record is updated.
     */
    @PreUpdate
    void updatedAt() {
        setDateRecordUpdated();
    }
        
    // ATTRIBUTES
    
    @Id
    @Column (name = "project_id")
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long projectId;
    @Column (length = 1000, name = "repository_link")
    private String repositoryLink;
    @Column (length = 1000, name = "file_name")
    private String fileName;
    @Column (length = 50, name = "status")
    @Enumerated (EnumType.STRING)
    private Status status = Status.Pending; // default value
    @Column (length = 2000, name = "status_message")
    private String statusMessage;
    @Column (length = 20, name = "repository_type")
    @Enumerated (EnumType.STRING)
    private RepositoryType repositoryType = RepositoryType.Git;
    // administrative dates
    @JsonFormat (shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "EST")
    @Basic(optional = false)
    @Column(name = "date_record_added", insertable = true, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateRecordAdded;
    @JsonFormat (shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "EST")
    @Basic(optional = false)
    @Column(name = "date_record_updated", insertable = true, updatable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateRecordUpdated;
    @Column (name = "date_last_maintained", insertable = true, updatable = true)
    @JsonFormat (shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "EST")
    @Temporal (TemporalType.TIMESTAMP)
    private Date dateLastMaintained;
    @Column (length = 50, name = "maintenance_status")
    @Enumerated (EnumType.STRING)
    private Status maintenanceStatus;
    @Column (length = 2000, name = "maintenance_message")
    private String maintenanceMessage;
    @ElementCollection
    @CollectionTable (name = "archive_project_xref",
            joinColumns = @JoinColumn (name = "project_id"))
    private Set<Long> codeIds = new HashSet<>();
    @Column(name = "cache_folder", length = 1000)
    private String cacheFolder;
    
    /**
     * Parses JSON in the request body of the reader into a Project object.
     * @param reader - A request reader containing JSON in the request body.
     * @return A Project object representing the data of the JSON in the request body.
     * @throws IOException on JSON parsing errors (IO errors)
     */
    public static Project fromJson(Reader reader) throws IOException {
        return mapper.readValue(reader, Project.class);
    }
    
    /**
     * Parse JSON String into a Project object.
     * 
     * @param project the JSON to read
     * @return a Project if possible
     * @throws IOException on JSON parsing errors
     */
    public static Project fromJson(String project) throws IOException {
        return mapper.readValue(project, Project.class);
    }
    
    /**
     * Convert this Project into a JSON String.
     * 
     * @return JSON String representing this Project
     */
    public String toJson() {
        return mapper.valueToTree(this).toString();
    }
}
