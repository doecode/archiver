/*
 */
package gov.osti.archiver;

import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import gov.osti.archiver.util.GitRepository;
import gov.osti.archiver.util.SubversionRepository;
import java.io.IOException;
import javax.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps external repositories "fresh" by synchronizing / fetching updates from
 * remotes, if possible.
 * 
 * @author ensornl
 */
public class RepositorySync extends Thread {
    // logger
    private static Logger log = LoggerFactory.getLogger(Archiver.class);
    // the Project to archive
    private Project project;
    // link to background maintenance caller
    private Maintainer callback;
    
    public RepositorySync(Project p, Maintainer instance) {
        project = p;
        callback = instance;
    }
    
    /**
     * Process a single Repository update in a Thread.
     */
    @Override
    public void run() {
        EntityManager em = ServletContextListener.createEntityManager();

        try {
            if (null!=project) {
                // find it
                Project p = em.find(Project.class, project.getProjectId());
                if (null==p) {
                    log.warn("Unable to look up Project: " + project.getProjectId());
                    return;
                }
                // check the Project
                if (Project.Status.Complete.equals(project.getStatus())) {
                    // for NON-FILE/NON-CONTAINER/NON-TAG type PROJECTS, do a maintenance pass
                    if (!Project.RepositoryType.File.equals(project.getRepositoryType()) && !Project.RepositoryType.Container.equals(project.getRepositoryType()) && !Project.RepositoryType.TaggedRelease.equals(project.getRepositoryType())) {
                        // update status, to show we are processing.
                        em.getTransaction().begin();
                        p.setMaintenanceStatus(Project.Status.Processing);
                        em.getTransaction().commit();

                        // start a maintenance transaction
                        em.getTransaction().begin();
                        
                        p.setDateLastMaintained();
                        
                        switch ( p.getRepositoryType() ) {
                            case Git:
                                // do a fetch/pull on this
                                try {
                                    String result = "Unknown Sync Error.";
                                    boolean forceReset = false;
                                    boolean forceCheckout = false;

                                    // attempt a Pull
                                    try {
                                        if(!GitRepository.isClean(project)) {
                                            result = GitRepository.reset(project);
                                        }

                                        result = GitRepository.pull(project);
                                    } catch ( Exception e ) {
                                        String msg = e.getMessage();
                                        if (msg.toUpperCase().endsWith("MERGING")) {
                                            forceReset = true;
                                            log.warn("PULL encountered MERGING issue on Project #" + project.getProjectId() + "! Forcing RESET!");
                                        }
                                        else if (msg.toUpperCase().startsWith("REMOTE ORIGIN DID NOT ADVERTISE REF")) {
                                            forceReset = true;
                                            forceCheckout = true;
                                            log.warn("PULL encountered REMOTE REF issue on Project #" + project.getProjectId() + "! Forcing RESET!");
                                        }
                                        else {
                                            throw e;
                                        }
                                    }
                                    if (forceReset)
                                        // attempt a Reset
                                        result = GitRepository.reset(project);

                                    if (forceCheckout)
                                        // attempt a Checkout
                                        result = GitRepository.checkout(project);

                                    if (forceReset)
                                        // re-attempt another Pull
                                        result = GitRepository.pull(project);

                                    // if we get here, assume success
                                    p.setMaintenanceStatus(Project.Status.Complete);
                                    p.setMaintenanceMessage(result);
                                } catch ( Exception e ) {
                                    log.warn("Sync Error on #" + project.getProjectId(), e);
                                    p.setMaintenanceStatus(Project.Status.Error);
                                    p.setMaintenanceMessage(e.getMessage());
                                }
                                break;
                                
                            case Subversion:
                                try {
                                    String result = "Unknown Sync Error.";
                                    boolean forceCleanup = false;

                                    try {
                                        result = SubversionRepository.pull(project);
                                    } catch ( Exception e ) {
                                        String msg = e.getMessage();
                                        if (msg.toUpperCase().contains("LOCKED")) {
                                            forceCleanup = true;
                                            log.warn("PULL encountered LOCKING issue on Project #" + project.getProjectId() + "! Forcing RESET!");
                                        }
                                        else
                                            throw e;
                                    }

                                    if (forceCleanup)
                                        // attempt a Reset
                                        result = SubversionRepository.cleanup(project);

                                    if (forceCleanup)
                                        // re-attempt another Pull
                                        result = SubversionRepository.pull(project);
                                    
                                    // assume success
                                    p.setMaintenanceStatus(Project.Status.Complete);
                                    p.setMaintenanceMessage(result);
                                } catch ( IOException e ) {
                                    log.warn("SVN IO Error for #" + project.getProjectId(), e);
                                    p.setMaintenanceStatus(Project.Status.Error);
                                    p.setMaintenanceMessage(e.getMessage());
                                }
                                break;
                                
                            default:
                                p.setMaintenanceStatus(Project.Status.Error);
                                p.setMaintenanceMessage("Unknown Repository Type: " + p.getRepositoryType().name());
                                break;
                        }
                        // commit the result
                        em.getTransaction().commit();
                    }
                } else {
                    log.warn("Project #" + project.getProjectId() + " not marked Complete.");
                }
            }
        } finally {
            // all done with this one
            if (callback != null) {
                callback.completed(project);
            }
            em.close();
        }
    }
}
