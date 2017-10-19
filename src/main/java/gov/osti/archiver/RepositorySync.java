/*
 */
package gov.osti.archiver;

import gov.osti.archiver.entity.Project;
import java.io.IOException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchResult;
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
    
    public RepositorySync(Project p) {
        project = p;
    }
    
    @Override
    public void run() {
        if (null==project) {
            log.warn("No Project to sync.");
            return;
        }
        
        // get a database connection
//        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            // check the Project
            if (Project.Status.Complete.equals(project.getStatus())) {
                switch ( project.getRepositoryType() ) {
                    case Git:
                        log.info("Fetching " + project.getRepositoryLink() + " into " + project.getCacheFolder());
                        Repository repo = new FileRepository(project.getCacheFolder());
                        FetchResult result = Git
                                .wrap(repo)
                                .fetch()
                                .setRemote(project.getRepositoryLink())
                                .call();
                        
                        log.info("Results : " + result.getMessages());
                        break;
                    default:
                        log.warn("Skipping #" + project.getProjectId() + " type=" + project.getRepositoryType());
                        break;
                }
            } else {
                log.warn("Project #" + project.getProjectId() + " not marked Complete.");
            }
        } catch ( GitAPIException e ) {
            log.warn("Error Fetching #" + project.getProjectId(), e);
        } catch ( IOException e ) {
            
        } finally {
//            em.close();
        }
    }
}
