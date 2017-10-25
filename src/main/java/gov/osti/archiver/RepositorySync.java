/*
 */
package gov.osti.archiver;

import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import java.io.File;
import java.io.IOException;
import javax.persistence.EntityManager;
import javax.servlet.Servlet;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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
                    if (Project.RepositoryType.Git.equals(project.getRepositoryType())) {
                        em.getTransaction().begin();
                        
                        p.setMaintenanceStatus(Project.Status.Processing);
                        p.setDateLastMaintained();
                        
                        // do a fetch/pull on this
                        try {
                            FileRepositoryBuilder builder = new FileRepositoryBuilder();
                            Repository repo = builder
                                    .setWorkTree(new File(project.getCacheFolder()))
                                    .findGitDir()
                                    .setMustExist(true)
                                    .build();
                            Git gud = new Git(repo);
                            PullResult result = gud.pull()
                                    .setRemote("origin")
                                    .call();
                            // update the Project information
                            p.setMaintenanceStatus(Project.Status.Complete);
                            p.setMaintenanceMessage(result.toString());
                        } catch ( GitAPIException e ) {
                            log.warn("Error Fetching #" + project.getProjectId(), e);
                            p.setMaintenanceStatus(Project.Status.Error);
                            p.setMaintenanceMessage(e.getMessage());
                        } catch ( IOException e ) {
                            log.warn("IO Error on #" + project.getProjectId(), e);
                            p.setMaintenanceStatus(Project.Status.Error);
                            p.setMaintenanceMessage(e.getMessage());
                        }
                        // store to the database
                        em.getTransaction().commit();
                    }
                } else {
                    log.warn("Project #" + project.getProjectId() + " not marked Complete.");
                }
            }
        } finally {
            // all done with this one
            callback.completed(project);
            em.close();
        }
    }
}
