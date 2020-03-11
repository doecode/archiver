/*
 */
package gov.osti.archiver.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gov.osti.archiver.entity.ArchiveRequest;
import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import gov.osti.archiver.util.Extractor;
import gov.osti.archiver.Maintainer;
import gov.osti.archiver.LaborCalculator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;
import javax.persistence.TypedQuery;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.compress.archivers.ArchiveException;
import org.eclipse.jgit.util.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;

/**
 * Archiver web services. 
 * 
 * GET /project/{codeId} -- retrieve existing Project if possible
 * POST /project -- store and archive a new Project
 * 
 * @author ensornl
 */
@Path("project")
public class ArchiveResource {
    // logger
    private static Logger log = LoggerFactory.getLogger(ArchiveResource.class);
    // base filesystem path to save information into
    private static String FILE_BASEDIR = ServletContextListener.getConfigurationProperty("file.archive");

    // get the SITE URL base for applications
    private static String SITE_URL = ServletContextListener.getConfigurationProperty("site.url");

    // EMAIL info
    private static final String EMAIL_HOST = ServletContextListener.getConfigurationProperty("email.host");
    private static final String EMAIL_FROM = ServletContextListener.getConfigurationProperty("email.from");

    // get File Approval info
    private static String FA_EMAIL = ServletContextListener.getConfigurationProperty("file.approval.email");
    
    // XML/JSON mapper reference
    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setTimeZone(TimeZone.getDefault());
    
    /**
     * Creates a new instance of ArchiveResource
     */
    public ArchiveResource() {
    }
    
