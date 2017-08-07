/*
 */
package gov.osti.archiver.util;

import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Take various input File types of compressed archives and unpack them into a
 * folder, then create a bare git repository from the content.
 * 
 * @author ensornl
 */
public class Extractor {
    // logger
    private static Logger log = LoggerFactory.getLogger(Extractor.class);
    // base file folder containing archive uploads
    private static String FILE_BASEDIR = ServletContextListener.getConfigurationProperty("file.archive");
    // get the GITLAB prefix URL for local uploads
    private static String GITLAB_LOCALURL = ServletContextListener.getConfigurationProperty("gitlab.localurl");
    
    /**
     * Given a Project with a FileName attached, attempt to uncompress the
     * archive file into a sub-folder and create a git repository from its
     * contents.
     * 
     * Filename is considered to be an absolute path; contents will be 
     * uncompressed into a folder based on the configured FILE BASEDIR value,
     * supplemented by the Project CODE ID and base file name.
     * 
     * @param project the Project in question
     * @return the filename path to the git repository created, or null if
     * none/no file to uncompress
     * @throws IOException on file IO errors
     * @throws ArchiveException on uncompress extraction errors
     */
    public static String uncompressArchive(Project project) throws IOException, ArchiveException {
        if (null==project.getFileName())
            return null;
        
        // the project FILE NAME
        String file_name = project.getFileName();
        // get the BASE
        File base_file = new File(file_name);
        if (!base_file.exists())
            throw new IOException ("Unable to read project file name.");
        java.nio.file.Path base_file_path = Paths.get(FILE_BASEDIR, 
                String.valueOf(project.getCodeId()), 
                base_file.getName());
        // use BASE FILE PATH (including NAME) as the EXTRACTION FOLDER
        File parent_folder = base_file_path.toFile();
        // ABORT if PARENT FOLDER EXISTS; might already be contents
        if (parent_folder.exists())
            throw new IOException ("Extraction folder already exists.");
        // attempt to create folder structure
        if (!parent_folder.mkdirs())
            throw new IOException ("Unable to create archive folder for extraction.");
        
        ArchiveInputStream in;
        // what if this is a COMPRESSED ARCHIVE? (e.g., .tar.gz, etc.)
        if (project.getFileName().endsWith(".gz")) {
            in = new ArchiveStreamFactory()
                    .createArchiveInputStream(
                            new BufferedInputStream(
                                    new GzipCompressorInputStream(
                                            new FileInputStream(project.getFileName()))));
        } else {
            in = new ArchiveStreamFactory()
                    .createArchiveInputStream(
                            new BufferedInputStream(
                                    new FileInputStream(project.getFileName())));
        }
        
        // iterate through the Archive, creating folders and extracting files.
        ArchiveEntry entry;
        
        while ( (entry=in.getNextEntry()) != null ) {
            // folders MAY NOT be ABSOLUTE, nor be "above" the PARENT FOLDER
            if (entry.isDirectory()) {
                // we cannot go "above" the PARENT FOLDER
                if (!base_file_path.resolve(entry.getName()).startsWith(base_file_path))
                    throw new IOException ("Illegal relative or absolute path in archive.");
                // create folder
                if (!base_file_path.resolve(entry.getName()).toFile().mkdirs())
                    throw new IOException ("Unable to create folder: " + entry.getName());
            } else {
                // extract file
                Files.copy(in, base_file_path.resolve(entry.getName()));
            }
        }
        
        // Create a bare GIT repository from the archive content just unpacked
        try {
            // init the Git repository
            Git git = Git
                    .init()
                    .setDirectory(base_file_path.toFile())
                    .call();
            // add the entire contents of the folder
            git.add().addFilepattern(".").call();
            // commit it
            git.commit().setMessage("Initial import").call();
            // finished
            git.close();
        } catch ( GitAPIException e ) {
            log.warn("Git failed: " + e.getMessage());
            throw new IOException ("Repository import failure: " + e.getMessage());
        }
        
        // send back an IMPORT URL for local git repository import to GITLAB
        return GITLAB_LOCALURL + base_file_path.toString();
    }
}
