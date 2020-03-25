/*
 */
package gov.osti.archiver;

import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static singleton Class to run archiver labor hour tasks in separate threads.
 * 
 * Instantiates once, is either active or not, and contains status of all the
 * current running labor hour tasks.
 * 
 * @author sowerst
 */
public class LaborCalculator {
   // singleton instance (lazy instantiation)
    private static LaborCalculator instance = null;
    // instance variables
    private Long projectCount = 0l;
    private Long finishedCount = 0l;
    // thread pool for tasks
    private ExecutorService threadPool;
    // for setting up thread pools
    private static final int MAX_THREADS = 5;
    // thread pool tasks
    private List<Future<?>> tasks = new ArrayList<>();
    
    private LaborCalculator() {
        // do not instantiate outside newInstance call
        
        // set up a basic Thread pool for background tasks
        threadPool = Executors.newFixedThreadPool(MAX_THREADS);
    }
    
    /**
     * Acquire the Singleton instance for this Service.
     * @return the LaborCalculator instance
     */
    public static LaborCalculator getInstance() {
        if (null==instance) {
            instance = new LaborCalculator();
        }
        
        return instance;
    }
    
    /**
     * Call this to clean up after ourselves, usually at application unload.
     */
    public static void close() {
        if (null!=instance) {
            instance.threadPool.shutdown();
        }
    }
    
    /**
     * Determine whether or not a LaborCalculator is running active tasks.
     * Check the background thread pool for active tasks; if none exist,
     * empty the list.
     * 
     * @return true if active threads are running, false if not
     */
    public boolean isActive() {
        boolean allDone = true;
        
        // examine each Future task status
        for ( Future<?> task : tasks ) {
            allDone &= task.isDone();
        }
        
        // no more active tasks, clear the pool
        if (allDone)
            tasks.clear();
        
        // send back the pool status
        return !allDone;
    }
    
    /**
     * Set the TOTAL number of Projects to process
     * @param count the PROJECT COUNT
     */
    protected void setProjectCount(Long count) {
        this.projectCount = count;
    }
    
    /**
     * Get the PROJECT COUNT
     * @return the COUNT of Projects being processed
     */
    public Long getProjectCount() {
        return this.projectCount;
    }
    
    public Long getFinishedCount() {
        return this.finishedCount;
    }
    
    /**
     * Callback hook for completing Project work.
     * 
     * @param p the Project just completed
     */
    public void completed(Project p) {
        // for now, just increment the count of completed projects
        ++finishedCount;
    }
    
    /**
     * Start the labor hour calculation tasks, if not already running.
     */
    public void start() {
        if (!isActive()) {
            // reset the counter
            this.finishedCount = 0l;
            // acquire the List of Projects on which to calculate labor hours
            EntityManager em = ServletContextListener.createEntityManager();
            try {
                List<Project.RepositoryType> repositoryTypesNonFiles = new ArrayList<>();
                repositoryTypesNonFiles.add(Project.RepositoryType.File);
                repositoryTypesNonFiles.add(Project.RepositoryType.Container);
                repositoryTypesNonFiles.add(Project.RepositoryType.TaggedRelease);
                
                List<Project.RepositoryType> repositoryTypesFiles = new ArrayList<>();
                repositoryTypesFiles.add(Project.RepositoryType.File);
                repositoryTypesFiles.add(Project.RepositoryType.TaggedRelease);

                // query up the projects set to process (FILE or REPOSITORY requiring Labor Calculation)
                TypedQuery<Project> projectQuery = em.createNamedQuery("Project.findLaborHourReady", Project.class)
                        .setParameter("typesNonFiles", repositoryTypesNonFiles)
                        .setParameter("typesFiles", repositoryTypesFiles)
                        .setParameter("status", Project.Status.Complete);
                List<Project> projects = projectQuery.getResultList();

                // get the count of active projects to process (for Labor Calculation)
                setProjectCount((long) (projects == null ? 0 : projects.size()));

                // add each to the thread pool
                projects.stream().forEach(project->{
                    tasks.add(threadPool.submit(new LaborHoursSync(project, this)));
                });
            } finally {
                em.close();
            }
        }
    }
}
