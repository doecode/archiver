/*
 */
package gov.osti.archiver.util;

import gov.osti.archiver.entity.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnCleanup;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

/**
 *
 * @author ensornl
 */
public class SubversionRepository {
    private static final Logger log = LoggerFactory.getLogger(SubversionRepository.class);
    
    /**
     * Assert validity of a URL as a GIT REPOSITORY.
     * 
     * @param url the URL to check
     * @return true if this URL points to a git repository, false if not, or unable to tell
     */
    public static boolean detect(String url) {
        SVNRepository repository = null;
        
        try {
            SVNURL repoUrl = SVNURL.parseURIEncoded(url);
            repository = SVNRepositoryFactory.create(repoUrl);
            
            Collection logs = repository.log(new String[] { "" }, null, 0, -1, true, true);
            
            // if we have some sort of log entry (even initial import), we are valid
            return !logs.isEmpty();
        } catch ( Exception e ) {
            log.warn("SVN Error for " + url + ": " + e.getMessage());
            return false;
        } finally {
            if (null!=repository)
                repository.closeSession();
        }
    }
    
    public static void clone(String url, Path path) throws IOException {
        SvnOperationFactory factory = new SvnOperationFactory();
        
        try {
            log.info("Checking out " + url + " to " + path.toString());
            final SvnCheckout checkout = factory.createCheckout();
            checkout.setSingleTarget(SvnTarget.fromFile(path.toFile()));
            checkout.setSource(SvnTarget.fromURL(SVNURL.parseURIEncoded(url)));
            
            checkout.run();
            log.info("Completed.");
        } catch ( SVNException e ) {
            log.warn("SVN Error for " + url + ": " + e.getMessage());
            throw new IOException (e.getMessage());
        } finally {
            factory.dispose();
        }
    }
    
    public static String pull(Project project) throws IOException {
        SvnOperationFactory factory = new SvnOperationFactory();
        
        try {
            FSRepositoryFactory.setup();
            
            final SvnUpdate update = factory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(new File(project.getCacheFolder())));
            long[] ids = update.run();
            
            return "Update successful.";
        } catch (SVNException e) {
            log.warn("SVN Update Error for #" + project.getProjectId() + ": " + e.getMessage());
            throw new IOException(e.getMessage());
        } finally {
            factory.dispose();
        }
    }

    public static String cleanup(Project project) throws IOException {
        SvnOperationFactory factory = new SvnOperationFactory();
        
        try {
            FSRepositoryFactory.setup();
            
            final SvnCleanup cleanup = factory.createCleanup();
            cleanup.setSingleTarget(SvnTarget.fromFile(new File(project.getCacheFolder())));
            cleanup.setBreakLocks(true);
            cleanup.run();
            
            return "Cleanup successful.";
        } catch ( SVNException e ) {
            log.warn("SVN Cleanup Error for #" + project.getProjectId() + ": " + e.getMessage());
            throw new IOException (e.getMessage());
        } finally {
            factory.dispose();
        }
    }
}
