/*
 */
package gov.osti.archiver.util;

import gov.osti.archiver.listener.ServletContextListener;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.codec.binary.Base64;

/**
 * Common HTTP-related utilities, such as HttpClient-based web requests for API
 * information.
 * 
 * @author ensornl
 */
public class HttpUtil {
    // logger
    protected static final Logger log = LoggerFactory.getLogger(HttpUtil.class);

    /** authentication information for accessing GitHub API **/
    private static String API_KEY = "";
    private static String API_USER = "";
    
    /**
     * Construct a GET request to the GitHub API.
     * 
     * @param url the base URL to use
     * @return an HttpGet Object to read project information from
     */
    private static HttpGet gitHubAPIGet(String url) {
        API_KEY = ServletContextListener.getConfigurationProperty("github.apikey");
        API_USER = ServletContextListener.getConfigurationProperty("github.user");

        HttpGet get = new HttpGet(url);
        // if authenticated, pass basic authentication header information
        // prevents API access limitations if authenticated
        if ( !"".equals(API_USER) ) {
            String authentication = API_USER + ":" + API_KEY;
            byte[] encoded = Base64.encodeBase64(authentication.getBytes(Charset.forName("ISO-8859-1")));
            get.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + new String(encoded));
        }
        return get;
    }
    
    /**
     * Perform an initial checkout of a Project.
     * 
     * @param url the REPOSITORY URL
     * @param path the Path to the output cache folder
     * @throws IOException on IO errors
     */
    public static void downloadTaggedRelease(String url, Path destination) throws IOException {
        try {
            // create structure as needed
            Files.createDirectories(destination.getParent());

            HttpGet get = gitHubAPIGet(url);

            // create an HTTP client to request through
            CloseableHttpClient hc = 
                    HttpClientBuilder
                    .create()
                    .setDefaultRequestConfig(RequestConfig
                        .custom()
                        .setConnectTimeout(60000)
                        .setConnectionRequestTimeout(60000)
                        .setSocketTimeout(60000)
                        .build())
                    .build();

            try (CloseableHttpResponse hr = hc.execute(get)) {
                HttpEntity entity = hr.getEntity();
                if (entity != null) {
                    try (FileOutputStream outstream = new FileOutputStream(destination.toFile())) {
                        entity.writeTo(outstream);
                    }
                }
            }

        } catch ( Exception e ) {
            log.warn("Tag Release download for URL: " + url + " failed: " + e.getMessage());
            throw new IOException("Download Failure: " + e.getMessage());
        }
    }
}
