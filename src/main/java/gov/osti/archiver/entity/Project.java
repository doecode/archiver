/*
 */
package gov.osti.archiver.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.osti.archiver.listener.ServletContextListener;

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
    @NamedQuery (name = "Project.findLatestByCodeId", query = "SELECT p FROM Project p WHERE p.codeIds = :ids AND p.repositoryType NOT IN :types ORDER BY p.dateRecordAdded DESC"),
    @NamedQuery (name = "Project.findLatestByCodeIdForTarget", query = "SELECT p FROM Project p WHERE p.codeIds = :ids AND p.repositoryType NOT IN :types AND (p.fileName = CONCAT(p.cacheFolder, :file) OR p.repositoryLink = :repo) ORDER BY p.dateRecordAdded DESC"),
    @NamedQuery (name = "Project.findLaborHourReady", query = "SELECT p FROM Project p WHERE p.status = :status and ((p.repositoryType NOT IN :typesNonFiles and p.dateLastMaintained IS NOT NULL and (p.dateLaborCalculated IS NULL or p.dateLaborCalculated < p.dateLastMaintained)) or (p.repositoryType IN :typesFiles and p.dateLaborCalculated IS NULL)) ORDER BY p.projectId"),
    @NamedQuery (name = "Project.findByStatus", query = "SELECT p FROM Project p WHERE p.status = :status"),
    @NamedQuery (name = "Project.findByType", query = "SELECT p FROM Project p WHERE p.repositoryType = :type and p.status = :status"),
    @NamedQuery (name = "Project.countByType", query = "SELECT COUNT(p) FROM Project p WHERE p.repositoryType = :type and p.status = :status"),
    @NamedQuery (name = "Project.findByNotTypes", query = "SELECT p FROM Project p WHERE p.repositoryType NOT IN :types and p.status = :status"),
    @NamedQuery (name = "Project.countByNotTypes", query = "SELECT COUNT(p) FROM Project p WHERE p.repositoryType NOT IN :types and p.status = :status")
})
public class Project implements Serializable {

    private static final long serialVersionUID = -8454319680573907732L;

    // logger
    private static final Logger log = LoggerFactory.getLogger(Project.class);

    // path to CLOC program
    private static String CLOC = ServletContextListener.getConfigurationProperty("laborhours.cloc");

    // path to SLOC URL
    private static String COCOMOII_URL = ServletContextListener.getConfigurationProperty("laborhours.cocomoii");
   
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
        File,
        Container,
        TaggedRelease
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
     * @return the labor cloc
     */
    public String getLaborCloc() {
        return cloc;
    }

    /**
     * @param cloc the cloc result to set
     */
    public void setLaborCloc(String cloc) {
        this.cloc = cloc;
    }

    /**
     * @return the labor sloc
     */
    public Integer getLaborSloc() {
        return sloc;
    }

    /**
     * @param sloc the sloc to set
     */
    public void setLaborSloc(Integer sloc) {
        this.sloc = sloc;
    }

    /**
     * @return the labor effort
     */
    public Double getLaborEffort() {
        return effort;
    }

    /**
     * @param effort the effort to set
     */
    public void setLaborEffort(Double effort) {
        this.effort = effort;
    }

    /**
     * @return the labor hours
     */
    public Double getLaborHours() {
        return laborHours;
    }

    /**
     * @param laborHours the laborHours to set
     */
    public void setLaborHours(Double laborHours) {
        this.laborHours = laborHours;
    }

    /**
     * @return the maintenanceStatus
     */
    public Status getLaborHourStatus() {
        return laborHourStatus;
    }

    /**
     * @param laborHourStatus the laborHourStatus to set
     */
    public void setLaborHourStatus(Status laborHourStatus) {
        this.laborHourStatus = laborHourStatus;
    }

    /**
     * Set the DATE LABOR CALCULATED value
     * @param dateLaborCalculated the dateLaborCalculated to set
     */
    public void setDateLaborCalculated(Date dateLaborCalculated) {
        this.dateLaborCalculated = dateLaborCalculated;
    }
    
    /**
     * Set the DATE LABOR CALCULATED to now.
     */
    public void setDateLaborCalculated() {
        this.setDateLaborCalculated(new Date());
    }

    /**
     * @return the file notification flag
     */
    public boolean getSendFileNotification() {
        return sendFileNotification;
    }

