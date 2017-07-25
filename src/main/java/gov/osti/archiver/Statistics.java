/*
 */
package gov.osti.archiver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Statistics information for a Repository/Project.
 * 
 * @author ensornl
 */
@JsonIgnoreProperties (ignoreUnknown = true)
public class Statistics {
    private Integer commitCount;
    private Long storageSize;
    private Long repositorySize;
    private Integer lfsObjectsSize;
    private Integer jobArtifactsSize;

    /**
     * @return the commitCount
     */
    public Integer getCommitCount() {
        return commitCount;
    }

    /**
     * @param commitCount the commitCount to set
     */
    public void setCommitCount(Integer commitCount) {
        this.commitCount = commitCount;
    }

    /**
     * @return the storageSize
     */
    public Long getStorageSize() {
        return storageSize;
    }

    /**
     * @param storageSize the storageSize to set
     */
    public void setStorageSize(Long storageSize) {
        this.storageSize = storageSize;
    }

    /**
     * @return the repositorySize
     */
    public Long getRepositorySize() {
        return repositorySize;
    }

    /**
     * @param repositorySize the repositorySize to set
     */
    public void setRepositorySize(Long repositorySize) {
        this.repositorySize = repositorySize;
    }

    /**
     * @return the lfsObjectsSize
     */
    public Integer getLfsObjectsSize() {
        return lfsObjectsSize;
    }

    /**
     * @param lfsObjectsSize the lfsObjectsSize to set
     */
    public void setLfsObjectsSize(Integer lfsObjectsSize) {
        this.lfsObjectsSize = lfsObjectsSize;
    }

    /**
     * @return the jobArtifactsSize
     */
    public Integer getJobArtifactsSize() {
        return jobArtifactsSize;
    }

    /**
     * @param jobArtifactsSize the jobArtifactsSize to set
     */
    public void setJobArtifactsSize(Integer jobArtifactsSize) {
        this.jobArtifactsSize = jobArtifactsSize;
    }
}
