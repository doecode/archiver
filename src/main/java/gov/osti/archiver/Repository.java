/*
 */
package gov.osti.archiver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import java.io.IOException;
import java.io.Reader;

/**
 * Basic metadata information about a Repository/Project.
 * 
 * @author ensornl
 */
@JsonIgnoreProperties (ignoreUnknown = true)
public class Repository {
    public static final String PUBLIC_VISIBILITY = "public";
    public static final String PRIVATE_VISIBILITY = "private";
    public static final String INTERNAL_VISIBILITY = "internal";
    
    private Long codeId;
    private Integer id;
    private String repositoryUrl;
    private String description;
    private String defaultBranch;
    private String visibility;
    private String sshUrlToRepo;
    private String httpUrlToRepo;
    private String[] tagList;
    private Owner owner;
    private String name;
    private String nameWithNamespace;
    private String path;
    private String pathWithNamespace;
    private boolean issuesEnabled;
    private Integer openIssuesCount;
    private boolean mergeRequestsEnabled;
    private boolean jobsEnabled;
    private boolean wikiEnabled;
    private boolean snippetsEnabled;
    private boolean containerRegistryEnabled;
    private String createdAt;
    private String lastActivityAt;
    private Integer creatorId;
    private boolean archived;
    private Integer forksCount;
    private Integer starsCount;
    private boolean publicJobs;
    private Group[] sharedWithGroups;
    private boolean requestAccessEnabled;
    private Statistics statistics;
    
    // Jackson object mapper
    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     * Attempt to load JSON from a Reader into a new Repository object.
     * 
     * @param reader the Reader to load from
     * @return a new Repository Object
     * @throws IOException on unexpected errors
     */
    public static Repository fromJson(Reader reader) throws IOException {
        return mapper.readValue(reader, Repository.class);
    }
    
    /**
     * Create a new Repository from a given JSON String.
     * 
     * @param json the JSON to read
     * @return a new Repository
     * @throws IOException on read errors
     */
    public static Repository fromJson(String json) throws IOException {
        return mapper.readValue(json, Repository.class);
    }
    
    /**
     * Convert this Object to JSON.
     * 
     * @return the JsonNode for this Object's values.
     */
    public JsonNode toJson() {
        return mapper.valueToTree(this);
    }
    
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
     * @return the id
     */
    public Integer getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the defaultBranch
     */
    public String getDefaultBranch() {
        return defaultBranch;
    }

    /**
     * @param defaultBranch the defaultBranch to set
     */
    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    /**
     * @return the visibility
     */
    public String getVisibility() {
        return visibility;
    }

    /**
     * @param visibility the visibility to set
     */
    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    /**
     * @return the sshUrlToRepo
     */
    public String getSshUrlToRepo() {
        return sshUrlToRepo;
    }

    /**
     * @param sshUrlToRepo the sshUrlToRepo to set
     */
    public void setSshUrlToRepo(String sshUrlToRepo) {
        this.sshUrlToRepo = sshUrlToRepo;
    }

    /**
     * @return the httpUrlToRepo
     */
    public String getHttpUrlToRepo() {
        return httpUrlToRepo;
    }

    /**
     * @param httpUrlToRepo the httpUrlToRepo to set
     */
    public void setHttpUrlToRepo(String httpUrlToRepo) {
        this.httpUrlToRepo = httpUrlToRepo;
    }

    /**
     * @return the tagList
     */
    public String[] getTagList() {
        return tagList;
    }

    /**
     * @param tagList the tagList to set
     */
    public void setTagList(String[] tagList) {
        this.tagList = tagList;
    }

    /**
     * @return the owner
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the nameWithNamespace
     */
    public String getNameWithNamespace() {
        return nameWithNamespace;
    }

    /**
     * @param nameWithNamespace the nameWithNamespace to set
     */
    public void setNameWithNamespace(String nameWithNamespace) {
        this.nameWithNamespace = nameWithNamespace;
    }

    /**
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * @param path the path to set
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * @return the pathWithNamespace
     */
    public String getPathWithNamespace() {
        return pathWithNamespace;
    }

    /**
     * @param pathWithNamespace the pathWithNamespace to set
     */
    public void setPathWithNamespace(String pathWithNamespace) {
        this.pathWithNamespace = pathWithNamespace;
    }

    /**
     * @return the issuesEnabled
     */
    public boolean isIssuesEnabled() {
        return issuesEnabled;
    }

    /**
     * @param issuesEnabled the issuesEnabled to set
     */
    public void setIssuesEnabled(boolean issuesEnabled) {
        this.issuesEnabled = issuesEnabled;
    }

