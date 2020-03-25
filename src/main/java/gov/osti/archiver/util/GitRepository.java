/*
 */
package gov.osti.archiver.util;

import gov.osti.archiver.entity.Project;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ensornl
 */
public class GitRepository {
    private static final Logger log = LoggerFactory.getLogger(GitRepository.class);

    /** GitHub API base URL **/
    private static final String GITHUB_BASE_URL = "https://api.github.com/repos/";
    
    /**
     * Determine if this is a GIT repository URL.
     * 
     * @param url the URL to check
     * @return true if GIT, false if not
     */
    public static boolean detect(String url) {
        // for GIT repos, append ".git" as a suffix
        url = url.replaceFirst("(?:\\/|[.]git)?$", ".git");

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
     * Attempt to identify the PROJECT NAME from the given URL.  
     * 
     * Criteria:  URL host should contain "github.com"; the project is assumed
     * to be the first two components of the PATH, splitting on the slash.  
     * (owner/project)
     * 
     * @param url the URL to process
     * @return the PROJECT NAME if able to parse; null if not, or unrecognized
     * URL
     */
    private static String getProjectFromUrl(String url) {
        try {
            String safeUrl = (null==url) ? "" : url.trim();
            // no longer assuming protocol, must be provided
            URI uri = new URI(safeUrl);
            
            // protection against bad URL input
            if (null!=uri.getHost()) {
                if (uri.getHost().contains("github.com")) {
                    String path = uri.getPath();
        
                    Pattern pattern = Pattern.compile("^([^\\/\\s]+\\/[^\\/\\s]+)");
                    Matcher matcher = pattern.matcher(path.substring(path.indexOf("/") + 1));
            
                    if(matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        } catch ( URISyntaxException e ) {
            // warn that URL is not a valid URI
            log.warn("Not a valid URI: " + url + " message: " + e.getMessage());
        } catch ( Exception e ) {
            // some unexpected error happened
            log.warn("Unexpected Error from " + url + " message: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Attempt to identify the TAG DOWNLOAD URL from the given URL.  
     * 
     * Criteria:  URL host should contain "github.com"
     * 
     * @param url the URL to process
     * @return the URL if able to parse; null if not, or unrecognized URL
     */
    public static String getTagDownloadUrl(String url) {
        // try to identify the NAME of the project
        String project = getProjectFromUrl(url);
        if (null==project)
            return null;

        // try to identify the TAG of the project
        String tag = getTagFromUrl(url);
        if (null==tag)
            return null;
        
        return GITHUB_BASE_URL + project + "/tarball/tags/" + tag;
    }
    
    /**
     * Attempt to identify the TAG NAME from the given URL.  
     * 
     * Criteria:  URL host should contain "github.com"
     * 
     * @param url the URL to process
     * @return the TAG NAME if able to parse; null if not, or unrecognized URL
     */
    public static String getTagFromUrl(String url) {
        String tag = null;
        
        Pattern pattern = Pattern.compile("github[.]com.*\\/releases\\/tag\\/(.*)");
        Matcher matcher = pattern.matcher(url);

        if(matcher.find()) {
            tag = matcher.group(1);
        }

        return tag;
    }
    
    /**
     * Attempt to identify if URL is a TAGGED RELEASE.  
     * 
     * Criteria:  URL host should contain "github.com"
     * 
     * @param url the URL to process
     * @return the TRUE/FALSE value of the result
     */
    public static boolean isTaggedRelease(String url) {
        return !StringUtils.isEmptyOrNull(getTagFromUrl(url));
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
