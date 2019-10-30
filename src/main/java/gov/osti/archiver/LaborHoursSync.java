/*
 */
package gov.osti.archiver;

import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import javax.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps calculated Labor Hours "fresh" by synchronizing against latest updates.
 * 
 * @author sowerst
 */
public class LaborHoursSync extends Thread {
    // logger
    private static Logger log = LoggerFactory.getLogger(Archiver.class);
    // the Project to sync Labor Hour calculation
    private Project project;
    // link to background LaborCalculator caller
    private LaborCalculator callback;
    
    public LaborHoursSync(Project p, LaborCalculator instance) {
        project = p;
        callback = instance;
    }
    
    /**
     * Process a single Labor Hour update in a Thread.
     */
    @Override
    public void run() {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            if (null!=project) {
                // find it
                Project p = em.find(Project.class, project.getProjectId());
                if (null==p) {
                    log.warn("Unable to look up Project for Labor Hours: " + project.getProjectId());
                    return;
                }
                // check the Project
                if (Project.Status.Complete.equals(project.getStatus())) {
                    // for NON-CONTAINER type PROJECTS, do a labor hour calculation
                    if (!Project.RepositoryType.Container.equals(project.getRepositoryType())) {
                        // update status, to show we are processing.
                        em.getTransaction().begin();
                        p.setLaborHourStatus(Project.Status.Processing);
                        em.getTransaction().commit();

                        // start a labor hour transaction
                        em.getTransaction().begin();

                        p.calculateLaborHours();
                        
                        // commit the result
                        em.getTransaction().commit();
                    }
                } else {
                    log.warn("Project #" + project.getProjectId() + " Labor Hours not marked Complete.");
                }
            }
        } finally {
            // all done with this one
            callback.completed(project);
            em.close();
        }
    }
}
