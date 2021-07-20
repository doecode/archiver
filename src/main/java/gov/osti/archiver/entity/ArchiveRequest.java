/*
 */
package gov.osti.archiver.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Simple POJO defining an ARCHIVE REQUEST.
 * 
 * Should contain a CODE ID and possibly a REPOSITORY URL value.
 * 
 * @author ensornl
 */
@JsonIgnoreProperties (ignoreUnknown = true)
public class ArchiveRequest {
    private Long codeId;
    private String repositoryLink;
    private String lastEditor;

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
     * @return the repositoryLink value
     */
    public String getRepositoryLink() {
        return repositoryLink;
    }

    /**
     * @param repositoryLink the repositoryUrl to set
     */
    public void setRepositoryLink(String repositoryLink) {
        this.repositoryLink = repositoryLink;
    }

    /**
     * @return the lastEditor email
     */
    public String getLastEditor() {
        return lastEditor;
    }

    /**
     * @param lastEditor the lastEditor email
     */
    public void setLastEditor(String lastEditor) {
        this.lastEditor = lastEditor;
    }
}
