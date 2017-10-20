/*
 */
package gov.osti.archiver;

import gov.osti.archiver.entity.Project;
import java.io.File;
import java.io.IOException;
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
        
        try {
            if (null!=project) {
                // check the Project
                if (Project.Status.Complete.equals(project.getStatus())) {
                    switch ( project.getRepositoryType() ) {
                        case Git:
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
                            // TODO : parse and handle Results per Project
                            break;
                    }
                } else {
                    log.warn("Project #" + project.getProjectId() + " not marked Complete.");
                }
            }
        } catch ( GitAPIException e ) {
            log.warn("Error Fetching #" + project.getProjectId(), e);
        } catch ( IOException e ) {
            log.warn("IO Error on #" + project.getProjectId(), e);
        } finally {
            // all done with this one
            callback.completed(project);
        }
    }
}
