/*
 */
package gov.osti.archiver;

import gov.osti.archiver.listener.ServletContextListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 * Manage interface between DOECode and GitLab archiver instance.
 * 
 * API call to populate new projects in GitLab via DOECode project metadata.
 * 
 * @author ensornl
 */
public class GitLab {
    // the base URL to call GITLAB
    private static String GITLAB_URL = ServletContextListener.getConfigurationProperty("gitlab.url");
    // GitLab API base prefix
    private static final String GITLAB_API_BASE = "/api/v3/projects";
    // user key token to POST
    private static final String PRIVATE_TOKEN = ServletContextListener.getConfigurationProperty("gitlab.apikey");
    
    public GitLab() {
        
    }
    
    public void importRepository(Repository repo) throws IOException {
        // set some reasonable default timeouts
        RequestConfig rc = RequestConfig.custom().setSocketTimeout(5000).setConnectTimeout(5000).build();
        // create an HTTP client to request through
        CloseableHttpClient hc = 
                HttpClientBuilder
                .create()
                .setDefaultRequestConfig(rc)
                .build();
        
        try {
            HttpPost request = new HttpPost(GITLAB_URL + GITLAB_API_BASE);
            request.addHeader("PRIVATE-TOKEN", PRIVATE_TOKEN);
            
            List<NameValuePair> parameters = new ArrayList<>();
            parameters.add(new BasicNameValuePair("visibility", (null==repo.getVisibility()) ? Repository.PUBLIC_VISIBILITY : repo.getVisibility()));
            parameters.add(new BasicNameValuePair("name", repo.getName()));
            parameters.add(new BasicNameValuePair("description", repo.getDescription()));
            parameters.add(new BasicNameValuePair("import_url", repo.getRepositoryUrl()));
            request.setEntity(new UrlEncodedFormEntity(parameters));
            
            CloseableHttpResponse response = hc.execute(request);
            
            if ( HttpStatus.SC_CREATED==response.getStatusLine().getStatusCode() ) {
                Repository message = Repository.fromJson(EntityUtils.toString(response.getEntity()));
                
                System.out.println("Posted new Project: " + message.getId() + " name=" + message.getName());
                System.out.println("Owned by " + message.getOwner().getName());
            } else {
                ErrorMessage errors = ErrorMessage.fromJson(EntityUtils.toString(response.getEntity()));
                
                System.out.println("Error Occurred: " + errors.getErrorMessage());
            }
            
        } finally {
            hc.close();
        }
    }
    
    public static Repository getRepository(int projectId) throws IOException {
        // set some reasonable default timeouts
        RequestConfig rc = RequestConfig.custom().setSocketTimeout(5000).setConnectTimeout(5000).build();
        // create an HTTP client to request through
        CloseableHttpClient hc = 
                HttpClientBuilder
                .create()
                .setDefaultRequestConfig(rc)
                .build();
        
        try {
            HttpGet request = new HttpGet(GITLAB_URL + GITLAB_API_BASE + "/" + projectId);
            request.addHeader("PRIVATE-TOKEN", PRIVATE_TOKEN);
            
            System.out.println("Requesting: " + request.toString());
            
            return Repository.fromJson(EntityUtils.toString(hc.execute(request).getEntity()));
        } finally {
            hc.close();
        }
    }
}
