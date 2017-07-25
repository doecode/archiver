/*
 */
package gov.osti.archiver;

import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Archive worker Thread.
 * 
 * Attempts to import a given Project into GitLab if possible.
 * 
 * @author ensornl
 */
public class Archiver extends Thread {
    // logger
    private static Logger log = LoggerFactory.getLogger(Archiver.class);
    // the Project to archive
    private Project project;
    // configuration parameters
    private static String GITLAB_URL = ServletContextListener.getConfigurationProperty("gitlab.url");
    private static String GITLAB_APIKEY = ServletContextListener.getConfigurationProperty("gitlab.apikey");
    private static String GITLAB_API_BASE = "/api/v3/projects";
    private static String DEFAULT_VISIBILITY = "internal";
    
    public Archiver(Project p) {
        project = p;
    }
    
    /**
     * Perform the Archiving jobs to get Project into GitLab.
     */
    @Override
    public void run() {
        // abort if not set up properly
        if (null==project) {
            log.warn("No Project information set to archive.");
            return;
        }
        
        // must have a GITLAB URL
        if ("".equals(GITLAB_URL)) {
            log.warn("GitLab archive instance not set up properly.");
            return;
        }
        
        // set some reasonable default timeouts
        RequestConfig rc = RequestConfig.custom().setSocketTimeout(5000).setConnectTimeout(5000).build();
        // create an HTTP client to request through
        CloseableHttpClient hc = 
                HttpClientBuilder
                .create()
                .setDefaultRequestConfig(rc)
                .build();
        // database interface
        EntityManager em = ServletContextListener.createEntityManager();

        try {
            // attempt to look up the Project
            Project p = em.find(Project.class, project.getCodeId());
            
            if (null==p) {
                log.warn("Project " + project.getCodeId() + " is not on file.");
                return;
            }
            // if PROJECT already exists on GITLAB (project ID is set) abort
            if (null!=p.getProjectId()) {
                log.info ("Project CODE ID " + p.getCodeId() + " is already on GitLab #" + p.getProjectId());
                return;
            }
            
            // attempt to post this to GITLAB
            HttpPost request = new HttpPost(GITLAB_URL + GITLAB_API_BASE);
            request.addHeader("PRIVATE-TOKEN", GITLAB_APIKEY);

            List<NameValuePair> parameters = new ArrayList<>();
            parameters.add(new BasicNameValuePair("visibility", DEFAULT_VISIBILITY));
            parameters.add(new BasicNameValuePair("name", project.getProjectName()));
            parameters.add(new BasicNameValuePair("description", project.getProjectDescription()));
            parameters.add(new BasicNameValuePair("import_url", project.getRepositoryLink()));
            request.setEntity(new UrlEncodedFormEntity(parameters));

            CloseableHttpResponse response = hc.execute(request);

            em.getTransaction().begin();
            
            if ( HttpStatus.SC_CREATED==response.getStatusLine().getStatusCode() ) {
                Repository message = Repository.fromJson(EntityUtils.toString(response.getEntity()));
                
                log.info("POSTED PROJECT: " + message.getId() + " name=" + message.getName());
                log.info("OWNED BY " + message.getOwner().getName());
                
                // store the information needed
                p.setProjectId(message.getId());
                // if GitLab imported better information, use it
                if (null==p.getProjectDescription())
                    p.setProjectDescription(message.getDescription());
                if (null==p.getProjectName())
                    p.setProjectName(message.getName());
                p.setStatusMessage("CREATED");
                p.setStatus(Project.Status.Complete);
                em.persist(p);
            } else {
                ErrorMessage errors = ErrorMessage.fromJson(EntityUtils.toString(response.getEntity()));

                // report the error message
                log.error("Error posting to GitLab: " + errors.getErrorMessage());
                
                p.setStatus(Project.Status.Error);
                p.setStatusMessage(errors.getErrorMessage());
                em.persist(p);
            }
            
            em.getTransaction().commit();
        } catch ( UnsupportedOperationException | UnsupportedEncodingException e ) {
            log.error("URL Encoding Error: " + e.getMessage());
        } catch ( IOException e ) {
            log.error("GitLab Communication Error: " + e.getMessage());
        } finally {
            try {
                hc.close(); 
                em.close();
            } catch ( IOException e ) {
                log.warn("Close Error: " + e.getMessage());
            }
        }
    }
}