    /**
     * @param sendFileNotification the boolean to set
     */
    public void setSendFileNotification(boolean sendFileNotification) {
        this.sendFileNotification = sendFileNotification;
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
    private RepositoryType repositoryType;
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
    @Lob
    @Column (name = "labor_cloc")
    private String cloc;
    @Column (name = "labor_sloc")
    private Integer sloc;
    @Column (name = "labor_effort")
    private Double effort;
    @Column (name = "labor_hours")
    private Double laborHours;
    @Column (length = 50, name = "labor_hours_status")
    @Enumerated (EnumType.STRING)
    private Status laborHourStatus;
    @Column (name = "date_labor_calculated", insertable = true, updatable = true)
    @JsonFormat (shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "EST")
    @Temporal (TemporalType.TIMESTAMP)
    private Date dateLaborCalculated;
    @Transient
    private boolean sendFileNotification = false;
    
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

    private ObjectNode calculateCloc () {        
        ObjectNode cloc = mapper.createObjectNode();

        // if CLOC path specified, calculate CLOC information
        if (!StringUtils.isEmptyOrNull(CLOC)) {        
            ProcessBuilder pb = null;
            Process proc = null;

            String stdOut = null;
            String stdErr = null;

            try {
                pb = new ProcessBuilder("perl", CLOC, "--json", getCacheFolder());

                proc = pb.start();
                
                StringWriter writer = new StringWriter();
                IOUtils.copy(proc.getInputStream(), writer, "UTF-8");
                stdOut = writer.toString();
                
                StringWriter writerErr = new StringWriter();
                IOUtils.copy(proc.getErrorStream(), writerErr, "UTF-8");
                stdErr = writerErr.toString();

                proc.waitFor();
                
                if (proc.exitValue() != 0) {
                    throw new Exception("The CLOC process exited abnormally:  " + proc.exitValue());
                }
            } catch (Exception e) {
                log.warn("CLOC calculation has failed for Project " + getProjectId() + "! [" + e.getMessage() + "]");
            } finally {
                if (proc!=null) proc.destroy();
            }

            JsonNode clocResult = null;
            try {
                clocResult = mapper.readTree(stdOut);
            } catch (Exception e){
                log.warn("CLOC JSON is invalid! [" + e.getMessage() + "]");
            }
            
            cloc.set("result", clocResult);
            cloc.put("error", stdErr);
        }

        try {
            setLaborCloc(mapper.writeValueAsString(cloc));
        } catch (Exception e) {
            log.warn("LABOR CLOC JSON is invalid! [" + e.getMessage() + "]");
        }

        return cloc;
    }

    private int calculateSloc() {
        int sloc = 0;
        try {
            JsonNode cloc = mapper.readTree(getLaborCloc());
            sloc = cloc.path("result").path("SUM").path("code").asInt();
        } catch (Exception e){
            log.warn("CLOC JSON is missing SUM code! [" + e.getMessage() + "]");
        }
        
        setLaborSloc(sloc);
        return sloc;
    }

    private double calculateEffort() {
        double effort = 0.0;

        Integer sloc = getLaborSloc();
        if (sloc != null && sloc > 0 && !StringUtils.isEmptyOrNull(COCOMOII_URL)) {
            // set up a connection
            CloseableHttpClient hc =
                    HttpClientBuilder
                    .create()
                    .setDefaultRequestConfig(RequestConfig
                            .custom()
                            .setSocketTimeout(300000)
                            .setConnectTimeout(300000)
                            .setConnectionRequestTimeout(300000)
                            .build())
                    .build();

            try {
                HttpPost post = new HttpPost(COCOMOII_URL);
                
                // Set "new size" parameter.
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("new_size", sloc.toString()));
                post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

                // make URL call
                HttpResponse response = hc.execute(post);

                int statusCode = response.getStatusLine().getStatusCode();
                String result = EntityUtils.toString(response.getEntity());

                if (HttpStatus.SC_OK!=statusCode) {
                    throw new IOException ("COCOMOII Error: " + result);
                }

                // scrape the EFFORT value from HTML response
                Pattern effortPattern = Pattern.compile("Effort = ([\\d\\.]+) Person-months");
                Matcher m = effortPattern.matcher(result);
                String scraped = null;
                if (m.find())
                    scraped = m.group(1);

                try {
                    effort = Double.parseDouble(scraped);
                }
                catch (Exception e) {
                    log.warn("Unable to parse Effort from COCOMOII!");
                }
            } catch ( IOException e ) {
                log.warn("Scrape Labor error: " + e.getMessage());
            } finally {
                try {
                    if (null!=hc) hc.close();
                } catch ( IOException e ) {
                    log.warn("Close Error: " + e.getMessage());
                }
            }
        }

        setLaborEffort(effort);
        return effort;
    }

    private double calculateLabor() {
        /*
        Use value from COCOMO II Book:
        Reference: https://dl.acm.org/citation.cfm?id=557000
        This is the value used by the Code.gov team:
        https://github.com/GSA/code-gov/blob/master/docs/labor_hour_calc.md
        */
        double labor = 0.0;
        try {
            labor = Math.round((getLaborEffort() * 152.0) * 10) / 10.0;
        } catch (Exception e) {
            log.warn("Unable to calculate Labor from Effort! [" + e.getMessage() + "]");
        }
        
        setLaborHours(labor);
        return labor;
    }

    public void calculateLaborHours() {
        // do not calculate if project is a container, incomplete, or cache is blank
        if (Project.RepositoryType.Container.equals(getRepositoryType()) || !Project.Status.Complete.equals(getStatus()) || StringUtils.isEmptyOrNull(getCacheFolder()))
            return;

        // process all the stages of Labor Hour calculation on the current object (exceptions are swallowed up and logged)
        calculateCloc();
        calculateSloc();
        calculateEffort();
        calculateLabor();
        setLaborHourStatus(Project.Status.Complete);
        setDateLaborCalculated();
    }
}
