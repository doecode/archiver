/*
 */
package gov.osti.archiver.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import gov.osti.archiver.Archiver;
import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import gov.osti.archiver.util.Extractor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.util.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // XML/JSON mapper reference
    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    /**
     * Create a JSONAPI errors Object.
     */
    protected class ErrorResponse {
        private int status;
        private List<String> errors = new ArrayList<>();

        public ErrorResponse(Response.Status s, String message) {
            status = s.getStatusCode();
            errors.add(message);
        }
        
        public ErrorResponse(Response.Status s, List<String> messages) {
            status = s.getStatusCode();
            errors.addAll(messages);
        }
        
        public void addError(String message) {
            errors.add(message);
        }
        
        public boolean addAll(List<String> messages) {
            return errors.addAll(messages);
        }
        
        @JsonIgnore
        public boolean isEmpty() {
            return errors.isEmpty();
        }
        
        public void setStatus(Response.Status s) {
            status = s.getStatusCode();
        }
        
        /**
         * @return the status
         */
        public int getStatus() {
            return status;
        }

        /**
         * @param status the status to set
         */
        public void setStatus(int status) {
            this.status = status;
        }

        /**
         * @return the errors
         */
        public List<String> getErrors() {
            return errors;
        }

        /**
         * @param errors the errors to set
         */
        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
    }
    
    /**
     * Create a singleton JSONAPI error response.
     * 
     * @param status the HTTP status of the error
     * @param message an error message
     * @return a Response in JSONAPI error format
     */
    protected Response errorResponse(Response.Status status, String message) {
        try {
            return Response
                    .status(status)
                    .entity(mapper.writeValueAsString(new ErrorResponse(status, message)))
                    .build();
        } catch ( JsonProcessingException e ) {
            log.warn("JSON Error: " + e.getMessage());
            return Response
                    .status(status)
                    .entity("Error: " + message)
                    .build();
        }
    }
    
    /**
     * Create an array of error messages in JSONAPI format.
     * 
     * @param status the HTTP status of the error
     * @param messages a set of messages to send
     * @return a Response in JSONAPI error format
     */
    protected Response errorResponse(Response.Status status, List<String> messages) {
        try {
            return Response
                    .status(status)
                    .entity(mapper.writeValueAsString(new ErrorResponse(status, messages)))
                    .build();
        } catch ( JsonProcessingException e ) {
            log.warn("JSON Error: " + e.getMessage());
            return Response
                    .status(status)
                    .entity("Error: " + StringUtils.join(messages, ", "))
                    .build();
        }
    }
    
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
     * @param codeId the CODE ID to look for
     * @return JSON of the Project if found
     */
    @GET
    @Path ("{codeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response find(@PathParam ("codeId") Long codeId) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            Project project = em.find(Project.class, codeId);
            
            // not found? say so.
            if (null==project)
                return errorResponse(Response.Status.NOT_FOUND, "Indicated Project not on file.");
            
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
     * Perform ARCHIVING of a given PROJECT.
     * 
     * Passed-in JSON should contain a CODE_ID from DOECODE, a PROJECT NAME,
     * PROJECT DESCRIPTION, and one of REPOSITORY LINK or FILE NAME.  If the former,
     * directly import into the GitLab archive; if the latter, extra steps need to be
     * taken to extract the file (assumed to be a compressed archive) into a holding
     * area, a git repository made of its content, and import THAT as a local
     * operation into GitLab.
     * 
     * Procedure:
     * 1. attempt to look up the PROJECT based on CODE_ID.
     * 2. if present, DELETE that PROJECT and start anew. (Drop from GitLab also if present there).
     * 3. if not, or DELETED, go ahead and insert a PENDING PROJECT record.
     * 4. hand off to worker thread to do the file operations and import into GitLab.
     * 
     * Response Codes:
     * 200 - Project already archived (no changes to repository or file), returns JSON
     * 201 - Created a new PROJECT and called the background thread to import
     * 400 - Missing required field(s) for processing, or unrecognized archive file format
     * 500 - unable to read the JSON
     * 
     * @param json the JSON of the PROJECT to archive
     * @param file (optional) a file, assumed to be a compressed archive, containing
     * the source code of the project
     * @param fileInfo (optional) if present, the filename disposition of the file
     * @return 
     */
    private Response doArchive(String json, InputStream file, FormDataContentDisposition fileInfo) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            Project project = mapper.readValue(json, Project.class);
            
            // must have a CODE_ID value
            if (null==project.getCodeId())
                return errorResponse(Response.Status.BAD_REQUEST, "Missing required Code ID value.");
            // must have at least a REPOSITORY LINK or FILE to process
            if (null==project.getRepositoryLink() && null==file)
                return errorResponse(Response.Status.BAD_REQUEST, "No repository link or file upload to cache.");
            
            // attempt to look up existing Project
            Project p = em.find(Project.class, project.getCodeId());
            
            /**
             * If the PROJECT is already on file, see if the repository link or
             * file has not changed; if they have not, we're okay (return OK).
             * Note that if a FILE is uploaded, we MUST assume that it's new, so
             * we MUST treat it as if changes happened.
             * 
             * If the PROJECTS aren't equivalent, we must:
             * 1. REMOVE any GitLab associated files if present,
             * 2. WIPE the PROJECT from the database,
             * 3. and WIPE any files that may be uploaded for this project.
             * 4. PROCESS this one as a new entry.
             */
            if (null!=p) {
                // check against what's passed in to see if there were changes
                // NOTE: If previous Project status was ERROR, assume we need to re-do
                if ( StringUtils.equalsIgnoreCase(p.getRepositoryLink(), project.getRepositoryLink()) &&
                     !Project.Status.Error.equals(p.getStatus()) &&
                     null==file ) {
                    // we haven't changed, so just return the DATABASE PROJECT
                    return Response
                            .status(Response.Status.OK)
                            .entity(p.toJson())
                            .build();
                }
                // call GitLab to REMOVE the existing project if needed
                if (null!=p.getProjectId()) {
                    // just call the GitLab API to DELETE existing Project record
                    Archiver.callGitLabDelete(p);
                }
                // REMOVE the Project as well
                em.getTransaction().begin();
                em.remove(p);
                em.getTransaction().commit();
                // WIPE OUT any files we may have uploaded
                wipeFiles(p.getCodeId());
            }
            
            // start the persistence transaction
            em.getTransaction().begin();
            
            // now we need to INSERT a new PROJECT
            project.setStatus(Project.Status.Pending);
                
            /**
             * for FILE UPLOADS, need to store in a new filesystem path,
             * and set the information in the Project.
             */
            if ( null!=file && null!=fileInfo ) {
                try {
                    String fileName = saveFile(file, project.getCodeId(), fileInfo.getFileName());
                    project.setFileName(fileName);
                    
                    try {
                        if (null==Extractor.detectArchiveFormat(fileName))
                            throw new ArchiveException ("Invalid or unknown archive format.");
                    } catch ( ArchiveException e ) {
                        log.warn("Invalid Archive for " + fileName + ": " + e.getMessage());
                        return errorResponse(Response.Status.BAD_REQUEST, "Unrecognized archive file type, unsupported format.");
                    }
                } catch ( IOException e ) {
                    log.error ("File Upload Failed: " + e.getMessage());
                    return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "File upload operation failed.");
                }
            }

            em.persist(project);

            em.getTransaction().commit();

            // fire off the background thread
            ServletContextListener.callArchiver(project);

            // return 201 with JSON
            return Response
                    .status(Response.Status.CREATED)
                    .entity(project.toJson())
                    .build();
        } catch ( IOException e ) { 
            log.warn("JSON Parser Error: " + e.getMessage());
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "JSON parsing error.");
        } finally {
            em.close();
        }
    }
    
    /**
     * POST a Project to be archived.  If the CODE ID is already on file, does 
     * nothing.  If not, create an initial Project entity in the database, and
     * spin off an archiving Thread to handle import into GitLab.
     * 
     * Note that this ALWAYS persists a new PROJECT, as we cannot determine
     * if the file is the same or not as previously uploaded.
     * 
     * Response Code:
     * 201 -- created new Project, OK
     * 400 -- no CODE ID supplied
     * 500 -- unable to parse incoming JSON
     * 
     * @param json the JSON of the Project to access
     * @param file (for multi-part upload requests) archive file containing
     * @param fileInfo (multi-part uploads) file disposition information
     * source project
     * @return 
     */
    @POST
    @Consumes (MediaType.MULTIPART_FORM_DATA)
    @Produces (MediaType.APPLICATION_JSON)
    public Response archive(
            @FormDataParam("project") FormDataBodyPart json,
            @FormDataParam("file") InputStream file,
            @FormDataParam("file") FormDataContentDisposition fileInfo) {
        // call the ARCHIVE process to do the work
        return doArchive(json.getValue(), file, fileInfo);
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
        return doArchive(json, null, null);
    }
    
    /**
     * Store a given File InputStream to a new base absolute path.
     * @param in the InputStream containing the File
     * @param codeId the CODE ID of the DOECODE Project to associate with
     * @param fileName the base file name to use
     * @throws IOException on IO errors
     * @return the new File name complete path
     */
    private static String saveFile(InputStream in, Long codeId, String fileName) throws IOException {
        // store this file in a designated base path
        java.nio.file.Path destination = Paths.get(FILE_BASEDIR, String.valueOf(codeId), fileName);
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
     * @param codeId the CODE ID to wipe files for
     * @throws IOException on file IO errors
     */
    private static void wipeFiles(Long codeId) throws IOException {
        // only do this if FILES EXIST
        if ( !Files.exists(Paths.get(FILE_BASEDIR, String.valueOf(codeId))) )
                return;
        
        // starting at the FILE_BASEDIR + codeId, wipe out the cached files and
        // any folders
        Files.walkFileTree(Paths.get(FILE_BASEDIR, String.valueOf(codeId)), 
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
