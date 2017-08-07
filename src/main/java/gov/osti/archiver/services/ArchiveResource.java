/*
 */
package gov.osti.archiver.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
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
     * Response Codes:
     * 200 - PROJECT is already on file, returns its JSON
     * 201 - Created a new PROJECT and called the background thread to import
     * 400 - Missing required field(s) for processing
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
            
            if (null==project.getCodeId())
                return errorResponse(Response.Status.BAD_REQUEST, "Missing required Code ID value.");
            
            // attempt to look up existing Project
            Project p = em.find(Project.class, project.getCodeId());
            
            if (null==p) {
                // not on file, create a new one
                em.getTransaction().begin();
                
                project.setStatus(Project.Status.Pending);
                
                /**
                 * for FILE UPLOADS, need to store in a new filesystem path,
                 * and set the information in the Project.
                 */
                if ( null!=file && null!=fileInfo ) {
                    try {
                        String fileName = saveFile(file, project.getCodeId(), fileInfo.getFileName());
                        project.setFileName(fileName);
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
            } else {
                // found it, return 200 with JSON
                return Response
                        .status(Response.Status.OK)
                        .entity(p.toJson())
                        .build();
            }
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
     * JSON should contain at least code_id, project_name, and repository_link.
     * 
     * Response Code:
     * 200 -- already on file, OK
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
     * POST a Project to archive in JSON format.
     * 
     * Response Codes:
     * 200 - OK, project already on file, return its JSON
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
        // save it
        Files.copy(in, destination);
        
        return destination.toString();
    }
}