    /**
     * @return the openIssuesCount
     */
    public Integer getOpenIssuesCount() {
        return openIssuesCount;
    }

    /**
     * @param openIssuesCount the openIssuesCount to set
     */
    public void setOpenIssuesCount(Integer openIssuesCount) {
        this.openIssuesCount = openIssuesCount;
    }

    /**
     * @return the mergeRequestsEnabled
     */
    public boolean isMergeRequestsEnabled() {
        return mergeRequestsEnabled;
    }

    /**
     * @param mergeRequestsEnabled the mergeRequestsEnabled to set
     */
    public void setMergeRequestsEnabled(boolean mergeRequestsEnabled) {
        this.mergeRequestsEnabled = mergeRequestsEnabled;
    }

    /**
     * @return the jobsEnabled
     */
    public boolean isJobsEnabled() {
        return jobsEnabled;
    }

    /**
     * @param jobsEnabled the jobsEnabled to set
     */
    public void setJobsEnabled(boolean jobsEnabled) {
        this.jobsEnabled = jobsEnabled;
    }

    /**
     * @return the wikiEnabled
     */
    public boolean isWikiEnabled() {
        return wikiEnabled;
    }

    /**
     * @param wikiEnabled the wikiEnabled to set
     */
    public void setWikiEnabled(boolean wikiEnabled) {
        this.wikiEnabled = wikiEnabled;
    }

    /**
     * @return the snippetsEnabled
     */
    public boolean isSnippetsEnabled() {
        return snippetsEnabled;
    }

    /**
     * @param snippetsEnabled the snippetsEnabled to set
     */
    public void setSnippetsEnabled(boolean snippetsEnabled) {
        this.snippetsEnabled = snippetsEnabled;
    }

    /**
     * @return the containerRegistryEnabled
     */
    public boolean isContainerRegistryEnabled() {
        return containerRegistryEnabled;
    }

    /**
     * @param containerRegistryEnabled the containerRegistryEnabled to set
     */
    public void setContainerRegistryEnabled(boolean containerRegistryEnabled) {
        this.containerRegistryEnabled = containerRegistryEnabled;
    }

    /**
     * @return the createdAt
     */
    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * @param createdAt the createdAt to set
     */
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * @return the lastActivityAt
     */
    public String getLastActivityAt() {
        return lastActivityAt;
    }

    /**
     * @param lastActivityAt the lastActivityAt to set
     */
    public void setLastActivityAt(String lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    /**
     * @return the creatorId
     */
    public Integer getCreatorId() {
        return creatorId;
    }

    /**
     * @param creatorId the creatorId to set
     */
    public void setCreatorId(Integer creatorId) {
        this.creatorId = creatorId;
    }

    /**
     * @return the archived
     */
    public boolean isArchived() {
        return archived;
    }

    /**
     * @param archived the archived to set
     */
    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    /**
     * @return the forksCount
     */
    public Integer getForksCount() {
        return forksCount;
    }

    /**
     * @param forksCount the forksCount to set
     */
    public void setForksCount(Integer forksCount) {
        this.forksCount = forksCount;
    }

    /**
     * @return the starsCount
     */
    public Integer getStarsCount() {
        return starsCount;
    }

    /**
     * @param starsCount the starsCount to set
     */
    public void setStarsCount(Integer starsCount) {
        this.starsCount = starsCount;
    }

    /**
     * @return the publicJobs
     */
    public boolean isPublicJobs() {
        return publicJobs;
    }

    /**
     * @param publicJobs the publicJobs to set
     */
    public void setPublicJobs(boolean publicJobs) {
        this.publicJobs = publicJobs;
    }

    /**
     * @return the sharedWithGroups
     */
    public Group[] getSharedWithGroups() {
        return sharedWithGroups;
    }

    /**
     * @param sharedWithGroups the sharedWithGroups to set
     */
    public void setSharedWithGroups(Group[] sharedWithGroups) {
        this.sharedWithGroups = sharedWithGroups;
    }

    /**
     * @return the requestAccessEnabled
     */
    public boolean isRequestAccessEnabled() {
        return requestAccessEnabled;
    }

    /**
     * @param requestAccessEnabled the requestAccessEnabled to set
     */
    public void setRequestAccessEnabled(boolean requestAccessEnabled) {
        this.requestAccessEnabled = requestAccessEnabled;
    }

    /**
     * @return the statistics
     */
    public Statistics getStatistics() {
        return statistics;
    }

    /**
     * @param statistics the statistics to set
     */
    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }

    /**
     * @return the repositoryUrl
     */
    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    /**
     * @param repositoryUrl the repositoryUrl to set
     */
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
    
    
}
