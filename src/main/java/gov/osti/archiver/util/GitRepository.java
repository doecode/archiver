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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
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
     * Determine if this is a GIT Repository URL.
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
        try (Git git = Git
            .cloneRepository()
            .setURI(url)
            .setDirectory(Files.
                    createDirectories(path).toFile())
            .setCloneAllBranches(true)
            .call()) {
            // do nothing, just try-with-resources to close file locks
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
     * Attempt to check if the repository's working directory is clear of
     * changes. 
     * 
     * @param project the Project to check
     * @return a Boolean describing if the directory is clean
     * @throws IOException on API or other IO error
     */
    public static boolean isClean(Project project) throws Exception {
        Git gud = null;
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            File cf = new File(project.getCacheFolder());
            Repository repo = builder.setWorkTree(cf).findGitDir(cf).setMustExist(true)
                    .build();
            gud = new Git(repo);
            
            return gud.status().call().isClean();
        } catch (Exception e) {
            log.warn("isClean Error on #" + project.getProjectId());
            throw new Exception(e.getMessage());
        }
    }
    
    /**
     * Attempt to MAINTAIN/PULL the repository in git format. If this DOES NOT throw
     * an IOException, one may assume success.
     * 
     * @param project the Project to maintain
     * @return a String describing the successful process
     * @throws IOException on API or other IO error
     */
    public static String pull(Project project) throws Exception {
        // do a fetch/pull on this
        Git gud = null;
        String pathToProject = "";
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            File cf = new File(project.getCacheFolder());
            Repository repo = builder.setWorkTree(cf).findGitDir(cf).setMustExist(true)
                    .build();
            pathToProject = repo.getDirectory().getAbsolutePath();
            gud = new Git(repo);
            
        
            Ref ref = Git.lsRemoteRepository().setRemote(project.getRepositoryLink()).callAsMap().get("HEAD");
            String branch = ref.getTarget().getName();

            CheckoutCommand chkCmd = gud.checkout();

            ObjectId branchLocal = repo.resolve(branch);

            // if no local branch, we must create
            if (branchLocal == null)
                chkCmd = chkCmd.setCreateBranch(true);

            // perform checkout
            Ref res = chkCmd
                .setName(branch)
                .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
                // .setForceRefUpdate(true) excluding prevents taking commits from branch and overlaying rather than switching. 
                .setStartPoint("origin/" + branch.substring(branch.lastIndexOf('/') + 1))
                .call();

            PullResult result = gud.pull().setRemoteBranchName(branch)   //("origin")
                    .call();
            // return the RESULT information
            return result.toString();
        } catch ( JGitInternalException e ) {
            if(!pathToProject.isEmpty()) {
                File indexLock = new File(pathToProject + "/index.lock");
                if(indexLock.delete()) {
                    log.warn("Successfully deleted index.lock file.");
                }else{
                    log.warn("Failed to delete index.lock file.");
                }
            }
            log.warn("PULL lock file Error on #" + project.getProjectId());
            throw new Exception (e.getMessage());
        }  catch (GitAPIException e) {
            log.warn("PULL API Error on #" + project.getProjectId());
            throw new Exception(e.getMessage());
        } catch (IOException e) {
            log.warn("PULL IO Error on #" + project.getProjectId());
            throw e;
        } catch (Exception e) {
            log.warn("PULL Error on #" + project.getProjectId());
            throw e;
        } finally {
            // clean up if necessary
            if (null != gud)
                gud.close();
        }
    }

    /**
     * Attempt to MAINTAIN/RESET the repository in git format. If this DOES NOT
     * throw an IOException, one may assume success.
     * 
     * @param project the Project to maintain
     * @return a String describing the successful process
     * @throws IOException on API or other IO error
     */
    public static String reset(Project project) throws Exception {
        // do a fetch/reset on this
        Git gud = null;
        String pathToProject = "";
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            File cf = new File(project.getCacheFolder());
            Repository repo = builder.setWorkTree(cf).findGitDir(cf).setMustExist(true)
                    .build();
            pathToProject = repo.getDirectory().getAbsolutePath();
            gud = new Git(repo);

            Ref ref = Git.lsRemoteRepository().setRemote(project.getRepositoryLink()).callAsMap().get("HEAD");
            // first doa  fetch
            gud.fetch()
                .setCheckFetchedObjects(true)
                .setRemoveDeletedRefs(true)
                .setForceUpdate(true)
                .call();

            // attempt to determine what the origin head commit is
            ObjectId originHead = ref.getObjectId();
            // reset to HEAD
            Ref result = gud.reset()
                .setMode(ResetType.HARD)
                .setRef(originHead.getName())
                .call();

            return result.toString();
        } catch ( JGitInternalException e ) {
            //delete lock file, hopefully fixes error for future runs
            if(!pathToProject.isEmpty()) {
                File indexLock = new File(pathToProject + "/index.lock");
                if(indexLock.delete()) {
                    log.warn("Successfully deleted index.lock file.");
                }else{
                    log.warn("Failed to delete index.lock file.");
                }
            }
            log.warn("RESET lock file Error on #" + project.getProjectId());
            throw new Exception (e.getMessage());
        }  catch ( GitAPIException e ) {
            log.warn("RESET API Error on #" + project.getProjectId());
            throw new Exception (e.getMessage());
        } catch ( IOException e ) {
            log.warn("RESET IO Error on #" + project.getProjectId());
            throw e;
        } catch ( Exception e ) {
            log.warn("RESET Error on #" + project.getProjectId());
            throw e;
        } finally {
            // clean up if necessary
            if (null!=gud)
                gud.close();
        }
    }

    /**
     * Attempt to MAINTAIN/CHECKOUT the repository in git format. If this DOES NOT
     * throw an IOException, one may assume success.
     * 
     * @param project the Project to maintain
     * @return a String describing the successful process
     * @throws IOException on API or other IO error
     */
    public static String checkout(Project project) throws Exception {
        // checkout the branch, with tracking
        Git gud = null;
        String branch = "master";
        String pathToProject = "";
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            File cf = new File(project.getCacheFolder());
            Repository repo = builder.setWorkTree(cf).findGitDir(cf).setMustExist(true)
                    .build();
            pathToProject = repo.getDirectory().getAbsolutePath();
            gud = new Git(repo);
            
            Ref ref = Git.lsRemoteRepository().setRemote(project.getRepositoryLink()).callAsMap().get("HEAD");
            branch = ref.getTarget().getName();

            // checkout cmd
            CheckoutCommand chkCmd = gud.checkout();

            ObjectId branchLocal = repo.resolve(branch);

            // if no local branch, we must create
            if (branchLocal == null)
                chkCmd = chkCmd.setCreateBranch(true);

            // perform checkout
            Ref result = chkCmd
                .setName(branch)
                .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
                .setForceRefUpdate(true)
                .setStartPoint("origin/" + branch.substring(branch.lastIndexOf('/') + 1))
                .call();
            
            return result.toString();
        } catch ( JGitInternalException e ) {
            if(!pathToProject.isEmpty()) {
                File indexLock = new File(pathToProject + "/index.lock");
                if(indexLock.delete()) {
                    log.warn("Successfully deleted index.lock file.");
                }else{
                    log.warn("Failed to delete index.lock file.");
                }
            }
            log.warn("CHECKOUT lock file Error on #" + project.getProjectId());
            throw new Exception (e.getMessage());
        } catch ( GitAPIException e ) {
            log.warn("CHECKOUT API Error on #" + project.getProjectId());
            throw new Exception (e.getMessage());
        } catch ( IOException e ) {
            log.warn("CHECKOUT IO Error on #" + project.getProjectId());
            throw e;
        } catch ( Exception e ) {
            log.warn("CHECKOUT Error on #" + project.getProjectId());
            throw e;
        } finally {
            // clean up if necessary
            if (null!=gud)
                gud.close();
        }
    }
}