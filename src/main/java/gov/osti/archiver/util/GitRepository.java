/*
 */
package gov.osti.archiver.util;

import gov.osti.archiver.entity.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ensornl
 */
public class GitRepository {
    private static final Logger log = LoggerFactory.getLogger(GitRepository.class);
    
    /**
     * Determine if this is a GIT repository URL.
     * 
     * @param url the URL to check
     * @return true if GIT, false if not
     */
    public static boolean detect(String url) {
        try {
            Collection<Ref> references = Git
                    .lsRemoteRepository()
                    .setHeads(true)
                    .setTags(true)
                    .setRemote(url)
                    .call();
            
            // if we get here with no EXCEPTION, assume it's a VALID REPOSITORY.
            return true;
        } catch ( Exception e ) {
            // jgit occasionally throws sloppy runtime exceptions
            log.warn("Repository URL " + url + " failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Perform an initial checkout of a Project.
     * 
     * @param url the REPOSITORY URL
     * @param path the Path to the output cache folder
     * @throws IOException on IO errors
     */
    public static void clone(String url, Path path) throws IOException {
        try {
            Git git = Git
                .cloneRepository()
                .setURI(url)
                .setDirectory(Files.
                        createDirectories(path).toFile())
                .setCloneAllBranches(true)
                .call();
        } catch ( GitAPIException e ) {
            log.warn("Git for URL: " + url + " failed: " + e.getMessage());
            throw new IOException("Git Failure: " + e.getMessage());
        }
    }
    
    /**
     * Attempt to MAINTAIN/PULL the repository in git format.  If this DOES NOT
     * throw an IOException, one may assume success.
     * 
     * @param project the Project to maintain
     * @return a String describing the successful process
     * @throws IOException on API or other IO error
     */
    public static String pull(Project project) throws IOException {
        // do a fetch/pull on this
        Git gud = null;
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repo = builder
                    .setWorkTree(new File(project.getCacheFolder()))
                    .findGitDir()
                    .setMustExist(true)
                    .build();
            gud = new Git(repo);
            PullResult result = gud.pull()
                    .setRemote("origin")
                    .call();
            // return the RESULT information
            return result.toString();
        } catch ( GitAPIException e ) {
            log.warn("Error Fetching #" + project.getProjectId(), e);
            throw new IOException (e.getMessage());
        } catch ( IOException e ) {
            log.warn("IO Error on #" + project.getProjectId(), e);
            throw e;
        } finally {
            // clean up if necessary
            if (null!=gud)
                gud.close();
        }
    }
}
