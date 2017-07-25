/*
 */
package gov.osti.archiver.listener;

import gov.osti.archiver.Archiver;
import gov.osti.archiver.entity.Project;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.servlet.ServletContextEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web application lifecycle listener.
 * 
 * Handles persistence unit and entity managers.
 *
 * @author ensornl
 */
public class ServletContextListener implements javax.servlet.ServletContextListener {
    // a Logger instance
    private static final Logger log = LoggerFactory.getLogger(ServletContextListener.class);

    // the database entity manager
    private static EntityManagerFactory emf;
    // Map of configured service parameters
    private static Properties configuration;
    // name of properties configuration file on the classpath
    private static final String PROPERTIES_FILE = "archiver.properties";
    // background Thread pool for archive processing
    private static ExecutorService threadPool;
    
    /**
     * Obtain the named configuration property from the "properties"
     * configuration file, if possible.
     * 
     * @param key the KEY name requested
     * @return the VALUE if found in the configuration properties, or blank
     * if not found or not set
     */
    public static String getConfigurationProperty(String key) {
        // lazy-load first time
        if (null==configuration) {
            configuration = new Properties(); // create a new instance
            InputStream in; // read from the ClassLoader
            
            try {
                in = ServletContextListener.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE);
                if (null!=in) configuration.load(in);
            } catch ( IOException e ) {
                log.warn("Context Initialization Failure: " + e.getMessage());
            }
        }
        // if the KEY is present, and DOES NOT start with "$", return it
        // otherwise, get an empty String
        return  configuration.containsKey(key) ?
                configuration.getProperty(key).startsWith("$") ?
                "" : configuration.getProperty(key) :
                "";
    }
    
    /**
     * Background Thread to perform Archive processes out-of-band.
     * 
     * @param project the Project to archive
     */
    public static void callArchiver(Project project) {
        if (null==threadPool) {
            threadPool = Executors.newFixedThreadPool(10);
        }
        
        threadPool.submit(new Archiver(project));        
    }

    /**
     * Called on application startup.
     * 
     * initialize the database persistence unit.
     * 
     * @param sce the ContextEvent to read parameters from
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // attempt to load the persistence layer
        String persistence_unit = sce.getServletContext().getInitParameter("persistence_unit");
        emf = Persistence.createEntityManagerFactory(persistence_unit);
        
        log.info("Archiver services started.");
    }

   
    /**
     * Called when application is shut down.
     * 
     * Release any resources held.
     * 
     * @param sce Event causing this shutdown
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("Shutting down Archiver services.");
        
        if (null!=emf)
            emf.close();
        if (null!=threadPool)
            threadPool.shutdown();
    }
    
    /**
     * Acquire an EntityManager for persistence operations.  Handling the resulting
     * EntityManager is the responsibility of the caller.  Make sure it is closed
     * appropriately.
     * 
     * @return an EntityManager from the Factory if possible
     */
    public static EntityManager createEntityManager() {
        if (null==emf)
            throw new IllegalStateException("Context not initialized!");
        
        return emf.createEntityManager();
    }
}
