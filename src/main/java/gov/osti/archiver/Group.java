/*
 */
package gov.osti.archiver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Group permissions and information for a Repository/Project.
 * 
 * @author ensornl
 */
@JsonIgnoreProperties (ignoreUnknown = true)
public class Group {
    private String groupName;
    private Integer groupId;
    private Integer groupAccessLevel;

    /**
     * @return the groupName
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * @param groupName the groupName to set
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    /**
     * @return the groupId
     */
    public Integer getGroupId() {
        return groupId;
    }

    /**
     * @param groupId the groupId to set
     */
    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    /**
     * @return the groupAccessLevel
     */
    public Integer getGroupAccessLevel() {
        return groupAccessLevel;
    }

    /**
     * @param groupAccessLevel the groupAccessLevel to set
     */
    public void setGroupAccessLevel(Integer groupAccessLevel) {
        this.groupAccessLevel = groupAccessLevel;
    }
    
}
