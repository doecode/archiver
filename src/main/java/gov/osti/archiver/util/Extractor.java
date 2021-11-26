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
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
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
    private static String FILE_LIMITED_BASEDIR = ServletContextListener.getConfigurationProperty("file.limited.archive");
    
    /**
     * Attempt to open an ArchiveInputStream based on the file_name specified.
     * 
     * @param file_name ABSOLUTE file system path to the file to open
     * @return an ArchiveInputStream if possible, or null if error or invalid
     * @throws ArchiveException on archiver errors, or unable to identify type
     * @throws IOException on file IO errors
     */
    public static ArchiveInputStream openArchiveStream(String file_name) throws ArchiveException, IOException {
        ArchiveInputStream in;

        if (null==file_name)
            return null;

        if (file_name.toLowerCase().endsWith(".gz") || file_name.toLowerCase().endsWith(".tgz")) {
            in = new ArchiveStreamFactory()
                    .createArchiveInputStream(
                            new BufferedInputStream(
                                    new GzipCompressorInputStream(
                                            new FileInputStream(file_name))));
        }
        else if (file_name.toLowerCase().endsWith(".bz2")) {
            in = new ArchiveStreamFactory()
                    .createArchiveInputStream(
                            new BufferedInputStream(
                                    new BZip2CompressorInputStream(
                                            new FileInputStream(file_name))));
        }
        else {
            in = new ArchiveStreamFactory()
                    .createArchiveInputStream(
                            new BufferedInputStream(new FileInputStream(file_name)));
        }
        return in;
    }

    /**
     * Attempt to figure out what the file is based on its file name.
     * 
     * @param file_name the FILE NAME of the archive file
     * @return a String identifying the underlying type of archive if possible, or null if not; should be one of
     * "zip", "tar", "ar", "cpio", etc.  Full list in Apache ArchiveStreamFactory constants.
     * 
     * @throws ArchiveException on archiver errors
     * @throws IOException on file IO errors
     */
    public static String detectArchiveFormat(String file_name) throws ArchiveException, IOException {
        String format = null;

        if (null!=file_name) {
            if (file_name.toLowerCase().endsWith("gz")) {
                format = ArchiveStreamFactory.detect(
                        new BufferedInputStream(
                                new GzipCompressorInputStream(
                                        Files.newInputStream(Paths.get(file_name)))));
            }
            else if (file_name.toLowerCase().endsWith("bz2")) {
                format = ArchiveStreamFactory.detect(
                        new BufferedInputStream(
                                new BZip2CompressorInputStream(
                                        Files.newInputStream(Paths.get(file_name)))));
            }
            else
                format = ArchiveStreamFactory.detect(
                        new BufferedInputStream(
                                Files.newInputStream(Paths.get(file_name))));
        }

        return format;
    }

    /**
     * Given a Project with a FileName attached, attempt to uncompress the
     * archive file into a sub-folder.
     * 
     * Filename is considered to be an absolute path; contents will be 
     * uncompressed into a folder based on the configured FILE BASEDIR value and
     * PROJECT ID.  Each PROJECT is considered to be a UNIQUE archive area.
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

        boolean isLimited = project.getIsLimited();
        String targetBaseDir = isLimited ? FILE_LIMITED_BASEDIR : FILE_BASEDIR;
        
        // uncompress the archive into the PROJECT folder
        Path base_file_path = Paths.get(
                targetBaseDir,
                String.valueOf(project.getProjectId()));
        File folder = base_file_path.toFile();
        if (!folder.exists())
            throw new IOException ("Extraction folder does not exist.");
        
        // open the archiver stream
        ArchiveInputStream in = openArchiveStream(project.getFileName());
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
                // might contain a directory reference
                if (!base_file_path.resolve(entry.getName()).startsWith(base_file_path))
                    throw new IOException ("Illegal relative or absolute path in file.");
                // create any intervening file paths necessary if applicable
                File parent = base_file_path.resolve(entry.getName()).toFile().getParentFile();
                if (!parent.exists() && !parent.mkdirs())
                    throw new IOException ("Unable to create folder for file: " + entry.getName());
                // extract file
                Files.copy(in, base_file_path.resolve(entry.getName()));
            }
        }
        
        // send back the file path created
        return base_file_path.toString();
    }
}
