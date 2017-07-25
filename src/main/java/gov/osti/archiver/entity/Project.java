/*
 */
package gov.osti.archiver.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ARCHIVER Project entity.
 * 
 * Contains relevant information from DOECode software project for archiving
 * purposes to GitLab.
 * 
 * @author ensornl
 */
@Entity
@Table (name = "ARCHIVE_PROJECT")
@JsonIgnoreProperties (ignoreUnknown = true)
public class Project implements Serializable {
    // logger
    private static final Logger log = LoggerFactory.getLogger(Project.class);

    // Jackson object mapper
    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    /**
     * @return the codeId
     */
    public Long getCodeId() {
        return codeId;
    }

    /**
     * @param codeId the codeId to set
     */
    public void setCodeId(Long codeId) {
        this.codeId = codeId;
    }

    /**
     * @return the projectId
     */
    public Integer getProjectId() {
        return projectId;
    }

    /**
     * @param projectId the projectId to set
     */
    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

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
     * @return the fileName
     */
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
     * @return the projectName
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * @param projectName the projectName to set
     */
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * @return the projectDescription
     */
    public String getProjectDescription() {
        return projectDescription;
    }

    /**
     * @param projectDescription the projectDescription to set
     */
    public void setProjectDescription(String projectDescription) {
        this.projectDescription = projectDescription;
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
    public enum Status {
        Pending,
        Processing,
        Error,
        Complete
    }
    
    public void setStatusMessage(String msg) {
        statusMessage = msg;
    }
    
    public String getStatusMessage() {
        return (null==statusMessage) ? "" : statusMessage.trim();
    }
    
    @Id
    @Column (name = "code_id")
    private Long codeId; // key from DOECODE database
    @Column (name = "project_id")
    private Integer projectId; // key into GITLAB instance
    @Column (length = 1000, name = "repository_link")
    private String repositoryLink;
    @Column (length = 1000, name = "file_name")
    private String fileName;
    @Column (length = 1000, name = "project_name")
    private String projectName;
    @Column (length = 4000, name = "project_description")
    private String projectDescription;
    @Column (length = 50, name = "status")
    @Enumerated (EnumType.STRING)
    private Status status = Status.Pending; // default value
    @Column (length = 500, name = "status_message")
    private String statusMessage;
    
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
