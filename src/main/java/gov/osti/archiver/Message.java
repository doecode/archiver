/*
 */
package gov.osti.archiver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error message handler for GitLab API responses.
 * 
 * 
 * {"message":{"route.path":["has already been taken"],"route":["is invalid"],"name":["has already been taken"],"path":["has already been taken"],"base":["Unable to save project. Error: Route path has already been taken, Route is invalid, Name has already been taken, Path has already been taken"],"limit_reached":[]}}
 * @author ensornl
 */
@JsonIgnoreProperties (ignoreUnknown = true)
public class Message {
    @JsonProperty (value = "route.path")
    private String[] routePath;
    private String[] route;
    private String[] name;
    private String[] path;
    private String[] base;
    
    /**
     * @return the routePath
     */
    public String[] getRoutePath() {
        return routePath;
    }

    /**
     * @param routePath the routePath to set
     */
    public void setRoutePath(String[] routePath) {
        this.routePath = routePath;
    }

    /**
     * @return the route
     */
    public String[] getRoute() {
        return route;
    }

    /**
     * @param route the route to set
     */
    public void setRoute(String[] route) {
        this.route = route;
    }

    /**
     * @return the name
     */
    public String[] getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String[] name) {
        this.name = name;
    }

    /**
     * @return the path
     */
    public String[] getPath() {
        return path;
    }

    /**
     * @param path the path to set
     */
    public void setPath(String[] path) {
        this.path = path;
    }

    /**
     * @return the base
     */
    public String[] getBase() {
        return base;
    }

    /**
     * @param base the base to set
     */
    public void setBase(String[] base) {
        this.base = base;
    }
}
