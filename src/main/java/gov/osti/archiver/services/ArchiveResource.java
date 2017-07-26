/*
 */
package gov.osti.archiver.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import javax.persistence.EntityManager;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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
    private static String FILE_BASEDIR = ServletContextListener.getConfigurationProperty("file.basedir");

    // XML/JSON mapper reference
    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
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
                return Response
                        .status(Response.Status.NOT_FOUND)
                        .entity("Indicated Project not on file.")
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
     * POST a Project to be archived.  If the CODE ID is already on file, does 
     * nothing.  If not, create an initial Project entity in the database, and
     * spin off an archiving Thread to handle import into GitLab.
     * 
     * JSON should contain at least code_id, project_name, and repository_link.
     * 
     * Response Code:
     * 200 -- already on file, OK
     * 201 -- created new Project, OK
     * 
     * @param code_id the DOECode CODE ID reference
     * @param project_name the PROJECT NAME 
     * @param project_description the PROJECT DESCRIPTION (optional)
     * @param repository_link the REPOSITORY LINK for cloning
     * @param file (for multi-part upload requests) archive file containing
     * @param fileInfo (multi-part uploads) file disposition information
     * source project
     * @return 
     */
    @POST
    @Consumes ({MediaType.APPLICATION_JSON, MediaType.MULTIPART_FORM_DATA})
    @Produces (MediaType.APPLICATION_JSON)
    public Response archive(
            @QueryParam ("code_id") Long code_id, 
            @QueryParam("project_name") String project_name,
            @QueryParam("project_description") String project_description,
            @QueryParam("repository_link") String repository_link,
            @FormDataParam("file") InputStream file,
            @FormDataParam("file") FormDataContentDisposition fileInfo) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            Project project = em.find(Project.class, code_id);
            
            if (null==project) {
                em.getTransaction().begin();
                
                // not on file, need to create it
                project = new Project();
                project.setCodeId(code_id);
                project.setProjectDescription(project_description);
                project.setProjectName(project_name);
                project.setRepositoryLink(repository_link);
                project.setStatus(Project.Status.Pending);
                
                /**
                 * for FILE UPLOADS, need to store in a new filesystem path,
                 * and set the information in the Project.
                 */
                if ( null!=file && null!=fileInfo ) {
                    try {
                        String fileName = saveFile(file, fileInfo.getFileName());
                        project.setFileName(fileName);
                    } catch ( IOException e ) {
                        log.error ("File Upload Failed: " + e.getMessage());
                        return Response
                                .status(Response.Status.HTTP_VERSION_NOT_SUPPORTED)
                                .entity("File upload operation failed.")
                                .build();
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
                        .entity(project.toJson())
                        .build();
            }
        } finally {
            em.close();
        }
    }
    
    /**
     * Store a given File InputStream to a new base absolute path.
     * @param in the InputStream containing the File
     * @param fileName the base file name to use
     * @throws IOException on IO errors
     * @return the new File name complete path
     */
    private static String saveFile(InputStream in, String fileName) throws IOException {
        // store this file in a designated base path
        java.nio.file.Path destination = FileSystems.getDefault().getPath(FILE_BASEDIR + File.separator + fileName);
        // save it
        Files.copy(in, destination);
        
        return destination.toString();
    }
}