    /**
     * Attempt to look up a given Project by its CODE ID.
     * 
     * Response Code:
     * 200 - record found, returns JSON
     * 404 - record not on file
     * 
     * @param projectId the CODE ID to look for
     * @return JSON of the Project if found
     */
    @GET
    @Path ("{projectId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response find(@PathParam ("projectId") Long projectId) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            Project project = em.find(Project.class, projectId);
            
            // not found? say so.
            if (null==project)
                return ErrorResponse
                        .notFound("Indicated Project not on file.")
                        .build();
            
            // found it, return as JSON
            return Response
                    .status(Response.Status.OK)
                    .entity(project.toJson())
                    .build();
        } finally {
            em.close();
        }
    }
    
    /**
     * Query for any PROJECT mapping to the indicated CODE ID from DOECODE.
     * 
     * Returns:
     * 200 - JSON containing referencing project(s)
     * 404 - No project found mapping to the indiciated CODE ID
     * 500 - JSON or database processing error
     * 
     * @param codeId the CODE ID value to search for
     * @return a Response containing the information
     */
    @GET
    @Path ("/codeid/{codeId}")
    @Produces (MediaType.APPLICATION_JSON)
    public Response findByCodeId(@PathParam ("codeId") Long codeId) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            // create a Set containing the CODE ID
            Set<Long> ids = new HashSet<>();
            ids.add(codeId);
            // Query it up
            TypedQuery<Project> query = em.createNamedQuery("Project.findByCodeId", Project.class)
                    .setParameter("ids", ids);
            List<Project> results = query.getResultList();
            
            // if no records match, return a Not Found response; otherwise, JSON
            return ( 0==results.size() ) ?
                    ErrorResponse
                    .notFound("Code ID not found.")
                    .build() :
                    Response
                    .ok()
                    .entity(mapper.writeValueAsString(results))
                    .build();
        } catch ( IOException e ) {
            log.warn("JSON parser error", e);
            return ErrorResponse
                    .internalServerError("JSON mapping error")
                    .build();
        } finally {
            em.close();
        }
    }
    
    /**
     * Query for most recent PROJECT mapping to the indicated CODE ID from DOECODE.
     * 
     * Returns:
     * 200 - JSON containing latest project
     * 404 - No project found mapping to the indiciated CODE ID
     * 500 - JSON or database processing error
     * 
     * @param codeId the CODE ID value to search for
     * @return a Response containing the information
     */
    @GET
    @Path ("/latest/{codeId}")
    @Produces (MediaType.APPLICATION_JSON)
    public Response findLatestByCodeId(@PathParam ("codeId") Long codeId, @QueryParam("fileName") String fileName, @QueryParam("repositoryLink") String repositoryLink) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            // create a Set containing the CODE ID
            Set<Long> ids = new HashSet<>();
            ids.add(codeId);
            
            Project p = null;

            List<Project.RepositoryType> repositoryTypes = new ArrayList<>();
            repositoryTypes.add(Project.RepositoryType.Container);

            boolean hasTarget = !(fileName == null && repositoryLink == null);
            String querySuffix = hasTarget ? "ForTarget" : "";
            String errorMessage = "Code ID contains no archived Project" + (hasTarget ? " for expected target" : "") + ".";

            String targetFile = StringUtils.isEmptyOrNull(fileName) ? "" : File.separator + fileName;
            String targetRepo = StringUtils.isEmptyOrNull(repositoryLink) ? "" : repositoryLink;

            // if no target is expected, just grab the latest, otherwise search for target
            TypedQuery<Project> query = em.createNamedQuery("Project.findLatestByCodeId" + querySuffix, Project.class)
                    .setParameter("ids", ids)
                    .setParameter("types", repositoryTypes);

            if (hasTarget)
                query.setParameter("file", targetFile)
                    .setParameter("repo", targetRepo);
            
            List<Project> results = query.setMaxResults(1).getResultList();

            // if no records match, return a Not Found response; otherwise, JSON
            if (0==results.size())
                return ErrorResponse
                .notFound(errorMessage)
                .build();

            // get latest project
            p = results.get(0);

            // get the FILE information
            ObjectNode info = mapper.createObjectNode();

            info.put("project_id", p.getProjectId());
            info.put("status", p.getStatus().toString());
            info.set("code_ids", mapper.valueToTree(p.getCodeIds()));
            info.put("repository_type", p.getRepositoryType().toString());
            info.put("repository_link", p.getRepositoryLink());
            info.put("file_path", p.getFileName());
            info.put("cache", p.getCacheFolder());
            
            JsonNode cloc = null;
            try {
                cloc = mapper.readTree(p.getLaborCloc());
            } catch (Exception e){
                cloc = null;
            }
            info.set("cloc", cloc);
            info.put("sloc", p.getLaborSloc());
            info.put("effort", p.getLaborEffort());
            info.put("labor_hours", p.getLaborHours());
            
            return Response
            .ok()
            .entity(mapper.writeValueAsString(info))
            .build();

        } catch ( IOException e ) {
            log.warn("JSON parser error", e);
            return ErrorResponse
                    .internalServerError("JSON mapping error")
                    .build();
        } finally {
            em.close();
        }
    }
    
    /**
     * Calculate Labor Hours for the latest PROJECT mapping to the indicated CODE ID from DOECODE.
     * 
     * Returns:
     * 200 - JSON containing referenced project
     * 404 - No project found mapping to the indiciated CODE ID
     * 500 - JSON or database processing error
     * 
     * @param codeId the CODE ID value to search for
     * @return a Response containing the information
     */
    @GET
    @Path ("/calculatelabor/{codeId}")
    @Produces (MediaType.APPLICATION_JSON)
    public Response calculateLaborByCodeId(@PathParam ("codeId") Long codeId, @QueryParam("fileName") String fileName, @QueryParam("repositoryLink") String repositoryLink) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            // create a Set containing the CODE ID
            Set<Long> ids = new HashSet<>();
            ids.add(codeId);
            
            Project p = null;

            List<Project.RepositoryType> repositoryTypes = new ArrayList<>();
            repositoryTypes.add(Project.RepositoryType.Container);

            boolean hasTarget = !(fileName == null && repositoryLink == null);
            String querySuffix = hasTarget ? "ForTarget" : "";
            String errorMessage = "Code ID contains no archived Project" + (hasTarget ? " for expected target" : "") + ".";

            String targetFile = StringUtils.isEmptyOrNull(fileName) ? "" : File.separator + fileName;
            String targetRepo = StringUtils.isEmptyOrNull(repositoryLink) ? "" : repositoryLink;

            // if no target is expected, just grab the latest, otherwise search for target
            TypedQuery<Project> query = em.createNamedQuery("Project.findLatestByCodeId" + querySuffix, Project.class)
                    .setParameter("ids", ids)
                    .setParameter("types", repositoryTypes);

            if (hasTarget)
                query.setParameter("file", targetFile)
                    .setParameter("repo", targetRepo);
            
            List<Project> results = query.setMaxResults(1).getResultList();

            // if no records match, return a Not Found response; otherwise, JSON
            if (0==results.size())
                return ErrorResponse
                .notFound(errorMessage)
                .build();

            // get latest project
            p = results.get(0);

            // if status is not Complete, return a Not Found response
            if (!Project.Status.Complete.equals(p.getStatus()))
                return ErrorResponse
                .notFound("Unable to calculate Labor Hours for Project that is not Complete!")
                .build();

            // if cache is not provided, return a Not Found response
            if (StringUtils.isEmptyOrNull(p.getCacheFolder()))
                return ErrorResponse
                .notFound("Unable to calculate Labor Hours if Cache Folder is not provided!")
                .build();

            // get the FILE information
            ObjectNode info = mapper.createObjectNode();

            info.put("project_id", p.getProjectId());
            info.set("code_ids", mapper.valueToTree(p.getCodeIds()));
            info.put("repository_type", p.getRepositoryType().toString());
            info.put("repository_link", p.getRepositoryLink());
            info.put("file_path", p.getFileName());
            info.put("cache", p.getCacheFolder());

            // calculate Labor Hours information, and save
            em.getTransaction().begin();
            p.calculateLaborHours();
            em.getTransaction().commit();
 
            // return info about the Labor Hour calculations
            JsonNode cloc;
            try {
                cloc = mapper.readTree(p.getLaborCloc());
            } catch (Exception e){
                cloc = null;
            }
            info.set("cloc", cloc);
            info.put("sloc", p.getLaborSloc());
            info.put("effort", p.getLaborEffort());
            info.put("labor_hours", p.getLaborHours());
            
                        
            return Response
            .ok()
            .entity(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(info))
            .build();

        } catch ( IOException e ) {
            log.warn("JSON parser error", e);
            return ErrorResponse
                    .internalServerError("JSON mapping error")
                    .build();
        } catch ( RollbackException | IllegalStateException e ) {
            log.warn("Labor STORAGE error", e);
            return ErrorResponse
                    .internalServerError("Labor STORAGE error")
                    .build();
        } finally {
            em.close();
        }
    }
    
    /**
     * Obtain a listing of any Projects in a given status condition.
     * 
     * @param status the Status code to use ("Error", "Pending", or "Complete")
     * @return JSON containing an array of Project records in status, if any.
     */
    @GET
    @Path ("/status/{status}")
    @Produces (MediaType.APPLICATION_JSON)
    public Response findByStatus(@PathParam("status") String status) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            TypedQuery<Project> query = em.createNamedQuery("Project.findByStatus", Project.class)
                    .setParameter("status", Project.Status.valueOf(status));
            List<Project> results = query.getResultList();
            
            return Response
                    .ok()
                    .entity(mapper.writeValueAsString(results))
                    .build();
        } catch ( IllegalArgumentException e ) {
            return ErrorResponse
                    .badRequest("Unknown status type " + status)
                    .build();
        } catch ( IOException e ) {
            log.warn("JSON Error", e);
            return ErrorResponse
                    .internalServerError("JSON processing error.")
                    .build();
        } finally {
            em.close();
        }
    }
    
    /**
     * Perform ARCHIVING of a given PROJECT.
     * 
     * Passed-in JSON should contain a CODE_ID from DOECODE, a PROJECT NAME,
     * PROJECT DESCRIPTION, and one of REPOSITORY LINK or FILE NAME.  If the former,
     * directly import into the archive; if the latter, extra steps need to be
     * taken to extract the file (assumed to be a compressed archive) into a holding
     * area, a git repository made of its content.
     * 
     * Procedure:
     * 1. look up the REPOSITORY LINK value if supplied.
     * 2. if found, add this CODE ID to its mapping, done.
     * 3. if not, create a PROJECT and attempt to git-import the content to cache. (separate thread)
     * 
     * File uploads are considered to be new entities each time, so a new PROJECT 
     * is always created.
     * 
     * Response Codes:
     * 200 - Project already archived (no changes to repository or file), returns JSON
     * 201 - Created a new PROJECT and called the background thread to import
     * 400 - Missing required field(s) for processing, or unrecognized archive file format
     * 500 - unable to read the JSON
     * 
     * @param json the JSON of the PROJECT to archive
     * @param file (optional) a file, assumed to be a compressed archive, containing the source code of the project
     * @param fileInfo (optional) if present, the filename disposition of the file
     * @param container (optional) a file, assumed to be a container image for the project
     * @param containerInfo (optional) if present, the filename disposition of the container
     * @return 
     */
    private Response doArchive(String json, InputStream file, FormDataContentDisposition fileInfo
            , InputStream container, FormDataContentDisposition containerInfo) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            ArchiveRequest ar = mapper.readValue(json, ArchiveRequest.class);
            
            // must have a CODE_ID value
            if (null==ar.getCodeId())
                return ErrorResponse
                        .badRequest("Missing required Code ID value.")
                        .build();

            //  construct a new PROJECT
            Project projectContainer = null;
            Project project = new Project();
            project.setRepositoryLink(ar.getRepositoryLink());
            project.addCodeId(ar.getCodeId());

            if (StringUtils.isEmptyOrNull(ar.getRepositoryLink()) && null==file && null==container) {
                return ErrorResponse
                        .badRequest("Missing required parameters.")
                        .build();
            }

            em.getTransaction().begin();

            // do we have a REPOSITORY LINK?
            if (!StringUtils.isEmptyOrNull(ar.getRepositoryLink())) {
                // FORCE protocol if not present
                if (!ar.getRepositoryLink().startsWith("http"))
                    ar.setRepositoryLink("https://" + ar.getRepositoryLink());
                // see if it's ALREADY been cached
                TypedQuery<Project> query = em.createNamedQuery("Project.findByRepositoryLink", Project.class)
                        .setParameter("url", ar.getRepositoryLink());
                
                try {
                    Project p = query.getSingleResult();
                    
                    // may need to add this CODE ID if multiple projects post to this
                    if ( p.addCodeId(ar.getCodeId()) ) {
                        // added one, merge it in
                        em.merge(p);
                        em.getTransaction().commit();
                    }
                    
                    // found it, send it back
                    return Response
                            .ok()
                            .entity(p.toJson())
                            .build();
                } catch ( NoResultException e ) {
                    // this is expected if not on file already, proceed
                }
                // no such thing, go ahead and create a PROJECT to hold this
                project.setStatus(Project.Status.Pending);
                
                em.persist(project); // get the UUID
                
            } else if (null!=file) {
                // we have a FILE to do; create a PROJECT and store it
                em.persist(project); // get us a PROJECT ID
                
                // attempt to store and extract the archive file
                try {
                    String fileName = saveFile(file, project.getProjectId(), fileInfo.getFileName());
                    project.setFileName(fileName);
                    // ensure we can tell what sort of archive we have
                    if (null==Extractor.detectArchiveFormat(fileName))
                        throw new ArchiveException("Invalid or unknown archive format.");
                } catch ( ArchiveException e ) {
                    log.warn("Invalid Archive for " + fileInfo.getFileName() + ": " + e.getMessage());
                    return ErrorResponse
                            .badRequest("Unrecognized archive file type, unsupported format.")
                            .build();
                } catch ( IOException e ) {
                    log.error ("File Upload Failed: " + e.getMessage());
                    return ErrorResponse
                            .internalServerError("File upload operation failed.")
                            .build();
                }
            }

            // handle containers
            if (null!=container) {
                // we have a CONTAINER to do; create a PROJECT and store it
                projectContainer = new Project();
                projectContainer.addCodeId(ar.getCodeId());

                em.persist(projectContainer); // get us a PROJECT ID

                // attempt to store the archive file
                try {
                    String containerName = saveFile(container, projectContainer.getProjectId(), containerInfo.getFileName());
                    projectContainer.setFileName(containerName);
                    // set type here, becauses Containers are never maintained
                    projectContainer.setRepositoryType(Project.RepositoryType.Container);
                } catch ( IOException e ) {
                    log.error ("Container Image Upload Failed: " + e.getMessage());
                    return ErrorResponse
                            .internalServerError("Container upload operation failed.")
                            .build();
                }
            }

            // got this far, we must be ready to call the background thread
            em.getTransaction().commit();

            if (null!=file)
                sendFileUploadNotification(project);

            // fire off the background thread
            ServletContextListener.callArchiver(project);
            if (projectContainer != null)
                ServletContextListener.callArchiver(projectContainer);

            // return 201 with JSON
            return Response
                    .status(Response.Status.CREATED)
                    .entity(project.toJson())
                    .build();
        } catch ( IOException e ) { 
            log.warn("JSON Parser Error: " + e.getMessage());
            return ErrorResponse
                    .internalServerError("JSON parsing error.")
                    .build();
        } catch ( PersistenceException e ) {
            log.warn("Database Error: ",e);
            return ErrorResponse
                    .internalServerError("Database persistence error.")
                    .build();
        } finally {
            if (em.getTransaction().isActive())
                em.getTransaction().rollback();
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
        for (Long c : p.getCodeIds()) {
            if (c > codeId)
                codeId = c;
        }

        // get the FILE information
        ObjectNode info = mapper.createObjectNode();

        String fileName = p.getFileName();
        java.nio.file.Path latestFile = Paths.get(FILE_BASEDIR, String.valueOf(p.getProjectId()), fileName.substring(fileName.lastIndexOf(File.separator) + 1));

        info.put("project_id", p.getProjectId());
        info.set("code_ids", mapper.valueToTree(p.getCodeIds()));
        info.put("file_path", latestFile.toString());

        String fileInfo;
        try {
            fileInfo = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(info);
        } catch (JsonProcessingException e1) {
            fileInfo = "Archiver Error:  Unable to retreive File Info!";
		}

        // send email
        try {
            HtmlEmail email = new HtmlEmail();
            email.setCharset(org.apache.commons.mail.EmailConstants.UTF_8);
            email.setHostName(EMAIL_HOST);

            email.setFrom(EMAIL_FROM);
            email.setSubject("File Upload Notification -- CODE ID: " + codeId);

            email.addTo(FA_EMAIL);

            StringBuilder msg = new StringBuilder();

            msg.append("<html>");
            msg.append("File Upload Approval Required:");

            String approvalLink = SITE_URL + "/approve?code_id=" + codeId;

            msg.append("<p>A new uploaded file needs Approval for CODE ID: <a href=\"")
                .append(approvalLink)
                .append("\">")
                .append(codeId)
                .append("</a></p>");

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
     * Process the maintenance of remote repositories as a background task.
     * 
     * @param command the command to issue; currently only "start" will begin
     * activation.  Any other command will simply return the current status.
     * 
     * @return a Response JSON containing the current status of the background
     * maintenance thread.
     */
    @GET
    @Produces (MediaType.APPLICATION_JSON)
    @Path ("/maintenance/{command}")
    public Response maintain(@PathParam("command") String command) {
        Maintainer maintainer = Maintainer.getInstance();
        
        if ("start".equalsIgnoreCase(command))
            maintainer.start();
        
        return Response
                .ok()
                .entity(mapper
                        .createObjectNode()
                        .put("active", maintainer.isActive())
                        .put("total", maintainer.getProjectCount())
                        .put("processed", maintainer.getFinishedCount())
                        .toString())
                .build();
    }
    
    /**
     * Process the labor hours of remote repositories as a background task.
     * 
     * @param command the command to issue; currently only "start" will begin
     * activation.  Any other command will simply return the current status.
     * 
     * @return a Response JSON containing the current status of the background
     * labor hours thread.
     */
    @GET
    @Produces (MediaType.APPLICATION_JSON)
    @Path ("/laborhours/{command}")
    public Response calculateLabor(@PathParam("command") String command) {
        LaborCalculator laborCalculator = LaborCalculator.getInstance();
        
        if ("start".equalsIgnoreCase(command))
            laborCalculator.start();
        
        return Response
                .ok()
                .entity(mapper
                        .createObjectNode()
                        .put("active", laborCalculator.isActive())
                        .put("total", laborCalculator.getProjectCount())
                        .put("processed", laborCalculator.getFinishedCount())
                        .toString())
                .build();
    }
    
    /**
     * POST a Project to be archived.  
     * 
     * Response Code:
     * 201 -- created new Project, OK
     * 400 -- no CODE ID supplied
     * 500 -- unable to parse incoming JSON
     * 
     * @param json the JSON of the Project to access
     * @param file (for multi-part upload requests) archive file containing
     * @param fileInfo (multi-part uploads) file disposition information
     * @param container the uploaded container image to archive
     * @param containerInfo disposition information for the container image name
     * @param sendFileNotification flag to determine if file upload notification should be sent
     * source project
     * @return 
     */
    @POST
    @Consumes (MediaType.MULTIPART_FORM_DATA)
    @Produces (MediaType.APPLICATION_JSON)
    public Response archive(
            @FormDataParam("project") String json,
            @FormDataParam("file") InputStream file,
            @FormDataParam("file") FormDataContentDisposition fileInfo,
            @FormDataParam("container") InputStream container,
            @FormDataParam("container") FormDataContentDisposition containerInfo) {
        // call the ARCHIVE process to do the work
        return doArchive(json, file, fileInfo, container, containerInfo);
    }
    
    /**
     * POST a Project to archive in JSON format.  JSON should contain at least
     * a CODE_ID and PROJECT_NAME value, with a REPOSITORY_LINK.
     * 
     * Response Codes:
     * 200 - OK, project is already on file
     * 201 - CREATED, new project created and logged
     * 400 - BAD REQUEST, missing required CODE_ID to map Project to
     * 500 - INTERNAL SERVER ERROR, unable to process JSON request
     * 
     * @param json the JSON of the Project to archive
     * @return a Response according to the disposition of the archived Project
     */
    @POST
    @Consumes (MediaType.APPLICATION_JSON)
    @Produces (MediaType.APPLICATION_JSON)
    public Response archive(String json) {
        return doArchive(json, null, null, null, null);
    }
    
    /**
     * Store a given File InputStream to a new base absolute path.
     * @param in the InputStream containing the File
     * @param projectId the CODE ID of the DOECODE Project to associate with
     * @param fileName the base file name to use
     * @throws IOException on IO errors
     * @return the new File name complete path
     */
    private static String saveFile(InputStream in, Long projectId, String fileName) throws IOException {
        // store this file in a designated base path
        java.nio.file.Path destination = Paths.get(FILE_BASEDIR, String.valueOf(projectId), fileName);
        // make the necessary file paths
        Files.createDirectories(destination.getParent());
        // save it
        Files.copy(in, destination);
        
        return destination.toString();
    }
    
    /**
     * Delete a PROJECT'S cache files, including any extracted files, if found.
     * 
     * Should be used as a RESET BUTTON for this Project.  Will do nothing if
     * no files exist to delete.
     * 
     * @param projectId the PROJECT ID to wipe out
     * @throws IOException on file IO errors
     */
    private static void wipeFiles(Long projectId) throws IOException {
        // only do this if FILES EXIST
        if ( !Files.exists(Paths.get(FILE_BASEDIR, String.valueOf(projectId))) )
                return;
        
        // starting at the FILE_BASEDIR + projectId, wipe out the cached files and
        // any folders
        Files.walkFileTree(Paths.get(FILE_BASEDIR, String.valueOf(projectId)), 
                new SimpleFileVisitor<java.nio.file.Path>() {
            @Override
            public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs)
            throws IOException
            {
                // delete this file if present
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(java.nio.file.Path directory, IOException e) throws IOException {
                // if there's no Exception, delete this, otherwise throw it
                if (null==e) {
                    // wipe this directory; should be empty
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                } else {
                    // cannot follow directory, abort
                    throw e;
                }
            }
        });
    }
}
