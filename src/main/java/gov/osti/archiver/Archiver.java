/*
 */
package gov.osti.archiver;

import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import gov.osti.archiver.util.Extractor;
import gov.osti.archiver.util.GitRepository;
import gov.osti.archiver.util.SubversionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import javax.persistence.EntityManager;
import org.apache.commons.compress.archivers.ArchiveException;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Archive worker Thread.
 * 
 * Attempts to cache a given Project if possible.
 * 
 * @author ensornl
 */
public class Archiver extends Thread {
    // logger
    private static Logger log = LoggerFactory.getLogger(Archiver.class);
    // base filesystem path to save information into
    private static String FILE_BASEDIR = ServletContextListener.getConfigurationProperty("file.archive");
    // the Project to archive
    private Project project;
    
    public Archiver(Project p) {
        project = p;
    }
    
    /**
     * Perform the Archiving jobs to get Project.
     * 
     * For FILE UPLOADS:
     * 1. unpack into a folder
     * 2. create a git repository bare on it
     * 3. done
     * 
     * For REPOSITORY UPLOADS:
     * 1. call external processes to attempt to download/cache/mirror
     * 2. done
     * 
     */
    @Override
    public void run() {
        // abort if not set up properly
        if (null==project) {
            log.warn("No Project information set to archive.");
            return;
        }
        // database interface
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            // attempt to look up the Project
            Project p = em.find(Project.class, project.getProjectId());
            
            if (null==p) {
                log.warn("Project " + project.getProjectId() + " is not on file.");
                return;
            }
            
            // start a data transaction
            em.getTransaction().begin();

            // if project is a Container, set cache folder, nothing else to do
            if (Project.RepositoryType.Container.equals(project.getRepositoryType())) {
                Path path = Paths
                        .get(FILE_BASEDIR,
                                String.valueOf(project.getProjectId()));

                p.setCacheFolder(path.toString());
            }
            else {
                /**
                 * File uploads are to be extracted to a temporary folder, a git
                 * repository created from the contents.
                 *
                 * Handled in the Extractor.  What comes back should be the base file
                 * path for the newly-created git repository from the archive contents.
                 *
                 * If this has some sort of IO error, record that fact and abort.
                 */
                if (StringUtils.isEmptyOrNull(project.getRepositoryLink()) &&
                    !StringUtils.isEmptyOrNull(project.getFileName())) {
                    try {
                        p.setRepositoryType(Project.RepositoryType.File);
                        p.setCacheFolder(Extractor.uncompressArchive(project));
                    } catch ( IOException | ArchiveException e ) {
                        log.warn("Archive extraction error: "+ e.getMessage());
                        p.setStatus(Project.Status.Error);
                        p.setStatusMessage("Archive Error: " + e.getMessage());
                        em.persist(p);
                        em.getTransaction().commit();
                        return;
                    }
                } else if (!StringUtils.isEmptyOrNull(project.getRepositoryLink())) {
                    // set up a CACHE FOLDER and CREATE if necessary
                    try {
                        Path path = Paths
                                .get(FILE_BASEDIR,
                                        String.valueOf(project.getProjectId()),
                                        UUID.randomUUID().toString());
                        Files.createDirectories(path);

                        p.setCacheFolder(path.toString());

                        // attempt to DETECT the REPOSITORY TYPE
                        if (GitRepository.detect(project.getRepositoryLink())) {
                            // for GIT repos, append ".git" as a suffix
                            p.setRepositoryLink(project.getRepositoryLink().replaceFirst("(?:\\/|[.]git)?$", ".git"));

                            p.setRepositoryType(Project.RepositoryType.Git);
                            GitRepository.clone(p.getRepositoryLink(), path);
                        } else if (SubversionRepository.detect(project.getRepositoryLink())) {
                            p.setRepositoryType(Project.RepositoryType.Subversion);
                            SubversionRepository.clone(project.getRepositoryLink(), path);
                        } else {
                            throw new IOException ("Unable to determine REPOSITORY TYPE");
                        }
                    } catch ( IOException e ) {
                        log.warn("IO Error checking out " + project.getRepositoryLink() + ": " + e.getMessage());
                        p.setStatus(Project.Status.Error);
                        p.setStatusMessage("Checkout IO Error");
                        em.persist(p);
                        em.getTransaction().commit();
                        return;
                    }
                } else {
                    log.warn("Archiver Request with no action specified: " + project.getProjectId());
                }
            }
            
            // post the changes
            p.setStatus(Project.Status.Complete);
            p.setStatusMessage("CREATED");
            em.persist(p);
            
            em.getTransaction().commit();

            // kick off labor hour calculation for file/repo in a new thread
            ServletContextListener.callLaborCalculation(p);
        } finally {
            // dispose of the EntityManager
            em.close();
        }
    }
}
