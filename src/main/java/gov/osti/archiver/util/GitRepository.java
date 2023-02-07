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

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
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
            Repository repo = builder.setWorkTree(new File(project.getCacheFolder())).findGitDir().setMustExist(true)
                    .build();
            pathToProject = repo.getDirectory().getAbsolutePath();
            gud = new Git(repo);
            PullResult result = gud.pull().setRemote("origin")
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
            Repository repo = builder.setWorkTree(new File(project.getCacheFolder())).findGitDir().setMustExist(true)
                    .build();
            pathToProject = repo.getDirectory().getAbsolutePath();
            gud = new Git(repo);

            // first doa  fetch
            gud.fetch()
            .setCheckFetchedObjects(true)
            .setRemoveDeletedRefs(true)
            .setForceUpdate(true)
            .call();

            // attempt to determine what the origin head commit is
            ObjectId originHead = getOriginHead(repo);

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
            Repository repo = builder.setWorkTree(new File(project.getCacheFolder())).findGitDir().setMustExist(true)
                    .build();
            pathToProject = repo.getDirectory().getAbsolutePath();
            gud = new Git(repo);

            // identify remote branches, if exist
            ObjectId masterRemote = repo.resolve("refs/remotes/origin/master");
            ObjectId mainRemote = repo.resolve("refs/remotes/origin/main");

            // if we couldn't find master/main, then we don't know how to proceed automatically
            if (masterRemote == null && mainRemote == null)
                throw new Exception("Unable to locate remote master/main branch!");

            // if master doesn't exist, use main if it does exist
            if (masterRemote == null)
                branch = "main";

            // checkout cmd
            CheckoutCommand chkCmd = gud.checkout();

            ObjectId branchLocal = repo.resolve("refs/heads/" + branch);

            // if no local branch, we must create
            if (branchLocal == null)
                chkCmd = chkCmd.setCreateBranch(true);

            // perform checkout
            Ref result = chkCmd
                .setName(branch)
                .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
                .setForceRefUpdate(true)
                .setStartPoint("origin/" + branch)
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

    /**
     * Attempt to locate the HEAD from Origin for the proper branch.
     * 
     * @param repo the Repository to evaluate
     * @return an ObjectId of the HEAD commit
     * @throws IOException on API or other IO error
     */
    private static ObjectId getOriginHead(Repository repo) throws Exception {
        // do a fetch/reset on this
        Git gud = null;
        try {
            gud = new Git(repo);

            ObjectId originHead = repo.resolve(Constants.R_REMOTES + "origin/" + Constants.HEAD);

            // if no remote HEAD is found, attempt to find master/main
            if (originHead == null) {
                ObjectId masterRemote = repo.resolve("refs/remotes/origin/master");
                ObjectId mainRemote = repo.resolve("refs/remotes/origin/main");

                // use master if exists, else main
                if (masterRemote != null) {
                    originHead = masterRemote;
                } else if (mainRemote != null) {
                    originHead = mainRemote;
                }
            }

            // if still no HEAD, attempt one last effort to brute force it
            if (originHead == null) {
                Collection<Ref> refList = gud.lsRemote().call();
                for (Ref ref : refList) {
                    if (ref == null)
                        continue;

                    Ref leaf = ref.getLeaf();
                    if (leaf == null)
                        continue;

                    if (leaf.getName().equals("HEAD")) {
                        String refId = ref.getObjectId().getName();
                        log.warn("Brute Forced HEAD location: " + refId);
                        originHead = ref.getObjectId();
                        break;
                    }
                }
            }

            // if still no HEAD, we have no way to proceed automatically.
            if (originHead == null)
                throw new Exception("Unable to determine Origin Head!");
                
            return originHead;
        } catch ( Exception e ) {
            log.warn("ORIGIN HEAD Error for repo: " + repo.getFullBranch());
            throw e;
        } finally {
            // clean up if necessary
            if (null!=gud)
                gud.close();
        }
    }
}
