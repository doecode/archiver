/*
 */
package gov.osti.archiver;

import gov.osti.archiver.entity.Project;
import gov.osti.archiver.entity.ProjectXref;
import gov.osti.archiver.entity.Project.RepositoryType;
import gov.osti.archiver.listener.ServletContextListener;
import gov.osti.archiver.util.Extractor;
import gov.osti.archiver.util.GitRepository;
import gov.osti.archiver.util.HttpUtil;
import gov.osti.archiver.util.SubversionRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TimeZone;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.compress.archivers.ArchiveException;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;

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
    private static String FILE_LIMITED_BASEDIR = ServletContextListener.getConfigurationProperty("file.limited.archive");

    // get the SITE URL base for applications
    private static String SITE_URL = ServletContextListener.getConfigurationProperty("site.url");

    // EMAIL info
    private static final String EMAIL_HOST = ServletContextListener.getConfigurationProperty("email.host");
    private static final String EMAIL_FROM = ServletContextListener.getConfigurationProperty("email.from");

    // get File Approval info
    private static String FA_EMAIL = ServletContextListener.getConfigurationProperty("file.approval.email");
    
    // get Project Deletion info
    private static String PD_EMAIL = ServletContextListener.getConfigurationProperty("project.deletion.email");
    
    // XML/JSON mapper reference
    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setTimeZone(TimeZone.getDefault());

    // Tagged Release
    private static final String TR_ARCHIVE_EXT = ".tar.gz";

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
     * For TAGGED RELEASES:
     * 1. download zip
     * 2. unpack into a folder
     * 3. create a git repository bare on it
     * 4. done
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

            boolean isLimited = project.getIsLimited();
            String targetBaseDir = isLimited ? FILE_LIMITED_BASEDIR : FILE_BASEDIR;
            
            // start a data transaction
            em.getTransaction().begin();

            // if project is a Container, set cache folder, nothing else to do
            if (Project.RepositoryType.Container.equals(project.getRepositoryType())) {
                Path path = Paths
                        .get(targetBaseDir,
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
                    if (GitRepository.isTaggedRelease(project.getRepositoryLink())) {
                        try {
                            p.setRepositoryType(Project.RepositoryType.TaggedRelease);
                            project.setSendFileNotification(true);

                            // download the tagged release
                            java.nio.file.Path destination = Paths.get(FILE_BASEDIR, String.valueOf(project.getProjectId()), String.valueOf(project.getProjectId()) + TR_ARCHIVE_EXT);
                            HttpUtil.downloadTaggedRelease(GitRepository.getTagDownloadUrl(project.getRepositoryLink()), destination);
                            
                            // unzip the tagged release
                            project.setFileName(destination.toString());
                            p.setCacheFolder(Extractor.uncompressArchive(project));
                        } catch ( IOException | ArchiveException e ) {
                            log.warn("Tagged Release extraction error: "+ e.getMessage());
                            p.setStatus(Project.Status.Error);
                            p.setStatusMessage("Tagged Release Error: " + e.getMessage());
                            em.persist(p);
                            em.getTransaction().commit();
                            return;
                        }
                    }
                    else {
                        // set up a CACHE FOLDER and CREATE if necessary
                        try {
                            Path path = Paths
                                    .get(targetBaseDir,
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

            // send notifications
            if (project.getSendFileNotification()) {
                p.setLastEditor(project.getLastEditor());
                p.setIsLimited(project.getIsLimited());
                sendFileUploadNotification(p);
            }
        } finally {
            // dispose of the EntityManager
            em.close();
        }
    }

    /**
     * Send a File Upload approval email notification on ANNOUNCEMENT of DOE CODE records with a file.
     *
     * @param p the METADATA to send notification for
     */
    private static void sendFileUploadNotification(Project p) {
        // if HOST or MD or PROJECT MANAGER NAME isn't set, cannot send
        if (StringUtils.isEmptyOrNull(EMAIL_HOST) ||
            StringUtils.isEmptyOrNull(EMAIL_FROM) ||
            null == p ||
            StringUtils.isEmptyOrNull(FA_EMAIL))
            return;

        // get latest CodeId for Project
        Long codeId = (long) -1;
        for (ProjectXref c : p.getCodeIds()) {
            if (c.getCodeId() > codeId)
                codeId = c.getCodeId();
        }

        // get the FILE information
        ObjectNode info = mapper.createObjectNode();

        boolean isLimited = p.getIsLimited();
        String targetBaseDir = isLimited ? FILE_LIMITED_BASEDIR : FILE_BASEDIR;

        String fileName = RepositoryType.TaggedRelease.equals(p.getRepositoryType()) ? String.valueOf(p.getProjectId()) + TR_ARCHIVE_EXT : p.getFileName();
        java.nio.file.Path latestFile = Paths.get(targetBaseDir, String.valueOf(p.getProjectId()), fileName.substring(fileName.lastIndexOf(File.separator) + 1));

        info.put("project_id", p.getProjectId());
        info.set("code_ids", mapper.valueToTree(p.getCodeIds()));
        info.put("file_path", latestFile.toString());

        String fileInfo;
        try {
            fileInfo = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(info);
        } catch (JsonProcessingException e1) {
            fileInfo = "Archiver Error:  Unable to retreive File Info!";
		}

        //get editor info
        String lastEditor = "Unknown";
        if (!StringUtils.isEmptyOrNull(p.getLastEditor())) {
            lastEditor = p.getLastEditor();
        }

        // send email
        try {
            HtmlEmail email = new HtmlEmail();
            email.setCharset(org.apache.commons.mail.EmailConstants.UTF_8);
            email.setHostName(EMAIL_HOST);

            email.setFrom(EMAIL_FROM);
            email.setSubject("File Upload Notification -- CODE ID: " + codeId);

            String[] toList = FA_EMAIL.split(", ?");
            for (String to : toList) 
                email.addTo(to);

            StringBuilder msg = new StringBuilder();

            msg.append("<html>");
            msg.append("File Upload Approval Required:");

            String approvalLink = SITE_URL + "/approve?code_id=" + codeId;

            msg.append("<p>A new uploaded file needs Approval for CODE ID: <a href=\"")
                .append(approvalLink)
                .append("\">")
                .append(codeId)
                .append("</a></p>");

            msg.append("<p>Last edited by: ")
                .append(lastEditor)
                .append("</p>");

            msg.append("<pre>"+fileInfo+"</pre>");
 
            msg.append("</html>");

            email.setHtmlMsg(msg.toString());

            email.send();
        } catch ( EmailException e ) {
            log.error("Unable to send File Upload notification to " + FA_EMAIL + " for #" + codeId);
            log.error("Message: " + e.getMessage());
        }
    }

    /**
     * Send a Project Deletion email notification on DELETION of DOE CODE records.
     *
     * @param p the METADATA to send notification for
     */
    public static void sendProjectDeletionNotification(ObjectNode data) {
        // if HOST or MD or PROJECT MANAGER NAME isn't set, cannot send
        if (StringUtils.isEmptyOrNull(EMAIL_HOST) ||
            StringUtils.isEmptyOrNull(EMAIL_FROM) ||
            null == data ||
            StringUtils.isEmptyOrNull(PD_EMAIL))
            return;

        // get info
        Long codeId = data.get("code_id").asLong();
        Long projectsDeleted = data.get("projects_deleted").asLong();
        String user = data.get("deleted_by").asText();

        ObjectNode minInfo = mapper.createObjectNode();
        minInfo.put("code_id", codeId);
        minInfo.put("projects_deleted", projectsDeleted);

        List<JsonNode> completed = new ArrayList<>();
        data.path("removed").forEach(completed::add);
        int removedCount = completed.size();
        String removedInfo;
        try {
            removedInfo = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(completed);
        } catch (JsonProcessingException e1) {
            removedInfo = "Archiver Delete Error:  Unable to retreive Removed Info!";
		}

        List<JsonNode> failed = new ArrayList<>();
        data.path("failed").forEach(failed::add);
        int failedCount = failed.size();
        String failedInfo;
        try {
            failedInfo = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(failed);
        } catch (JsonProcessingException e1) {
            failedInfo = "Archiver Delete Error:  Unable to retreive Failed Info!";
		}

        String fileInfo;
        try {
            fileInfo = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(minInfo);
        } catch (JsonProcessingException e1) {
            fileInfo = "Archiver Delete Error:  Unable to retreive Deletion Info!";
		}

        // send email
        try {
            HtmlEmail email = new HtmlEmail();
            email.setCharset(org.apache.commons.mail.EmailConstants.UTF_8);
            email.setHostName(EMAIL_HOST);

            email.setFrom(EMAIL_FROM);
            email.setSubject("Project Deletion Notification (Archiver) -- CODE ID: " + codeId);

            String[] toList = PD_EMAIL.split(", ?");
            for (String to : toList) 
                email.addTo(to);

            StringBuilder msg = new StringBuilder();

            msg.append("<html>");
            msg.append("Project Deletion Notification:");

            msg.append("<p>Deleted by: ")
                .append(user)
                .append("</p>");

            msg.append("<pre>"+fileInfo+"</pre>");
            
            if (failedCount > 0)
                msg.append("<BR><BR><span style='color:red'>Failed Removals:</span><BR><pre>"+failedInfo+"</pre>");
            
            if (removedCount > 0)
                msg.append("<BR><BR>Completed Removals:<BR><pre>"+removedInfo+"</pre>");
 
            msg.append("</html>");

            email.setHtmlMsg(msg.toString());

            email.send();
        } catch ( EmailException e ) {
            log.error("Unable to send Project Deletion notification to " + PD_EMAIL + " for #" + codeId);
            log.error("Message: " + e.getMessage());
        }
    }
}
